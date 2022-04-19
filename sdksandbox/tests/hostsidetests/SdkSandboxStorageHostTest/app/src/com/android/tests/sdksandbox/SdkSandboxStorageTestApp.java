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

package com.android.tests.sdksandbox;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeRemoteSdkCallback;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxStorageTestApp {

    private static final String CODE_PROVIDER_PACKAGE =
            "com.android.tests.codeprovider.storagetest";

    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";

    private static Context sContext;

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        sContext = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = sContext.getSystemService(
                SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
    }

    // Run a phase of the test inside the code loaded for this app
    private void runPhaseInsideCode(String phaseName) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_KEY_PHASE_NAME, phaseName);
        mSdkSandboxManager.requestSurfacePackage(CODE_PROVIDER_PACKAGE, 0, 500, 500, bundle);
    }

    @Test
    public void testSdkDataPackageDirectory_SharedStorageIsUsable() throws Exception {
        // First load code
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mSdkSandboxManager.loadSdk(CODE_PROVIDER_PACKAGE, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Run phase inside the code
        runPhaseInsideCode("testSdkDataPackageDirectory_SharedStorageIsUsable");

        // Wait for code to finish handling the request
        assertThat(callback.isRequestSurfacePackageSuccessful()).isFalse();
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkDirs() throws Exception {
        // First load code
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mSdkSandboxManager.loadSdk(CODE_PROVIDER_PACKAGE, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void testSdkDataIsAttributedToApp() throws Exception {
        // First load sdk
        FakeRemoteSdkCallback callback = new FakeRemoteSdkCallback();
        mSdkSandboxManager.loadSdk(CODE_PROVIDER_PACKAGE, new Bundle(), Runnable::run, callback);
        // Wait for sdk to finish loading
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        final StorageStatsManager stats = InstrumentationRegistry.getInstrumentation().getContext()
                                                .getSystemService(StorageStatsManager.class);
        int uid = Process.myUid();
        UserHandle user = Process.myUserHandle();

        // Have the sdk use up space
        final StorageStats initialAppStats = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats initialUserStats = stats.queryStatsForUser(UUID_DEFAULT, user);

        runPhaseInsideCode("testSdkDataIsAttributedToApp");

        // Wait for sdk to finish handling the request
        callback.isRequestSurfacePackageSuccessful();
        final StorageStats finalAppStats = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats finalUserStats = stats.queryStatsForUser(UUID_DEFAULT, user);

        // Verify the space used with a few hundred kilobytes error margin
        long deltaAppSize = 2000000;
        long deltaCacheSize = 1000000;
        long errorMarginSize = 100000;
        assertMostlyEquals(deltaAppSize,
                    finalAppStats.getDataBytes() - initialAppStats.getDataBytes(),
                           errorMarginSize);
        assertMostlyEquals(deltaAppSize,
                    finalUserStats.getDataBytes() - initialUserStats.getDataBytes(),
                           errorMarginSize);
        assertMostlyEquals(deltaCacheSize,
                    finalAppStats.getCacheBytes() - initialAppStats.getCacheBytes(),
                           errorMarginSize);
        assertMostlyEquals(deltaCacheSize,
                    finalUserStats.getCacheBytes() - initialUserStats.getCacheBytes(),
                           errorMarginSize);
    }

    private static void assertMostlyEquals(long expected, long actual, long delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionFailedError("Expected roughly " + expected + " but was " + actual);
        }
    }
}
