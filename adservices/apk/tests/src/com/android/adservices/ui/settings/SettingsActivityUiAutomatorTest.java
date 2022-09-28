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
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityUiAutomatorTest {
    private static final String BASIC_SAMPLE_PACKAGE = "android.test.adservices.ui.SETTINGS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;
    private static Context sContext;
    private static Intent sIntent;
    static ViewModelProvider sViewModelProvider = Mockito.mock(ViewModelProvider.class);
    static ConsentManager sConsentManager;
    private MockitoSession mStaticMockSession;
    private PhFlags mPhFlags;

    /**
     * This is used by {@link AdServicesSettingsActivityWrapper}. Provides a mocked {@link
     * ViewModelProvider} that serves mocked view models, which use a mocked {@link ConsentManager},
     * which gives mocked data.
     *
     * @return the mocked {@link ViewModelProvider}
     */
    public ViewModelProvider generateMockedViewModelProvider() {
        sConsentManager =
                spy(ConsentManager.getInstance(ApplicationProvider.getApplicationContext()));
        List<Topic> tempList = new ArrayList<>();
        tempList.add(Topic.create(10001, 1, 1));
        tempList.add(Topic.create(10002, 1, 1));
        tempList.add(Topic.create(10003, 1, 1));
        ImmutableList<Topic> topicsList = ImmutableList.copyOf(tempList);
        doReturn(topicsList).when(sConsentManager).getKnownTopicsWithConsent();

        tempList = new ArrayList<>();
        tempList.add(Topic.create(10004, 1, 1));
        tempList.add(Topic.create(10005, 1, 1));
        ImmutableList<Topic> blockedTopicsList = ImmutableList.copyOf(tempList);
        doReturn(blockedTopicsList).when(sConsentManager).getTopicsWithRevokedConsent();

        List<App> appTempList = new ArrayList<>();
        appTempList.add(App.create("app1"));
        appTempList.add(App.create("app2"));
        ImmutableList<App> appsList = ImmutableList.copyOf(appTempList);
        doReturn(appsList).when(sConsentManager).getKnownAppsWithConsent();

        appTempList = new ArrayList<>();
        appTempList.add(App.create("app3"));
        ImmutableList<App> blockedAppsList = ImmutableList.copyOf(appTempList);
        doReturn(blockedAppsList).when(sConsentManager).getAppsWithRevokedConsent();

        doNothing().when(sConsentManager).resetTopicsAndBlockedTopics();
        doNothing().when(sConsentManager).resetTopics();
        doNothing().when(sConsentManager).revokeConsentForTopic(any(Topic.class));
        doNothing().when(sConsentManager).restoreConsentForTopic(any(Topic.class));
        try {
            doNothing().when(sConsentManager).resetAppsAndBlockedApps();
            doNothing().when(sConsentManager).resetApps();
            doNothing().when(sConsentManager).revokeConsentForApp(any(App.class));
            doNothing().when(sConsentManager).restoreConsentForApp(any(App.class));
        } catch (IOException e) {
            e.printStackTrace();
        }
        doNothing().when(sConsentManager).resetMeasurement();

        TopicsViewModel topicsViewModel =
                new TopicsViewModel(ApplicationProvider.getApplicationContext(), sConsentManager);
        AppsViewModel appsViewModel =
                new AppsViewModel(ApplicationProvider.getApplicationContext(), sConsentManager);
        MainViewModel mainViewModel =
                new MainViewModel(ApplicationProvider.getApplicationContext(), sConsentManager);
        doReturn(topicsViewModel).when(sViewModelProvider).get(TopicsViewModel.class);
        doReturn(mainViewModel).when(sViewModelProvider).get(MainViewModel.class);
        doReturn(appsViewModel).when(sViewModelProvider).get(AppsViewModel.class);
        return sViewModelProvider;
    }

    @BeforeClass
    public static void classSetup() {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Launch the app
        sContext = ApplicationProvider.getApplicationContext();
        sIntent = new Intent(BASIC_SAMPLE_PACKAGE);
        sIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(PhFlags.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        mPhFlags = spy(PhFlags.getInstance());
        doReturn(true).when(mPhFlags).getUIDialogsFeatureEnabled();
        ExtendedMockito.doReturn(mPhFlags).when(PhFlags::getInstance);

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // launch
        sContext.startActivity(sIntent);

        // Wait for the app to appear
        sDevice.wait(Until.hasObject(By.pkg(BASIC_SAMPLE_PACKAGE).depth(0)), LAUNCH_TIMEOUT);

        // set consent to true if not
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isTrue();
        if (!mainSwitch.isChecked()) mainSwitch.click();
        assertThat(mainSwitch.isChecked()).isTrue();
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
        assertThat(mainSwitch.isChecked()).isFalse();

        // reset to opted in
        mainSwitch.click();
        assertThat(mainSwitch.isChecked()).isTrue();

        // click switch
        mainSwitch.click();
        dialogTitle = getElement(R.string.settingsUI_dialog_opt_out_title);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel
        negativeText.click();
        assertThat(mainSwitch.isChecked()).isTrue();
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
        verify(sConsentManager).revokeConsentForTopic(any(Topic.class));
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
        verify(sConsentManager).revokeConsentForTopic(any(Topic.class));
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
        verify(sConsentManager).restoreConsentForTopic(any(Topic.class));
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
        verify(sConsentManager).resetTopics();

        // click reset again
        scrollToAndClick(R.string.settingsUI_reset_topics_title);
        dialogTitle = getElement(R.string.settingsUI_dialog_reset_topic_message);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(sConsentManager).resetTopics();
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
        verify(sConsentManager).revokeConsentForApp(any(App.class));
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
        verify(sConsentManager).revokeConsentForApp(any(App.class));
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
        verify(sConsentManager).restoreConsentForApp(any(App.class));
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
        verify(sConsentManager).resetApps();

        // click reset again
        scrollToAndClick(R.string.settingsUI_reset_apps_title);
        dialogTitle = getElement(R.string.settingsUI_dialog_reset_app_message);
        UiObject negativeText = getElement(R.string.settingsUI_dialog_negative_text);
        assertThat(dialogTitle.exists()).isTrue();
        assertThat(negativeText.exists()).isTrue();

        // cancel and verify it has still only been called once
        negativeText.click();
        verify(sConsentManager).resetApps();
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }
}
