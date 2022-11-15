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

import android.adservices.adselection.Tracing;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Trace;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

/** Class to fetch JavaScript code both on and off device. */
public class JsFetcher {
    @VisibleForTesting
    static final String MISSING_BIDDING_LOGIC = "Error fetching bidding js logic";

    private final ListeningExecutorService mBackgroundExecutorService;
    private final ListeningExecutorService mLightweightExecutorService;
    private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    private final AdServicesHttpsClient mAdServicesHttpsClient;

    public JsFetcher(
            @NonNull ListeningExecutorService backgroundExecutorService,
            @NonNull ListeningExecutorService lightweightExecutorService,
            @NonNull CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper,
            @NonNull AdServicesHttpsClient adServicesHttpsClient) {
        mBackgroundExecutorService = backgroundExecutorService;
        mCustomAudienceDevOverridesHelper = customAudienceDevOverridesHelper;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutorService;
    }

    /**
     * Fetch the buyer decision logic. Check locally to see if an override is present, otherwise
     * fetch from server.
     *
     * @return buyer decision logic
     */
    public FluentFuture<String> getBuyerDecisionLogic(
            @NonNull final Uri decisionLogicUri,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC);

        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mCustomAudienceDevOverridesHelper.getBiddingLogicOverride(
                                                owner, buyer, name)));
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            if (jsOverride == null) {
                                LogUtil.v(
                                        "Fetching buyer decision logic from server: %s",
                                        decisionLogicUri.toString());
                                return mAdServicesHttpsClient.fetchPayload(decisionLogicUri);
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current Custom Audience. "
                                                + "Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Trace.endAsyncSection(Tracing.GET_BUYER_DECISION_LOGIC, traceCookie);
                            LogUtil.w(
                                    e, "Exception encountered when fetching buyer decision logic");
                            throw new IllegalStateException(MISSING_BIDDING_LOGIC);
                        },
                        mLightweightExecutorService);
    }
}
