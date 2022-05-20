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

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.AdServicesExecutors;
import com.android.adservices.service.devapi.AdSelectionOverrider;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
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
    @NonNull private final AdSelectionHttpClient mAdSelectionHttpClient;
    @NonNull private final ExecutorService mExecutor;
    @NonNull private final Context mContext;
    @NonNull private final DevContextFilter mDevContextFilter;

    @VisibleForTesting
    AdSelectionServiceImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AdSelectionHttpClient adSelectionHttpClient,
            @NonNull DevContextFilter devContextFilter,
            @NonNull ExecutorService executorService,
            @NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionHttpClient = adSelectionHttpClient;
        mDevContextFilter = devContextFilter;
        mExecutor = executorService;
        mContext = context;
    }

    /** Creates an instance of {@link AdSelectionServiceImpl} to be used. */
    public AdSelectionServiceImpl(@NonNull Context context) {
        this(
                AdSelectionDatabase.getInstance(context).adSelectionEntryDao(),
                CustomAudienceDatabase.getInstance(context).customAudienceDao(),
                new AdSelectionHttpClient(AdServicesExecutors.getBackgroundExecutor()),
                DevContextFilter.create(context),
                AdServicesExecutors.getBackgroundExecutor(),
                context);
    }

    //TODO(b/233116758): Validate all the fields inside the adSelectionConfig.
    @Override
    public void runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig, @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(callback);

        AdSelectionRunner adSelectionRunner = new AdSelectionRunner(mContext,
                mCustomAudienceDao,
                mAdSelectionEntryDao,
                mExecutor);
        adSelectionRunner.runAdSelection(adSelectionConfig, callback);
    }

    @Override
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        // TODO(b/225990194): Add end to end test
        Objects.requireNonNull(requestParams);
        Objects.requireNonNull(callback);

        DevContext devContext = mDevContextFilter.createDevContext();

        ImpressionReporter reporter =
                new ImpressionReporter(
                        mContext,
                        mExecutor,
                        mAdSelectionEntryDao,
                        mAdSelectionHttpClient,
                        devContext);
        reporter.reportImpression(requestParams, callback);
    }

    @Override
    public void overrideAdSelectionConfigRemoteInfo(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionOverrideCallback callback) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(decisionLogicJS);
        Objects.requireNonNull(callback);

        DevContext devContext = mDevContextFilter.createDevContext();

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(devContext, mAdSelectionEntryDao, mExecutor);

        overrider.addOverride(adSelectionConfig, decisionLogicJS, callback);
    }

    @Override
    public void removeAdSelectionConfigRemoteInfoOverride(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionOverrideCallback callback) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(callback);

        DevContext devContext = mDevContextFilter.createDevContext();

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(devContext, mAdSelectionEntryDao, mExecutor);

        overrider.removeOverride(adSelectionConfig, callback);
    }

    @Override
    public void resetAllAdSelectionConfigRemoteOverrides(
            @NonNull AdSelectionOverrideCallback callback) {
        Objects.requireNonNull(callback);

        DevContext devContext = mDevContextFilter.createDevContext();

        AdSelectionOverrider overrider =
                new AdSelectionOverrider(devContext, mAdSelectionEntryDao, mExecutor);

        overrider.removeAllOverrides(callback);
    }
}
