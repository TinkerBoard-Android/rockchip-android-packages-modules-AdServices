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

package com.android.adservices.customaudience;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

public class CustomAudienceServiceTest {

    private final Flags mFlagsWithCustomAudienceSwitchOn = new FlagsWithKillSwitchOn();
    private final Flags mFlagsWithCustomAudienceSwitchOff = new FlagsWithKillSwitchOff();

    @Mock private CustomAudienceServiceImpl mMockCustomAudienceServiceImpl;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private PackageManager mPackageManagerMock;

    private MockitoSession mStaticMockSession;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(ConsentManager.class)
                        .spyStatic(CustomAudienceServiceImpl.class)
                        .mockStatic(MddJobService.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testBindableCustomAudienceServiceKillSwitchOn() {
        CustomAudienceService customAudienceService =
                new CustomAudienceService(mFlagsWithCustomAudienceSwitchOn);
        customAudienceService.onCreate();
        IBinder binder = customAudienceService.onBind(getIntentForCustomAudienceService());
        assertNull(binder);

        verify(mConsentManagerMock, never()).getConsent(any());
        verify(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()), never());
    }

    @Test
    public void testBindableCustomAudienceServiceKillSwitchOff() {
        doReturn(mMockCustomAudienceServiceImpl)
                .when(() -> CustomAudienceServiceImpl.create(any(Context.class)));
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any(Context.class)));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(true).when(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));

        CustomAudienceService customAudienceServiceSpy =
                new CustomAudienceService(mFlagsWithCustomAudienceSwitchOff);

        spyOn(customAudienceServiceSpy);
        doReturn(mPackageManagerMock).when(customAudienceServiceSpy).getPackageManager();

        customAudienceServiceSpy.onCreate();
        IBinder binder = customAudienceServiceSpy.onBind(getIntentForCustomAudienceService());
        assertNotNull(binder);

        verify(mConsentManagerMock).getConsent(any());
        verify(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));
    }

    private Intent getIntentForCustomAudienceService() {
        return new Intent(ApplicationProvider.getApplicationContext(), CustomAudienceService.class);
    }

    private static class FlagsWithKillSwitchOn implements Flags {
        @Override
        public boolean getFledgeCustomAudienceServiceKillSwitch() {
            return true;
        }
    }

    private static class FlagsWithKillSwitchOff implements Flags {
        @Override
        public boolean getFledgeCustomAudienceServiceKillSwitch() {
            return false;
        }
    }
}
