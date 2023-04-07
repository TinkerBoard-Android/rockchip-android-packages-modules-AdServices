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

import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.app.adservices.topics.TopicParcel;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Class to manage blocked {@link Topic}s. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedTopicsManager {
    private static BlockedTopicsManager sSingleton;
    @VisibleForTesting static final String SHARED_PREFS_BLOCKED_TOPICS = "PPAPI_Blocked_Topics";

    @VisibleForTesting
    static final String SHARED_PREFS_KEY_HAS_MIGRATED =
            "BLOCKED_TOPICS_HAS_MIGRATED_TO_SYSTEM_SERVER";

    @VisibleForTesting
    static final String SHARED_PREFS_KEY_PPAPI_HAS_CLEARED = "BLOCKED_TOPICS_HAS_CLEARED_IN_PPAPI";

    private static final String ERROR_MESSAGE_RECORD_BLOCKED_TOPIC =
            "Failed to record the blocked topic.";
    private static final String ERROR_MESSAGE_REMOVE_BLOCKED_TOPIC =
            "Failed to remove the blocked topic.";
    private static final String ERROR_MESSAGE_GET_BLOCKED_TOPICS =
            "Failed to get all blocked topics.";

    private static final String ERROR_MESSAGE_CLEAR_BLOCKED_TOPICS_IN_SYSTEM_SERVER =
            "Failed to clear all blocked topics in system server.";
    private static final Object LOCK = new Object();

    private final TopicsDao mTopicsDao;
    private final AdServicesManager mAdServicesManager;
    private final int mBlockedTopicsSourceOfTruth;

    @VisibleForTesting
    BlockedTopicsManager(
            @NonNull TopicsDao topicsDao,
            @NonNull AdServicesManager adServicesManager,
            @Flags.ConsentSourceOfTruth int blockedTopicsSourceOfTruth) {
        mTopicsDao = topicsDao;
        mAdServicesManager = adServicesManager;
        mBlockedTopicsSourceOfTruth = blockedTopicsSourceOfTruth;
    }

    /** Returns an instance of the {@link BlockedTopicsManager} given a context. */
    @NonNull
    public static BlockedTopicsManager getInstance(Context context) {
        synchronized (LOCK) {
            if (sSingleton == null) {
                // Execute one-time migration of blocked topics if needed.
                int blockedTopicsSourceOfTruth =
                        FlagsFactory.getFlags().getBlockedTopicsSourceOfTruth();
                AdServicesManager adServicesManager = AdServicesManager.getInstance(context);
                TopicsDao topicsDao = TopicsDao.getInstance(context);
                handleBlockedTopicsMigrationIfNeeded(
                        context, topicsDao, adServicesManager, blockedTopicsSourceOfTruth);

                sSingleton =
                        new BlockedTopicsManager(
                                topicsDao, adServicesManager, blockedTopicsSourceOfTruth);
            }
            return sSingleton;
        }
    }

    /**
     * Revoke consent for provided {@link Topic} (block topic). This topic will not be returned by
     * any of the {@link TopicsWorker} methods.
     *
     * @param topic {@link Topic} to block.
     */
    public void blockTopic(@NonNull Topic topic) {
        LogUtil.v("BlockedTopicsManager.blockTopic");

        synchronized (LOCK) {
            try {
                switch (mBlockedTopicsSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mTopicsDao.recordBlockedTopic(topic);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.recordBlockedTopic(
                                List.of(convertTopicToTopicParcel(topic)));
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mTopicsDao.recordBlockedTopic(topic);
                        mAdServicesManager.recordBlockedTopic(
                                List.of(convertTopicToTopicParcel(topic)));
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants
                                        .ERROR_MESSAGE_INVALID_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException(ERROR_MESSAGE_RECORD_BLOCKED_TOPIC, e);
            }
        }
    }

    /**
     * Restore consent for provided {@link Topic} (unblock the topic). This topic can be returned by
     * any of the {@link TopicsWorker} methods.
     *
     * @param topic {@link Topic} to restore consent for.
     */
    public void unblockTopic(@NonNull Topic topic) {
        LogUtil.v("BlockedTopicsManager.unblockTopic");

        synchronized (LOCK) {
            try {
                switch (mBlockedTopicsSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        mTopicsDao.removeBlockedTopic(topic);
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        mAdServicesManager.removeBlockedTopic(convertTopicToTopicParcel(topic));
                        break;
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mTopicsDao.removeBlockedTopic(topic);
                        mAdServicesManager.removeBlockedTopic(convertTopicToTopicParcel(topic));
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants
                                        .ERROR_MESSAGE_INVALID_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException(ERROR_MESSAGE_REMOVE_BLOCKED_TOPIC);
            }
        }
    }

    /**
     * Get a {@link List} of {@link Topic}s which are blocked.
     *
     * @return {@link List} a {@link List} of blocked {@link Topic}s.
     */
    @NonNull
    public List<Topic> retrieveAllBlockedTopics() {
        synchronized (LOCK) {
            try {
                switch (mBlockedTopicsSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        return mTopicsDao.retrieveAllBlockedTopics();
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                        // In case of PPAPI_AND_SYSTEM_SERVER, read from the system server.
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        return mAdServicesManager.retrieveAllBlockedTopics().stream()
                                .map(this::convertTopicParcelToTopic)
                                .collect(Collectors.toList());
                    default:
                        throw new RuntimeException(
                                ConsentConstants
                                        .ERROR_MESSAGE_INVALID_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException(ERROR_MESSAGE_GET_BLOCKED_TOPICS);
            }
        }
    }

    /**
     * Clear preserved blocked topics in system server when the blocked topic source of truth
     * contains SYSTEM_SERVER
     */
    public void clearAllBlockedTopicsInSystemServiceIfNeeded() {
        synchronized (LOCK) {
            try {
                switch (mBlockedTopicsSourceOfTruth) {
                    case Flags.PPAPI_ONLY:
                        // Return directly. PPAPI data is handled by
                        // mCacheManager.clearAllTopicsData() and this method is to only clear
                        // preserved blocked topics in system server.
                        break;
                    case Flags.SYSTEM_SERVER_ONLY:
                        // Intentional fallthrough
                    case Flags.PPAPI_AND_SYSTEM_SERVER:
                        mAdServicesManager.clearAllBlockedTopics();
                        break;
                    default:
                        throw new RuntimeException(
                                ConsentConstants
                                        .ERROR_MESSAGE_INVALID_BLOCKED_TOPICS_SOURCE_OF_TRUTH);
                }
            } catch (RuntimeException e) {
                throw new RuntimeException(ERROR_MESSAGE_CLEAR_BLOCKED_TOPICS_IN_SYSTEM_SERVER);
            }
        }
    }

    // Handle different migration requests based on current blocked topics source of Truth
    // PPAPI_ONLY: reset the shared preference to reset status of migrating blocked topics from
    //             PPAPI to system server. This allows the migration to happen after switching
    //             the source of truth to system server again.
    // PPAPI_AND_SYSTEM_SERVER: migrate blocked topics from PPAPI to system server.
    // SYSTEM_SERVER_ONLY: migrate blocked topics from PPAPI to system server and clear PPAPI
    //                     blocked topics
    @VisibleForTesting
    static void handleBlockedTopicsMigrationIfNeeded(
            @NonNull Context context,
            @NonNull TopicsDao topicsDao,
            @NonNull AdServicesManager adServicesManager,
            @Flags.ConsentSourceOfTruth int blockedTopicsSourceOfTruth) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(topicsDao);

        if (blockedTopicsSourceOfTruth != Flags.PPAPI_ONLY) {
            Objects.requireNonNull(adServicesManager);
        }

        switch (blockedTopicsSourceOfTruth) {
            case Flags.PPAPI_ONLY:
                // Technically we only need to reset the SHARED_PREFS_KEY_HAS_MIGRATED bit once.
                // What we need is clearIfSet operation which is not available in SP. So here we
                // always reset the bit since otherwise we need to read the SP to read the value and
                // the clear the value.
                // The only flow we would do are:
                // Case 1: DUAL-> PPAPI if there is a bug in System Server
                // Case 2: DUAL -> SYSTEM_SERVER_ONLY: if everything goes smoothly.
                resetSharedPreference(context, SHARED_PREFS_KEY_HAS_MIGRATED);
                break;
            case Flags.PPAPI_AND_SYSTEM_SERVER:
                mayMigratePpApiBlockedTopicsToSystemService(context, topicsDao, adServicesManager);
                break;
            case Flags.SYSTEM_SERVER_ONLY:
                mayMigratePpApiBlockedTopicsToSystemService(context, topicsDao, adServicesManager);
                mayClearPpApiBlockedTopics(context, topicsDao);
                break;
            default:
                break;
        }
    }

    // Set the shared preference to false for given key.
    @VisibleForTesting
    static void resetSharedPreference(
            @NonNull Context context, @NonNull String sharedPreferenceKey) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(sharedPreferenceKey);

        SharedPreferences sharedPreferences =
                context.getSharedPreferences(SHARED_PREFS_BLOCKED_TOPICS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(sharedPreferenceKey);

        if (editor.commit()) {
            LogUtil.d("Finish resetting shared preference for " + sharedPreferenceKey);
        } else {
            LogUtil.e("Failed to reset shared preference for " + sharedPreferenceKey);
        }
    }

    // Perform a one-time migration to migrate existing PPAPI blocked topics.
    @VisibleForTesting
    static void mayMigratePpApiBlockedTopicsToSystemService(
            @NonNull Context context,
            @NonNull TopicsDao topicsDao,
            @NonNull AdServicesManager adServicesManager) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(topicsDao);
        Objects.requireNonNull(adServicesManager);

        // Exit if migration has happened.
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(SHARED_PREFS_BLOCKED_TOPICS, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false)) {
            LogUtil.v(
                    "Blocked topics migration has happened to user %d, skip...",
                    context.getUser().getIdentifier());
            return;
        }
        LogUtil.d("Start migrating blocked topics from PPAPI to System Service");

        // Migrate blocked topics to System Service.
        List<TopicParcel> topicParcels = new ArrayList<>();
        for (Topic topic : topicsDao.retrieveAllBlockedTopics()) {
            topicParcels.add(convertTopicToTopicParcel(topic));
        }
        adServicesManager.recordBlockedTopic(topicParcels);

        // Save migration has happened into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, true);

        if (editor.commit()) {
            LogUtil.d("Finish migrating blocked topics from PPAPI to System Service");
        } else {
            LogUtil.e(
                    "Finish migrating blocked topics from PPAPI to System Service but shared"
                            + " preference is not updated.");
        }
    }

    // Clear PPAPI blocked topics if fully migrated to use system server blocked topics. This is
    // because system blocked topics cannot be migrated back to PPAPI. This data clearing should
    // only happen once.
    @VisibleForTesting
    static void mayClearPpApiBlockedTopics(@NonNull Context context, @NonNull TopicsDao topicsDao) {
        // Exit if PPAPI blocked topics has cleared.
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(SHARED_PREFS_BLOCKED_TOPICS, Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean(
                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false)) {
            return;
        }

        LogUtil.d("Start clearing blocked topics in PPAPI.");
        topicsDao.deleteAllEntriesFromTable(TopicsTables.BlockedTopicsContract.TABLE);

        // Save that PPAPI blocked topics has cleared into shared preferences.
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);

        if (editor.commit()) {
            LogUtil.d("Finish clearing blocked topics in PPAPI.");
        } else {
            LogUtil.e(
                    "Finish clearing blocked topics in PPAPI but shared preference is not"
                            + " updated.");
        }
    }

    @VisibleForTesting
    static TopicParcel convertTopicToTopicParcel(@NonNull Topic topic) {
        return new TopicParcel.Builder()
                .setTopicId(topic.getTopic())
                .setTaxonomyVersion(topic.getTaxonomyVersion())
                .setModelVersion(topic.getModelVersion())
                .build();
    }

    private Topic convertTopicParcelToTopic(@NonNull TopicParcel topicParcel) {
        return Topic.create(
                topicParcel.getTopicId(),
                topicParcel.getTaxonomyVersion(),
                topicParcel.getModelVersion());
    }
}
