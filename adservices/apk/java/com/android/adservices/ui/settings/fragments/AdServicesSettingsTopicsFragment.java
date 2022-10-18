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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.delegates.TopicsActionDelegate;
import com.android.adservices.ui.settings.viewadatpors.TopicsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;

import java.util.function.Function;

/** Fragment for the topics view of the AdServices Settings App. */
public class AdServicesSettingsTopicsFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.topics_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupViewModel(view);
        initActionListeners();
    }

    // initialize all action listeners except for actions in topics list
    private void initActionListeners() {
        TopicsActionDelegate actionDelegate =
                ((TopicsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initTopicsFragment(this);
    }

    // initializes view model connection with topics list.
    // (Action listeners for each item in the list will be handled by the adapter)
    private void setupViewModel(View rootView) {
        // create adapter
        TopicsViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(TopicsViewModel.class);
        Function<Topic, View.OnClickListener> getOnclickListener =
                topic -> view -> viewModel.revokeTopicConsentButtonClickHandler(topic);
        TopicsListViewAdapter adapter =
                new TopicsListViewAdapter(
                        requireContext(), viewModel.getTopics(), getOnclickListener, false);

        // set adapter for recyclerView
        RecyclerView recyclerView = rootView.findViewById(R.id.topics_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        View noTopicsMessage = rootView.findViewById(R.id.no_topics_message);
        View emptyTopicsHiddenSection = rootView.findViewById(R.id.empty_topics_hidden_section);
        View blockedTopicsButton = rootView.findViewById(R.id.blocked_topics_button);

        // "Empty State": the state when the non-blocked list of apps/topics is empty.
        // blocked_apps_when_empty_state_button is added to noTopicsMessage
        // noTopicsMessages is visible only when Empty State
        // blocked_topics_when_empty_state_button differs from blocked_topics_button
        // in style with rounded corners, centered, colored
        viewModel
                .getTopics()
                .observe(
                        getViewLifecycleOwner(),
                        topicsList -> {
                            if (topicsList.isEmpty()) {
                                noTopicsMessage.setVisibility(View.VISIBLE);
                                emptyTopicsHiddenSection.setVisibility(View.GONE);
                                blockedTopicsButton.setVisibility(View.GONE);
                            } else {
                                noTopicsMessage.setVisibility(View.GONE);
                                emptyTopicsHiddenSection.setVisibility(View.VISIBLE);
                                blockedTopicsButton.setVisibility(View.VISIBLE);
                            }
                            adapter.notifyDataSetChanged();
                        });

        // locked_topics_when_empty_state_button is disabled if there is no blocked topics
        View blockedTopicsWhenEmptyStateButton =
                rootView.findViewById(R.id.blocked_topics_when_empty_state_button);
        viewModel
                .getBlockedTopics()
                .observe(
                        getViewLifecycleOwner(),
                        blockedTopicsList -> {
                            if (blockedTopicsList.isEmpty()) {
                                blockedTopicsWhenEmptyStateButton.setEnabled(false);
                                blockedTopicsWhenEmptyStateButton.setAlpha(
                                        getResources().getFloat(R.dimen.disabled_button_alpha));
                            } else {
                                blockedTopicsWhenEmptyStateButton.setEnabled(true);
                                blockedTopicsWhenEmptyStateButton.setAlpha(
                                        getResources().getFloat(R.dimen.enabled_button_alpha));
                            }
                        });
    }
}
