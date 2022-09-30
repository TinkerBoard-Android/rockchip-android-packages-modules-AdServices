/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.Service;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxController;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SharedPreferencesKey;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.webkit.WebView;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import dalvik.system.PathClassLoader;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;

/** Implementation of Sdk Sandbox Service. */
public class SdkSandboxServiceImpl extends Service {

    private static final String TAG = "SdkSandbox";

    // Mapping from sdk name to its holder
    @GuardedBy("mHeldSdk")
    private final Map<String, SandboxedSdkHolder> mHeldSdk = new ArrayMap<>();

    private Injector mInjector;
    private ISdkSandboxService.Stub mBinder;

    static class Injector {

        private final Context mContext;

        Injector(Context context) {
            mContext = context;
        }

        int getCallingUid() {
            return Binder.getCallingUidOrThrow();
        }

        Context getContext() {
            return mContext;
        }

        long getCurrentTime() {
            return System.currentTimeMillis();
        }
    }

    public SdkSandboxServiceImpl() {
    }

    @VisibleForTesting
    SdkSandboxServiceImpl(Injector injector) {
        mInjector = injector;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        mBinder = new SdkSandboxServiceDelegate();
        mInjector = new Injector(getApplicationContext());
    }

    /**
     * Initializes the Sandbox.
     *
     * <p>Sandbox should be initialized before any SDKs are loaded.
     *
     * @param sdkToServiceCallback for initialization of {@link SdkSandboxLocalSingleton}
     */
    public void initialize(ISdkToServiceCallback sdkToServiceCallback) {
        enforceCallerIsSystemServer();
        SdkSandboxLocalSingleton.initInstance(sdkToServiceCallback.asBinder());
    }

