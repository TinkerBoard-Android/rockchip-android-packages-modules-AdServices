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

package com.android.adservices.service.topics;

import static android.adservices.topics.GetTopicsResponse.RESULT_OK;

import android.adservices.topics.GetTopicsResponse;
import android.annotation.NonNull;
import android.annotation.WorkerThread;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.AdServicesConfig;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Worker class to handle Topics API Implementation.
 *
 * <p>This class is thread safe.
 *
 * @hide
 */
@ThreadSafe
@WorkerThread
public class TopicsWorker {
    // Singleton instance of the TopicsWorker.
    private static volatile TopicsWorker sTopicsWorker;

    // Lock for concurrent Read and Write processing in TopicsWorker.
    // Read-only API will only need to acquire Read Lock.
    // Write API (can update data) will need to acquire Write Lock.
    // This lock allows concurrent Read API and exclusive Write API.
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    private final CacheManager mCacheManager;

    private TopicsWorker(CacheManager cacheManager) {
        mCacheManager = cacheManager;
    }

    /**
     * Gets an instance of TopicsWorker to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static TopicsWorker getInstance() {
        if (sTopicsWorker == null) {
            synchronized (TopicsWorker.class) {
                if (sTopicsWorker == null) {
                    sTopicsWorker = new TopicsWorker(CacheManager.getInstance());
                }
            }
        }
        return sTopicsWorker;
    }

    /**
     * Gets an instance of TopicsWorker to be used in test only.
     * Don't use singleton pattern since we want to create different instance per test case.
     */
    @VisibleForTesting
    @NonNull
    static TopicsWorker getInstanceForTest(CacheManager cacheManager) {
        return new TopicsWorker(cacheManager);
    }

    /**
     * Get topics for the specified app and sdk.
     * @param app the app
     * @param sdk the sdk. In case the app calls the Topics API directly, the skd == empty string.
     * @return the Topics Response.
     */
    @NonNull
    public GetTopicsResponse getTopics(@NonNull String app, @NonNull String sdk) {
        mReadWriteLock.readLock().lock();
        try {
            // TODO: fix this hard coded epochId.
            long epochId = 1L;
            List<Topic> topics = mCacheManager.getTopics(epochId,
                    AdServicesConfig.getTopicsNumberOfLookBackEpochs(), app, sdk);

            List<Long> taxonomyVersions = new ArrayList<>(topics.size());
            List<Long> modelVersions = new ArrayList<>(topics.size());
            List<String> topicStrings = new ArrayList<>(topics.size());

            for (Topic topic : topics) {
                taxonomyVersions.add(topic.getTaxonomyVersion());
                modelVersions.add(topic.getModelVersion());
                topicStrings.add(topic.getTopic());
            }

            return new GetTopicsResponse.Builder()
                    .setResultCode(RESULT_OK)
                    .setTaxonomyVersions(taxonomyVersions)
                    .setModelVersions(modelVersions)
                    .setTopics(topicStrings)
                    .build();
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }
}
