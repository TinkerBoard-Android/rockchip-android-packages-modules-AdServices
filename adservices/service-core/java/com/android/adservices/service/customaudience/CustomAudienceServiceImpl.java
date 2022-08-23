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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ICustomAudienceService;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.os.LimitExceededException;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CallingAppUidSupplierBinderImpl;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.CustomAudienceOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/** Implementation of the Custom Audience service. */
public class CustomAudienceServiceImpl extends ICustomAudienceService.Stub {
    @NonNull private final Context mContext;
    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final FledgeAllowListsFilter mFledgeAllowListsFilter;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final Flags mFlags;
    @NonNull private final Supplier<Throttler> mThrottlerSupplier;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    private CustomAudienceServiceImpl(@NonNull Context context) {
        this(
                context,
                CustomAudienceImpl.getInstance(context),
                FledgeAuthorizationFilter.create(context, AdServicesLoggerImpl.getInstance()),
                new FledgeAllowListsFilter(
                        FlagsFactory.getFlags(), AdServicesLoggerImpl.getInstance()),
                ConsentManager.getInstance(context),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesLoggerImpl.getInstance(),
                AppImportanceFilter.create(
                        context,
                        AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation()),
                FlagsFactory.getFlags(),
                () ->
                        Throttler.getInstance(
                                FlagsFactory.getFlags().getSdkRequestPermitsPerSecond()),
                CallingAppUidSupplierBinderImpl.create());
    }

    /** Creates a new instance of {@link CustomAudienceServiceImpl}. */
    public static CustomAudienceServiceImpl create(@NonNull Context context) {
        return new CustomAudienceServiceImpl(context);
    }

