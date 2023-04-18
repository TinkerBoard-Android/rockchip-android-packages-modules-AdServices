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

package com.android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.AdServicesSettingsUsageReported;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test to check that Ui API logging to StatsD
 *
 * <p>The activity simply called Ui Settings Page which trigger the log event, and then check it in
 * statsD.
 *
 * <p>Instead of extending DeviceTestCase, this JUnit4 test extends {@link IDeviceTest} and is run
 * with tradefed's DeviceJUnit4ClassRunner
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class UiApiLoggingHostTest implements IDeviceTest {
    private static final String CLASS =
            "com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity";
    private static final String TARGET_PACKAGE = "com.google.android.adservices.api";
    private static final String TARGET_PACKAGE_AOSP = "com.android.adservices.api";

    @Rule public TestMetrics mMetrics = new TestMetrics();

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(getDevice().getApiLevel() >= 33);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        disableGlobalKillSwitch();

        disableMddBackgroundTasks(true);
        overrideDisableTopicsEnrollmentCheck(/* enrolmentCheckFlag */ "1");
        stopPacakageAPI();
    }

    @After
    public void tearDown() throws Exception {
        disableMddBackgroundTasks(false);
        stopPacakageAPI();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    // TODO(b/245400146): Get package Name for Topics API instead of running the test twice.
    @Test
    public void testStartSettingMainActivityAndGetUiLog() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);

        startSettingMainActivity(TARGET_PACKAGE, device, /* isAosp */ false);

        // Fetch a list of happened log events and their data
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(device);

        // Adservice Package Name is different in aosp and non-aosp devices. Attempt again with the
        // other
        // package Name if it fails at the first time;
        if (data.isEmpty()) {
            ConfigUtils.removeConfig(getDevice());
            ReportUtils.clearReports(getDevice());
            startSettingMainActivity(TARGET_PACKAGE_AOSP, device, /* isAosp */ true);
            data = ReportUtils.getEventMetricDataList(device);
        }

        // We trigger only one event from activity, should only see one event in the list
        assertThat(data).hasSize(1);

        // Verify the log event data
        AtomsProto.AdServicesSettingsUsageReported adServicesSettingsUsageReported =
                data.get(0).getAtom().getAdServicesSettingsUsageReported();
        assertThat(adServicesSettingsUsageReported.getAction())
                .isEqualTo(
                        AdServicesSettingsUsageReported.AdServiceSettingsName
                                .PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED);
    }

    private void startSettingMainActivity(String apiName, ITestDevice device, boolean isAosp)
            throws Exception {
        // Upload the config.
        final StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(apiName);

        ConfigUtils.addEventMetric(config, Atom.AD_SERVICES_SETTINGS_USAGE_REPORTED_FIELD_NUMBER);
        ConfigUtils.uploadConfig(device, config);
        // Start the ui main activity, it will make a ui log call
        startUiMainActivity(device, isAosp);
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
    }

    // Switch on/off for MDD service. Default value is false, which means MDD is enabled.
    private void disableMddBackgroundTasks(boolean isSwitchedOff)
            throws DeviceNotAvailableException {
        getDevice()
                .executeShellCommand(
                        "device_config put adservices mdd_background_task_kill_switch "
                                + isSwitchedOff);
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val)
            throws DeviceNotAvailableException {
        // Setting it to 1 here disables the Topics' enrollment check.
        getDevice()
                .executeShellCommand(
                        "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }

    // Disable global_kill_switch to ignore the effect of actual PH values.
    private void disableGlobalKillSwitch() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("device_config put adservices global_kill_switch false");
    }

    public void startUiMainActivity(ITestDevice device, boolean isAosp)
            throws DeviceNotAvailableException {
        String packageName = isAosp ? TARGET_PACKAGE_AOSP : TARGET_PACKAGE;
        device.executeShellCommand("am start -n " + packageName + "/" + CLASS);
    }

    public void stopPacakageAPI() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("am force-stop " + TARGET_PACKAGE);
        getDevice().executeShellCommand("am force-stop " + TARGET_PACKAGE_AOSP);
    }
}
