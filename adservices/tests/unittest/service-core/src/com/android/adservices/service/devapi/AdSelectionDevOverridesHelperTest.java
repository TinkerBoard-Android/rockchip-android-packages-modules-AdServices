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

package com.android.adservices.service.devapi;

import static com.android.adservices.service.devapi.AdSelectionDevOverridesHelper.calculateAdSelectionConfigId;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionOverride;

import org.junit.Before;
import org.junit.Test;

public class AdSelectionDevOverridesHelperTest {
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfig();
    private static final String AD_SELECTION_CONFIG_ID =
            AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(AD_SELECTION_CONFIG);
    private static final String APP_PACKAGE_NAME = "com.test.app";
    private static final String DECISION_LOGIC_JS = "function test() {return 'hello';}";
    private AdSelectionEntryDao mAdSelectionEntryDao;

    @Before
    public void setUp() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
    }

    @Test
    public void testCalculateAdSelectionIdGeneratesSameIdForTheSameObject() {
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        assertThat(calculateAdSelectionConfigId(adSelectionConfig))
                .isEqualTo(calculateAdSelectionConfigId(adSelectionConfig));
    }

    @Test
    public void testCalculateAdSelectionIdGeneratesDifferentIdsForDifferentData() {
        assertThat(
                        calculateAdSelectionConfigId(
                                AdSelectionConfigFixture.anAdSelectionConfigBuilder().build()))
                .isNotEqualTo(
                        calculateAdSelectionConfigId(
                                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                                        .setSeller("another seller")
                                        .build()));
    }

    @Test
    public void testGetDecisionLogicOverrideFindsMatchingOverride() {
        mAdSelectionEntryDao.persistAdSelectionOverride(
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setDecisionLogicJS(DECISION_LOGIC_JS)
                        .build());

        DevContext devContext =
                DevContext.builder()
                        .setCallingAppPackageName(APP_PACKAGE_NAME)
                        .setDevOptionsEnabled(true)
                        .build();

        AdSelectionDevOverridesHelper helper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);

        assertThat(helper.getDecisionLogicOverride(AD_SELECTION_CONFIG))
                .isEqualTo(DECISION_LOGIC_JS);
    }

    @Test
    public void testGetDecisionLogicOverrideReturnsNullIfDevOptionsAreDisabled() {
        mAdSelectionEntryDao.persistAdSelectionOverride(
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setDecisionLogicJS(DECISION_LOGIC_JS)
                        .build());

        DevContext devContext = DevContext.createForDevOptionsDisabled();

        AdSelectionDevOverridesHelper helper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);

        assertThat(helper.getDecisionLogicOverride(AD_SELECTION_CONFIG)).isNull();
    }

    @Test
    public void testGetDecisionLogicOverrideReturnsNullIfTheOverrideBelongsToAnotherApp() {
        mAdSelectionEntryDao.persistAdSelectionOverride(
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setDecisionLogicJS(DECISION_LOGIC_JS)
                        .build());

        DevContext devContext =
                DevContext.builder()
                        .setCallingAppPackageName(APP_PACKAGE_NAME + ".different")
                        .setDevOptionsEnabled(true)
                        .build();

        AdSelectionDevOverridesHelper helper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);

        assertThat(helper.getDecisionLogicOverride(AD_SELECTION_CONFIG)).isNull();
    }
}