    /** Loads SDK. */
    public void loadSdk(
            String callingPackageName,
            ApplicationInfo applicationInfo,
            String sdkName,
            String sdkProviderClassName,
            String sdkCeDataDir,
            String sdkDeDataDir,
            Bundle params,
            ILoadSdkInSandboxCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {
        enforceCallerIsSystemServer();
        final long token = Binder.clearCallingIdentity();
        try {
            loadSdkInternal(
                    callingPackageName,
                    applicationInfo,
                    sdkName,
                    sdkProviderClassName,
                    sdkCeDataDir,
                    sdkDeDataDir,
                    params,
                    callback,
                    sandboxLatencyInfo);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** Unloads SDK. */
    public void unloadSdk(
            String sdkName, IUnloadSdkCallback callback, SandboxLatencyInfo sandboxLatencyInfo) {
        enforceCallerIsSystemServer();
        final long token = Binder.clearCallingIdentity();
        try {
            sandboxLatencyInfo.setTimeSandboxCalledSdk(mInjector.getCurrentTime());
            unloadSdkInternal(sdkName);
            sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.getCurrentTime());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.getCurrentTime());
        try {
            callback.onUnloadSdk(sandboxLatencyInfo);
        } catch (RemoteException ignore) {
            Log.e(TAG, "Could not send onUnloadSdk");
        }
    }

    /** Sync data from client. */
    public void syncDataFromClient(SharedPreferencesUpdate update) {
        enforceCallerIsSystemServer();

        SharedPreferences pref = getClientSharedPreferences();
        SharedPreferences.Editor editor = pref.edit();
        final Bundle data = update.getData();
        for (SharedPreferencesKey keyInUpdate : update.getKeysInUpdate()) {
            updateSharedPreferences(editor, data, keyInUpdate);
        }
        editor.apply();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SharedPreferences getClientSharedPreferences() {
        // TODO(b/248214708): We should retrieve synced data from a separate internal storage
        // directory.
        return mInjector
                .getContext()
                .getSharedPreferences(
                        SdkSandboxController.CLIENT_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private void updateSharedPreferences(
            SharedPreferences.Editor editor, Bundle data, SharedPreferencesKey keyInUpdate) {
        final String key = keyInUpdate.getName();

        if (!data.containsKey(key)) {
            // key was specified but bundle didn't have the key; meaning it has been removed.
            editor.remove(key);
            return;
        }

        final int type = keyInUpdate.getType();
        try {
            switch (type) {
                case SharedPreferencesKey.KEY_TYPE_STRING:
                    editor.putString(key, data.getString(key, ""));
                    break;
                case SharedPreferencesKey.KEY_TYPE_BOOLEAN:
                    editor.putBoolean(key, data.getBoolean(key, false));
                    break;
                case SharedPreferencesKey.KEY_TYPE_INTEGER:
                    editor.putInt(key, data.getInt(key, 0));
                    break;
                case SharedPreferencesKey.KEY_TYPE_FLOAT:
                    editor.putFloat(key, data.getFloat(key, 0.0f));
                    break;
                case SharedPreferencesKey.KEY_TYPE_LONG:
                    editor.putLong(key, data.getLong(key, 0L));
                    break;
                case SharedPreferencesKey.KEY_TYPE_STRING_SET:
                    final ArraySet<String> castedValue =
                            new ArraySet<>(data.getStringArrayList(key));
                    editor.putStringSet(key, castedValue);
                    break;
                default:
                    Log.e(
                            TAG,
                            "Unknown type found in default SharedPreferences for Key: "
                                    + key
                                    + " Type: "
                                    + type);
            }
        } catch (ClassCastException ignore) {
            editor.remove(key);
            // TODO(b/239403323): Once error reporting is supported, we should return error to the
            // user instead.
            Log.e(
                    TAG,
                    "Wrong type found in default SharedPreferences for Key: "
                            + key
                            + " Type: "
                            + type);
        }
    }

    /**
     * Checks if the SDK sandbox is disabled. This will be {@code true} iff the WebView provider is
     * not visible to the sandbox.
     */
    public void isDisabled(ISdkSandboxDisabledCallback callback) {
        enforceCallerIsSystemServer();
        PackageInfo info = WebView.getCurrentWebViewPackage();
        PackageInfo webViewProviderInfo = null;
        boolean isDisabled = false;
        try {
            if (info != null) {
                webViewProviderInfo =
                        mInjector
                                .getContext()
                                .getPackageManager()
                                .getPackageInfo(
                                        info.packageName, PackageManager.PackageInfoFlags.of(0));
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Could not verify if the SDK sandbox should be disabled", e);
            isDisabled = true;
        }
        isDisabled |= webViewProviderInfo == null;
        try {
            callback.onResult(isDisabled);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not call back into ISdkSandboxDisabledCallback", e);
        }
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mInjector.getContext().enforceCallingPermission(android.Manifest.permission.DUMP,
                "Can't dump " + TAG);
        synchronized (mHeldSdk) {
            // TODO(b/211575098): Use IndentingPrintWriter for better formatting
            if (mHeldSdk.isEmpty()) {
                writer.println("mHeldSdk is empty");
            } else {
                writer.print("mHeldSdk size: ");
                writer.println(mHeldSdk.size());
                for (SandboxedSdkHolder sandboxedSdkHolder : mHeldSdk.values()) {
                    sandboxedSdkHolder.dump(writer);
                    writer.println();
                }
            }
        }
    }

    private void enforceCallerIsSystemServer() {
        if (mInjector.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "Only system_server is allowed to call this API, actual calling uid is "
                            + mInjector.getCallingUid());
        }
    }

    private void loadSdkInternal(
            @NonNull String callingPackageName,
            @NonNull ApplicationInfo applicationInfo,
            @NonNull String sdkName,
            @NonNull String sdkProviderClassName,
            @Nullable String sdkCeDataDir,
            @Nullable String sdkDeDataDir,
            @NonNull Bundle params,
            @NonNull ILoadSdkInSandboxCallback callback,
            @NonNull SandboxLatencyInfo sandboxLatencyInfo) {
        synchronized (mHeldSdk) {
            if (mHeldSdk.containsKey(sdkName)) {
                sendLoadError(
                        callback,
                        ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED,
                        "Already loaded sdk for package " + applicationInfo.packageName,
                        sandboxLatencyInfo);
                return;
            }
        }

        ClassLoader loader = getClassLoader(applicationInfo);
        SandboxedSdkHolder sandboxedSdkHolder = new SandboxedSdkHolder();

        // We want to ensure that SandboxedSdkContext.getSystemService() will return different
        // instances for different SandboxedSdkContext contexts, so that different SDKs
        // running in the same sdk sandbox process don't share the same manager instance.
        // Because SandboxedSdkContext is a ContextWrapper, it delegates the getSystemService()
        // call to its base context. If we use an application context here as a base context
        // when creating an instance of SandboxedSdkContext it will mean that all instances of
        // SandboxedSdkContext will return the same manager instances.

        // In order to create per-SandboxedSdkContext instances in getSystemService, each
        // SandboxedSdkContext needs to have use ContextImpl as a base context. The ContextImpl
        // is hidden, so we can't instantiate it directly. However, the
        // createCredentialProtectedStorageContext() will always create a new ContextImpl
        // object, which is why we are using it as a base context when creating an instance of
        // SandboxedSdkContext.
        // TODO(b/242889021): make this detail internal to SandboxedSdkContext
        Context ctx = mInjector.getContext().createCredentialProtectedStorageContext();
        SandboxedSdkContext sandboxedSdkContext =
                new SandboxedSdkContext(
                        ctx,
                        loader,
                        callingPackageName,
                        applicationInfo,
                        sdkName,
                        sdkCeDataDir,
                        sdkDeDataDir);
        sandboxedSdkHolder.init(
                params,
                callback,
                sdkProviderClassName,
                loader,
                sandboxedSdkContext,
                mInjector,
                sandboxLatencyInfo);
        synchronized (mHeldSdk) {
            mHeldSdk.put(sdkName, sandboxedSdkHolder);
        }
    }

    private void unloadSdkInternal(@NonNull String sdkName) {
        synchronized (mHeldSdk) {
            SandboxedSdkHolder sandboxedSdkHolder = mHeldSdk.get(sdkName);
            if (sandboxedSdkHolder != null) {
                sandboxedSdkHolder.unloadSdk();
                mHeldSdk.remove(sdkName);
            }
        }
    }

    private void sendLoadError(
            ILoadSdkInSandboxCallback callback,
            int errorCode,
            String message,
            SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.getCurrentTime());
        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
        try {
            callback.onLoadSdkError(new LoadSdkException(errorCode, message), sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadCodeError");
        }
    }

    private ClassLoader getClassLoader(ApplicationInfo appInfo) {
        final ClassLoader current = getClass().getClassLoader();
        final ClassLoader parent = current != null ? current.getParent() : null;
        return new PathClassLoader(appInfo.sourceDir, parent);
    }

    final class SdkSandboxServiceDelegate extends ISdkSandboxService.Stub {
        @Override
        public void initialize(@NonNull ISdkToServiceCallback sdkToServiceCallback) {
            Objects.requireNonNull(sdkToServiceCallback, "sdkToServiceCallback should not be null");
            SdkSandboxServiceImpl.this.initialize(sdkToServiceCallback);
        }

        @Override
        public void loadSdk(
                @NonNull String callingPackageName,
                @NonNull ApplicationInfo applicationInfo,
                @NonNull String sdkName,
                @NonNull String sdkProviderClassName,
                @Nullable String sdkCeDataDir,
                @Nullable String sdkDeDataDir,
                @NonNull Bundle params,
                @NonNull ILoadSdkInSandboxCallback callback,
                @NonNull SandboxLatencyInfo sandboxLatencyInfo) {
            sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                    mInjector.getCurrentTime());

            Objects.requireNonNull(callingPackageName, "callingPackageName should not be null");
            Objects.requireNonNull(applicationInfo, "applicationInfo should not be null");
            Objects.requireNonNull(sdkName, "sdkName should not be null");
            Objects.requireNonNull(sdkProviderClassName, "sdkProviderClassName should not be null");
            Objects.requireNonNull(params, "params should not be null");
            Objects.requireNonNull(callback, "callback should not be null");
            if (TextUtils.isEmpty(sdkProviderClassName)) {
                throw new IllegalArgumentException("sdkProviderClassName must not be empty");
            }

            SdkSandboxServiceImpl.this.loadSdk(
                    callingPackageName,
                    applicationInfo,
                    sdkName,
                    sdkProviderClassName,
                    sdkCeDataDir,
                    sdkDeDataDir,
                    params,
                    callback,
                    sandboxLatencyInfo);
        }

        @Override
        public void unloadSdk(
                @NonNull String sdkName,
                @NonNull IUnloadSdkCallback callback,
                @NonNull SandboxLatencyInfo sandboxLatencyInfo) {
            Objects.requireNonNull(sandboxLatencyInfo, "sandboxLatencyInfo should not be null");
            sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                    mInjector.getCurrentTime());
            Objects.requireNonNull(sdkName, "sdkName should not be null");
            Objects.requireNonNull(callback, "callback should not be null");
            SdkSandboxServiceImpl.this.unloadSdk(sdkName, callback, sandboxLatencyInfo);
        }

        @Override
        public void syncDataFromClient(@NonNull SharedPreferencesUpdate update) {
            Objects.requireNonNull(update, "update should not be null");
            SdkSandboxServiceImpl.this.syncDataFromClient(update);
        }

        @Override
        public void isDisabled(@NonNull ISdkSandboxDisabledCallback callback) {
            Objects.requireNonNull(callback, "callback should not be null");
            SdkSandboxServiceImpl.this.isDisabled(callback);
        }
    }
}
