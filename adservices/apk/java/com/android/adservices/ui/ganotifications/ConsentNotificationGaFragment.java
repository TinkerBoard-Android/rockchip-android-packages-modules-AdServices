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
package com.android.adservices.ui.ganotifications;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.ui.notifications.ConsentNotificationConfirmationFragment.IS_CONSENT_GIVEN_ARGUMENT_KEY;
import static com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity.FROM_NOTIFICATION_KEY;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnScrollChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.adservices.api.R;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;

/** Fragment for the topics view of the AdServices Settings App. */
public class ConsentNotificationGaFragment extends Fragment {
    public static final String IS_EU_DEVICE_ARGUMENT_KEY = "isEUDevice";
    public static final String IS_INFO_VIEW_EXPANDED_KEY = "is_info_view_expanded";
    private boolean mIsEUDevice;
    private boolean mIsInfoViewExpanded = false;
    private @Nullable ScrollToBottomController mScrollToBottomController;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return setupActivity(inflater, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        logLandingPageDisplayed();
        setupListeners(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mScrollToBottomController != null) {
            mScrollToBottomController.saveInstanceState(savedInstanceState);
        }
        savedInstanceState.putBoolean(IS_INFO_VIEW_EXPANDED_KEY, mIsInfoViewExpanded);
    }

    private void logLandingPageDisplayed() {
        UIStats uiStats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(
                                mIsEUDevice
                                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                        .setAction(
                                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED)
                        .build();
        AdServicesLoggerImpl.getInstance().logUIStats(uiStats);
    }

    private View setupActivity(LayoutInflater inflater, ViewGroup container) {
        mIsEUDevice =
                requireActivity().getIntent().getBooleanExtra(IS_EU_DEVICE_ARGUMENT_KEY, true);
        View rootView;
        if (mIsEUDevice) {
            rootView =
                    inflater.inflate(
                            R.layout.consent_notification_ga_fragment_eu, container, false);
        } else {
            rootView =
                    inflater.inflate(R.layout.consent_notification_ga_fragment, container, false);
        }
        return rootView;
    }

    private void setupListeners(Bundle savedInstanceState) {
        TextView howItWorksExpander = requireActivity().findViewById(R.id.how_it_works_expander);
        if (savedInstanceState != null) {
            setInfoViewState(savedInstanceState.getBoolean(IS_INFO_VIEW_EXPANDED_KEY, false));
        }
        howItWorksExpander.setOnClickListener(view -> setInfoViewState(!mIsInfoViewExpanded));

        Button leftControlButton = requireActivity().findViewById(R.id.leftControlButton);
        leftControlButton.setOnClickListener(
                view -> {
                    if (mIsEUDevice) {
                        // TODO(b/254350760): For EU: Topics Off; FLEDGE and Measurement On.
                        //  For Row: all three on. will need to change ConsentNotificationTrigger
                        // opt-out confirmation activity
                        ConsentManager.getInstance(requireContext()).disable(requireContext());
                        Bundle args = new Bundle();
                        args.putBoolean(IS_CONSENT_GIVEN_ARGUMENT_KEY, false);
                        startConfirmationFragment(args);
                    } else {
                        // go to settings activity
                        Intent intent =
                                new Intent(requireActivity(), AdServicesSettingsMainActivity.class);
                        intent.putExtra(FROM_NOTIFICATION_KEY, true);
                        startActivity(intent);
                        requireActivity().finish();
                    }
                });

        Button rightControlButton = requireActivity().findViewById(R.id.rightControlButton);
        ScrollView scrollView = requireView().findViewById(R.id.notification_fragment_scrollview);

        mScrollToBottomController =
                new ScrollToBottomController(
                        scrollView, leftControlButton, rightControlButton, savedInstanceState);
        mScrollToBottomController.bind();
        // check whether it can scroll vertically and update buttons after layout can be measured
        scrollView.post(() -> mScrollToBottomController.updateButtonsIfHasScrolledToBottom());
    }

    private void setInfoViewState(boolean expanded) {
        View text = requireActivity().findViewById(R.id.how_it_works_expanded_text);
        TextView expander = requireActivity().findViewById(R.id.how_it_works_expander);
        if (expanded) {
            mIsInfoViewExpanded = true;
            text.setVisibility(View.VISIBLE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_minimize, 0);
        } else {
            mIsInfoViewExpanded = false;
            text.setVisibility(View.GONE);
            expander.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand, 0);
        }
    }

