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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.common.AdDataFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.content.Context;
import android.os.RemoteException;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

@RunWith(MockitoJUnitRunner.class)
public class CustomAudienceManagementServiceImplTest {

    private static final Executor DIRECT_EXECUTOR = MoreExecutors.directExecutor();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE = new CustomAudience.Builder()
            .setOwner(CustomAudienceFixture.VALID_OWNER)
            .setBuyer(CustomAudienceFixture.VALID_BUYER)
            .setName(CustomAudienceFixture.VALID_NAME)
            .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
            .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
            .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
            .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
            .setAds(AdDataFixture.VALID_ADS)
            .build();

    @Mock
    private Context mContext;
    @Mock
    private CustomAudienceManagementImpl mCustomAudienceManagement;
    @Mock
    private ICustomAudienceCallback mICustomAudienceCallback;

    private CustomAudienceManagementServiceImpl mService;

    @Before
    public void setup() {
        mService = new CustomAudienceManagementServiceImpl(mContext, mCustomAudienceManagement,
                DIRECT_EXECUTOR);
    }

    @Test
    public void testJoinCustomAudience_runNormally() throws RemoteException {
        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);
        verify(mCustomAudienceManagement).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_nullInput() {
        assertThrows(NullPointerException.class,
                () -> mService.joinCustomAudience(null, mICustomAudienceCallback));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }


    @Test
    public void testJoinCustomAudience_nullCallback() {
        assertThrows(NullPointerException.class,
                () -> mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, null));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_errorCreateCustomAudience()
            throws RemoteException {
        doThrow(RuntimeException.class)
                .when(mCustomAudienceManagement)
                .joinCustomAudience(VALID_CUSTOM_AUDIENCE);

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mCustomAudienceManagement).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onFailure(any(FledgeErrorResponse.class));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testJoinCustomAudience_errorReturnCallback()
            throws RemoteException {
        doThrow(RemoteException.class)
                .when(mICustomAudienceCallback)
                .onSuccess();

        mService.joinCustomAudience(VALID_CUSTOM_AUDIENCE, mICustomAudienceCallback);

        verify(mCustomAudienceManagement).joinCustomAudience(VALID_CUSTOM_AUDIENCE);
        verify(mICustomAudienceCallback).onSuccess();
        verify(mICustomAudienceCallback).onFailure(any(FledgeErrorResponse.class));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }


    @Test
    public void testLeaveCustomAudience_runNormally() throws RemoteException {
        mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceManagement).leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullOwner() {
        assertThrows(NullPointerException.class,
                () -> mService.leaveCustomAudience(null, CustomAudienceFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME, mICustomAudienceCallback));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullBuyer() {
        assertThrows(NullPointerException.class,
                () -> mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER, null,
                        CustomAudienceFixture.VALID_NAME, mICustomAudienceCallback));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullName() {
        assertThrows(NullPointerException.class,
                () -> mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER, null, mICustomAudienceCallback));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_nullCallback() {
        assertThrows(NullPointerException.class,
                () -> mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME, null));
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_errorCallManagementImpl() throws RemoteException {
        doThrow(RuntimeException.class)
                .when(mCustomAudienceManagement)
                .leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME);

        mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceManagement).leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }

    @Test
    public void testLeaveCustomAudience_errorReturnCallback() throws RemoteException {
        doThrow(RemoteException.class)
                .when(mICustomAudienceCallback)
                .onSuccess();

        mService.leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME,
                mICustomAudienceCallback);

        verify(mCustomAudienceManagement).leaveCustomAudience(CustomAudienceFixture.VALID_OWNER,
                CustomAudienceFixture.VALID_BUYER, CustomAudienceFixture.VALID_NAME);
        verify(mICustomAudienceCallback).onSuccess();
        verifyNoMoreInteractions(mCustomAudienceManagement, mICustomAudienceCallback, mContext);
    }
}
