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

package com.android.adservices.service;

import static java.lang.Float.parseFloat;

import android.annotation.NonNull;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/** Flags Implementation that delegates to DeviceConfig. */
// TODO(b/228037065): Add validation logics for Feature flags read from PH.
public final class PhFlags implements Flags {
    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Common Keys
    static final String KEY_MAINTENANCE_JOB_PERIOD_MS = "maintenance_job_period_ms";
    static final String KEY_MAINTENANCE_JOB_FLEX_MS = "maintenance_job_flex_ms";

    // Topics keys
    static final String KEY_TOPICS_EPOCH_JOB_PERIOD_MS = "topics_epoch_job_period_ms";
    static final String KEY_TOPICS_EPOCH_JOB_FLEX_MS = "topics_epoch_job_flex_ms";
    static final String KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC =
            "topics_percentage_for_random_topics";
    static final String KEY_TOPICS_NUMBER_OF_TOP_TOPICS = "topics_number_of_top_topics";
    static final String KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS = "topics_number_of_random_topics";
    static final String KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS = "topics_number_of_lookback_epochs";

    // Topics classifier keys
    static final String KEY_CLASSIFIER_TYPE = "classifier_type";
    static final String KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS = "classifier_number_of_top_labels";
    static final String KEY_CLASSIFIER_THRESHOLD = "classifier_threshold";
    static final String KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS = "classifier_description_max_words";
    static final String KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH = "classifier_description_max_length";

    // Measurement keys
    static final String KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS =
            "measurement_event_main_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS =
            "measurement_event_fallback_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL =
            "measurement_aggregate_encryption_key_coordinator_url";
    static final String KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS =
            "measurement_aggregate_main_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS =
            "measurement_aggregate_fallback_reporting_job_period_ms";
    static final String KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS =
            "measurement_network_connect_timeout_ms";
    static final String KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS =
            "measurement_network_read_timeout_ms";
    static final String KEY_MEASUREMENT_DB_SIZE_LIMIT = "measurement_db_size_limit";
    static final String KEY_MEASUREMENT_MANIFEST_FILE_URL = "mdd_measurement_manifest_file_url";
    static final String KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS =
            "measurement_registration_input_event_valid_window_ms";
    static final String KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED =
            "measurement_is_click_verification_enabled";

    // FLEDGE Custom Audience keys
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT = "fledge_custom_audience_max_count";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT =
            "fledge_custom_audience_per_app_max_count";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT =
            "fledge_custom_audience_max_owner_count";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS =
            "fledge_custom_audience_default_expire_in_days";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS =
            "fledge_custom_audience_max_activate_in_days";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS =
            "fledge_custom_audience_max_expire_in_days";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B =
            "key_fledge_custom_audience_max_name_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B =
            "key_fledge_custom_audience_max_daily_update_uri_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B =
            "key_fledge_custom_audience_max_bidding_logic_uri_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B =
            "fledge_custom_audience_max_user_bidding_signals_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B =
            "fledge_custom_audience_max_trusted_bidding_data_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B =
            "fledge_custom_audience_max_ads_size_b";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS =
            "fledge_custom_audience_max_num_ads";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS =
            "fledge_custom_audience_active_time_window_ms";

    // FLEDGE Background Fetch keys
    static final String KEY_FLEDGE_BACKGROUND_FETCH_ENABLED = "fledge_background_fetch_enabled";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS =
            "fledge_background_fetch_job_period_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS =
            "fledge_background_fetch_job_flex_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS =
            "fledge_background_fetch_job_max_runtime_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED =
            "fledge_background_fetch_max_num_updated";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE =
            "fledge_background_fetch_thread_pool_size";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S =
            "fledge_background_fetch_eligible_update_base_interval_s";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS =
            "fledge_background_fetch_network_connect_timeout_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS =
            "fledge_background_fetch_network_read_timeout_ms";
    static final String KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B =
            "fledge_background_fetch_max_response_size_b";

    // FLEDGE Ad Selection keys
    static final String KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT =
            "fledge_ad_selection_concurrent_bidding_count";
    static final String KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS =
            "fledge_ad_selection_bidding_timeout_per_ca_ms";
    static final String KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS =
            "fledge_ad_selection_scoring_timeout_ms";
    static final String KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS =
            "fledge_ad_selection_overall_timeout_ms";
    static final String KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS =
            "fledge_report_impression_overall_timeout_ms";
    static final String KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY =
            "topics_number_of_epochs_to_keep_in_history";