    private void startConfirmationFragment(Bundle args) {
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container_view,
                        ConsentNotificationConfirmationGaFragment.class,
                        args)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }

    /**
     * Allows the positive, acceptance button to scroll the view.
     *
     * <p>When the positive button first appears it will show the text "More". When the user taps
     * the button, the view will scroll to the bottom. Once the view has scrolled to the bottom, the
     * button text will be replaced with the acceptance text. Once the text has changed, the button
     * will trigger the positive action no matter where the view is scrolled.
     */
    private class ScrollToBottomController implements OnScrollChangeListener {
        private static final String STATE_HAS_SCROLLED_TO_BOTTOM = "has_scrolled_to_bottom";
        private static final int SCROLL_DIRECTION_DOWN = 1;
        private static final double SCROLL_MULTIPLIER = 0.8;

        private final ScrollView mScrollContainer;
        private final Button mLeftControlButton;
        private final Button mRightControlButton;

        private boolean mHasScrolledToBottom;

        ScrollToBottomController(
                ScrollView scrollContainer,
                Button leftControlButton,
                Button rightControlButton,
                @Nullable Bundle savedInstanceState) {
            this.mScrollContainer = scrollContainer;
            this.mLeftControlButton = leftControlButton;
            this.mRightControlButton = rightControlButton;
            mHasScrolledToBottom =
                    savedInstanceState != null
                            && savedInstanceState.containsKey(STATE_HAS_SCROLLED_TO_BOTTOM)
                            && savedInstanceState.getBoolean(STATE_HAS_SCROLLED_TO_BOTTOM);
        }

        public void bind() {
            mScrollContainer.setOnScrollChangeListener(this);
            mRightControlButton.setOnClickListener(this::onMoreOrAcceptClicked);
            updateControlButtons();
        }

        public void saveInstanceState(Bundle bundle) {
            if (mHasScrolledToBottom) {
                bundle.putBoolean(STATE_HAS_SCROLLED_TO_BOTTOM, true);
            }
        }

        private void updateControlButtons() {
            if (mHasScrolledToBottom) {
                mLeftControlButton.setVisibility(View.VISIBLE);
                mRightControlButton.setText(
                        mIsEUDevice
                                ? R.string.notificationUI_right_control_button_text_eu
                                : R.string.notificationUI_right_control_button_text);
            } else {
                mLeftControlButton.setVisibility(View.INVISIBLE);
                mRightControlButton.setText(R.string.notificationUI_more_button_text);
            }
        }

        private void onMoreOrAcceptClicked(View view) {
            Context context = getContext();
            if (context == null) {
                return;
            }

            if (mHasScrolledToBottom) {
                if (mIsEUDevice) {
                    // opt-in confirmation activity
                    ConsentManager.getInstance(requireContext()).enable(requireContext());
                    Bundle args = new Bundle();
                    args.putBoolean(IS_CONSENT_GIVEN_ARGUMENT_KEY, true);
                    startConfirmationFragment(args);
                } else {
                    // acknowledge and dismiss
                    requireActivity().finish();
                }
            } else {
                mScrollContainer.smoothScrollTo(
                        0,
                        mScrollContainer.getScrollY()
                                + (int) (mScrollContainer.getHeight() * SCROLL_MULTIPLIER));
            }
        }

        @Override
        public void onScrollChange(
                View view, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            updateButtonsIfHasScrolledToBottom();
        }

        void updateButtonsIfHasScrolledToBottom() {
            if (!mScrollContainer.canScrollVertically(SCROLL_DIRECTION_DOWN)) {
                mHasScrolledToBottom = true;
                updateControlButtons();
            }
        }
    }
}