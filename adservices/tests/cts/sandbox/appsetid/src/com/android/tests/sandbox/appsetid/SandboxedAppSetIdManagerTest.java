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

package com.android.tests.sandbox.appsetid;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/*
 * Test AppSetId API running within the Sandbox.
 */
@RunWith(JUnit4.class)
public class SandboxedAppSetIdManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "com.android.tests.providers.appsetidsdk";
    // Used to get the package name. Copied over from com.android.adservices.AdServicesCommon
    private static final String APPSETID_SERVICE_NAME = "android.adservices.APPSETID_SERVICE";

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setup() throws TimeoutException, InterruptedException {
        // Start a foreground activity
        SimpleActivity.startAndWaitForSimpleActivity(sContext, Duration.ofMillis(1000));
        overridingBeforeTest();
    }

    @After
    public void shutDown() {
        overridingAfterTest();
        SimpleActivity.stopSimpleActivity(sContext);
    }

    @Test
    public void loadSdkAndRunAppSetIdApi() throws Exception {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), CALLBACK_EXECUTOR, callback);

        // This verifies that the appsetidsdk in the Sandbox gets back the correct appsetid.
        // If the appsetidsdk did not get correct appsetid, it will trigger the
        // callback.onLoadSdkError
        // callback.isLoadSdkSuccessful returns true if there were no errors.
        assertWithMessage(
                        callback.isLoadSdkSuccessful()
                                ? "Callback was successful"
                                : "Callback failed with message " + callback.getLoadSdkErrorMsg())
                .that(callback.isLoadSdkSuccessful())
                .isTrue();
    }

    private void overridingBeforeTest() {
        overridingAdservicesLoggingLevel("VERBOSE");
        // The setup for this test:
        // SandboxedAppSetIdManagerTest is the test app. It will load the appsetidsdk into the
        // Sandbox.
        // The appsetidsdk (running within the Sandbox) will query AppSetId API and verify that the
        // correct
        // appsetid are returned.
        // After appsetidsdk verifies the result, it will communicate back to the
        // SandboxedAppSetIdManagerTest via the loadSdk's callback.
        // In this test, we use the loadSdk's callback as a 2-way communications between the Test
        // app (this class) and the Sdk running within the Sandbox process.

        overrideAdservicesGlobalKillSwitch(true);
    }

    // Reset back the original values.
    private void overridingAfterTest() {
        overridingAdservicesLoggingLevel("INFO");
        overrideAdservicesGlobalKillSwitch(false);
    }

    private void overridingAdservicesLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.adservices %s", loggingLevel);
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    // If isOverride = true, override global_kill_switch to OFF to allow adservices
    // If isOverride = false, override global_kill_switch to meaningless value so that PhFlags will
    // use the default value.
    private void overrideAdservicesGlobalKillSwitch(boolean isOverride) {
        String overrideString = isOverride ? "false" : "null";
        ShellUtils.runShellCommand("setprop debug.adservices.global_kill_switch " + overrideString);
    }

    // Used to get the package name. Copied over from com.android.adservices.AndroidServiceBinder
    @NonNull
    private static String getAdServicesPackageName() {
        final Intent intent = new Intent(APPSETID_SERVICE_NAME);
        final List<ResolveInfo> resolveInfos =
                sContext.getPackageManager()
                        .queryIntentServices(intent, PackageManager.MATCH_SYSTEM_ONLY);

        if (resolveInfos == null || resolveInfos.isEmpty()) {
            String errorMsg =
                    "Failed to find resolveInfo for adServices service. Intent action: "
                            + APPSETID_SERVICE_NAME;
            throw new IllegalStateException(errorMsg);
        }

        if (resolveInfos.size() > 1) {
            String errorMsg = "Found multiple services for the same intent action. ";
            throw new IllegalStateException(errorMsg);
        }

        final ServiceInfo serviceInfo = resolveInfos.get(0).serviceInfo;
        if (serviceInfo == null) {
            String errorMsg = "Failed to find serviceInfo for adServices service. ";
            throw new IllegalStateException(errorMsg);
        }

        return serviceInfo.packageName;
    }
}