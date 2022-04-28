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

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.util.concurrent.FluentFuture;

/**
 *  Defines the bid generator applied on the ads of each custom audience during the
 *  ad selection.
 */
interface AdBidGenerator {
    /**
     * This function uses the buyer-provided signals and javascript to generate bids for each of the
     * remarketing ads and pick a winner from each custom audience with the best bid.
     *
     * @param customAudience provides the ads and related information to run filtering and bidding.
     * @param buyerDecisionLogicJs is the fetched buyer-side provided javascript.
     * @param adSelectionSignals includes any information the SSP would provide during for bidding.
     * @param buyerSignals contains any information the SDP would provide to the bidding stage.
     * @param contextualSignals Contextual information about the App where the Ad is being shown, Ad
     *     slot and size, geographic location information, the seller invoking the ad selection and
     *     so on.
     * @return the ad candidate with the best bid from each Custom Audience.
     */
    @Nullable
    FluentFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull DBCustomAudience customAudience,
            @NonNull String buyerDecisionLogicJs,
            @NonNull String adSelectionSignals,
            @NonNull String buyerSignals,
            @NonNull String contextualSignals);
}
