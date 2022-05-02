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

package com.android.adservices.service.adselection;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

public class CustomAudienceBiddingInfoTest {
    private static final Uri BIDDING_LOGIC_URL = CustomAudienceFixture.VALID_BIDDING_LOGIC_URL;
    private static final String BUYER_DECISION_LOGIC_JS = "buyer_decision_logic_javascript";
    private static final String OWNER = "owner";
    private static final String BUYER = "buyer";
    private static final String NAME = "name";
    private static final Instant NOW = Instant.now();
    private static final Instant ACTIVATION_TIME = NOW;
    private static final Instant EXPIRATION_TIME = NOW.plus(Duration.ofDays(1));
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            new CustomAudienceSignals.Builder()
                    .setOwner(OWNER)
                    .setBuyer(BUYER)
                    .setName(NAME)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .build();
    private static final DBCustomAudience CUSTOM_AUDIENCE =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER)
                    .setBuyer(BUYER)
                    .setName(NAME)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                    .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                    .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(
                            new DBTrustedBiddingData.Builder()
                                    .setUrl(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_URL)
                                    .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                    .build())
                    .setBiddingLogicUrl(BIDDING_LOGIC_URL)
                    .setAds(
                            AdDataFixture.VALID_ADS.stream()
                                    .map(DBAdData::fromServiceObject)
                                    .collect(Collectors.toList()))
                    .build();

    @Test
    public void testCustomAudienceBiddingInfo() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        BIDDING_LOGIC_URL, BUYER_DECISION_LOGIC_JS, CUSTOM_AUDIENCE_SIGNALS);
        assertEquals(customAudienceBiddingInfo.getBiddingLogicUrl(), BIDDING_LOGIC_URL);
        assertEquals(customAudienceBiddingInfo.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
    }

    @Test
    public void testCustomAudienceBiddingInfoFromCA() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(CUSTOM_AUDIENCE, BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getBiddingLogicUrl(), BIDDING_LOGIC_URL);
        assertEquals(customAudienceBiddingInfo.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
    }

    @Test
    public void testCustomAudienceBiddingInfoBuilder() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.builder()
                        .setBiddingLogicUrl(BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .build();
        assertEquals(customAudienceBiddingInfo.getBiddingLogicUrl(), BIDDING_LOGIC_URL);
        assertEquals(customAudienceBiddingInfo.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
    }
}
