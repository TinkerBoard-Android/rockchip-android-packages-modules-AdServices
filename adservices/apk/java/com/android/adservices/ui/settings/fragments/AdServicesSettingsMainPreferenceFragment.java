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
package com.android.adservices.ui.settings.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.android.adservices.api.R;
import com.android.adservices.ui.settings.ActionDelegate;
import com.android.adservices.ui.settings.AdServicesSettingsActivity;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;

import java.util.Objects;

/** Fragment for the main view of the AdServices Settings App. */
public class AdServicesSettingsMainPreferenceFragment extends PreferenceFragmentCompat {

    public static final String ERROR_MESSAGE_VIEW_MODEL_EXCEPTION_WHILE_GET_CONSENT =
            "getConsent method failed. Will not change consent value in view model.";
    public static final String PRIVACY_SANDBOX_BETA_SWITCH_KEY = "privacy_sandbox_beta_switch";
    public static final String TOPICS_PREFERENCE_BUTTON_KEY = "topics_preference";
    public static final String APPS_PREFERENCE_BUTTON_KEY = "apps_preference";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main_preferences, rootKey);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getListView().setOverScrollMode(View.OVER_SCROLL_NEVER);
        setupViewModel();
        initActionListeners();
    }

    // initialize all action listeners
    private void initActionListeners() {
        ActionDelegate actionDelegate =
                ((AdServicesSettingsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initMainFragment(this);
    }

    private void setupViewModel() {
        MainViewModel model =
                ((AdServicesSettingsActivity) requireActivity())
                        .getViewModelProvider()
                        .get(MainViewModel.class);

        SwitchPreference switchPreference =
                Objects.requireNonNull(findPreference(PRIVACY_SANDBOX_BETA_SWITCH_KEY));
        Preference topicsPreference =
                Objects.requireNonNull(findPreference(TOPICS_PREFERENCE_BUTTON_KEY));
        Preference appsPreference =
                Objects.requireNonNull(findPreference(APPS_PREFERENCE_BUTTON_KEY));
        model.getConsent()
                .observe(
                        getViewLifecycleOwner(),
                        consentGiven -> {
                            switchPreference.setChecked(consentGiven);
                            topicsPreference.setVisible(consentGiven);
                            appsPreference.setVisible(consentGiven);
                        });
    }
}
