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

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Selects ad outcome based on previous winners of Remarketing Ads.
 *
 * <p>A new instance is assumed to be created for every call.
 */
public class AdOutcomeSelectorImpl implements AdOutcomeSelector {
    @VisibleForTesting
    static final String MISSING_SCORING_LOGIC = "Error fetching scoring decision logic";

    @VisibleForTesting
    static final String OUTCOME_SELECTION_TIMED_OUT =
            "Outcome selection exceeded allowed time limit";

    @VisibleForTesting
    static final String OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT =
            "Outcome selection Js execution returned either a failed status or more than one ad";

    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final Flags mFlags;

    public AdOutcomeSelectorImpl(
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull ListeningExecutorService lightweightExecutor,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull AdSelectionDevOverridesHelper adSelectionDevOverridesHelper,
            @NonNull Flags flags) {
        Objects.requireNonNull(adSelectionScriptEngine);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(flags);

        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutor;
        mBackgroundExecutorService = backgroundExecutor;
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionDevOverridesHelper = adSelectionDevOverridesHelper;
        mFlags = flags;
    }

    /**
     * Compares ads based on their bids and selection signals.
     *
     * @param adSelectionIdWithBidAndRenderUris list of ad selection id and bid pairs
     * @param config {@link AdSelectionFromOutcomesConfig} instance
     * @return a Future of {@code Long} {code @AdSelectionId} of the winner. If no winner then
     *     returns null
     */
    @Override
    public FluentFuture<Long> runAdOutcomeSelector(
            @NonNull List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUris,
            @NonNull AdSelectionFromOutcomesConfig config) {
        Objects.requireNonNull(adSelectionIdWithBidAndRenderUris);
        Objects.requireNonNull(config);

        FluentFuture<String> selectionLogicJsFuture =
                FluentFuture.from(getAdOutcomeSelectorLogic(config));

        FluentFuture<Long> selectedOutcomeFuture =
                selectionLogicJsFuture.transformAsync(
                        selectionLogic ->
                                mAdSelectionScriptEngine.selectOutcome(
                                        selectionLogic,
                                        adSelectionIdWithBidAndRenderUris,
                                        config.getSelectionSignals()),
                        mLightweightExecutorService);

        int traceCookie = Tracing.beginAsyncSection(Tracing.RUN_OUTCOME_SELECTION);
        return selectedOutcomeFuture
                .withTimeout(
                        mFlags.getAdSelectionSelectingOutcomeTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_OUTCOME_SELECTION, traceCookie);
                            return handleTimeoutError(e);
                        },
                        mLightweightExecutorService)
                .catching(
                        IllegalStateException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_OUTCOME_SELECTION, traceCookie);
                            return handleIllegalStateException(e);
                        },
                        mLightweightExecutorService);
    }

    @Nullable
    private Long handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, OUTCOME_SELECTION_TIMED_OUT);
        throw new UncheckedTimeoutException(OUTCOME_SELECTION_TIMED_OUT);
    }

    /**
     * Handles {@link IllegalStateException} that can be thrown in {@link
     * AdSelectionScriptEngine#selectOutcome} if the result's status is failure or results contains
     * more than one item.
     */
    @Nullable
    private Long handleIllegalStateException(IllegalStateException e) {
        LogUtil.e(e, OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT);
        throw new IllegalStateException(OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT);
    }

    private ListenableFuture<String> getAdOutcomeSelectorLogic(
            AdSelectionFromOutcomesConfig config) {
        // TODO(b/254500329) Implement overrides
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mAdSelectionDevOverridesHelper.getSelectionLogicOverride(
                                                config)));

        FluentFuture<String> jsLogicFuture =
                jsOverrideFuture.transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                LogUtil.v("Fetching Outcome Selector Logic from the server");
                                return FluentFuture.from(
                                                mAdServicesHttpsClient.fetchPayload(
                                                        config.getSelectionLogicUri()))
                                        .transform(
                                                response -> response.getResponseBody(),
                                                mLightweightExecutorService);
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current selection logic Uri. "
                                                + "Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService);

        return jsLogicFuture.catching(
                Exception.class,
                e -> {
                    LogUtil.e(e, "Exception encountered when fetching outcome selection logic");
                    throw new IllegalStateException(MISSING_SCORING_LOGIC);
                },
                mLightweightExecutorService);
    }
}
