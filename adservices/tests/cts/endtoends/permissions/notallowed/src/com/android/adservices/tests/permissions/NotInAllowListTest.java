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

package com.android.adservices.tests.permissions;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(JUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class NotInAllowListTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String CALLER_NOT_ALLOWED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Caller is not allowed.";

    @Before
    public void setup() {
        overrideConsentManagerDebugMode(true);
        overridingAdservicesLoggingLevel("VERBOSE");
        overrideAdservicesGlobalKillSwitch(true);
    }

    @After
    public void teardown() {
        overrideConsentManagerDebugMode(false);
        overrideAdservicesGlobalKillSwitch(false);
    }

    @Test
    public void testNotInAllowList() {
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient1.getTopics().get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_ALLOWED);
    }

    private void overridingAdservicesLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.adservices %s", loggingLevel);
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode(boolean isGiven) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.consent_manager_debug_mode " + isGiven);
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    // If isOverride = true, override global_kill_switch to OFF to allow adservices
    // If isOverride = false, override global_kill_switch to meaningless value so that PhFlags will
    // use the default value.
    private void overrideAdservicesGlobalKillSwitch(boolean isOverride) {
        String overrideString = isOverride ? "false" : "null";
        ShellUtils.runShellCommand("setprop debug.adservices.global_kill_switch " + overrideString);
    }
}
