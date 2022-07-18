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

package android.adservices.adselection;

import android.annotation.NonNull;
import android.os.OutcomeReceiver;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This POJO represents the {@link
 * AdSelectionManager#overrideAdSelectionConfigRemoteInfo(AddAdSelectionOverrideRequest, Executor,
 * OutcomeReceiver)} request
 *
 * <p>It contains, a {@link AdSelectionConfig} which will serve as the identifier for the specific
 * override, a {@code String} decisionLogicJs and {@code String} trustedScoringSignals field
 * representing the override value
 */
public class AddAdSelectionOverrideRequest {
    @NonNull private final AdSelectionConfig mAdSelectionConfig;

    @NonNull private final String mDecisionLogicJs;

    @NonNull private final String mTrustedScoringSignals;

    /** Builds a {@link AddAdSelectionOverrideRequest} instance. */
    public AddAdSelectionOverrideRequest(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull String decisionLogicJs,
            @NonNull String trustedScoringSignals) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(decisionLogicJs);
        Objects.requireNonNull(trustedScoringSignals);

        mAdSelectionConfig = adSelectionConfig;
        mDecisionLogicJs = decisionLogicJs;
        mTrustedScoringSignals = trustedScoringSignals;
    }

    /**
     * @return an instance of {@link AdSelectionConfig}, the configuration of the ad selection
     *     process. This configuration provides the data necessary to run Ad Selection flow that
     *     generates bids and scores to find a wining ad for rendering.
     */
    @NonNull
    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }

    /**
     * @return The override javascript result, should be a string that contains valid JS code. The
     *     code should contain the scoring logic that will be executed during Ad selection.
     */
    @NonNull
    public String getDecisionLogicJs() {
        return mDecisionLogicJs;
    }

    /**
     * @return The override trusted scoring signals, should be a valid json string. The trusted
     *     signals would be fed into the scoring logic during Ad Selection.
     */
    @NonNull
    public String getTrustedScoringSignals() {
        return mTrustedScoringSignals;
    }
}
