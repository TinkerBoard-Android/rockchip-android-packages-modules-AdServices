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

package com.android.adservices.service.common.compat;

import static com.google.common.truth.Truth.assertThat;

import android.os.Process;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;

public class ProcessCompatUtilsTest {
    private MockitoSession mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Process.class)
                        .mockStatic(SdkLevel.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testIsSdkSandboxUid_onSMinus_notSdkSandboxUid() {
        int uid = 100;
        ExtendedMockito.doReturn(false).when(SdkLevel::isAtLeastT);
        assertThat(ProcessCompatUtils.isSdkSandboxUid(uid)).isFalse();
    }

    @Test
    public void testIsSdkSandboxUid_onT_sdkSandboxUId() {
        int uid = 100;
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandboxUid(uid));
        assertThat(ProcessCompatUtils.isSdkSandboxUid(uid)).isTrue();
    }

    @Test
    public void testIsSdkSandboxUid_onT_notSdkSandboxUId() {
        int uid = 100;
        ExtendedMockito.doReturn(true).when(SdkLevel::isAtLeastT);
        ExtendedMockito.doReturn(false).when(() -> Process.isSdkSandboxUid(uid));
        assertThat(ProcessCompatUtils.isSdkSandboxUid(uid)).isFalse();
    }
}