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

package com.android.adservices.data.topics;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Data Access Object for the Topics API.
 */
public class TopicsDao {
    private static TopicsDao sSingleton;

    @SuppressWarnings("unused")
    private final DbHelper mDbHelper; // Used in tests.

    /**
     * It's only public to unit test.
     *
     * @param dbHelper The database to query
     */
    @VisibleForTesting
    public TopicsDao(DbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /** Returns an instance of the TopicsDAO given a context. */
    @NonNull
    public static TopicsDao getInstance(@NonNull Context context) {
        synchronized (TopicsDao.class) {
            if (sSingleton == null) {
                sSingleton = new TopicsDao(DbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    /**
     * Persist the apps and their classification topics.
     *
     * @param epochId                    the epoch Id to persist
     * @param taxonomyVersion            The version of taxonomy
     * @param modelVersion               The version of model
     * @param appClassificationTopicsMap Map of app -> classified topics
     */
    @VisibleForTesting
    public void persistAppClassificationTopics(long epochId, long taxonomyVersion,
            long modelVersion, @NonNull Map<String, List<Integer>> appClassificationTopicsMap) {
        Objects.requireNonNull(appClassificationTopicsMap);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<String, List<Integer>> entry : appClassificationTopicsMap.entrySet()) {
            String app = entry.getKey();

            // save each topic in the list by app -> topic mapping in the DB
            for (Integer topic : entry.getValue()) {
                ContentValues values = new ContentValues();
                values.put(TopicsTables.AppClassificationTopicsContract.EPOCH_ID, epochId);
                values.put(TopicsTables.AppClassificationTopicsContract.APP, app);
                values.put(TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION,
                        taxonomyVersion);
                values.put(TopicsTables.AppClassificationTopicsContract.MODEL_VERSION,
                        modelVersion);
                values.put(TopicsTables.AppClassificationTopicsContract.TOPIC, topic);

                try {
                    db.insert(TopicsTables.AppClassificationTopicsContract.TABLE,
                            /* nullColumnHack */ null, values);
                } catch (SQLException e) {
                    LogUtil.e("Failed to persist classified Topics. Exception : "
                            + e.getMessage());
                }
            }
        }
    }

    /**
     * Get the map of apps and their classification topics.
     *
     * @param epochId the epoch Id to retrieve
     * @return {@link Map} a map of app -> topics
     */
    @VisibleForTesting
    @NonNull
    public Map<String, List<Topic>> retrieveAppClassificationTopics(long epochId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        Map<String, List<Topic>> appTopicsMap = new HashMap<>();
        if (db == null) {
            return appTopicsMap;
        }

        String[] projection = {
                TopicsTables.AppClassificationTopicsContract.APP,
                TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION,
                TopicsTables.AppClassificationTopicsContract.MODEL_VERSION,
                TopicsTables.AppClassificationTopicsContract.TOPIC,
        };

        String selection = TopicsTables.AppClassificationTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (
                Cursor cursor = db.query(
                        TopicsTables.AppClassificationTopicsContract.TABLE,   // The table to query
                        projection,        // The array of columns to return (pass null to get all)
                        selection,         // The columns for the WHERE clause
                        selectionArgs,     // The values for the WHERE clause
                        null,      // don't group the rows
                        null,       // don't filter by row groups
                        null       // The sort order
                )) {
            while (cursor.moveToNext()) {
                String app = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppClassificationTopicsContract.APP));
                long taxonomyVersion = cursor.getLong(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION));
                long modelVersion = cursor.getLong(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppClassificationTopicsContract.MODEL_VERSION));
                String topicString = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppClassificationTopicsContract.TOPIC));
                Topic topic = Topic.create(
                        Integer.parseInt(topicString), taxonomyVersion, modelVersion);

                List<Topic> list = appTopicsMap.getOrDefault(app, new ArrayList<>());
                list.add(topic);
                appTopicsMap.put(app, list);
            }
        }

        return appTopicsMap;
    }

    /**
     * Persist the list of Top Topics in this epoch to DB.
     *
     * @param epochId   Id of current epoch
     * @param topTopics the topics list to persist into DB
     */
    @VisibleForTesting
    public void persistTopTopics(long epochId, @NonNull List<Integer> topTopics) {
        // topTopics the Top Topics: a list of 5 top topics and the 6th topic
        // which was selected randomly. We can refer this 6th topic as the random-topic.
        Objects.requireNonNull(topTopics);
        Preconditions.checkArgument(topTopics.size() == 6);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(TopicsTables.TopTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.TopTopicsContract.TOPIC1, topTopics.get(0));
        values.put(TopicsTables.TopTopicsContract.TOPIC2, topTopics.get(1));
        values.put(TopicsTables.TopTopicsContract.TOPIC3, topTopics.get(2));
        values.put(TopicsTables.TopTopicsContract.TOPIC4, topTopics.get(3));
        values.put(TopicsTables.TopTopicsContract.TOPIC5, topTopics.get(4));
        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, topTopics.get(5));

        try {
            db.insert(TopicsTables.TopTopicsContract.TABLE,
                    /* nullColumnHack */null, values);
        } catch (SQLException e) {
            LogUtil.e("Failed to persist Top Topics. Exception : " + e.getMessage());
        }

    }

    /**
     * Return the Top Topics. This will retrieve a list of 5 top topics and the 6th random topic
     * from DB.
     *
     * @param epochId the epochId to retrieve the top topics.
     * @return List of Top Topics.
     */
    @VisibleForTesting
    @NonNull
    public List<Integer> retrieveTopTopics(long epochId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return new ArrayList<>();
        }

        String[] projection = {
                TopicsTables.TopTopicsContract.TOPIC1,
                TopicsTables.TopTopicsContract.TOPIC2,
                TopicsTables.TopTopicsContract.TOPIC3,
                TopicsTables.TopTopicsContract.TOPIC4,
                TopicsTables.TopTopicsContract.TOPIC5,
                TopicsTables.TopTopicsContract.RANDOM_TOPIC,
        };

        String selection = TopicsTables.AppClassificationTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (
                Cursor cursor = db.query(
                        TopicsTables.TopTopicsContract.TABLE,   // The table to query
                        projection,        // The array of columns to return (pass null to get all)
                        selection,         // The columns for the WHERE clause
                        selectionArgs,     // The values for the WHERE clause
                        null,      // don't group the rows
                        null,       // don't filter by row groups
                        null       // The sort order
                )) {
            if (cursor.moveToNext()) {
                Integer topic1 =
                        Integer.parseInt(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                TopicsTables.TopTopicsContract.TOPIC1)));
                Integer topic2 =
                        Integer.parseInt(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                TopicsTables.TopTopicsContract.TOPIC2)));
                Integer topic3 =
                        Integer.parseInt(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                TopicsTables.TopTopicsContract.TOPIC3)));
                Integer topic4 =
                        Integer.parseInt(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                TopicsTables.TopTopicsContract.TOPIC4)));
                Integer topic5 =
                        Integer.parseInt(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                TopicsTables.TopTopicsContract.TOPIC5)));
                Integer randomTopic =
                        Integer.parseInt(
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                                TopicsTables.TopTopicsContract.RANDOM_TOPIC)));
                return Arrays.asList(topic1, topic2, topic3, topic4, topic5, randomTopic);
            }
        }

        return new ArrayList<>();
    }

    /**
     * Record the App and SDK into the Usage History table.
     *
     * @param epochId epochId epoch id to record
     * @param app     app name
     * @param sdk     sdk name
     */
    public void recordUsageHistory(long epochId, @NonNull String app, @NonNull String sdk) {
        Objects.requireNonNull(app);
        Objects.requireNonNull(sdk);
        Preconditions.checkStringNotEmpty(app);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(TopicsTables.UsageHistoryContract.APP, app);
        values.put(TopicsTables.UsageHistoryContract.SDK, sdk);
        values.put(TopicsTables.UsageHistoryContract.EPOCH_ID, epochId);

        try {
            db.insert(TopicsTables.UsageHistoryContract.TABLE,
                    /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            LogUtil.e("Failed to record App-Sdk usage history." + e.getMessage());
        }
    }

    /**
     * Record the usage history for app only
     *
     * @param epochId epoch id to record
     * @param app     app name
     */
    public void recordAppUsageHistory(long epochId, @NonNull String app) {
        Objects.requireNonNull(app);
        Preconditions.checkStringNotEmpty(app);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(TopicsTables.AppUsageHistoryContract.APP, app);
        values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, epochId);

        try {
            db.insert(TopicsTables.AppUsageHistoryContract.TABLE,
                    /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            LogUtil.e("Failed to record App Only usage history." + e.getMessage());
        }
    }


    /**
     * Return all apps and their SDKs that called Topics API in the epoch.
     *
     * @param epochId the epoch to retrieve the app and sdk usage for.
     * @return Return Map<App, List<SDK>>.
     */
    @NonNull
    public Map<String, List<String>> retrieveAppSdksUsageMap(long epochId) {
        Map<String, List<String>> appSdksUsageMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return appSdksUsageMap;
        }

        String[] projection = {
                TopicsTables.UsageHistoryContract.APP,
                TopicsTables.UsageHistoryContract.SDK,
        };

        String selection = TopicsTables.UsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (
                Cursor cursor =
                        db.query(/* distinct = */true,
                                TopicsTables.UsageHistoryContract.TABLE, projection,
                                selection,
                                selectionArgs, null, null,
                                null, null)
        ) {
            while (cursor.moveToNext()) {
                String app = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.UsageHistoryContract.APP));
                String sdk = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.UsageHistoryContract.SDK));
                if (!appSdksUsageMap.containsKey(app)) {
                    appSdksUsageMap.put(app, new ArrayList<>());
                }
                appSdksUsageMap.get(app).add(sdk);
            }
        }

        return appSdksUsageMap;
    }

    /**
     * Get topic api usage of an app in an epoch.
     *
     * @param epochId the epoch to retrieve the app usage for.
     * @return Map<App, UsageCount>, how many times an app called topics API in this epoch
     */
    @NonNull
    public Map<String, Integer> retrieveAppUsageMap(long epochId) {
        Map<String, Integer> appUsageMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return appUsageMap;
        }

        String[] projection = {
                TopicsTables.AppUsageHistoryContract.APP,
        };

        String selection = TopicsTables.AppUsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs = {String.valueOf(epochId)};

        try (
                Cursor cursor =
                        db.query(TopicsTables.AppUsageHistoryContract.TABLE, projection,
                                selection,
                                selectionArgs, null, null,
                                null, null)
        ) {
            while (cursor.moveToNext()) {
                String app = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppUsageHistoryContract.APP));
                appUsageMap.put(app, appUsageMap.getOrDefault(app, 0) + 1);
            }
        }

        return appUsageMap;
    }

    /**
     * Persist the Callers can learn topic map to DB.
     *
     * @param epochId           the epoch Id.
     * @param callerCanLearnMap callerCanLearnMap = Map<Topic, Set<Caller>>
     *                          This is a Map from Topic to set of App or Sdk (Caller = App or Sdk)
     *                          that can learn
     *                          about that topic. This is similar to the table Can Learn Topic in
     *                          the explainer.
     */
    public void persistCallerCanLearnTopics(
            long epochId, @NonNull Map<Integer, Set<String>> callerCanLearnMap) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<Integer, Set<String>> entry : callerCanLearnMap.entrySet()) {
            Integer topic = entry.getKey();
            Set<String> callers = entry.getValue();

            for (String caller : callers) {
                ContentValues values = new ContentValues();
                values.put(TopicsTables.CallerCanLearnTopicsContract.CALLER, caller);
                values.put(TopicsTables.CallerCanLearnTopicsContract.TOPIC, topic);
                values.put(TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID, epochId);

                try {
                    db.insert(TopicsTables.CallerCanLearnTopicsContract.TABLE,
                            /* nullColumnHack */ null, values);
                } catch (SQLException e) {
                    LogUtil.e(e, "Failed to record can learn topic.");
                }
            }
        }
    }

    /**
     * Retrieve the CallersCanLearnTopicsMap
     * This is a Map from Topic to set of App or Sdk (Caller = App or Sdk) that can learn about that
     * topic. This is similar to the table Can Learn Topic in the explainer.
     * We will look back numberOfLookBackEpochs epochs. The current explainer uses 3 past epochs.
     * Basically we select epochId between [epochId - numberOfLookBackEpochs + 1, epochId]
     *
     * @param epochId                the epochId
     * @param numberOfLookBackEpochs Look back numberOfLookBackEpochs.
     * @return a Map<Topic, Set<Caller>>  where Caller = App or Sdk.
     */
    @VisibleForTesting
    @NonNull
    public Map<Integer, Set<String>> retrieveCallerCanLearnTopicsMap(
            long epochId, int numberOfLookBackEpochs) {
        Preconditions.checkArgumentPositive(
                numberOfLookBackEpochs, "numberOfLookBackEpochs must be positive!");

        Map<Integer, Set<String>> callerCanLearnMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return callerCanLearnMap;
        }

        String[] projection = {
                TopicsTables.CallerCanLearnTopicsContract.CALLER,
                TopicsTables.CallerCanLearnTopicsContract.TOPIC,
        };

        // Select epochId between [epochId - numberOfLookBackEpochs + 1, epochId]
        String selection =
                " ? <= "
                        + TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID
                        + " AND "
                        + TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID
                        + " <= ?";
        String[] selectionArgs = {
                String.valueOf(epochId - numberOfLookBackEpochs + 1), String.valueOf(epochId)
        };

        try (Cursor cursor =
                     db.query(
                             /* distinct = */ true,
                             TopicsTables.CallerCanLearnTopicsContract.TABLE,
                             projection,
                             selection,
                             selectionArgs,
                             null,
                             null,
                             null,
                             null)) {
            if (cursor == null) {
                return callerCanLearnMap;
            }

            while (cursor.moveToNext()) {
                String caller =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.CallerCanLearnTopicsContract.CALLER));
                Integer topic =
                        Integer.parseInt(cursor.getString(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.CallerCanLearnTopicsContract.TOPIC)));

                if (!callerCanLearnMap.containsKey(topic)) {
                    callerCanLearnMap.put(topic, new HashSet<>());
                }
                callerCanLearnMap.get(topic).add(caller);
            }
        }

        return callerCanLearnMap;
    }

    // Persist the Apps, Sdks returned topics to DB.
    // returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>

    /**
     * Persist the Apps, Sdks returned topics to DB.
     *
     * @param epochId         the epoch Id
     * @param taxonomyVersion The Taxonomy Version
     * @param modelVersion    The Model Version
     *                        returnedAppSdkTopics = Map<Pair<App, Sdk>, Topic>
     */
    public void persistReturnedAppTopicsMap(long epochId, long taxonomyVersion, long modelVersion,
            @NonNull Map<Pair<String, String>, Integer> returnedAppSdkTopics) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        for (Map.Entry<Pair<String, String>, Integer> app : returnedAppSdkTopics.entrySet()) {
            // Entry: Key = <Pair<App, Sdk>, Value = Topic.
            ContentValues values = new ContentValues();
            values.put(TopicsTables.ReturnedTopicContract.EPOCH_ID, epochId);
            values.put(TopicsTables.ReturnedTopicContract.APP, app.getKey().first);
            values.put(TopicsTables.ReturnedTopicContract.SDK, app.getKey().second);
            values.put(TopicsTables.ReturnedTopicContract.TOPIC, app.getValue());
            values.put(TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION, taxonomyVersion);
            values.put(TopicsTables.ReturnedTopicContract.MODEL_VERSION, modelVersion);

            try {
                db.insert(TopicsTables.ReturnedTopicContract.TABLE,
                        /* nullColumnHack */ null, values);
            } catch (SQLException e) {
                LogUtil.e(e, "Failed to record returned topic.");
            }
        }
    }

    /**
     * Retrieve from the Topics ReturnedTopics Table and populate into the map.
     * Will return topics for epoch with epochId in [epochId - numberOfLookBackEpochs + 1, epochId]
     *
     * @param epochId                the current epochId
     * @param numberOfLookBackEpochs How many epoch to look back. The curent explainer uses 3 epochs
     * @return Map<EpochId, Map < Pair < App, Sdk>, Topic>
     */
    @NonNull
    public Map<Long, Map<Pair<String, String>, Topic>> retrieveReturnedTopics(long epochId,
            int numberOfLookBackEpochs) {
        Map<Long, Map<Pair<String, String>, Topic>> topicsMap = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return topicsMap;
        }

        String[] projection = {
                TopicsTables.ReturnedTopicContract.EPOCH_ID,
                TopicsTables.ReturnedTopicContract.APP,
                TopicsTables.ReturnedTopicContract.SDK,
                TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION,
                TopicsTables.ReturnedTopicContract.MODEL_VERSION,
                TopicsTables.ReturnedTopicContract.TOPIC,
        };

        // Select epochId between [epochId - numberOfLookBackEpochs + 1, epochId]
        String selection = " ? <= " + TopicsTables.ReturnedTopicContract.EPOCH_ID
                + " AND " + TopicsTables.ReturnedTopicContract.EPOCH_ID + " <= ?";
        String[] selectionArgs = {String.valueOf(epochId - numberOfLookBackEpochs + 1),
                String.valueOf(epochId)};

        try (Cursor cursor = db.query(
                TopicsTables.ReturnedTopicContract.TABLE,   // The table to query
                projection,             // The array of columns to return (pass null to get all)
                selection,              // The columns for the WHERE clause
                selectionArgs,          // The values for the WHERE clause
                null,           // don't group the rows
                null,            // don't filter by row groups
                null            // The sort order
        )) {
            if (cursor == null) {
                return topicsMap;
            }

            while (cursor.moveToNext()) {
                long cursorEpochId = cursor.getLong(cursor.getColumnIndexOrThrow(
                        TopicsTables.ReturnedTopicContract.EPOCH_ID));
                String app = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.ReturnedTopicContract.APP));
                String sdk = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.ReturnedTopicContract.SDK));
                long taxonomyVersion = cursor.getLong(cursor.getColumnIndexOrThrow(
                        TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION));
                long modelVersion = cursor.getInt(cursor.getColumnIndexOrThrow(
                        TopicsTables.ReturnedTopicContract.MODEL_VERSION));
                String topicString = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.ReturnedTopicContract.TOPIC));

                // Building Map<EpochId, Map<Pair<AppId, AdTechId>, Topic>
                if (!topicsMap.containsKey(cursorEpochId)) {
                    topicsMap.put(cursorEpochId, new HashMap<>());
                }

                Topic topic = Topic.create(
                        Integer.parseInt(topicString), taxonomyVersion, modelVersion);
                topicsMap.get(cursorEpochId).put(Pair.create(app, sdk), topic);
            }
        }

        return topicsMap;
    }

    /**
     * Record {@link Topic} which should be blocked.
     *
     * @param topic {@link Topic} to block.
     */
    public void recordBlockedTopic(@NonNull Topic topic) {
        Objects.requireNonNull(topic);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }
        // Create a new map of values, where column names are the keys
        ContentValues values = getContentValuesForBlockedTopic(topic);

        try {
            db.insert(TopicsTables.BlockedTopicsContract.TABLE,
                    /* nullColumnHack */ null, values);
        } catch (SQLException e) {
            LogUtil.e("Failed to record blocked topic." + e.getMessage());
        }
    }

    @NonNull
    private ContentValues getContentValuesForBlockedTopic(@NonNull Topic topic) {
        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(TopicsTables.BlockedTopicsContract.TOPIC, topic.getTopic());
        values.put(TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION, topic.getTaxonomyVersion());
        values.put(TopicsTables.BlockedTopicsContract.MODEL_VERSION, topic.getModelVersion());
        return values;
    }

    /**
     * Remove blocked {@link Topic}.
     *
     * @param topic blocked {@link Topic} to remove.
     */
    public void removeBlockedTopic(@NonNull Topic topic) {
        Objects.requireNonNull(topic);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Where statement for triplet: topics, taxonomyVersion, modelVersion
        String whereClause = " ? = " + TopicsTables.BlockedTopicsContract.TOPIC
                + " AND " + TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION + " = ?"
                + " AND " + TopicsTables.BlockedTopicsContract.MODEL_VERSION + " = ?";
        String[] whereArgs = {String.valueOf(topic.getTopic()),
                String.valueOf(topic.getTaxonomyVersion()), String.valueOf(
                topic.getModelVersion())};

        try {
            db.delete(TopicsTables.BlockedTopicsContract.TABLE,
                    whereClause, whereArgs);
        } catch (SQLException e) {
            LogUtil.e("Failed to record blocked topic." + e.getMessage());
        }
    }

    /**
     * Get a {@link List} of {@link Topic}s which are blocked.
     *
     * @return {@link List} a {@link List} of blocked {@link Topic}s.s
     */
    @NonNull
    public List<Topic> retrieveAllBlockedTopics() {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        List<Topic> blockedTopics = new ArrayList<>();
        if (db == null) {
            return blockedTopics;
        }

        try (Cursor cursor =
                db.query(
                        /* distinct = */ true,
                        TopicsTables.BlockedTopicsContract.TABLE, // The table to query
                        null, // Get all columns (null for all)
                        null, // Select all columns (null for all)
                        null, // Select all columns (null for all)
                        null, // Don't group the rows
                        null, // Don't filter by row groups
                        null, // don't sort
                        null // don't limit
                        )) {
            while (cursor.moveToNext()) {
                long taxonomyVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.TAXONOMY_VERSION));
                long modelVersion =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.MODEL_VERSION));
                int topicInt =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        TopicsTables.BlockedTopicsContract.TOPIC));
                Topic topic = Topic.create(topicInt, taxonomyVersion, modelVersion);

                blockedTopics.add(topic);
            }
        }

        return blockedTopics;
    }

    /**
     * Delete from epoch-related tables for data older than/equal to certain epoch in DB.
     *
     * @param tableName the table to delete data from
     * @param epochColumnName epoch Column name for given table
     * @param epochToDeleteFrom the epoch to delete starting from (inclusive)
     */
    public void deleteDataOfOldEpochs(
            @NonNull String tableName, @NonNull String epochColumnName, long epochToDeleteFrom) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Delete epochId before epochToDeleteFrom (including epochToDeleteFrom)
        String deletion = " " + epochColumnName + " <= ?";
        String[] deletionArgs = {String.valueOf(epochToDeleteFrom)};

        try {
            db.delete(tableName, deletion, deletionArgs);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to delete old epochs' data.");
        }
    }
}
