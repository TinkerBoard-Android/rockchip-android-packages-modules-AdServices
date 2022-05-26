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
package com.example.adservices.samples.topics.sampleapp;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.adservices.topics.GetTopicsResponse;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Android application activity for testing Topics API by providing a button in UI that initiate
 * user's interaction with Topics Manager in the background. Response from Topics API will be shown
 * in the app as text as well as toast message. In case anything goes wrong in this process, error
 * message will also be shown in toast to suggest the Exception encountered.
 */
public class MainActivity extends AppCompatActivity {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String NEWLINE = "\n";
    private static final String TAG = "SampleApp";
    private static List<String> mSdkNameList = new ArrayList<>(
        Arrays.asList("SdkName1", "SdkName2", "SdkName3"));
    private Button mTopicsClientButton;
    private TextView mResultTextView;
    private AdvertisingTopicsClient mAdvertisingTopicsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTopicsClientButton = findViewById(R.id.topics_client_button);
        mResultTextView = findViewById(R.id.textView);
        registerGetTopicsButton();
    }

    private void registerGetTopicsButton() {
        mTopicsClientButton.setOnClickListener(v -> {
            mResultTextView.setText("");
            for (String sdkName : mSdkNameList) {
                mAdvertisingTopicsClient = new AdvertisingTopicsClient.Builder()
                    .setContext(this)
                    .setSdkName(sdkName)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
                ListenableFuture<GetTopicsResponse> getTopicsResponseFuture =
                        mAdvertisingTopicsClient.getTopics();

                Futures.addCallback(
                        getTopicsResponseFuture,
                        new FutureCallback<GetTopicsResponse>() {
                            @Override
                            public void onSuccess(GetTopicsResponse result) {
                                Log.d(TAG, "GetTopics for sdk " + sdkName + " succeeded!");
                                String topics = getTopics(result.getTopics());
                                mResultTextView.append(sdkName + "'s topics: "
                                        + NEWLINE + topics + NEWLINE);
                                Log.d(TAG, sdkName + "'s topics: " + NEWLINE + topics + NEWLINE);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                Log.e(TAG, "Failed to getTopics for sdk " + sdkName);
                                mResultTextView.append("Failed to getTopics for sdk "
                                        + sdkName + NEWLINE);
                            }
                        },
                        directExecutor());
            }
        });
    }

    private String getTopics(List<Integer> arr) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (int topic : arr) {
            sb.append(index++).append(". ").append(topic).append(NEWLINE);
        }
        return sb.toString();
    }
}
