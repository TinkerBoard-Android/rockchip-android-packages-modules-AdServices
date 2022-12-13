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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ctssdkprovider.ICtsSdkProviderApi;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CustomizedSdkContextTest {
    private static final String SDK_NAME_1 = "com.android.ctssdkprovider";
    // TODO(b/255937439): Guard the feature with a feature flag
    private static final boolean FEATURE_CUSTOMIZED_CONTEXT_ENABLED = SdkLevel.isAtLeastU();

    @Rule
    public final ActivityScenarioRule<TestActivity> mRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private SdkSandboxManager mSdkSandboxManager;
    private ICtsSdkProviderApi mSdk;

    @Before
    public void setup() {
        assumeTrue(
                "FEATURE_CUSTOMIZED_CONTEXT_ENABLED is not enabled",
                FEATURE_CUSTOMIZED_CONTEXT_ENABLED);
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        mRule.getScenario();
    }

    @After
    public void tearDown() {
        try {
            mSdkSandboxManager.unloadSdk(SDK_NAME_1);
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testStoragePaths() throws Exception {
        loadSdk();
        mSdk.testStoragePaths();
    }

    @Test
    public void testSdkPermissions() throws Exception {
        // Collect list of permissions requested by sdk sandbox
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final PackageInfo sdkSandboxPackage =
                pm.getPackageInfo(
                        pm.getSdkSandboxPackageName(),
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));

        // Verify sdk context has the same permissions granted
        loadSdk();
        for (int i = 0; i < sdkSandboxPackage.requestedPermissions.length; i++) {
            final String permissionName = sdkSandboxPackage.requestedPermissions[i];
            boolean result = mSdk.isPermissionGranted(permissionName);
            assertWithMessage("Sdk does not have permission: " + permissionName)
                    .that(result)
                    .isTrue();
        }
    }

    private void loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        mSdk = ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }
}
