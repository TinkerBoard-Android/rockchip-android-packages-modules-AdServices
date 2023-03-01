/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.adservices.adselection.ReportInteractionRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportInteractionRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.ReportInteractionRequest;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.LimitExceededException;
import android.os.RemoteException;
import android.os.Trace;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.util.Preconditions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Interaction Reporting logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class InteractionReporter {
    @ReportInteractionRequest.ReportingDestination
    private static final int[] POSSIBLE_DESTINATIONS =
            new int[] {FLAG_REPORTING_DESTINATION_SELLER, FLAG_REPORTING_DESTINATION_BUYER};

    @NonNull private final Context mContext;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final FledgeServiceFilter mFledgeServiceFilter;
    private int mCallerUid;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;

    public InteractionReporter(
            @NonNull Context context,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull FledgeServiceFilter fledgeServiceFilter,
            int callerUid,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(fledgeServiceFilter);
        Objects.requireNonNull(fledgeAuthorizationFilter);

        mContext = context;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mFledgeServiceFilter = fledgeServiceFilter;
        mCallerUid = callerUid;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
    }

    /**
     * Run the interaction report logic asynchronously. Searches the {@code
     * registered_ad_interactions} database for matches based on the provided {@code adSelectionId},
     * {@code interactionKey}, {@code destinations} that we get from {@link ReportInteractionInput}
     * Then, attaches {@code interactionData} to each found Uri and performs a POST request.
     *
     * <p>After validating the inputParams and request context, invokes {@link
     * ReportInteractionCallback#onSuccess()} before continuing with reporting. If we encounter a
     * failure during request validation, we invoke {@link
     * ReportInteractionCallback#onFailure(FledgeErrorResponse)} and exit early.
     */
    public void reportInteraction(
            @NonNull ReportInteractionInput inputParams,
            @NonNull ReportInteractionCallback callback) {
        long adSelectionId = inputParams.getAdSelectionId();
        String callerPackageName = inputParams.getCallerPackageName();
        FluentFuture<Void> filterAndValidateRequestFuture =
                FluentFuture.from(
                        Futures.submit(
                                () -> {
                                    try {
                                        Trace.beginSection(Tracing.VALIDATE_REQUEST);
                                        LogUtil.v("Starting filtering and validation.");
                                        mFledgeServiceFilter.filterRequest(
                                                null,
                                                callerPackageName,
                                                mFlags
                                                        .getEnforceForegroundStatusForFledgeReportInteraction(),
                                                mCallerUid,
                                                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                                                Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION);
                                        Preconditions.checkArgument(
                                                mAdSelectionEntryDao
                                                        .doesAdSelectionMatchingCallerPackageNameExist(
                                                                adSelectionId, callerPackageName));
                                    } finally {
                                        LogUtil.v("Completed filtering and validation.");
                                        Trace.endSection();
                                    }
                                },
                                mLightweightExecutorService));
        filterAndValidateRequestFuture.addCallback(
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        notifySuccessToCaller(callback);
                        performReporting(inputParams);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(t, "Report Interaction failed!");
                        if (t instanceof ConsentManager.RevokedConsentException) {
                            // Fail Silently by notifying success to caller, but logging the error
                            mAdServicesLogger.logFledgeApiCallStats(
                                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                                    AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED,
                                    0);
                            notifySuccessToCaller(callback);

                        } else {
                            notifyFailureToCaller(callback, t);
                        }
                    }
                },
                mLightweightExecutorService);
    }

    private void performReporting(@NonNull ReportInteractionInput inputParams) {
        FluentFuture<List<Uri>> reportingUrisFuture = getReportingUris(inputParams);
        reportingUrisFuture
                .transformAsync(
                        reportingUris -> doReport(reportingUris, inputParams),
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<List<Void>>() {
                            @Override
                            public void onSuccess(List<Void> result) {
                                LogUtil.d("Report Interaction succeeded!");
                                mAdServicesLogger.logFledgeApiCallStats(
                                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                                        AdServicesStatusUtils.STATUS_SUCCESS,
                                        0);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                LogUtil.e(
                                        t,
                                        "Report Interaction failure encountered during reporting!");
                                mAdServicesLogger.logFledgeApiCallStats(
                                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                                        AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                                        0);
                            }
                        },
                        mLightweightExecutorService);
    }

    private FluentFuture<List<Uri>> getReportingUris(ReportInteractionInput inputParams) {
        return fetchReportingUris(inputParams)
                .transformAsync(
                        reportingUris -> filterReportingUris(inputParams, reportingUris),
                        mLightweightExecutorService);
    }

    private FluentFuture<List<Uri>> fetchReportingUris(ReportInteractionInput inputParams) {
        LogUtil.v(
                "Fetching ad selection entry ID %d for caller \"%s\"",
                inputParams.getAdSelectionId(), inputParams.getCallerPackageName());
        long adSelectionId = inputParams.getAdSelectionId();
        int destinationsBitField = inputParams.getReportingDestinations();
        String interactionKey = inputParams.getInteractionKey();

        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            List<Uri> resultingReportingUris = new ArrayList<>();
                            for (@ReportInteractionRequest.ReportingDestination
                            int destination : POSSIBLE_DESTINATIONS) {
                                if (bitExists(destination, destinationsBitField)) {
                                    if (mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                                            adSelectionId, interactionKey, destination)) {
                                        resultingReportingUris.add(
                                                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                                                        adSelectionId,
                                                        interactionKey,
                                                        destination));
                                    }
                                }
                            }
                            return resultingReportingUris;
                        }));
    }

    private FluentFuture<List<Uri>> filterReportingUris(
            ReportInteractionInput inputParams, List<Uri> reportingUris) {
        return FluentFuture.from(
                mLightweightExecutorService.submit(
                        () -> {
                            if (mFlags.getDisableFledgeEnrollmentCheck()) {
                                return reportingUris;
                            } else {
                                // Do enrollment check and only add Uris that pass enrollment
                                String callerPackageName = inputParams.getCallerPackageName();
                                ArrayList<Uri> validatedUris = new ArrayList<>();

                                for (Uri uri : reportingUris) {
                                    try {
                                        mFledgeAuthorizationFilter.assertAdTechAllowed(
                                                mContext,
                                                callerPackageName,
                                                AdTechIdentifier.fromString(uri.getHost()),
                                                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);
                                        validatedUris.add(uri);
                                    } catch (
                                            FledgeAuthorizationFilter.AdTechNotAllowedException
                                                    exception) {
                                        LogUtil.d(
                                                String.format(
                                                        "Enrollment check failed! Skipping"
                                                                + " reporting for %s:",
                                                        uri));
                                    }
                                }
                                return validatedUris;
                            }
                        }));
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void invokeFailure(
            @NonNull ReportInteractionCallback callback, int statusCode, String errorMessage) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(statusCode)
                            .setErrorMessage(errorMessage)
                            .build());
            resultCode = statusCode;
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send failed result to the callback");
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, resultCode, 0);
        }
    }

    private void notifySuccessToCaller(@NonNull ReportInteractionCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            LogUtil.e(e, "Unable to send successful result to the callback");
            int resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, resultCode, 0);
        }
    }

    private void notifyFailureToCaller(
            @NonNull ReportInteractionCallback callback, @NonNull Throwable t) {
        if (t instanceof IllegalArgumentException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, t.getMessage());
        } else if (t instanceof AppImportanceFilter.WrongCallingApplicationStateException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, t.getMessage());
        } else if (t instanceof FledgeAllowListsFilter.AppNotAllowedException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED, t.getMessage());
        } else if (t instanceof FledgeAuthorizationFilter.CallerMismatchException) {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_UNAUTHORIZED, t.getMessage());
        } else if (t instanceof LimitExceededException) {
            invokeFailure(
                    callback, AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, t.getMessage());
        } else {
            invokeFailure(callback, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, t.getMessage());
        }
    }

    private boolean bitExists(
            @ReportInteractionRequest.ReportingDestination int bit,
            @ReportInteractionRequest.ReportingDestination int bitSet) {
        return (bit & bitSet) != 0;
    }

    private ListenableFuture<List<Void>> doReport(
            List<Uri> reportingUris, ReportInteractionInput inputParams) {
        LogUtil.i(reportingUris.toString());
        List<ListenableFuture<Void>> reportingFuturesList = new ArrayList<>();
        String interactionData = inputParams.getInteractionData();

        for (Uri uri : reportingUris) {
            reportingFuturesList.add(mAdServicesHttpsClient.postPlainText(uri, interactionData));
        }
        return Futures.allAsList(reportingFuturesList);
    }
}
