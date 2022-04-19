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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.net.Uri;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class DBAdSelectionTest {
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Uri BIDDING_LOGIC_URL = Uri.parse("http://www.domain.com/logic");
    private static final Uri RENDER_URL = Uri.parse("http://www.domain.com/advert");
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    ;
    private static final long AD_SELECTION_ID = 1;
    private static final String CONTEXTUAL_SIGNALS = "contextual_signals";

    private static final double BID = 5;

    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            CustomAudienceSignalsFixture.aCustomAudienceSignals();

    @Test
    public void testBuildDBAdSelection() {
        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setContextualSignals(CONTEXTUAL_SIGNALS)
                        .setBiddingLogicUrl(BIDDING_LOGIC_URL)
                        .setWinningAdRenderUrl(RENDER_URL)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .build();

        assertEquals(dbAdSelection.getAdSelectionId(), AD_SELECTION_ID);
        assertEquals(dbAdSelection.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
        assertEquals(dbAdSelection.getContextualSignals(), CONTEXTUAL_SIGNALS);
        assertEquals(dbAdSelection.getBiddingLogicUrl(), BIDDING_LOGIC_URL);
        assertEquals(dbAdSelection.getWinningAdRenderUrl(), RENDER_URL);
        assertEquals(dbAdSelection.getWinningAdBid(), BID, 0);
        assertEquals(dbAdSelection.getCreationTimestamp(), ACTIVATION_TIME);
    }

    @Test
    public void testFailsToBuildContextualAdWithNonNullBiddingLogicUrl() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setBiddingLogicUrl(BIDDING_LOGIC_URL)
                            .setWinningAdRenderUrl(RENDER_URL)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildContextualAdWithNonNullCustomAudienceSignals() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setWinningAdRenderUrl(RENDER_URL)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildDBAdSelectionWithUnsetAdSelectionId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelection.Builder()
                            .setAdSelectionId(0)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setContextualSignals(CONTEXTUAL_SIGNALS)
                            .setBiddingLogicUrl(BIDDING_LOGIC_URL)
                            .setWinningAdRenderUrl(RENDER_URL)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .build();
                });
    }
}
