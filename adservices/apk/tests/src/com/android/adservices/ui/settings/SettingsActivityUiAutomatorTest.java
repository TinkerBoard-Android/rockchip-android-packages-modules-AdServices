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

package com.android.adservices.ui.settings;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityUiAutomatorTest {
    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.test.adservices.ui.MAIN";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;
    static ViewModelProvider sViewModelProvider = Mockito.mock(ViewModelProvider.class);
    static ConsentManager sConsentManager;
    private MockitoSession mStaticMockSession;
    private PhFlags mPhFlags;
    private ConsentManager mConsentManager;
    @Mock Flags mMockFlags;

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        // Static mocking
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(PhFlags.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(ConsentManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // prepare objects used by static mocking
        mConsentManager =
                spy(ConsentManager.getInstance(ApplicationProvider.getApplicationContext()));
        List<Topic> tempList = new ArrayList<>();
        tempList.add(Topic.create(10001, 1, 1));
        tempList.add(Topic.create(10002, 1, 1));
        tempList.add(Topic.create(10003, 1, 1));
        ImmutableList<Topic> topicsList = ImmutableList.copyOf(tempList);
        doReturn(topicsList).when(mConsentManager).getKnownTopicsWithConsent();

        tempList = new ArrayList<>();
        tempList.add(Topic.create(10004, 1, 1));
        tempList.add(Topic.create(10005, 1, 1));
        ImmutableList<Topic> blockedTopicsList = ImmutableList.copyOf(tempList);
        doReturn(blockedTopicsList).when(mConsentManager).getTopicsWithRevokedConsent();

        List<App> appTempList = new ArrayList<>();
        appTempList.add(App.create("app1"));
        appTempList.add(App.create("app2"));
        ImmutableList<App> appsList = ImmutableList.copyOf(appTempList);
        doReturn(appsList).when(mConsentManager).getKnownAppsWithConsent();

        appTempList = new ArrayList<>();
        appTempList.add(App.create("app3"));
        ImmutableList<App> blockedAppsList = ImmutableList.copyOf(appTempList);
        doReturn(blockedAppsList).when(mConsentManager).getAppsWithRevokedConsent();

        doNothing().when(mConsentManager).resetTopicsAndBlockedTopics();
        doNothing().when(mConsentManager).resetTopics();
        doNothing().when(mConsentManager).revokeConsentForTopic(any(Topic.class));
        doNothing().when(mConsentManager).restoreConsentForTopic(any(Topic.class));
        try {
            doNothing().when(mConsentManager).resetAppsAndBlockedApps();
            doNothing().when(mConsentManager).resetApps();
            doNothing().when(mConsentManager).revokeConsentForApp(any(App.class));
            doNothing().when(mConsentManager).restoreConsentForApp(any(App.class));
        } catch (IOException e) {
            e.printStackTrace();
        }
        doNothing().when(mConsentManager).resetMeasurement();

        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        mPhFlags = spy(PhFlags.getInstance());
        doReturn(true).when(mPhFlags).getUIDialogsFeatureEnabled();
        ExtendedMockito.doReturn(mPhFlags).when(PhFlags::getInstance);
        ExtendedMockito.doReturn(mConsentManager)
                .when(() -> ConsentManager.getInstance(any(Context.class)));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
        doNothing().when(mConsentManager).enable(any(Context.class));
        doNothing().when(mConsentManager).disable(any(Context.class));
        startActivityFromHomeAndCheckMainSwitch();
    }

    private void startActivityFromHomeAndCheckMainSwitch() throws UiObjectNotFoundException {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // launch app
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(PRIVACY_SANDBOX_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(
                Until.hasObject(By.pkg(PRIVACY_SANDBOX_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    private void scrollToAndClick(int resId) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject element =
                sDevice.findObject(
                        new UiSelector().childSelector(new UiSelector().text(getString(resId))));
        scrollView.scrollIntoView(element);
        element.click();
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }

    private UiObject getElement(int resId, int index) {
        return sDevice.findObject(new UiSelector().text(getString(resId)).instance(index));
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    @Test
    public void optOutDialogTest() throws UiObjectNotFoundException {
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isTrue();

        // click switch
        mainSwitch.click();
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_opt_out_title);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_opt_out_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();

        // click switch
        mainSwitch.click();
        dialogTitle = getElement(R.string.settingsUI_dialog_opt_out_title);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel
        negativeText.click();
    }

    @Test
    public void blockTopicDialogTest() throws UiObjectNotFoundException {
        // open topics view
        scrollToAndClick(R.string.settingsUI_topics_title);
        UiObject blockTopicText = getElement(R.string.settingsUI_block_topic_title, 0);
        assertThat(blockTopicText.exists()).isTrue();

        // click block
        blockTopicText.click();
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_block_topic_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_block_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).revokeConsentForTopic(any(Topic.class));
        blockTopicText = getElement(R.string.settingsUI_block_topic_title, 0);
        assertThat(blockTopicText.exists()).isTrue();

        // click block again
        blockTopicText.click();
        dialogTitle = getElement(R.string.settingsUI_dialog_block_topic_message);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).revokeConsentForTopic(any(Topic.class));
    }

    @Test
    public void unblockTopicDialogTest() throws UiObjectNotFoundException {
        // open topics view
        scrollToAndClick(R.string.settingsUI_topics_title);

        // open blocked topics view
        scrollToAndClick(R.string.settingsUI_blocked_topics_title);
        UiObject unblockTopicText = getElement(R.string.settingsUI_unblock_topic_title, 0);
        assertThat(unblockTopicText.exists()).isTrue();

        // click unblock
        unblockTopicText.click();
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_unblock_topic_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_unblock_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).restoreConsentForTopic(any(Topic.class));
        unblockTopicText = getElement(R.string.settingsUI_unblock_topic_title, 0);
        assertThat(unblockTopicText.exists()).isTrue();
    }

    @Test
    public void resetTopicDialogTest() throws UiObjectNotFoundException {
        // open topics view
        scrollToAndClick(R.string.settingsUI_topics_title);

        // click reset
        scrollToAndClick(R.string.settingsUI_reset_topics_title);
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_reset_topic_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_reset_topic_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).resetTopics();

        // click reset again
        scrollToAndClick(R.string.settingsUI_reset_topics_title);
        dialogTitle = getElement(R.string.settingsUI_dialog_reset_topic_message);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).resetTopics();
    }

    @Test
    public void blockAppDialogTest() throws UiObjectNotFoundException, IOException {
        // open apps view
        scrollToAndClick(R.string.settingsUI_apps_title);
        UiObject blockAppText = getElement(R.string.settingsUI_block_app_title, 0);
        assertThat(blockAppText.exists()).isTrue();

        // click block
        blockAppText.click();
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_block_app_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_block_app_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).revokeConsentForApp(any(App.class));
        blockAppText = getElement(R.string.settingsUI_block_app_title, 0);
        assertThat(blockAppText.exists()).isTrue();

        // click block again
        blockAppText.click();
        dialogTitle = getElement(R.string.settingsUI_dialog_block_app_message);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).revokeConsentForApp(any(App.class));
    }

    @Test
    public void unblockAppDialogTest() throws UiObjectNotFoundException, IOException {
        // open apps view
        scrollToAndClick(R.string.settingsUI_apps_title);

        // open blocked apps view
        scrollToAndClick(R.string.settingsUI_blocked_apps_title);
        UiObject unblockAppText = getElement(R.string.settingsUI_unblock_app_title, 0);
        assertThat(unblockAppText.exists()).isTrue();

        // click unblock
        unblockAppText.click();
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_unblock_app_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_unblock_app_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).restoreConsentForApp(any(App.class));
        unblockAppText = getElement(R.string.settingsUI_unblock_app_title, 0);
        assertThat(unblockAppText.exists()).isTrue();
    }

    @Test
    public void resetAppDialogTest() throws UiObjectNotFoundException, IOException {
        // open apps view
        scrollToAndClick(R.string.settingsUI_apps_title);

        // click reset
        scrollToAndClick(R.string.settingsUI_reset_apps_title);
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_reset_app_message);
        UiObject positiveText = getElement(R.string.settingsUI_dialog_reset_app_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // confirm
        positiveText.click();
        verify(mConsentManager).resetApps();

        // click reset again
        scrollToAndClick(R.string.settingsUI_reset_apps_title);
        dialogTitle = getElement(R.string.settingsUI_dialog_reset_app_message);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).resetApps();
    }

    @Test
    public void resetMeasurementDialogTest() throws UiObjectNotFoundException {
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();

        startActivityFromHomeAndCheckMainSwitch();
        // open measurement view
        scrollToAndClick(R.string.settingsUI_measurement_view_title);

        // click reset
        scrollToAndClick(R.string.settingsUI_measurement_view_reset_title);
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_reset_measurement_title);
        UiObject positiveText =
                getElement(R.string.settingsUI_dialog_reset_measurement_positive_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(positiveText.exists()).isTrue();

        // click positive button and confirm mConsentManager.resetMeasurement is called
        positiveText.click();
        verify(mConsentManager).resetMeasurement();

        // click reset again
        scrollToAndClick(R.string.settingsUI_measurement_view_reset_title);
        dialogTitle = getElement(R.string.settingsUI_dialog_reset_measurement_title);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // click cancel and verify it has still only been called once
        negativeText.click();
        verify(mConsentManager).resetMeasurement();
    }

    @Test
    public void disableMeasurementTest() throws UiObjectNotFoundException {
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();
        // start the activity again to reflect the GaUxFeature flag change
        startActivityFromHomeAndCheckMainSwitch();
        // the entry point of ads measurement should be hidden
        UiObject adsMeasurementTitle = getElement(R.string.settingsUI_measurement_view_title);
        assertThat(adsMeasurementTitle.exists()).isFalse();
    }

    @Test
    public void disableDialogFeatureTest() throws UiObjectNotFoundException {
        doReturn(false).when(mPhFlags).getUIDialogsFeatureEnabled();
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isTrue();

        // click switch
        mainSwitch.click();
        UiObject dialogTitle = getElement(R.string.settingsUI_dialog_opt_out_title);
        assertThat(dialogTitle.exists()).isFalse();

        // open topics view
        scrollToAndClick(R.string.settingsUI_topics_title);
        UiObject blockTopicText = getElement(R.string.settingsUI_block_topic_title, 0);
        assertThat(blockTopicText.exists()).isTrue();

        // block topic
        blockTopicText.click();
        dialogTitle = getElement(R.string.settingsUI_dialog_block_topic_message);
        assertThat(dialogTitle.exists()).isFalse();
        verify(mConsentManager).revokeConsentForTopic(any(Topic.class));

        // reset topic
        scrollToAndClick(R.string.settingsUI_reset_topics_title);
        dialogTitle = getElement(R.string.settingsUI_dialog_reset_topic_message);
        assertThat(dialogTitle.exists()).isFalse();
        verify(mConsentManager).resetTopics();

        // open unblock topic view
        scrollToAndClick(R.string.settingsUI_blocked_topics_title);
        UiObject unblockTopicText = getElement(R.string.settingsUI_unblock_topic_title, 0);
        assertThat(unblockTopicText.exists()).isTrue();

        // click unblock
        unblockTopicText.click();
        dialogTitle = getElement(R.string.settingsUI_dialog_unblock_topic_message);
        assertThat(dialogTitle.exists()).isFalse();
        verify(mConsentManager).restoreConsentForTopic(any(Topic.class));
    }

    /**
     * Test for the Button to show blocked topics when the list of Topics is Empty The Button should
     * be disabled if blocked topics is empty
     *
     * @throws UiObjectNotFoundException
     */
    @Test
    public void blockedTopicsWhenEmptyStateButtonTest() throws UiObjectNotFoundException {
        // Return an empty topics list
        doReturn(ImmutableList.of()).when(mConsentManager).getKnownTopicsWithConsent();
        // Return a non-empty blocked topics list
        List<Topic> tempList = new ArrayList<>();
        tempList.add(Topic.create(10004, 1, 1));
        tempList.add(Topic.create(10005, 1, 1));
        ImmutableList<Topic> blockedTopicsList = ImmutableList.copyOf(tempList);
        doReturn(blockedTopicsList).when(mConsentManager).getTopicsWithRevokedConsent();
        // navigate to topics page
        scrollToAndClick(R.string.settingsUI_topics_title);
        UiObject blockedTopicsWhenEmptyStateButton =
                sDevice.findObject(
                        new UiSelector()
                                .className("android.widget.Button")
                                .text(getString(R.string.settingsUI_blocked_topics_title)));

        assertThat(blockedTopicsWhenEmptyStateButton.isEnabled()).isTrue();
    }
}
