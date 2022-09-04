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
package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__ADID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_ADID;

import android.adservices.adid.GetAdIdParam;
import android.adservices.adid.IAdIdService;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link IAdIdService}.
 *
 * @hide
 */
public class AdIdServiceImpl extends IAdIdService.Stub {
    private final Context mContext;
    private final AdIdWorker mAdIdWorker;
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final AdServicesLogger mAdServicesLogger;
    private final Clock mClock;
    private final Flags mFlags;
    private final AppImportanceFilter mAppImportanceFilter;

    public AdIdServiceImpl(
            Context context,
            AdIdWorker adidWorker,
            AdServicesLogger adServicesLogger,
            Clock clock,
            Flags flags,
            AppImportanceFilter appImportanceFilter) {
        mContext = context;
        mAdIdWorker = adidWorker;
        mAdServicesLogger = adServicesLogger;
        mClock = clock;
        mFlags = flags;
        mAppImportanceFilter = appImportanceFilter;
    }

    @Override
    public void getAdId(
            @NonNull GetAdIdParam adIdParam,
            @NonNull CallerMetadata callerMetadata,
            @NonNull IGetAdIdCallback callback) {

        final long startServiceTime = mClock.elapsedRealtime();
        final String packageName = adIdParam.getAppPackageName();
        final String sdkPackageName = adIdParam.getSdkPackageName();

        // We need to save the Calling Uid before offloading to the background executor. Otherwise
        // the Binder.getCallingUid will return the PPAPI process Uid. This also needs to be final
        // since it's used in the lambda.
        final int callingUid = Binder.getCallingUidOrThrow();

        // Check the permission in the same thread since we're looking for caller's permissions.
        // Note: The permission check uses sdk package name since PackageManager checks if the
        // permission is declared in the manifest of that package name.
        boolean hasAdIdPermission =
                PermissionHelper.hasAdIdPermission(
                        mContext, Process.isSdkSandboxUid(callingUid), sdkPackageName);

        sBackgroundExecutor.execute(
                () -> {
                    int resultCode = STATUS_SUCCESS;

                    try {
                        if (!canCallerInvokeAdIdService(
                                hasAdIdPermission, adIdParam, callingUid, callback)) {
                            return;
                        }
                        mAdIdWorker.getAdId(packageName, callingUid, callback);

                    } catch (Exception e) {
                        LogUtil.e(e, "Unable to send result to the callback");
                        resultCode = STATUS_INTERNAL_ERROR;
                    } finally {
                        long binderCallStartTimeMillis = callerMetadata.getBinderElapsedTimestamp();
                        long serviceLatency = mClock.elapsedRealtime() - startServiceTime;
                        // Double it to simulate the return binder time is same to call binder time
                        long binderLatency = (startServiceTime - binderCallStartTimeMillis) * 2;

                        final int apiLatency = (int) (serviceLatency + binderLatency);
                        mAdServicesLogger.logApiCallStats(
                                new ApiCallStats.Builder()
                                        .setCode(AdServicesStatsLog.AD_SERVICES_API_CALLED)
                                        .setApiClass(AD_SERVICES_API_CALLED__API_CLASS__ADID)
                                        .setApiName(AD_SERVICES_API_CALLED__API_NAME__GET_ADID)
                                        .setAppPackageName(packageName)
                                        .setSdkPackageName(sdkPackageName)
                                        .setLatencyMillisecond(apiLatency)
                                        .setResultCode(resultCode)
                                        .build());
                    }
                });
    }

    // Enforce whether caller is from foreground.
    private void enforceForeground(int callingUid) {
        // If caller calls Topics API from Sandbox, regard it as foreground.
        // Also enable a flag to force switch on/off this enforcing.
        if (Process.isSdkSandboxUid(callingUid) || !mFlags.getEnforceForegroundStatusForAdId()) {
            return;
        }

        // Call utility method in AppImportanceFilter to enforce foreground status
        //  Throw WrongCallingApplicationStateException  if the assertion fails.
        mAppImportanceFilter.assertCallerIsInForeground(
                callingUid, AD_SERVICES_API_CALLED__API_NAME__GET_ADID, null);
    }

    /**
     * Check whether caller can invoke the AdId API. The caller is not allowed to do it when one of
     * the following occurs:
     *
     * <ul>
     *   <li>Permission was not requested.
     *   <li>Caller is not allowed - not present in the allowed list.
     * </ul>
     *
     * @param sufficientPermission boolean which tells whether caller has sufficient permissions.
     * @param adIdParam {@link GetAdIdParam} to get information about the request.
     * @param callback {@link IGetAdIdCallback} to invoke when caller is not allowed.
     * @return true if caller is allowed to invoke AdId API, false otherwise.
     */
    private boolean canCallerInvokeAdIdService(
            boolean sufficientPermission,
            GetAdIdParam adIdParam,
            int callingUid,
            IGetAdIdCallback callback) {
        // Enforce caller calls AdId API from foreground.
        try {
            enforceForeground(callingUid);
        } catch (WrongCallingApplicationStateException backgroundCaller) {
            invokeCallbackWithStatus(
                    callback, STATUS_BACKGROUND_CALLER, backgroundCaller.getMessage());
            return false;
        }

        if (!sufficientPermission) {
            invokeCallbackWithStatus(
                    callback,
                    STATUS_PERMISSION_NOT_REQUESTED,
                    "Unauthorized caller. Permission not requested.");
            return false;
        }
        // This needs to access PhFlag which requires READ_DEVICE_CONFIG which
        // is not granted for binder thread. So we have to check it with one
        // of non-binder thread of the PPAPI.
        boolean appCanUsePpapi =
                AllowLists.isPackageAllowListed(
                        mFlags.getPpapiAppAllowList(), adIdParam.getAppPackageName());
        if (!appCanUsePpapi) {
            invokeCallbackWithStatus(
                    callback,
                    STATUS_CALLER_NOT_ALLOWED,
                    "Unauthorized caller. Caller is not allowed.");
            return false;
        }

        // Check whether calling package belongs to the callingUid
        int resultCode =
                enforceCallingPackageBelongsToUid(adIdParam.getAppPackageName(), callingUid);
        if (resultCode != STATUS_SUCCESS) {
            invokeCallbackWithStatus(callback, resultCode, "Caller is not authorized.");
            return false;
        }
        return true;
    }

    private void invokeCallbackWithStatus(
            IGetAdIdCallback callback,
            @AdServicesStatusUtils.StatusCode int statusCode,
            String message) {
        LogUtil.e(message);
        try {
            callback.onError(statusCode);
        } catch (RemoteException e) {
            LogUtil.e(e, String.format("Fail to call the callback. %s", message));
        }
    }

    // Enforce that the callingPackage has the callingUid.
    private int enforceCallingPackageBelongsToUid(String callingPackage, int callingUid) {
        int appCallingUid = SdkRuntimeUtil.getCallingAppUid(callingUid);
        int packageUid;
        try {
            packageUid = mContext.getPackageManager().getPackageUid(callingPackage, /* flags */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(e, callingPackage + " not found");
            return STATUS_UNAUTHORIZED;
        }
        if (packageUid != appCallingUid) {
            LogUtil.e(callingPackage + " does not belong to uid " + callingUid);
            return STATUS_UNAUTHORIZED;
        }
        return STATUS_SUCCESS;
    }

}