    @VisibleForTesting
    public CustomAudienceServiceImpl(
            @NonNull Context context,
            @NonNull CustomAudienceImpl customAudienceImpl,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull FledgeAllowListsFilter fledgeAllowListsFilter,
            @NonNull ConsentManager consentManager,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull Flags flags,
            @NonNull Supplier<Throttler> throttlerSupplier,
            @NonNull CallingAppUidSupplier callingAppUidSupplier) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceImpl);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(fledgeAllowListsFilter);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(throttlerSupplier);
        mContext = context;
        mCustomAudienceImpl = customAudienceImpl;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mFledgeAllowListsFilter = fledgeAllowListsFilter;
        mConsentManager = consentManager;
        mDevContextFilter = devContextFilter;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mAppImportanceFilter = appImportanceFilter;
        mFlags = flags;
        mThrottlerSupplier = throttlerSupplier;
        mCallingAppUidSupplier = callingAppUidSupplier;
    }

    /**
     * Adds a user to a custom audience.
     *
     * @hide
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String ownerPackageName,
            @NonNull ICustomAudienceCallback callback) {
        LogUtil.v("Entering joinCustomAudience");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(customAudience);
            Objects.requireNonNull(ownerPackageName);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);
        LogUtil.v("Running service");
        mExecutorService.execute(
                () -> doJoinCustomAudience(customAudience, ownerPackageName, callback, callerUid));
    }

    /** Try to join the custom audience and signal back to the caller using the callback. */
    private void doJoinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String ownerPackageName,
            @NonNull ICustomAudienceCallback callback,
            final int callerUid) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(ownerPackageName);
        Objects.requireNonNull(callback);

        LogUtil.v("Entering doJoinCustomAudience");

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                LogUtil.v("Validating caller package name");
                mFledgeAuthorizationFilter.assertCallingPackageName(
                        ownerPackageName, callerUid, apiName);

                LogUtil.v("Validating API is not throttled");
                assertCallerNotThrottled(FLEDGE_API_JOIN_CUSTOM_AUDIENCE, ownerPackageName);

                if (mFlags.getEnforceForegroundStatusForFledgeCustomAudience()) {
                    LogUtil.v("Checking caller is in foreground");
                    mAppImportanceFilter.assertCallerIsInForeground(
                            ownerPackageName, apiName, null);
                }

                if (!mFlags.getDisableFledgeEnrollmentCheck()) {
                    mFledgeAuthorizationFilter.assertAdTechAllowed(
                            mContext, ownerPackageName, customAudience.getBuyer(), apiName);
                }

                mFledgeAllowListsFilter.assertAppCanUsePpapi(ownerPackageName, apiName);

                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mContext.getPackageManager(), ownerPackageName)) {
                    LogUtil.v("Joining custom audience");
                    mCustomAudienceImpl.joinCustomAudience(customAudience, ownerPackageName);
                    BackgroundFetchJobService.scheduleIfNeeded(mContext, mFlags, false);
                    // TODO(b/233681870): Investigate implementation of actual failures
                    //  in logs/metrics
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    LogUtil.v("Consent revoked");
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (Exception exception) {
                resultCode = notifyFailure(callback, exception);
                return;
            }

            callback.onSuccess();
        } catch (Exception exception) {
            LogUtil.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
            }
        }
    }

    private int notifyFailure(ICustomAudienceCallback callback, Exception exception)
            throws RemoteException {
        int resultCode;
        if (exception instanceof NullPointerException
                || exception instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        } else if (exception instanceof WrongCallingApplicationStateException) {
            resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
        } else if (exception instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            resultCode = AdServicesStatusUtils.STATUS_UNAUTHORIZED;
        } else if (exception instanceof FledgeAuthorizationFilter.AdTechNotAllowedException
                || exception instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            resultCode = AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
        } else if (exception instanceof LimitExceededException) {
            resultCode = AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
        } else if (exception instanceof IllegalStateException) {
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } else {
            LogUtil.e(exception, "Unexpected error during operation");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        }
        callback.onFailure(
                new FledgeErrorResponse.Builder()
                        .setStatusCode(resultCode)
                        .setErrorMessage(exception.getMessage())
                        .build());
        return resultCode;
    }

    /**
     * Attempts to remove a user from a custom audience.
     *
     * @hide
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void leaveCustomAudience(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(ownerPackageName);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);
        mExecutorService.execute(
                () -> doLeaveCustomAudience(ownerPackageName, buyer, name, callback, callerUid));
    }

    /** Try to leave the custom audience and signal back to the caller using the callback. */
    private void doLeaveCustomAudience(
            @NonNull String ownerPackageName,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull ICustomAudienceCallback callback,
            final int callerUid) {
        Objects.requireNonNull(ownerPackageName);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(callback);

        final int apiName = AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        // The filters log internally, so don't accidentally log again
        boolean shouldLog = false;
        try {
            try {
                mFledgeAuthorizationFilter.assertCallingPackageName(
                        ownerPackageName, callerUid, apiName);

                assertCallerNotThrottled(FLEDGE_API_LEAVE_CUSTOM_AUDIENCE, ownerPackageName);

                if (mFlags.getEnforceForegroundStatusForFledgeCustomAudience()) {
                    mAppImportanceFilter.assertCallerIsInForeground(
                            ownerPackageName, apiName, null);
                }

                if (!mFlags.getDisableFledgeEnrollmentCheck()) {
                    mFledgeAuthorizationFilter.assertAdTechAllowed(
                            mContext, ownerPackageName, buyer, apiName);
                }

                mFledgeAllowListsFilter.assertAppCanUsePpapi(ownerPackageName, apiName);

                shouldLog = true;

                // Fail silently for revoked user consent
                if (!mConsentManager.isFledgeConsentRevokedForApp(
                        mContext.getPackageManager(), ownerPackageName)) {
                    // TODO(b/233681870): Investigate implementation of actual failures
                    //  in logs/metrics
                    mCustomAudienceImpl.leaveCustomAudience(ownerPackageName, buyer, name);
                    resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    resultCode = AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
                }
            } catch (WrongCallingApplicationStateException
                    | LimitExceededException
                    | FledgeAuthorizationFilter.CallerMismatchException
                    | FledgeAuthorizationFilter.AdTechNotAllowedException
                    | FledgeAllowListsFilter.AppNotAllowedException exception) {
                // Catch these specific exceptions, but report them back to the caller
                resultCode = notifyFailure(callback, exception);
                return;
            } catch (Exception exception) {
                // For all other exceptions, report success
                LogUtil.e(exception, "Unexpected error leaving custom audience");
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }

            callback.onSuccess();
            // TODO(b/233681870): Investigate implementation of actual failures in
            //  logs/metrics
        } catch (Exception exception) {
            LogUtil.e(exception, "Unable to send result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            if (shouldLog) {
                mAdServicesLogger.logFledgeApiCallStats(apiName, resultCode);
            }
        }
    }

    /**
     * Adds a custom audience override with the given information.
     *
     * <p>If the owner does not match the calling package name, fail silently.
     *
     * @hide
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void overrideCustomAudienceRemoteInfo(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull String biddingLogicJS,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull CustomAudienceOverrideCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(biddingLogicJS);
            Objects.requireNonNull(trustedBiddingSignals);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.addOverride(owner, buyer, name, biddingLogicJS, trustedBiddingSignals, callback);
    }

    /**
     * Removes a custom audience override with the given information.
     *
     * @hide
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void removeCustomAudienceRemoteInfoOverride(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull CustomAudienceOverrideCallback callback) {
        final int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(buyer);
            Objects.requireNonNull(name);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeOverride(owner, buyer, name, callback);
    }

    /**
     * Ensures that the caller package is not throttled from calling current the API
     *
     * @param callerPackageName the package name, which should be verified
     * @throws LimitExceededException if the provided {@code callerPackageName} exceeds its rate
     *     limits
     * @return an ignorable {@code null}
     */
    private Void assertCallerNotThrottled(
            final Throttler.ApiKey apiName, final String callerPackageName)
            throws LimitExceededException {
        Throttler throttler = mThrottlerSupplier.get();
        boolean isThrottled = !throttler.tryAcquire(apiName, callerPackageName);

        if (isThrottled) {
            LogUtil.e("Rate Limit Reached for API: %s", apiName);
            throw new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE);
        }
        return null;
    }

    /**
     * Resets all custom audience overrides for a given caller.
     *
     * @hide
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    public void resetAllCustomAudienceOverrides(@NonNull CustomAudienceOverrideCallback callback) {
        final int apiName = AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;

        // Caller permissions must be checked in the binder thread, before anything else
        mFledgeAuthorizationFilter.assertAppDeclaredPermission(mContext, apiName);

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow to fail fast
            throw exception;
        }

        final int callerUid = getCallingUid(apiName);

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        CustomAudienceDao customAudienceDao = mCustomAudienceImpl.getCustomAudienceDao();

        CustomAudienceOverrider overrider =
                new CustomAudienceOverrider(
                        devContext,
                        customAudienceDao,
                        mExecutorService,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags);

        overrider.removeAllOverrides(callback, callerUid);
    }

    private int getCallingUid(int apiNameLoggingId) throws IllegalStateException {
        try {
            return mCallingAppUidSupplier.getCallingAppUid();
        } catch (IllegalStateException illegalStateException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    apiNameLoggingId, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw illegalStateException;
        }
    }
}
