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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.SdkRuntimeUtil;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of {@link AdSelectionService}.
 *
 * @hide
 */
public class AdSelectionServiceImpl extends AdSelectionService.Stub {

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ExecutorService mExecutor;
    @NonNull private final Context mContext;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final DevContextFilter mDevContextFilter;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;

    private static final String API_NOT_AUTHORIZED_MSG =
            "This API is not enabled for the given app because either dev options are disabled or"
                    + " the app is not debuggable.";

    @VisibleForTesting
    public AdSelectionServiceImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContextFilter devContextFilter,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull ExecutorService executorService,
            @NonNull Context context,
            ConsentManager consentManager,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags) {
        Objects.requireNonNull(context, "Context must be provided.");
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContextFilter);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mDevContextFilter = devContextFilter;
        mAppImportanceFilter = appImportanceFilter;
        mExecutor = executorService;
        mContext = context;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
    }

    /** Creates a new instance of {@link AdSelectionServiceImpl}. */
    public static AdSelectionServiceImpl create(@NonNull Context context) {
        return new AdSelectionServiceImpl(context);
    }

    /** Creates an instance of {@link AdSelectionServiceImpl} to be used. */
    private AdSelectionServiceImpl(@NonNull Context context) {
        this(
                AdSelectionDatabase.getInstance(context).adSelectionEntryDao(),
                CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                new AdServicesHttpsClient(AdServicesExecutors.getBackgroundExecutor()),
                DevContextFilter.create(context),
                AppImportanceFilter.create(
                        context,
                        AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                        () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation()),
                AdServicesExecutors.getBackgroundExecutor(),
                context,
                ConsentManager.getInstance(context),
                AdServicesLoggerImpl.getInstance(),
                FlagsFactory.getFlags());
    }

    // TODO(b/233116758): Validate all the fields inside the adSelectionConfig.
    @Override
    public void runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig, @NonNull AdSelectionCallback callback) {
        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(callback);

            AdSelectionConfigValidator adSelectionConfigValidator =
                    new AdSelectionConfigValidator();
            adSelectionConfigValidator.validate(adSelectionConfig);
        } catch (NullPointerException | IllegalArgumentException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        AdSelectionRunner adSelectionRunner =
                new AdSelectionRunner(
                        mContext,
                        mCustomAudienceDao,
                        mAdSelectionEntryDao,
                        mExecutor,
                        mConsentManager,
                        mAdServicesLogger,
                        devContext,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingAppUid());

        adSelectionRunner.runAdSelection(adSelectionConfig, callback);
    }

    @Override
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        try {
            Objects.requireNonNull(requestParams);
            Objects.requireNonNull(callback);
            AdSelectionConfigValidator adSelectionConfigValidator =
                    new AdSelectionConfigValidator();
            adSelectionConfigValidator.validate(requestParams.getAdSelectionConfig());
        } catch (NullPointerException | IllegalArgumentException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        ImpressionReporter reporter =
                new ImpressionReporter(
                        mContext,
                        mExecutor,
                        mAdSelectionEntryDao,
                        mAdServicesHttpsClient,
                        mConsentManager,
                        devContext,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingAppUid());
        reporter.reportImpression(requestParams, callback);
    }

    @Override
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull AdSelectionOverrideCallback callback) {
        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(decisionLogicJS);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                    AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                    AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingAppUid());

        overrider.addOverride(adSelectionConfig, decisionLogicJS, trustedScoringSignals, callback);
    }

    private int getCallingAppUid() {
        return SdkRuntimeUtil.getCallingAppUid(Binder.getCallingUidOrThrow());
    }

    @Override
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;

        try {
            Objects.requireNonNull(adSelectionConfig);
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    shortApiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    shortApiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingAppUid());

        overrider.removeOverride(adSelectionConfig, callback);
    }

    @Override
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        // Auto-generated variable name is too long for lint check
        int shortApiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;

        try {
            Objects.requireNonNull(callback);
        } catch (NullPointerException exception) {
            mAdServicesLogger.logFledgeApiCallStats(
                    shortApiName, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
            // Rethrow because we want to fail fast
            throw exception;
        }

        DevContext devContext = mDevContextFilter.createDevContext();

        if (!devContext.getDevOptionsEnabled()) {
            mAdServicesLogger.logFledgeApiCallStats(
                    shortApiName, AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
            throw new SecurityException(API_NOT_AUTHORIZED_MSG);
        }

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(
                        devContext,
                        mAdSelectionEntryDao,
                        mExecutor,
                        mContext.getPackageManager(),
                        mConsentManager,
                        mAdServicesLogger,
                        mAppImportanceFilter,
                        mFlags,
                        getCallingAppUid());

        overrider.removeAllOverrides(callback);
    }

    /** Close down method to be invoked when the PPAPI process is shut down. */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void destroy() {
        LogUtil.i("Shutting down AdSelectionService");
        JSScriptEngine.getInstance(mContext).shutdown();
    }
}
