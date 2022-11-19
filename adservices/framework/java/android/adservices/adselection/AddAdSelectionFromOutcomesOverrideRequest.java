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

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;

import java.util.Objects;

/**
 * This POJO represents the {@link
 * TestAdSelectionManager#overrideAdSelectionFromOutcomesConfigRemoteInfo} (
 * AddAdSelectionOverrideRequest, Executor, OutcomeReceiver)} request
 *
 * <p>It contains, a {@link AdSelectionFromOutcomesConfig} which will serve as the identifier for
 * the specific override, a {@code String} selectionLogicJs and {@code String} selectionSignals
 * field representing the override value
 *
 */
public class AddAdSelectionFromOutcomesOverrideRequest {
    // TODO(b/254500329): use AdSelectionConfig when that change is checked in
    @NonNull private final AdSelectionFromOutcomesConfig mAdSelectionFromOutcomesConfig;

    @NonNull private final String mSelectionLogicJs;

    @NonNull private final AdSelectionSignals mSelectionSignals;

    /** Builds a {@link AddAdSelectionFromOutcomesOverrideRequest} instance. */
    public AddAdSelectionFromOutcomesOverrideRequest(
            @NonNull AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            @NonNull String selectionLogicJs,
            @NonNull AdSelectionSignals selectionSignals) {
        Objects.requireNonNull(adSelectionFromOutcomesConfig);
        Objects.requireNonNull(selectionLogicJs);
        Objects.requireNonNull(selectionSignals);

        mAdSelectionFromOutcomesConfig = adSelectionFromOutcomesConfig;
        mSelectionLogicJs = selectionLogicJs;
        mSelectionSignals = selectionSignals;
    }

    /**
     * @return an instance of {@link AdSelectionFromOutcomesConfig}, the configuration of the ad
     *     selection process. This configuration provides the data necessary to run Ad Selection
     *     flow that generates bids and scores to find a wining ad for rendering.
     */
    @NonNull
    public AdSelectionFromOutcomesConfig getAdSelectionConfig() {
        return mAdSelectionFromOutcomesConfig;
    }

    /**
     * @return The override javascript result, should be a string that contains valid JS code. The
     *     code should contain the scoring logic that will be executed during Ad selection.
     */
    @NonNull
    public String getSelectionLogicJs() {
        return mSelectionLogicJs;
    }

    /**
     * @return The override trusted scoring signals, should be a valid json string. The trusted
     *     signals would be fed into the scoring logic during Ad Selection.
     */
    @NonNull
    public AdSelectionSignals getSelectionSignals() {
        return mSelectionSignals;
    }
}
