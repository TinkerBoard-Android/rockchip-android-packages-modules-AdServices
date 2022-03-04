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


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Container class for Topics API table definitions and constants.
 */
public final class TopicsTables {

    static final String TOPICS_TABLE_PREFIX = "topics_";

    // Topics Taxonomy Table.
    interface TaxonomyContract {
        String TABLE = TOPICS_TABLE_PREFIX + "taxonomy";
        String ID = "_id";
        String VERSION = "version";
        String TOPIC = "topic";
    }

    // Table Create Statement for the Topics Epoch table
    private static final String CREATE_TABLE_TOPICS_TAXONOMY =
            "CREATE TABLE "
                    + TaxonomyContract.TABLE
                    + "("
                    + TaxonomyContract.ID + " INTEGER PRIMARY KEY, "
                    + TaxonomyContract.VERSION + " TEXT, "
                    + TaxonomyContract.TOPIC + " TEXT"
                    + ")";

    // This table has apps' classification Topics generated by the ML Classifier.
    // In each epoch computation, the ML Classifier will generate topics for each app that uses
    // the Topics API in the epoch.
    interface AppClassificationTopicsContract {
        String TABLE = TOPICS_TABLE_PREFIX + "app_classification_topics";
        String ID = "_id";
        String EPOCH_ID = "epoch_id";
        String APP = "app";
        String TAXONOMY_VERSION = "taxonomy_version";
        String MODEL_VERSION = "model_version";
        String TOPIC = "topic";
    }

    // Create Statement for the returned Topics table
    private static final String CREATE_TABLE_APP_CLASSIFICATION_TOPICS =
            "CREATE TABLE "
                    + AppClassificationTopicsContract.TABLE
                    + "("
                    + AppClassificationTopicsContract.ID + " INTEGER PRIMARY KEY, "
                    + AppClassificationTopicsContract.EPOCH_ID + " INTEGER NOT NULL, "
                    + AppClassificationTopicsContract.APP + " TEXT NOT NULL, "
                    + AppClassificationTopicsContract.TAXONOMY_VERSION + " INTEGER NOT NULL, "
                    + AppClassificationTopicsContract.MODEL_VERSION + " INTEGER NOT NULL, "
                    + AppClassificationTopicsContract.TOPIC + " TEXT NOT NULL"
                    + ")";

    // This table has callers and which topics they can learn.
    // Caller can be either
    // (1) app in case the app called the Topics API directly.
    // (2) sdk in case the sdk called the Topics API.
    interface CallerCanLearnTopicsContract {
        String TABLE = TOPICS_TABLE_PREFIX + "caller_can_learn_topic";
        String ID = "_id";
        String EPOCH_ID = "epoch_id";
        String CALLER = "caller";
        String TOPIC = "topic";
    }

    // Create Statement for the Caller Learned Topic table.
    private static final String CREATE_TABLE_CALLER_CAN_LEARN_TOPICS =
            "CREATE TABLE "
                    + CallerCanLearnTopicsContract.TABLE
                    + "("
                    + CallerCanLearnTopicsContract.ID + " INTEGER PRIMARY KEY, "
                    + CallerCanLearnTopicsContract.EPOCH_ID + " INTEGER NOT NULL, "
                    + CallerCanLearnTopicsContract.CALLER + " TEXT NOT NULL, "
                    + CallerCanLearnTopicsContract.TOPIC + " TEXT NOT NULL"
                    + ")";

    // TODO(b/223446202): Make this table to configurable numbers of top topics.
    // Top Topics Table.
    // There are top 5 topics and 1 random topic.
    // In case there is not enough usage to generate top 5 topics, random ones will be generated.
    interface TopTopicsContract {
        String TABLE = TOPICS_TABLE_PREFIX + "top_topics";
        String EPOCH_ID = "epoch_id";
        String TOPIC1 = "topic1";
        String TOPIC2 = "topic2";
        String TOPIC3 = "topic3";
        String TOPIC4 = "topic4";
        String TOPIC5 = "topic5";
        String RANDOM_TOPIC = "random_topic";
    }

    // Table Create Statement for the Top Topics table
    private static final String CREATE_TABLE_TOP_TOPICS =
            "CREATE TABLE "
                    + TopTopicsContract.TABLE
                    + "("
                    + TopTopicsContract.EPOCH_ID + " INTEGER PRIMARY KEY, "
                    + TopTopicsContract.TOPIC1 + " TEXT NOT NULL, "
                    + TopTopicsContract.TOPIC2 + " TEXT NOT NULL, "
                    + TopTopicsContract.TOPIC3 + " TEXT NOT NULL, "
                    + TopTopicsContract.TOPIC4 + " TEXT NOT NULL, "
                    + TopTopicsContract.TOPIC5 + " TEXT NOT NULL, "
                    + TopTopicsContract.RANDOM_TOPIC + " TEXT NOT NULL"
                    + ")";

    // The returned topic for the app or for the sdk.
    // Note: for App usages directly without any SDK, the SDK Name is set to empty
    // string.
    interface ReturnedTopicContract {
        String TABLE = TOPICS_TABLE_PREFIX + "returned_topics";
        String ID = "_id";
        String EPOCH_ID = "epoch_id";
        String APP = "app";
        String SDK = "sdk";
        String TAXONOMY_VERSION = "taxonomy_version";
        String MODEL_VERSION = "model_version";
        String TOPIC = "topic";
    }

    // Create Statement for the returned Topics table
    private static final String CREATE_TABLE_RETURNED_TOPIC =
            "CREATE TABLE "
                    + ReturnedTopicContract.TABLE
                    + "("
                    + ReturnedTopicContract.ID + " INTEGER PRIMARY KEY, "
                    + ReturnedTopicContract.EPOCH_ID + " INTEGER NOT NULL, "
                    + ReturnedTopicContract.APP + " TEXT NOT NULL, "
                    + ReturnedTopicContract.SDK + " TEXT NOT NULL, "
                    + ReturnedTopicContract.TAXONOMY_VERSION + " INTEGER NOT NULL, "
                    + ReturnedTopicContract.MODEL_VERSION + " INTEGER NOT NULL, "
                    + ReturnedTopicContract.TOPIC + " TEXT NOT NULL"
                    + ")";

    // Table to store the app/sdk usage history.
    // Whenever an app or sdk calls the Topics API, one entry will be generated with the timestamp.
    interface UsageHistoryContract {
        String TABLE = TOPICS_TABLE_PREFIX + "usage_history";
        String EPOCH_ID = "timestamp";
        String APP = "app";
        String SDK = "sdk";
    }

    // Create Statement for the Usage History table
    private static final String CREATE_TABLE_USAGE_HISTORY =
            "CREATE TABLE "
                    + UsageHistoryContract.TABLE
                    + "("
                    + UsageHistoryContract.EPOCH_ID + " INTEGER NOT NULL, "
                    + UsageHistoryContract.APP + " TEXT NOT NULL, "
                    + UsageHistoryContract.SDK + " TEXT"
                    + ")";

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_TOPICS_TAXONOMY,
                            CREATE_TABLE_APP_CLASSIFICATION_TOPICS,
                            CREATE_TABLE_TOP_TOPICS,
                            CREATE_TABLE_RETURNED_TOPIC,
                            CREATE_TABLE_USAGE_HISTORY,
                            CREATE_TABLE_CALLER_CAN_LEARN_TOPICS));

    // Private constructor to prevent instantiation.
    private TopicsTables() {
    }
}
