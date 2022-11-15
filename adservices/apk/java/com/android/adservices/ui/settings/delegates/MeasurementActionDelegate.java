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
package com.android.adservices.ui.settings.delegates;

import static com.android.adservices.ui.settings.viewmodels.MeasurementViewModel.MeasurementViewModelUiEvent.RESET_MEASUREMENT;

import android.view.View;

import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.PhFlags;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.MeasurementActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMeasurementFragment;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel;
import com.android.adservices.ui.settings.viewmodels.MeasurementViewModel.MeasurementViewModelUiEvent;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
public class MeasurementActionDelegate extends BaseActionDelegate {
    private final MeasurementActivity mMeasurementActivity;
    private final MeasurementViewModel mMeasurementViewModel;

    public MeasurementActionDelegate(
            MeasurementActivity measurementActivity, MeasurementViewModel measurementViewModel) {
        super(measurementActivity);
        this.mMeasurementActivity = measurementActivity;
        this.mMeasurementViewModel = measurementViewModel;
        listenToMeasurementViewModelUiEvents();
    }

    private void listenToMeasurementViewModelUiEvents() {
        Observer<MeasurementViewModelUiEvent> observer =
                event -> {
                    if (event == null) {
                        return;
                    }
                    try {
                        if (event == RESET_MEASUREMENT) {
                            logUIAction(ActionEnum.RESET_TOPIC_SELECTED);
                            if (PhFlags.getInstance().getUIDialogsFeatureEnabled()) {
                                DialogManager.showResetMeasurementDialog(
                                        mMeasurementActivity, mMeasurementViewModel);
                            } else {
                                mMeasurementViewModel.resetMeasurement();
                            }
                        }
                    } finally {
                        mMeasurementViewModel.uiEventHandled();
                    }
                };
        mMeasurementViewModel.getUiEvents().observe(mMeasurementActivity, observer);
    }

    /**
     * Configure all UI elements in {@link AdServicesSettingsMeasurementFragment} to handle user
     * actions.
     */
    public void initMeasurementFragment(AdServicesSettingsMeasurementFragment fragment) {
        mMeasurementActivity.setTitle(R.string.settingsUI_measurement_view_title);
        configureResetMeasurementButton(fragment);
    }

    private void configureResetMeasurementButton(AdServicesSettingsMeasurementFragment fragment) {
        View resetMeasurementButton =
                fragment.requireView().findViewById(R.id.reset_measurement_button);

        resetMeasurementButton.setOnClickListener(
                view -> mMeasurementViewModel.resetMeasurementButtonClickHandler());
    }
}