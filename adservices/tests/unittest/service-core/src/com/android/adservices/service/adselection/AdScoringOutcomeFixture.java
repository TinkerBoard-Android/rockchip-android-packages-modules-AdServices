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

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.CustomAudienceBiddingInfoFixture;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;

public class AdScoringOutcomeFixture {

    public static AdScoringOutcome.Builder anAdScoringBuilder(
            AdTechIdentifier buyerName, Double score) {

        AdBiddingOutcome adBiddingOutcome =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(buyerName, 1.0).build();

        return AdScoringOutcome.builder()
                .setAdWithScore(
                        AdWithScore.builder()
                                .setAdWithBid(adBiddingOutcome.getAdWithBid())
                                .setScore(score)
                                .build())
                .setDecisionLogicUri(
                        adBiddingOutcome.getCustomAudienceBiddingInfo().getBiddingLogicUri())
                .setCustomAudienceSignals(
                        adBiddingOutcome.getCustomAudienceBiddingInfo().getCustomAudienceSignals())
                .setDecisionLogicJs(
                        adBiddingOutcome.getCustomAudienceBiddingInfo().getBuyerDecisionLogicJs())
                .setDecisionLogicJsDownloaded(true)
                .setBuyer(
                        adBiddingOutcome
                                .getCustomAudienceBiddingInfo()
                                .getCustomAudienceSignals()
                                .getBuyer());
    }

    public static AdScoringOutcome.Builder anAdScoringBuilderWithAdCounterKeys(
            AdTechIdentifier buyer, Double score) {
        return AdScoringOutcome.builder()
                .setAdWithScore(
                        AdWithScore.builder()
                                .setAdWithBid(
                                        new AdWithBid(
                                                AdDataFixture.getValidFilterAdDataByBuyer(buyer, 0),
                                                1.0))
                                .setScore(score)
                                .build())
                .setBuyer(buyer)
                .setDecisionLogicUri(CustomAudienceBiddingInfoFixture.VALID_BIDDING_LOGIC_URI)
                .setCustomAudienceSignals(
                        CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                                .setBuyer(buyer)
                                .build())
                .setDecisionLogicJs(CustomAudienceBiddingInfoFixture.BUYER_DECISION_LOGIC_JS)
                .setDecisionLogicJsDownloaded(true);
    }
}
