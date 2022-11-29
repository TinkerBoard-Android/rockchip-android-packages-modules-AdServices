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

package android.app.sdksandbox.testutils;

import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.android.sdksandbox.IComputeSdkStorageCallback;
import com.android.sdksandbox.ILoadSdkInSandboxCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxDisabledCallback;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.IUnloadSdkCallback;
import com.android.sdksandbox.SandboxLatencyInfo;

import java.util.List;

public class FakeSdkSandboxService extends ISdkSandboxService.Stub {
    private long mTimeSystemServerCalledSandbox = -1;
    private long mTimeSandboxReceivedCallFromSystemServer = -1;
    private long mTimeSandboxCalledSdk = -1;
    private long mTimeSdkCallCompleted = -1;
    private long mTimeSandboxCalledSystemServer = -1;

    private ILoadSdkInSandboxCallback mLoadSdkInSandboxCallback;
    private final ISdkSandboxManagerToSdkSandboxCallback mManagerToSdkCallback;
    private IRequestSurfacePackageFromSdkCallback mRequestSurfacePackageFromSdkCallback = null;
    private IUnloadSdkCallback mUnloadSdkCallback = null;
    private IComputeSdkStorageCallback mComputeSdkStorageCallback = null;

    private boolean mSurfacePackageRequested = false;
    private int mInitializationCount = 0;

    boolean mIsDisabledResponse = false;
    public boolean dieOnLoad = false;

    private SharedPreferencesUpdate mLastSyncUpdate = null;

    public FakeSdkSandboxService() {
        mManagerToSdkCallback = new FakeManagerToSdkCallback();
    }

    public void setTimeValues(
            long timeSystemServerCalledSandbox,
            long timeSandboxReceivedCallFromSystemServer,
            long timeSandboxCalledSdk,
            long timeSdkCallCompleted,
            long timeSandboxCalledSystemServer) {
        mTimeSystemServerCalledSandbox = timeSystemServerCalledSandbox;
        mTimeSandboxReceivedCallFromSystemServer = timeSandboxReceivedCallFromSystemServer;
        mTimeSandboxCalledSdk = timeSandboxCalledSdk;
        mTimeSdkCallCompleted = timeSdkCallCompleted;
        mTimeSandboxCalledSystemServer = timeSandboxCalledSystemServer;
    }

    @Override
    public void initialize(ISdkToServiceCallback sdkToServiceCallback) {
        mInitializationCount++;
    }

    @Override
    public void loadSdk(
            String callingPackageName,
            ApplicationInfo info,
            String sdkName,
            String sdkProviderClass,
            String ceDataDir,
            String deDataDir,
            Bundle params,
            ILoadSdkInSandboxCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo)
            throws DeadObjectException {
        if (dieOnLoad) {
            throw new DeadObjectException();
        }
        mLoadSdkInSandboxCallback = callback;
    }

    @Override
    public void unloadSdk(
            String sdkName, IUnloadSdkCallback callback, SandboxLatencyInfo sandboxLatencyInfo) {
        mUnloadSdkCallback = callback;
    }

    @Override
    public void syncDataFromClient(SharedPreferencesUpdate update) {
        mLastSyncUpdate = update;
    }

    @Override
    public void isDisabled(ISdkSandboxDisabledCallback callback) {
        try {
            callback.onResult(mIsDisabledResponse);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
    }

    @Override
    public void computeSdkStorage(
            List<String> cePackagePaths,
            List<String> dePackagePaths,
            IComputeSdkStorageCallback callback) {
        mComputeSdkStorageCallback = callback;
    }

    @Nullable
    public Bundle getLastSyncData() {
        return mLastSyncUpdate.getData();
    }

    @Nullable
    public SharedPreferencesUpdate getLastSyncUpdate() {
        return mLastSyncUpdate;
    }

    public int getInitializationCount() {
        return mInitializationCount;
    }

    public void sendLoadCodeSuccessful() throws RemoteException {
        mLoadSdkInSandboxCallback.onLoadSdkSuccess(
                new SandboxedSdk(new Binder()),
                mManagerToSdkCallback,
                new SandboxLatencyInfo(mTimeSystemServerCalledSandbox));
    }

    public void sendLoadCodeSuccessfulWithSandboxLatencies() throws RemoteException {
        mLoadSdkInSandboxCallback.onLoadSdkSuccess(
                new SandboxedSdk(new Binder()), mManagerToSdkCallback, createSandboxLatencyInfo());
    }

    public boolean isSdkUnloaded() {
        return mUnloadSdkCallback != null;
    }

    public void sendStorageInfoToSystemServer() throws RemoteException {
        mComputeSdkStorageCallback.onStorageInfoComputed(0F, 0F);
    }

    public void sendLoadCodeError() throws Exception {
        Class<?> clz = Class.forName("android.app.sdksandbox.LoadSdkException");
        LoadSdkException exception =
                (LoadSdkException)
                        clz.getConstructor(Integer.TYPE, String.class)
                                .newInstance(
                                        SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                                        "Internal error");
        mLoadSdkInSandboxCallback.onLoadSdkError(
                exception, new SandboxLatencyInfo(mTimeSystemServerCalledSandbox));
    }

    public void sendSurfacePackageReady(SandboxLatencyInfo sandboxLatencyInfo)
            throws RemoteException {
        if (mSurfacePackageRequested) {
            mRequestSurfacePackageFromSdkCallback.onSurfacePackageReady(
                    /*surfacePackage=*/ null,
                    /*surfacePackageId=*/ 1,
                    /*params=*/ new Bundle(),
                    sandboxLatencyInfo);
        }
    }

    public void sendUnloadSdkSuccess() throws Exception {
        mUnloadSdkCallback.onUnloadSdk(createSandboxLatencyInfo());
    }

    // TODO(b/242684679): Use iRequestSurfacePackageFromSdkCallback instead of fake callback
    public void sendSurfacePackageError(
            int errorCode, String errorMsg, FakeRequestSurfacePackageCallbackBinder callback)
            throws RemoteException {
        callback.onSurfacePackageError(errorCode, errorMsg, System.currentTimeMillis());
    }

    public void setIsDisabledResponse(boolean response) {
        mIsDisabledResponse = response;
    }

    private SandboxLatencyInfo createSandboxLatencyInfo() {
        final SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(mTimeSystemServerCalledSandbox);
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                mTimeSandboxReceivedCallFromSystemServer);
        sandboxLatencyInfo.setTimeSandboxCalledSdk(mTimeSandboxCalledSdk);
        sandboxLatencyInfo.setTimeSdkCallCompleted(mTimeSdkCallCompleted);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mTimeSandboxCalledSystemServer);

        return sandboxLatencyInfo;
    }

    private class FakeManagerToSdkCallback extends ISdkSandboxManagerToSdkSandboxCallback.Stub {
        @Override
        public void onSurfacePackageRequested(
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                Bundle extraParams,
                SandboxLatencyInfo sandboxLatencyInfo,
                IRequestSurfacePackageFromSdkCallback iRequestSurfacePackageFromSdkCallback) {
            mSurfacePackageRequested = true;
            mRequestSurfacePackageFromSdkCallback = iRequestSurfacePackageFromSdkCallback;
        }
    }
}
