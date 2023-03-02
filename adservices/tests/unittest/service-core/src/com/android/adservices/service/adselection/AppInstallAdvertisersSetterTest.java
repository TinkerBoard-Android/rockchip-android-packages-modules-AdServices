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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.os.LimitExceededException;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAppInstallPermissions;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class AppInstallAdvertisersSetterTest {

    private static final int UID = 42;

    private static final SetAppInstallAdvertisersInput SAMPLE_INPUT =
            getSampleSetAppInstallAdvertisersInputBuilder().build();
    private static final List<DBAppInstallPermissions> DB_WRITE_FOR_SAMPLE_INPUT =
            SAMPLE_INPUT.getAdvertisers().stream()
                    .map(
                            x ->
                                    new DBAppInstallPermissions.Builder()
                                            .setBuyer(x)
                                            .setPackageName(SAMPLE_INPUT.getCallerPackageName())
                                            .build())
                    .collect(Collectors.toList());

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private final ListeningExecutorService mExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    @Mock private AppInstallDao mAppInstallDao;
    @Mock private AdServicesLogger mAdServicesLogger;
    @Mock private Flags mFlags;
    @Mock private FledgeServiceFilter mFledgeServiceFilter;

    @Mock private ConsentManager mConsentManager;

    private AppInstallAdvertisersSetter mAppInstallAdvertisersSetter;

    @Before
    public void setup() {
        mAppInstallAdvertisersSetter =
                new AppInstallAdvertisersSetter(
                        mAppInstallDao,
                        mExecutorService,
                        mAdServicesLogger,
                        mFlags,
                        mFledgeServiceFilter,
                        mConsentManager,
                        UID);
        when(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false);
    }

    @Test
    public void testSetAppInstallAdvertisersSuccess() throws Exception {
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        verify(mFledgeServiceFilter)
                .filterRequest(
                        null,
                        CommonFixture.TEST_PACKAGE_NAME_1,
                        true,
                        false,
                        UID,
                        AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                        Throttler.ApiKey.FLEDGE_API_SET_APP_INSTALL_ADVERTISERS);
        assertTrue(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mAppInstallDao)
                .setAdTechsForPackage(
                        eq(CommonFixture.TEST_PACKAGE_NAME_1), eq(DB_WRITE_FOR_SAMPLE_INPUT));
    }

    @Test
    public void testSetAppInstallAdvertisersFailure() throws Exception {
        doThrow(new RuntimeException()).when(mAppInstallDao).setAdTechsForPackage(any(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testSetAppInstallAdvertisersRevokedConsent() throws Exception {
        when(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(true);
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        verify(mConsentManager)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        SAMPLE_INPUT.getCallerPackageName());
        assertTrue(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
        verifyNoMoreInteractions(mAppInstallDao);
    }

    @Test
    public void testSetAppInstallAdvertisersBadInput() throws Exception {
        SetAppInstallAdvertisersInput input =
                getSampleSetAppInstallAdvertisersInputBuilder()
                        .setAdvertisers(
                                new HashSet<>(Arrays.asList(CommonFixture.INVALID_EMPTY_BUYER)))
                        .build();
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(input);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        verifyNoMoreInteractions(mAppInstallDao);
        // Consent should be checked after validations are run
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersBackgroundCaller() throws Exception {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mFledgeServiceFilter)
                .filterRequest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_BACKGROUND_CALLER);
        verifyNoMoreInteractions(mAppInstallDao);
        // Consent should be checked after foreground check
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersAppNotAllowed() throws Exception {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mFledgeServiceFilter)
                .filterRequest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED);
        verifyNoMoreInteractions(mAppInstallDao);
        // Consent should be checked after app permissions check
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersUidMismatch() throws Exception {
        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mFledgeServiceFilter)
                .filterRequest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        verifyNoMoreInteractions(mAppInstallDao);
        // Consent should be checked after package name check
        verifyNoMoreInteractions(mConsentManager);
    }

    @Test
    public void testSetAppInstallAdvertisersLimitExceeded() throws Exception {
        doThrow(new LimitExceededException())
                .when(mFledgeServiceFilter)
                .filterRequest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any());
        SetAppInstallAdvertisersTestCallback callback = callSetAppInstallAdvertisers(SAMPLE_INPUT);

        assertFalse(callback.mIsSuccess);
        verifyLog(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED);
        verifyNoMoreInteractions(mAppInstallDao);
        // Consent should be checked after throttling
        verifyNoMoreInteractions(mConsentManager);
    }

    private void verifyLog(int status) {
        verify(mAdServicesLogger)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS, status, 0);
    }

    private SetAppInstallAdvertisersTestCallback callSetAppInstallAdvertisers(
            SetAppInstallAdvertisersInput request) throws Exception {
        CountDownLatch logLatch = new CountDownLatch(1);
        CountDownLatch callbackLatch = new CountDownLatch(1);
        SetAppInstallAdvertisersTestCallback callback =
                new SetAppInstallAdvertisersTestCallback(callbackLatch);
        callSetAppInstallAdvertisers(request, callback, callbackLatch, logLatch);
        return callback;
    }

    private void callSetAppInstallAdvertisers(
            SetAppInstallAdvertisersInput request,
            SetAppInstallAdvertisersCallback.Stub callback,
            CountDownLatch callbackLatch,
            CountDownLatch logLatch)
            throws Exception {

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    logLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        mAppInstallAdvertisersSetter.setAppInstallAdvertisers(request, callback);
        callbackLatch.await(5, TimeUnit.SECONDS);
        logLatch.await(5, TimeUnit.SECONDS);
    }

    private static SetAppInstallAdvertisersInput.Builder
            getSampleSetAppInstallAdvertisersInputBuilder() {
        return new SetAppInstallAdvertisersInput.Builder()
                .setAdvertisers(CommonFixture.BUYER_SET)
                .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    public static class SetAppInstallAdvertisersTestCallback
            extends SetAppInstallAdvertisersCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public SetAppInstallAdvertisersTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}