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

package com.android.adservices.service.measurement;

import java.util.concurrent.TimeUnit;

/**
 * Class for holding privacy related parameters.
 * All values in this class are temporary and subject to change based on feedback and testing.
 */
public final class PrivacyParams {

    /**
     * Max reports for 'Navigation' {@link Source}.
     */
    public static final int NAVIGATION_SOURCE_MAX_REPORTS = 3;

    /**
     * Max reports for 'Event' {@link Source}.
     */
    public static final int EVENT_SOURCE_MAX_REPORTS = 1;

    /**
     * Max reports for Install Attributed 'Navigation' {@link Source}.
     */
    public static final int INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS = 3;

    /**
     * Max reports for Install Attributed 'Event' {@link Source}.
     */
    public static final int INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS = 2;

    /**
     * Maximum attributions per rate limit window.
     * Rate limit unit: (Source Site, Destination Site, Reporting Site, Window).
     */
    public static final int MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

    /**
     * Rate limit window for (Source Site, Destination Site, Reporting Site, Window) privacy unit.
     * 28 days.
     */
    public static final long RATE_LIMIT_WINDOW_MILLISECONDS = TimeUnit.DAYS.toMillis(30);

    /**
     * Early reporting window for 'Navigation' {@link Source}.
     * 2 days and 7 days.
     */
    public static final long[] NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS = new long[]{
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7)
    };

    /**
     * Early reporting window for 'Event' {@link Source}.
     * No windows.
     */
    public static final long[] EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS = new long[]{ };

    /**
     * Early reporting window for Install Attributed 'Navigation' {@link Source}.
     * 2 days and 7 days.
     */
    public static final long[] INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS =
            new long[]{ TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7) };

    /**
     * Early reporting window for Install Attributed 'Event' {@link Source}.
     * 2 days.
     */
    public static final long[] INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS =
            new long[]{ TimeUnit.DAYS.toMillis(2) };

    /**
     * {@link Source} Noise probability for 'Event'
     */
    public static final double EVENT_NOISE_PROBABILITY = 0.0000025D;

    /**
     * {@link Source} Noise probability for 'Navigation'
     */
    public static final double NAVIGATION_NOISE_PROBABILITY = 0.0024263D;

    /**
     * {@link Source} Noise probability for 'Event' which supports install attribution.
     */
    public static final double INSTALL_ATTR_EVENT_NOISE_PROBABILITY = 0.0000125D;

    /**
     * {@link Source} Noise probability for 'Navigation' which supports install attribution.
     */
    public static final double INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY =
                NAVIGATION_NOISE_PROBABILITY;

    /**
     * Trigger data cardinality for 'Event' {@link Source} attribution.
     */
    public static final int EVENT_TRIGGER_DATA_CARDINALITY = 2;

    /**
     * Trigger data cardinality for 'Navigation' {@link Source} attribution.
     */
    public static final int NAVIGATION_TRIGGER_DATA_CARDINALITY = 8;

    /**
     * Min expiration value in seconds for attribution reporting register source.
     */
    public static final long MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
            TimeUnit.DAYS.toSeconds(2);

    /**
     * Max expiration value in seconds for attribution reporting register source. This value is
     * also the default no expiration was specified.
     */
    public static final long MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS =
            TimeUnit.DAYS.toSeconds(30);

    /**
     * Minimum limit of duration to determine attribution for a verified installation.
     */
    public static final long MIN_INSTALL_ATTRIBUTION_WINDOW = TimeUnit.DAYS.toSeconds(2);

    /**
     * Maximum limit of duration to determine attribution for a verified installation.
     */
    public static final long MAX_INSTALL_ATTRIBUTION_WINDOW = TimeUnit.DAYS.toSeconds(30);

    /**
     * Default and minimum value for cooldown period of source which led to installation.
     */
    public static final long MIN_POST_INSTALL_EXCLUSIVITY_WINDOW = 0;

    /**
     * Maximum acceptable install cooldown period.
     */
    public static final long MAX_POST_INSTALL_EXCLUSIVITY_WINDOW = TimeUnit.DAYS.toSeconds(30);

    /**
     * L1, the maximum sum of the contributions (values) across all buckets for a given source
     * event.
     */
    public static final int MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE = 65536;

    /** Amount of bytes allocated for aggregate histogram bucket */
    public static final int AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE = 16;

    /** Amount of bytes allocated for aggregate histogram value */
    public static final int AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE = 4;

    /**
     * Max distinct ad-techs for attribution per { Advertiser X Publisher X TimePeriod }.
     */
    public static final int MAX_DISTINCT_AD_TECHS_PER_PUBLISHER_X_DESTINATION_IN_ATTRIBUTION = 10;

    /**
     * Max distinct advertisers with pending impressions per { Publisher X TimePeriod }.
     */
    public static final int MAX_DISTINCT_DESTINATIONS_PER_PUBLISHER_IN_ACTIVE_SOURCE = 100;

    /**
     * Max distinct ad-techs with source registration per { Publisher X Advertiser X TimePeriod }.
     */
    public static final int MAX_DISTINCT_AD_TECHS_PER_PUBLISHER_X_DESTINATION_IN_SOURCE = 100;

    private PrivacyParams() {
    }
}