    // Fledge invoking app status keys
    static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION =
            "fledge_ad_selection_enforce_foreground_status_run_ad_selection";
    static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION =
            "fledge_ad_selection_enforce_foreground_status_report_impression";
    static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE =
            "fledge_ad_selection_enforce_foreground_status_ad_selection_override";
    static final String KEY_FOREGROUND_STATUS_LEVEL = "foreground_validation_status_level";
    static final String KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE =
            "fledge_ad_selection_enforce_foreground_status_custom_audience";
    static final String KEY_ENFORCE_FOREGROUND_STATUS_TOPICS = "topics_enforce_foreground_status";

    // Fledge JS isolate setting keys
    static final String KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE =
            "fledge_js_isolate_enforce_max_heap_size";
    static final String KEY_ISOLATE_MAX_HEAP_SIZE_BYTES = "fledge_js_isolate_max_heap_size_bytes";

    // MDD keys.
    static final String KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS = "downloader_connection_timeout_ms";
    static final String KEY_DOWNLOADER_READ_TIMEOUT_MS = "downloader_read_timeout_ms";
    static final String KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS = "downloader_max_download_threads";
    static final String KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL =
            "mdd_topics_classifier_manifest_file_url";

    // Killswitch keys
    static final String KEY_GLOBAL_KILL_SWITCH = "global_kill_switch";
    static final String KEY_MEASUREMENT_KILL_SWITCH = "measurement_kill_switch";
    static final String KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH =
            "measurement_api_delete_registrations_kill_switch";
    static final String KEY_MEASUREMENT_API_STATUS_KILL_SWITCH =
            "measurement_api_status_kill_switch";
    static final String KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH =
            "measurement_api_register_source_kill_switch";
    static final String KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH =
            "measurement_api_register_trigger_kill_switch";
    static final String KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH =
            "measurement_api_register_web_source_kill_switch";
    static final String KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH =
            "measurement_api_register_web_trigger_kill_switch";
    static final String KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH =
            "measurement_job_aggregate_fallback_reporting_kill_switch";
    static final String KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH =
            "measurement_job_aggregate_reporting_kill_switch";
    static final String KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH =
            "measurement_job_attribution_kill_switch";
    static final String KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH =
            "measurement_job_delete_expired_kill_switch";
    static final String KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH =
            "measurement_job_event_fallback_reporting_kill_switch";
    static final String KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH =
            "measurement_job_event_reporting_kill_switch";
    static final String KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH =
            "measurement_receiver_install_attribution_kill_switch";
    static final String KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH =
            "measurement_receiver_delete_packages_kill_switch";
    static final String KEY_TOPICS_KILL_SWITCH = "topics_kill_switch";
    static final String KEY_MDD_BACKGROUND_TASK_KILL_SWITCH = "mdd_background_task_kill_switch";
    static final String KEY_MDD_LOGGER_KILL_SWITCH = "mdd_logger_kill_switch";
    static final String KEY_ADID_KILL_SWITCH = "adid_kill_switch";
    static final String KEY_APPSETID_KILL_SWITCH = "appsetid_kill_switch";
    static final String KEY_FLEDGE_SELECT_ADS_KILL_SWITCH = "fledge_select_ads_kill_switch";
    static final String KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH =
            "fledge_custom_audience_service_kill_switch";

    // App/SDK AllowList/DenyList keys
    static final String KEY_PPAPI_APP_ALLOW_LIST = "ppapi_app_allow_list";
    static final String KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST = "ppapi_app_signature_allow_list";

    // Rate Limit keys
    static final String KEY_SDK_REQUEST_PERMITS_PER_SECOND = "sdk_request_permits_per_second";

    // Adservices enable status keys.
    static final String KEY_ADSERVICES_ENABLED = "adservice_enabled";

    // Disable enrollment check
    static final String KEY_DISABLE_TOPICS_ENROLLMENT_CHECK = "disable_topics_enrollment_check";
    static final String KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK = "disable_fledge_enrollment_check";

    // Disable Measurement enrollment check.
    static final String KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK =
            "disable_measurement_enrollment_check";

    // SystemProperty prefix. We can use SystemProperty to override the AdService Configs.
    private static final String SYSTEM_PROPERTY_PREFIX = "debug.adservices.";

    // Consent Notification debug mode keys.
    static final String KEY_CONSENT_NOTIFICATION_DEBUG_MODE = "consent_notification_debug_mode";

    // Consent Manager debug mode keys.
    static final String KEY_CONSENT_MANAGER_DEBUG_MODE = "consent_manager_debug_mode";

    // App/SDK AllowList/DenyList keys that have access to the web registration APIs
    static final String KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST = "web_context_client_allow_list";

