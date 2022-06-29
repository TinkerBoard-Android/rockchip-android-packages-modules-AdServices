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

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Container class for Topics API table definitions and constants. */
public final class TopicsTables {

    static final String TOPICS_TABLE_PREFIX = "topics_";

    /** Topics Taxonomy Table. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface TaxonomyContract {
        String TABLE = TOPICS_TABLE_PREFIX + "taxonomy";
        String ID = "_id";
        String TAXONOMY_VERSION = "taxonomy_version";
        String MODEL_VERSION = "model_version";
        String TOPIC = "topic";
    }

    // Table Create Statement for the Topics Epoch table
    private static final String CREATE_TABLE_TOPICS_TAXONOMY =
            "CREATE TABLE "
                    + TaxonomyContract.TABLE
                    + "("
                    + TaxonomyContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + TaxonomyContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + TaxonomyContract.MODEL_VERSION
                    + " INTEGER NOT NULL, "
                    + TaxonomyContract.TOPIC
                    + " INTEGER NOT NULL"
                    + ")";

    /**
     * This table has apps' classification Topics generated by the ML Classifier. In each epoch
     * computation, the ML Classifier will generate topics for each app that uses the Topics API in
     * the epoch.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface AppClassificationTopicsContract {
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
                    + AppClassificationTopicsContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + AppClassificationTopicsContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + AppClassificationTopicsContract.APP
                    + " TEXT NOT NULL, "
                    + AppClassificationTopicsContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + AppClassificationTopicsContract.MODEL_VERSION
                    + " INTEGER NOT NULL, "
                    + AppClassificationTopicsContract.TOPIC
                    + " INTEGER NOT NULL"
                    + ")";

    /**
     * This table has callers and which topics they can learn. Caller can be either (1) app in case
     * the app called the Topics API directly. (2) sdk in case the sdk called the Topics API.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface CallerCanLearnTopicsContract {
        String TABLE = TOPICS_TABLE_PREFIX + "caller_can_learn_topic";
        String ID = "_id";
        String EPOCH_ID = "epoch_id";
        String CALLER = "caller";
        String TOPIC = "topic";
        String TAXONOMY_VERSION = "taxonomy_version";
        String MODEL_VERSION = "model_version";
    }

    // Create Statement for the Caller Learned Topic table.
    private static final String CREATE_TABLE_CALLER_CAN_LEARN_TOPICS =
            "CREATE TABLE "
                    + CallerCanLearnTopicsContract.TABLE
                    + "("
                    + CallerCanLearnTopicsContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + CallerCanLearnTopicsContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + CallerCanLearnTopicsContract.CALLER
                    + " TEXT NOT NULL, "
                    + CallerCanLearnTopicsContract.TOPIC
                    + " INTEGER NOT NULL, "
                    + CallerCanLearnTopicsContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + CallerCanLearnTopicsContract.MODEL_VERSION
                    + " INTEGER NOT NULL"
                    + ")";

    // TODO(b/223446202): Make this table to configurable numbers of top topics.

    /**
     * Top Topics Table. There are top 5 topics and 1 random topic. In case there is not enough
     * usage to generate top 5 topics, random ones will be generated.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface TopTopicsContract {
        String TABLE = TOPICS_TABLE_PREFIX + "top_topics";
        String ID = "_id";
        String EPOCH_ID = "epoch_id";
        String TOPIC1 = "topic1";
        String TOPIC2 = "topic2";
        String TOPIC3 = "topic3";
        String TOPIC4 = "topic4";
        String TOPIC5 = "topic5";
        String RANDOM_TOPIC = "random_topic";
        String TAXONOMY_VERSION = "taxonomy_version";
        String MODEL_VERSION = "model_version";
    }

    // Table Create Statement for the Top Topics table
    private static final String CREATE_TABLE_TOP_TOPICS =
            "CREATE TABLE "
                    + TopTopicsContract.TABLE
                    + "("
                    + TopTopicsContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + TopTopicsContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.TOPIC1
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.TOPIC2
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.TOPIC3
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.TOPIC4
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.TOPIC5
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.RANDOM_TOPIC
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + TopTopicsContract.MODEL_VERSION
                    + " INTEGER NOT NULL"
                    + ")";

    /**
     * The returned topic for the app or for the sdk. Note: for App usages directly without any SDK,
     * the SDK Name is set to empty string.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface ReturnedTopicContract {
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
                    + ReturnedTopicContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + ReturnedTopicContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + ReturnedTopicContract.APP
                    + " TEXT NOT NULL, "
                    + ReturnedTopicContract.SDK
                    + " TEXT NOT NULL, "
                    + ReturnedTopicContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + ReturnedTopicContract.MODEL_VERSION
                    + " INTEGER NOT NULL, "
                    + ReturnedTopicContract.TOPIC
                    + " INTEGER NOT NULL"
                    + ")";

    /**
     * Table to store the app/sdk usage history. Whenever an app or sdk calls the Topics API, one
     * entry will be generated with the timestamp.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface UsageHistoryContract {
        String TABLE = TOPICS_TABLE_PREFIX + "usage_history";
        String EPOCH_ID = "epoch_id";
        String APP = "app";
        String SDK = "sdk";
    }

    // Create Statement for the Usage History table
    private static final String CREATE_TABLE_USAGE_HISTORY =
            "CREATE TABLE "
                    + UsageHistoryContract.TABLE
                    + "("
                    + UsageHistoryContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + UsageHistoryContract.APP
                    + " TEXT NOT NULL, "
                    + UsageHistoryContract.SDK
                    + " TEXT"
                    + ")";

    /**
     * Table to store history for app only Whenever an app calls the Topics API, one entry will be
     * generated.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface AppUsageHistoryContract {
        String TABLE = TOPICS_TABLE_PREFIX + "app_usage_history";
        String ID = "_id";
        String EPOCH_ID = "epoch_id";
        String APP = "app";
    }

    // Create Statement for the Usage History App Only table
    private static final String CREATE_TABLE_APP_USAGE_HISTORY =
            "CREATE TABLE "
                    + AppUsageHistoryContract.TABLE
                    + "("
                    + AppUsageHistoryContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + AppUsageHistoryContract.EPOCH_ID
                    + " INTEGER NOT NULL, "
                    + AppUsageHistoryContract.APP
                    + " TEXT NOT NULL"
                    + ")";

    /** Table to store all blocked {@link Topic}s. Blocked topics are controlled by user. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public interface BlockedTopicsContract {
        String TABLE = TOPICS_TABLE_PREFIX + "blocked";
        String ID = "_id";
        String TAXONOMY_VERSION = "taxonomy_version";
        String MODEL_VERSION = "model_version";
        String TOPIC = "topic";
    }

    // Create Statement for the blocked topics table.
    private static final String CREATE_TABLE_BLOCKED_TOPICS =
            "CREATE TABLE "
                    + BlockedTopicsContract.TABLE
                    + "("
                    + BlockedTopicsContract.ID
                    + " INTEGER PRIMARY KEY, "
                    + BlockedTopicsContract.TAXONOMY_VERSION
                    + " INTEGER NOT NULL, "
                    + BlockedTopicsContract.MODEL_VERSION
                    + " INTEGER NOT NULL, "
                    + BlockedTopicsContract.TOPIC
                    + " INTEGER NOT NULL"
                    + ")";

    /**
     * Table to store the original timestamp when the user calls Topics API. This table should have
     * only 1 row that stores the origin.
     */
    public interface EpochOriginContract {
        String TABLE = TOPICS_TABLE_PREFIX + "epoch_origin";
        String ONE_ROW_CHECK = "one_row_check"; // to constrain 1 origin
        String ORIGIN = "origin";
    }

    // At the first time inserting a record, it won't persist one_row_check field so that this first
    // entry will have one_row_check = 1. Therefore, further persisting is not allowed as primary
    // key cannot be duplicated value and one_row_check is constrained to only equal to 1 to forbid
    // any increment.
    private static final String CREATE_TABLE_EPOCH_ORIGIN =
            "CREATE TABLE "
                    + EpochOriginContract.TABLE
                    + "("
                    + EpochOriginContract.ONE_ROW_CHECK
                    + " INTEGER PRIMARY KEY DEFAULT 1, "
                    + EpochOriginContract.ORIGIN
                    + " INTEGER NOT NULL, "
                    + "CONSTRAINT one_row_constraint CHECK ("
                    + EpochOriginContract.ONE_ROW_CHECK
                    + " = 1) "
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
                            CREATE_TABLE_APP_USAGE_HISTORY,
                            CREATE_TABLE_CALLER_CAN_LEARN_TOPICS,
                            CREATE_TABLE_BLOCKED_TOPICS,
                            CREATE_TABLE_EPOCH_ORIGIN));

    // Private constructor to prevent instantiation.
    private TopicsTables() {}
}