    // Maximum possible percentage for percentage variables
    static final int MAX_PERCENTAGE = 100;

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    @Override
    public long getTopicsEpochJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        long topicsEpochJobPeriodMs =
                SystemProperties.getLong(
                        getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_PERIOD_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
                                /* defaultValue */ TOPICS_EPOCH_JOB_PERIOD_MS));
        if (topicsEpochJobPeriodMs <= 0) {
            throw new IllegalArgumentException("topicsEpochJobPeriodMs should > 0");
        }
        return topicsEpochJobPeriodMs;
    }

    @Override
    public long getTopicsEpochJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        long topicsEpochJobFlexMs =
                SystemProperties.getLong(
                        getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_FLEX_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                                /* defaultValue */ TOPICS_EPOCH_JOB_FLEX_MS));
        if (topicsEpochJobFlexMs <= 0) {
            throw new IllegalArgumentException("topicsEpochJobFlexMs should > 0");
        }
        return topicsEpochJobFlexMs;
    }

    @Override
    public int getTopicsPercentageForRandomTopic() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        int topicsPercentageForRandomTopic =
                SystemProperties.getInt(
                        getSystemPropertyName(KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC),
                        /* defaultValue */ DeviceConfig.getInt(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
                                /* defaultValue */ TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC));
        if (topicsPercentageForRandomTopic < 0 || topicsPercentageForRandomTopic > MAX_PERCENTAGE) {
            throw new IllegalArgumentException(
                    "topicsPercentageForRandomTopic should be between 0 and 100");
        }
        return topicsPercentageForRandomTopic;
    }

    @Override
    public int getTopicsNumberOfTopTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        int topicsNumberOfTopTopics =
                DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_TOPICS_NUMBER_OF_TOP_TOPICS,
                        /* defaultValue */ TOPICS_NUMBER_OF_TOP_TOPICS);
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfRandomTopics() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        int topicsNumberOfTopTopics =
                DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS,
                        /* defaultValue */ TOPICS_NUMBER_OF_RANDOM_TOPICS);
        if (topicsNumberOfTopTopics < 0) {
            throw new IllegalArgumentException("topicsNumberOfTopTopics should >= 0");
        }

        return topicsNumberOfTopTopics;
    }

    @Override
    public int getTopicsNumberOfLookBackEpochs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        int topicsNumberOfLookBackEpochs =
                DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS,
                        /* defaultValue */ TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS);
        if (topicsNumberOfLookBackEpochs < 1) {
            throw new IllegalArgumentException("topicsNumberOfLookBackEpochs should  >= 1");
        }

        return topicsNumberOfLookBackEpochs;
    }

    @Override
    public int getClassifierType() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getInt(
                getSystemPropertyName(KEY_CLASSIFIER_TYPE),
                DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_CLASSIFIER_TYPE,
                        /* defaultValue */ DEFAULT_CLASSIFIER_TYPE));
    }

    @Override
    public int getClassifierNumberOfTopLabels() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getInt(
                getSystemPropertyName(KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS),
                DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS,
                        /* defaultValue */ CLASSIFIER_NUMBER_OF_TOP_LABELS));
    }

    @Override
    public float getClassifierThreshold() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getFloat(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_CLASSIFIER_THRESHOLD,
                /* defaultValue */ CLASSIFIER_THRESHOLD);
    }

    @Override
    public int getClassifierDescriptionMaxWords() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_CLASSIFIER_DESCRIPTION_MAX_WORDS,
                /* defaultValue */ CLASSIFIER_DESCRIPTION_MAX_WORDS);
    }

    @Override
    public int getClassifierDescriptionMaxLength() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_CLASSIFIER_DESCRIPTION_MAX_LENGTH,
                /* defaultValue */ CLASSIFIER_DESCRIPTION_MAX_LENGTH);
    }

    @Override
    public long getMaintenanceJobPeriodMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobPeriodMs =
                SystemProperties.getLong(
                        getSystemPropertyName(KEY_MAINTENANCE_JOB_PERIOD_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MAINTENANCE_JOB_PERIOD_MS,
                                /* defaultValue */ MAINTENANCE_JOB_PERIOD_MS));
        if (maintenanceJobPeriodMs < 0) {
            throw new IllegalArgumentException("maintenanceJobPeriodMs should  >= 0");
        }
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return maintenanceJobPeriodMs;
    }

    @Override
    public long getMaintenanceJobFlexMs() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig) and then
        // hard-coded value.
        long maintenanceJobFlexMs =
                SystemProperties.getLong(
                        getSystemPropertyName(KEY_MAINTENANCE_JOB_FLEX_MS),
                        /* defaultValue */ DeviceConfig.getLong(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MAINTENANCE_JOB_FLEX_MS,
                                /* defaultValue */ MAINTENANCE_JOB_FLEX_MS));

        if (maintenanceJobFlexMs <= 0) {
            throw new IllegalArgumentException("maintenanceJobFlexMs should  > 0");
        }

        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return maintenanceJobFlexMs;
    }

    @Override
    public long getMeasurementEventMainReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementEventFallbackReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public String getMeasurementAggregateEncryptionKeyCoordinatorUrl() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL,
                /* defaultValue */ MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL);
    }

    @Override
    public long getMeasurementAggregateMainReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS,
                /* defaultValue */ MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS);
    }

    @Override
    public int getMeasurementNetworkConnectTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS,
                /* defaultValue */ MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getMeasurementNetworkReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS,
                /* defaultValue */ MEASUREMENT_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public long getMeasurementDbSizeLimit() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_DB_SIZE_LIMIT,
                /* defaultValue */ MEASUREMENT_DB_SIZE_LIMIT);
    }

    @Override
    public String getMeasurementManifestFileUrl() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_MANIFEST_FILE_URL,
                /* defaultValue */ MEASUREMENT_MANIFEST_FILE_URL);
    }

    @Override
    public long getMeasurementRegistrationInputEventValidWindowMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS,
                /* defaultValue */ MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS);
    }

    @Override
    public boolean getMeasurementIsClickVerificationEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED,
                /* defaultValue */ MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED);
    }

    @Override
    public long getFledgeCustomAudienceMaxCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudiencePerAppMaxCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceMaxOwnerCount() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT);
    }

    @Override
    public long getFledgeCustomAudienceDefaultExpireInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxActivationDelayInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS);
    }

    @Override
    public long getFledgeCustomAudienceMaxExpireInMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS);
    }

    @Override
    public int getFledgeCustomAudienceMaxNameSizeB() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxDailyUpdateUriSizeB() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxBiddingLogicUriSizeB() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxAdsSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B);
    }

    @Override
    public int getFledgeCustomAudienceMaxNumAds() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS);
    }

    @Override
    public long getFledgeCustomAudienceActiveTimeWindowInMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS,
                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS);
    }

    @Override
    public boolean getFledgeBackgroundFetchEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_ENABLED,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_ENABLED);
    }

    @Override
    public long getFledgeBackgroundFetchJobPeriodMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobFlexMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS);
    }

    @Override
    public long getFledgeBackgroundFetchJobMaxRuntimeMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS);
    }

    @Override
    public long getFledgeBackgroundFetchMaxNumUpdated() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED);
    }

    @Override
    public int getFledgeBackgroundFetchThreadPoolSize() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE);
    }

    @Override
    public long getFledgeBackgroundFetchEligibleUpdateBaseIntervalS() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS);
    }

    @Override
    public int getFledgeBackgroundFetchMaxResponseSizeB() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B,
                /* defaultValue */ FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B);
    }

    @Override
    public int getAdSelectionConcurrentBiddingCount() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT,
                /* defaultValue */ FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT);
    }

    @Override
    public long getAdSelectionBiddingTimeoutPerCaMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS);
    }

    @Override
    public long getAdSelectionScoringTimeoutMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS);
    }

    @Override
    public long getAdSelectionOverallTimeoutMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS);
    }

    @Override
    public long getReportImpressionOverallTimeoutMs() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS,
                /* defaultValue */ FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS);
    }

    // MDD related flags.
    @Override
    public int getDownloaderConnectionTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS,
                /* defaultValue */ DOWNLOADER_CONNECTION_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderReadTimeoutMs() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_DOWNLOADER_READ_TIMEOUT_MS,
                /* defaultValue */ DOWNLOADER_READ_TIMEOUT_MS);
    }

    @Override
    public int getDownloaderMaxDownloadThreads() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS,
                /* defaultValue */ DOWNLOADER_MAX_DOWNLOAD_THREADS);
    }

    @Override
    public String getMddTopicsClassifierManifestFileUrl() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL,
                /* defaultValue */ MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL);
    }

    // Group of All Killswitches
    @Override
    public boolean getGlobalKillSwitch() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_GLOBAL_KILL_SWITCH),
                /* defaultValue */ DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_GLOBAL_KILL_SWITCH,
                        /* defaultValue */ GLOBAL_KILL_SWITCH));
    }

    // MEASUREMENT Killswitches
    @Override
    public boolean getMeasurementKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiDeleteRegistrationsKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementApiStatusKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_API_STATUS_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_API_STATUS_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiRegisterSourceKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiRegisterTriggerKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementApiRegisterWebSourceKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementApiRegisterWebTriggerKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementJobAggregateFallbackReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName = KEY_MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_FALLBACK_REPORTING_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getMeasurementJobAggregateReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_JOB_AGGREGATE_REPORTING_KILL_SWITCH,
                                defaultValue));
    }

    @Override
    public boolean getMeasurementJobAttributionKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_ATTRIBUTION_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementJobDeleteExpiredKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_DELETE_EXPIRED_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementJobEventFallbackReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName = KEY_MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_JOB_EVENT_FALLBACK_REPORTING_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getMeasurementJobEventReportingKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH,
                                /* defaultValue */ MEASUREMENT_JOB_EVENT_REPORTING_KILL_SWITCH));
    }

    @Override
    public boolean getMeasurementReceiverInstallAttributionKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final String flagName = KEY_MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        final boolean defaultValue = MEASUREMENT_RECEIVER_INSTALL_ATTRIBUTION_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(flagName),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES, flagName, defaultValue));
    }

    @Override
    public boolean getMeasurementReceiverDeletePackagesKillSwitch() {
        // We check the Global Killswitch first then Measurement Killswitch.
        // As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        final boolean defaultValue = MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH;
        return getGlobalKillSwitch()
                || getMeasurementKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MEASUREMENT_RECEIVER_DELETE_PACKAGES_KILL_SWITCH,
                                defaultValue));
    }

    // ADID Killswitches
    @Override
    public boolean getAdIdKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_ADID_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_ADID_KILL_SWITCH,
                                /* defaultValue */ ADID_KILL_SWITCH));
    }

    // APPSETID Killswitch.
    @Override
    public boolean getAppSetIdKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_APPSETID_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_APPSETID_KILL_SWITCH,
                                /* defaultValue */ APPSETID_KILL_SWITCH));
    }

    // TOPICS Killswitches
    @Override
    public boolean getTopicsKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_TOPICS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_TOPICS_KILL_SWITCH,
                                /* defaultValue */ TOPICS_KILL_SWITCH));
    }

    // MDD Killswitches
    @Override
    public boolean getMddBackgroundTaskKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MDD_BACKGROUND_TASK_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MDD_BACKGROUND_TASK_KILL_SWITCH,
                                /* defaultValue */ MDD_BACKGROUND_TASK_KILL_SWITCH));
    }

    // MDD Logger Killswitches
    @Override
    public boolean getMddLoggerKillSwitch() {
        // We check the Global Killswitch first. As a result, it overrides all other killswitches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_MDD_LOGGER_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_MDD_LOGGER_KILL_SWITCH,
                                /* defaultValue */ MDD_LOGGER_KILL_SWITCH));
    }

    // FLEDGE Kill switches

    @Override
    public boolean getFledgeSelectAdsKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_FLEDGE_SELECT_ADS_KILL_SWITCH,
                                /* defaultValue */ FLEDGE_SELECT_ADS_KILL_SWITCH));
    }

    @Override
    public boolean getFledgeCustomAudienceServiceKillSwitch() {
        // We check the Global Kill switch first. As a result, it overrides all other kill switches.
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        return getGlobalKillSwitch()
                || SystemProperties.getBoolean(
                        getSystemPropertyName(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH),
                        /* defaultValue */ DeviceConfig.getBoolean(
                                DeviceConfig.NAMESPACE_ADSERVICES,
                                /* flagName */ KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH,
                                /* defaultValue */ FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH));
    }

    @Override
    public String getPpapiAppAllowList() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_PPAPI_APP_ALLOW_LIST,
                /* defaultValue */ PPAPI_APP_ALLOW_LIST);
    }

    // PPAPI Signature allow-list.
    @Override
    public String getPpapiAppSignatureAllowList() {
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST,
                /* defaultValue */ PPAPI_APP_SIGNATURE_ALLOW_LIST);
    }

    // Rate Limit Flags.
    @Override
    public float getSdkRequestPermitsPerSecond() {
        // The priority of applying the flag values: SystemProperties, PH (DeviceConfig), then
        // hard-coded value.
        try {
            String sdkPermitString =
                    SystemProperties.get(getSystemPropertyName(KEY_SDK_REQUEST_PERMITS_PER_SECOND));
            if (!TextUtils.isEmpty(sdkPermitString)) {
                return parseFloat(sdkPermitString);
            }
        } catch (NumberFormatException e) {
            LogUtil.e(e, "Failed to parse SdkRequestPermitsPerSecond");
            return SDK_REQUEST_PERMITS_PER_SECOND;
        }

        float sdkRequestPermitsPerSecond =
                DeviceConfig.getFloat(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_SDK_REQUEST_PERMITS_PER_SECOND,
                        /* defaultValue */ SDK_REQUEST_PERMITS_PER_SECOND);

        if (sdkRequestPermitsPerSecond <= 0) {
            throw new IllegalArgumentException("sdkRequestPermitsPerSecond should > 0");
        }
        return sdkRequestPermitsPerSecond;
    }

    @Override
    public boolean getAdServicesEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ADSERVICES_ENABLED,
                /* defaultValue */ ADSERVICES_ENABLED);
    }

    @Override
    public int getNumberOfEpochsToKeepInHistory() {
        int numberOfEpochsToKeepInHistory =
                DeviceConfig.getInt(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY,
                        /* defaultValue */ NUMBER_OF_EPOCHS_TO_KEEP_IN_HISTORY);

        if (numberOfEpochsToKeepInHistory < 1) {
            throw new IllegalArgumentException("numberOfEpochsToKeepInHistory should  >= 0");
        }

        return numberOfEpochsToKeepInHistory;
    }

    @Override
    public boolean isDisableTopicsEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK),
                /* defaultValue */ DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_DISABLE_TOPICS_ENROLLMENT_CHECK,
                        /* defaultValue */ DISABLE_TOPICS_ENROLLMENT_CHECK));
    } //

    @Override
    public boolean isDisableMeasurementEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK),
                /* defaultValue */ DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK,
                        /* defaultValue */ DISABLE_MEASUREMENT_ENROLLMENT_CHECK));
    }

    @Override
    public boolean getDisableFledgeEnrollmentCheck() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK),
                /* defaultValue */ DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK,
                        /* defaultValue */ DISABLE_FLEDGE_ENROLLMENT_CHECK));
    }

    @Override
    public boolean getEnforceForegroundStatusForTopics() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_ENFORCE_FOREGROUND_STATUS_TOPICS),
                /* defaultValue */ DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        /* flagName */ KEY_ENFORCE_FOREGROUND_STATUS_TOPICS,
                        /* defaultValue */ ENFORCE_FOREGROUND_STATUS_TOPICS));
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeReportImpression() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION);
    }

    @Override
    public int getForegroundStatuslLevelForValidation() {
        return DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_FOREGROUND_STATUS_LEVEL,
                /* defaultValue */ FOREGROUND_STATUS_LEVEL);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeOverrides() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDES);
    }

    @Override
    public boolean getEnforceForegroundStatusForFledgeCustomAudience() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE,
                /* defaultValue */ ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE);
    }

    @Override
    public boolean getEnforceIsolateMaxHeapSize() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE,
                /* defaultValue */ ENFORCE_ISOLATE_MAX_HEAP_SIZE);
    }

    @Override
    public long getIsolateMaxHeapSizeBytes() {
        return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ISOLATE_MAX_HEAP_SIZE_BYTES,
                /* defaultValue */ ISOLATE_MAX_HEAP_SIZE_BYTES);
    }

    @Override
    public String getWebContextClientAppAllowList() {
        return DeviceConfig.getString(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST,
                /* defaultValue */ WEB_CONTEXT_CLIENT_ALLOW_LIST);
    }

    @VisibleForTesting
    static String getSystemPropertyName(String key) {
        return SYSTEM_PROPERTY_PREFIX + key;
    }

    @Override
    public boolean getConsentNotificationDebugMode() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_CONSENT_NOTIFICATION_DEBUG_MODE,
                /* defaultValue */ CONSENT_NOTIFICATION_DEBUG_MODE);
    }

    @Override
    public boolean getConsentManagerDebugMode() {
        return SystemProperties.getBoolean(
                getSystemPropertyName(KEY_CONSENT_MANAGER_DEBUG_MODE),
                /* defaultValue */ CONSENT_MANAGER_DEBUG_MODE);
    }

    @Override
    public void dump(@NonNull PrintWriter writer, @Nullable String[] args) {
        writer.println("==== AdServices PH Flags Dump Enrollment ====");
        writer.println(
                "\t"
                        + KEY_DISABLE_TOPICS_ENROLLMENT_CHECK
                        + " = "
                        + isDisableTopicsEnrollmentCheck());
        writer.println(
                "\t"
                        + KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK
                        + " = "
                        + getDisableFledgeEnrollmentCheck());
        writer.println(
                "\t"
                        + KEY_DISABLE_MEASUREMENT_ENROLLMENT_CHECK
                        + " = "
                        + isDisableMeasurementEnrollmentCheck());

        writer.println("==== AdServices PH Flags Dump killswitches ====");
        writer.println("\t" + KEY_GLOBAL_KILL_SWITCH + " = " + getGlobalKillSwitch());
        writer.println("\t" + KEY_TOPICS_KILL_SWITCH + " = " + getTopicsKillSwitch());
        writer.println("\t" + KEY_ADID_KILL_SWITCH + " = " + getAdIdKillSwitch());
        writer.println("\t" + KEY_APPSETID_KILL_SWITCH + " = " + getAppSetIdKillSwitch());
        writer.println(
                "\t"
                        + KEY_SDK_REQUEST_PERMITS_PER_SECOND
                        + " = "
                        + getSdkRequestPermitsPerSecond());

        writer.println(
                "\t"
                        + KEY_MDD_BACKGROUND_TASK_KILL_SWITCH
                        + " = "
                        + getMddBackgroundTaskKillSwitch());
        writer.println("\t" + KEY_MDD_LOGGER_KILL_SWITCH + " = " + getMddLoggerKillSwitch());

        writer.println("==== AdServices PH Flags Dump AllowList ====");
        writer.println(
                "\t"
                        + KEY_PPAPI_APP_SIGNATURE_ALLOW_LIST
                        + " = "
                        + getPpapiAppSignatureAllowList());
        writer.println("\t" + KEY_PPAPI_APP_ALLOW_LIST + " = " + getPpapiAppAllowList());

        writer.println("==== AdServices PH Flags Dump MDD related flags: ====");
        writer.println(
                "\t" + KEY_MEASUREMENT_MANIFEST_FILE_URL + " = " + getMeasurementManifestFileUrl());
        writer.println(
                "\t"
                        + KEY_DOWNLOADER_CONNECTION_TIMEOUT_MS
                        + " = "
                        + getDownloaderConnectionTimeoutMs());
        writer.println(
                "\t" + KEY_DOWNLOADER_READ_TIMEOUT_MS + " = " + getDownloaderReadTimeoutMs());
        writer.println(
                "\t"
                        + KEY_DOWNLOADER_MAX_DOWNLOAD_THREADS
                        + " = "
                        + getDownloaderMaxDownloadThreads());
        writer.println(
                "\t"
                        + KEY_MDD_TOPICS_CLASSIFIER_MANIFEST_FILE_URL
                        + " = "
                        + getMddTopicsClassifierManifestFileUrl());

        writer.println("==== AdServices PH Flags Dump Topics related flags ====");
        writer.println("\t" + KEY_TOPICS_EPOCH_JOB_PERIOD_MS + " = " + getTopicsEpochJobPeriodMs());
        writer.println("\t" + KEY_TOPICS_EPOCH_JOB_FLEX_MS + " = " + getTopicsEpochJobFlexMs());
        writer.println(
                "\t"
                        + KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC
                        + " = "
                        + getTopicsPercentageForRandomTopic());
        writer.println(
                "\t" + KEY_TOPICS_NUMBER_OF_TOP_TOPICS + " = " + getTopicsNumberOfTopTopics());
        writer.println(
                "\t"
                        + KEY_TOPICS_NUMBER_OF_RANDOM_TOPICS
                        + " = "
                        + getTopicsNumberOfRandomTopics());
        writer.println(
                "\t"
                        + KEY_TOPICS_NUMBER_OF_LOOK_BACK_EPOCHS
                        + " = "
                        + getTopicsNumberOfLookBackEpochs());

        writer.println(
                "\t"
                        + KEY_CLASSIFIER_NUMBER_OF_TOP_LABELS
                        + " = "
                        + getClassifierNumberOfTopLabels());
        writer.println("\t" + KEY_CLASSIFIER_TYPE + " = " + getClassifierType());
        writer.println("\t" + KEY_CLASSIFIER_THRESHOLD + " = " + getClassifierThreshold());

        writer.println(
                "\t"
                        + KEY_ENFORCE_FOREGROUND_STATUS_TOPICS
                        + " = "
                        + getEnforceForegroundStatusForTopics());

        writer.println("==== AdServices PH Flags Dump Measurement related flags: ====");
        writer.println("\t" + KEY_MEASUREMENT_DB_SIZE_LIMIT + " = " + getMeasurementDbSizeLimit());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_EVENT_MAIN_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementEventMainReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementEventFallbackReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_AGGREGATE_ENCRYPTION_KEY_COORDINATOR_URL
                        + " = "
                        + getMeasurementAggregateEncryptionKeyCoordinatorUrl());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAggregateMainReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_PERIOD_MS
                        + " = "
                        + getMeasurementAggregateFallbackReportingJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_NETWORK_CONNECT_TIMEOUT_MS
                        + " = "
                        + getMeasurementNetworkConnectTimeoutMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_NETWORK_READ_TIMEOUT_MS
                        + " = "
                        + getMeasurementNetworkReadTimeoutMs());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_IS_CLICK_VERIFICATION_ENABLED
                        + " = "
                        + getMeasurementIsClickVerificationEnabled());
        writer.println(
                "\t"
                        + KEY_MEASUREMENT_REGISTRATION_INPUT_EVENT_VALID_WINDOW_MS
                        + " = "
                        + getMeasurementRegistrationInputEventValidWindowMs());

        writer.println("==== AdServices PH Flags Dump FLEDGE related flags: ====");
        writer.println(
                "\t" + KEY_FLEDGE_SELECT_ADS_KILL_SWITCH + " = " + getFledgeSelectAdsKillSwitch());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH
                        + " = "
                        + getFledgeCustomAudienceServiceKillSwitch());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT
                        + " = "
                        + getFledgeCustomAudienceMaxCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT
                        + " = "
                        + getFledgeCustomAudienceMaxOwnerCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT
                        + " = "
                        + getFledgeCustomAudiencePerAppMaxCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN_MS
                        + " = "
                        + getFledgeCustomAudienceDefaultExpireInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN_MS
                        + " = "
                        + getFledgeCustomAudienceMaxActivationDelayInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_EXPIRE_IN_MS
                        + " = "
                        + getFledgeCustomAudienceMaxExpireInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxNameSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_DAILY_UPDATE_URI_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxDailyUpdateUriSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxBiddingLogicUriSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxUserBiddingSignalsSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxTrustedBiddingDataSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B
                        + " = "
                        + getFledgeCustomAudienceMaxAdsSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS
                        + " = "
                        + getFledgeCustomAudienceActiveTimeWindowInMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS
                        + " = "
                        + getFledgeCustomAudienceMaxNumAds());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_ENABLED
                        + " = "
                        + getFledgeBackgroundFetchEnabled());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS
                        + " = "
                        + getFledgeBackgroundFetchJobPeriodMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS
                        + " = "
                        + getFledgeBackgroundFetchJobFlexMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_MAX_NUM_UPDATED
                        + " = "
                        + getFledgeBackgroundFetchMaxNumUpdated());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_THREAD_POOL_SIZE
                        + " = "
                        + getFledgeBackgroundFetchThreadPoolSize());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_ELIGIBLE_UPDATE_BASE_INTERVAL_S
                        + " = "
                        + getFledgeBackgroundFetchEligibleUpdateBaseIntervalS());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS
                        + " = "
                        + getFledgeBackgroundFetchNetworkConnectTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS
                        + " = "
                        + getFledgeBackgroundFetchNetworkReadTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_BACKGROUND_FETCH_MAX_RESPONSE_SIZE_B
                        + " = "
                        + getFledgeBackgroundFetchMaxResponseSizeB());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_CONCURRENT_BIDDING_COUNT
                        + " = "
                        + getAdSelectionConcurrentBiddingCount());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS
                        + " = "
                        + getAdSelectionBiddingTimeoutPerCaMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS
                        + " = "
                        + getAdSelectionScoringTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS
                        + " = "
                        + getAdSelectionOverallTimeoutMs());
        writer.println(
                "\t"
                        + KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS
                        + " = "
                        + getReportImpressionOverallTimeoutMs());

        writer.println(
                "\t"
                        + KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE
                        + " = "
                        + getEnforceForegroundStatusForFledgeOverrides());

        writer.println(
                "\t"
                        + KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION
                        + " = "
                        + getEnforceForegroundStatusForFledgeReportImpression());

        writer.println(
                "\t"
                        + KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION
                        + " = "
                        + getEnforceForegroundStatusForFledgeRunAdSelection());

        writer.println(
                "\t"
                        + KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_CUSTOM_AUDIENCE
                        + " = "
                        + getEnforceForegroundStatusForFledgeCustomAudience());

        writer.println(
                "\t"
                        + KEY_FOREGROUND_STATUS_LEVEL
                        + " = "
                        + getForegroundStatuslLevelForValidation());

        writer.println(
                "\t" + KEY_ENFORCE_ISOLATE_MAX_HEAP_SIZE + " = " + getEnforceIsolateMaxHeapSize());

        writer.println(
                "\t" + KEY_ISOLATE_MAX_HEAP_SIZE_BYTES + " = " + getIsolateMaxHeapSizeBytes());

        writer.println("==== AdServices PH Flags Dump STATUS ====");
        writer.println("\t" + KEY_ADSERVICES_ENABLED + " = " + getAdServicesEnabled());
        writer.println(
                "\t"
                        + KEY_FOREGROUND_STATUS_LEVEL
                        + " = "
                        + getForegroundStatuslLevelForValidation());
    }
}
