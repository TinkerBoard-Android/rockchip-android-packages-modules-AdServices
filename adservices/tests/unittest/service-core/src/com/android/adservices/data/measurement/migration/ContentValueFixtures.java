/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.measurement.migration;

import android.content.ContentValues;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateReport;

public class ContentValueFixtures {

    public static class AsyncRegistrationValues {
        public static final String ID = "async_registration_id";
        public static final String REGISTRATION_URI = "android-app://com.registration";
        public static final String WEB_DESTINATION = "https://com.web.destination";
        public static final String OS_DESTINATION = "android-app://com.os.destination";
        public static final String VERIFIED_DESTINATION = "android-app://com.verified.destination";
        public static final String TOP_ORIGIN = "android-app://com.top.origin";
        public static final long REDIRECT = AsyncRegistration.RedirectType.DAISY_CHAIN;
        public static final long INPUT_EVENT = 1;
        public static final String REGISTRANT = "android-app://com.registrant";
        public static final long SCHEDULED_TIME = 8640000000L;
        public static final long RETRY_COUNT = 4;
        public static final long LAST_PROCESSING_TIME = 8650000000L;
        public static final long TYPE = AsyncRegistration.RegistrationType.WEB_TRIGGER.ordinal();

        // Added in V3.
        public static final String ENROLLMENT_ID = "enrollment-id";
        public static final long REDIRECT_TYPE = AsyncRegistration.RedirectType.DAISY_CHAIN;
        public static final long REDIRECT_COUNT = 10;
        public static final long SOURCE_TYPE = Source.SourceType.NAVIGATION.ordinal();
        public static final long REQUEST_TIME = 8660000000L;
        public static final long DEBUG_KEY_ALLOWED = 1;
        public static final long AD_ID_PERMISSION = 0;

        // Added in V6.
        public static final String REGISTRATION_ID = "xna_registration_id";
    }

    public static class SourceValues {
        public static final String ID = "source_id";
        public static final long EVENT_ID = 123L;
        public static final String PUBLISHER = "android-app://com.publisher";
        public static final long PUBLISHER_TYPE = 5;
        public static final String APP_DESTINATION = "android-app://com.destination";
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final long EVENT_TIME = 8640000000L;
        public static final long EXPIRY_TIME = 8640000010L;
        public static final long PRIORITY = 100L;
        public static final long STATUS = Source.Status.MARKED_TO_DELETE;
        public static final String DEDUP_KEYS = "1001";
        public static final String SOURCE_TYPE = Source.SourceType.EVENT.toString();
        public static final String REGISTRANT = "android-app://com.registrant";
        public static final long ATTRIBUTION_MODE = Source.AttributionMode.FALSELY;
        public static final long INSTALL_ATTRIBUTION_WINDOW = 841839879274L;
        public static final long INSTALL_COOLDOWN_WINDOW = 8418398274L;
        public static final long IS_INSTALL_ATTRIBUTED = 1;
        public static final String FILTER_DATA = "test filter data";
        public static final String AGGREGATE_SOURCE_V2_AND_BELOW =
                "[{\"id\": \"campaignCounts\",\"key_piece\": \"0x159\"},"
                        + "{\"id\": \"geoValue\",\"key_piece\": \"0x5\"}]";
        public static final long AGGREGATE_CONTRIBUTIONS = 6;
        public static final String WEB_DESTINATION = "https://destination.com";
        public static final long DEBUG_KEY = 12345L;

        // Added in V3.
        public static final String AGGREGATE_SOURCE_V3 =
                "{\"geoValue\":\"0x5\",\"campaignCounts\":\"0x159\"}";
        public static final long DEBUG_REPORTING = 10;
        public static final long AD_ID_PERMISSION = 11;
        public static final long AR_DEBUG_PERMISSION = 12;

        // Added in V6.
        public static final String EVENT_REPORT_DEDUP_KEY = "1001";
        public static final String AGGREGATE_REPORT_DEDUP_KEY = "2002";
        public static final long EVENT_REPORT_WINDOW = 400000L;
        public static final long AGGREGATE_REPORT_WINDOW = 500000L;
        public static final String REGISTRATION_ID = "xna_registration_id";
        public static final String SHARED_AGGREGATION_KEY = "shared_aggregation_key";
        public static final long INSTALL_TIME = 8660000000L;
    }

    public static class TriggerValues {
        public static final String ID = "trigger_id";
        public static final String ATTRIBUTION_DESTINATION = "android-app://com.destination";
        public static final long DESTINATION_TYPE = EventSurfaceType.WEB;
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final long TRIGGER_TIME = 8630000000L;
        public static final String FILTERS_V2_AND_BELOW = "{\"id1\":[\"val11\",\"val12\"]}";
        public static final String EVENT_TRIGGERS_V2_AND_BELOW =
                "[{\"trigger_data\":2,\"filters\":"
                        + FILTERS_V2_AND_BELOW
                        + ",\"not_filters\":"
                        + FILTERS_V2_AND_BELOW
                        + "},"
                        + "{\"priority\":100}]";
        public static final long STATUS = Trigger.Status.MARKED_TO_DELETE;
        public static final String REGISTRANT = "android-app://com.registrant";
        public static final String AGGREGATE_TRIGGER_DATA_V2_AND_BELOW =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"key11\"],"
                        + "\"filters\":"
                        + FILTERS_V2_AND_BELOW
                        + ",\"not_filters\":"
                        + FILTERS_V2_AND_BELOW
                        + "}]";
        public static final String AGGREGATE_VALUES =
                "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}";
        public static final long DEBUG_KEY = 27836L;

        // Added in V3
        public static final String NOT_FILTERS = "{\"id2\":[\"val21\",\"val22\"]}";
        public static final String FILTERS_V3 = "[" + FILTERS_V2_AND_BELOW + "]";
        public static final String EVENT_TRIGGERS_V3 =
                "[{\"trigger_data\":2,\"filters\":"
                        + FILTERS_V3
                        + ",\"not_filters\":"
                        + FILTERS_V3
                        + "},"
                        + "{\"priority\":100,\"trigger_data\":\"0\"}]";
        public static final String AGGREGATE_TRIGGER_DATA_V3 =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"key11\"],"
                        + "\"filters\":"
                        + FILTERS_V3
                        + ",\"not_filters\":"
                        + FILTERS_V3
                        + "}]";
        public static final long DEBUG_REPORTING = 10;
        public static final long AD_ID_PERMISSION = 11;
        public static final long AR_DEBUG_PERMISSION = 12;

        // Added in V6.
        public static final String ATTRIBUTION_CONFIG = "attribution_config";
        public static final String X_NETWORK_KEY_MAPPING = "x_network_key_mapping";
        public static final String AGGREGATABLE_DEDUPLICATION_KEYS =
                "aggregatable_deduplication_keys";
    }

    public static class AttributionValues {
        public static final String ID = "attribution_id";
        public static final String SOURCE_SITE = "android-app://com.source-site";
        public static final String SOURCE_ORIGIN = "android-app://com.source-origin";
        public static final String DESTINATION_SITE = "android-app://com.destination-site";
        public static final String DESTINATION_ORIGIN = "android-app://com.destination-origin";
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final long TRIGGER_TIME = 8630000000L;
        public static final String REGISTRANT = "android-app://com.registrant";

        // Added in V3.
        public static final String SOURCE_ID = "source_id";
        public static final String TRIGGER_ID = "trigger_id";
    }

    public static class EventReportValues {
        public static final String ID = "event_report_id";
        public static final long SOURCE_ID_V2_AND_BELOW = 123L;
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final String ATTRIBUTION_DESTINATION = "android-app://com.destination";
        public static final long REPORT_TIME = 8640000000L;
        public static final long TRIGGER_DATA = 1;
        public static final long TRIGGER_PRIORITY = 100L;
        public static final long TRIGGER_DEDUP_KEY = 56;
        public static final long TRIGGER_TIME = 8630000000L;
        public static final long STATUS = EventReport.Status.MARKED_TO_DELETE;
        public static final String SOURCE_TYPE = Source.SourceType.EVENT.toString();
        public static final double RANDOMIZED_TRIGGER_RATE = 0.0000025F;

        // Added in V2
        public static final long SOURCE_DEBUG_KEY = 12345L;
        public static final long TRIGGER_DEBUG_KEY = 34567L;

        // Added in V3
        public static final long SOURCE_EVENT_ID = SOURCE_ID_V2_AND_BELOW;
        public static final String SOURCE_ID_V3 = "source_id";
        public static final String TRIGGER_ID = "trigger_id";
        public static final long DEBUG_REPORT_STATUS = 4;
    }

    public static class AggregateReportValues {
        public static final String ID = "aggregate_report_id";
        public static final String PUBLISHER = "android-app://com.publisher";
        public static final String ATTRIBUTION_DESTINATION = "android-app://com.destination";
        public static final long SOURCE_REGISTRATION_TIME = 8640000000L;
        public static final long SCHEDULED_REPORT_TIME = 8640000200L;
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final String DEBUG_CLEARTEXT_PAYLOAD = "payload";
        public static final long STATUS = AggregateReport.Status.MARKED_TO_DELETE;
        public static final String API_VERSION = "api_version";

        // Added in V2
        public static final long SOURCE_DEBUG_KEY = 12345L;
        public static final long TRIGGER_DEBUG_KEY = 34567L;

        // Added in V3.
        public static final String SOURCE_ID = "source_id";
        public static final String TRIGGER_ID = "trigger_id";
        public static final long DEBUG_REPORT_STATUS = 4;
    }

    public static class AggregateEncryptionKeyValues {
        public static final String ID = "aggregate_encryption_key_id";
        public static final String KEY_ID = "key_id";
        public static final String PUBLIC_KEY = "public_key";
        public static final long EXPIRY = 8640000000L;
    }

    public static class DebugReportValues {
        // Added in V3.
        public static final String ID = "debug_report_id";
        public static final String TYPE = "source-noised";
        public static final String BODY = "{\"source_event_id\":\"123\"}";
        public static final String ENROLLMENT_ID = "enrollment_id";
    }

    public static ContentValues generateAsyncRegistrationContentValuesV1() {
        ContentValues asyncRegistration = new ContentValues();

        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.ID, AsyncRegistrationValues.ID);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                AsyncRegistrationValues.REGISTRATION_URI);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                AsyncRegistrationValues.WEB_DESTINATION);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                AsyncRegistrationValues.OS_DESTINATION);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                AsyncRegistrationValues.VERIFIED_DESTINATION);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                AsyncRegistrationValues.TOP_ORIGIN);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.REDIRECT,
                AsyncRegistrationValues.REDIRECT);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT,
                AsyncRegistrationValues.INPUT_EVENT);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                AsyncRegistrationValues.REGISTRANT);
        asyncRegistration.put(
                MeasurementTablesDeprecated.AsyncRegistration.SCHEDULED_TIME,
                AsyncRegistrationValues.SCHEDULED_TIME);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                AsyncRegistrationValues.RETRY_COUNT);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.LAST_PROCESSING_TIME,
                AsyncRegistrationValues.LAST_PROCESSING_TIME);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.TYPE, AsyncRegistrationValues.TYPE);

        return asyncRegistration;
    }

    public static ContentValues generateSourceContentValuesV1() {
        ContentValues source = new ContentValues();

        source.put(MeasurementTables.SourceContract.ID, SourceValues.ID);
        source.put(MeasurementTables.SourceContract.EVENT_ID, SourceValues.EVENT_ID);
        source.put(MeasurementTables.SourceContract.PUBLISHER, SourceValues.PUBLISHER);
        source.put(MeasurementTables.SourceContract.PUBLISHER_TYPE, SourceValues.PUBLISHER_TYPE);
        source.put(MeasurementTables.SourceContract.APP_DESTINATION, SourceValues.APP_DESTINATION);
        source.put(MeasurementTables.SourceContract.ENROLLMENT_ID, SourceValues.ENROLLMENT_ID);
        source.put(MeasurementTables.SourceContract.EVENT_TIME, SourceValues.EVENT_TIME);
        source.put(MeasurementTables.SourceContract.EXPIRY_TIME, SourceValues.EXPIRY_TIME);
        source.put(MeasurementTables.SourceContract.PRIORITY, SourceValues.PRIORITY);
        source.put(MeasurementTables.SourceContract.STATUS, SourceValues.STATUS);
        source.put(MeasurementTablesDeprecated.Source.DEDUP_KEYS, SourceValues.DEDUP_KEYS);
        source.put(MeasurementTables.SourceContract.SOURCE_TYPE, SourceValues.SOURCE_TYPE);
        source.put(MeasurementTables.SourceContract.REGISTRANT, SourceValues.REGISTRANT);
        source.put(
                MeasurementTables.SourceContract.ATTRIBUTION_MODE, SourceValues.ATTRIBUTION_MODE);
        source.put(
                MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                SourceValues.INSTALL_ATTRIBUTION_WINDOW);
        source.put(
                MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                SourceValues.INSTALL_COOLDOWN_WINDOW);
        source.put(
                MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
                SourceValues.IS_INSTALL_ATTRIBUTED);
        source.put(MeasurementTables.SourceContract.FILTER_DATA, SourceValues.FILTER_DATA);
        source.put(
                MeasurementTables.SourceContract.AGGREGATE_SOURCE,
                SourceValues.AGGREGATE_SOURCE_V2_AND_BELOW);
        source.put(
                MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
                SourceValues.AGGREGATE_CONTRIBUTIONS);
        source.put(MeasurementTables.SourceContract.WEB_DESTINATION, SourceValues.WEB_DESTINATION);
        source.put(MeasurementTables.SourceContract.DEBUG_KEY, SourceValues.DEBUG_KEY);

        return source;
    }

    public static ContentValues generateTriggerContentValuesV1() {
        ContentValues trigger = new ContentValues();

        trigger.put(MeasurementTables.TriggerContract.ID, TriggerValues.ID);
        trigger.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                TriggerValues.ATTRIBUTION_DESTINATION);
        trigger.put(
                MeasurementTables.TriggerContract.DESTINATION_TYPE, TriggerValues.DESTINATION_TYPE);
        trigger.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, TriggerValues.ENROLLMENT_ID);
        trigger.put(MeasurementTables.TriggerContract.TRIGGER_TIME, TriggerValues.TRIGGER_TIME);
        trigger.put(
                MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                TriggerValues.EVENT_TRIGGERS_V2_AND_BELOW);
        trigger.put(MeasurementTables.TriggerContract.STATUS, TriggerValues.STATUS);
        trigger.put(MeasurementTables.TriggerContract.REGISTRANT, TriggerValues.REGISTRANT);
        trigger.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                TriggerValues.AGGREGATE_TRIGGER_DATA_V2_AND_BELOW);
        trigger.put(
                MeasurementTables.TriggerContract.AGGREGATE_VALUES, TriggerValues.AGGREGATE_VALUES);
        trigger.put(MeasurementTables.TriggerContract.FILTERS, TriggerValues.FILTERS_V2_AND_BELOW);
        trigger.put(MeasurementTables.TriggerContract.DEBUG_KEY, TriggerValues.DEBUG_KEY);

        return trigger;
    }

    public static ContentValues generateAttributionContentValuesV1() {
        ContentValues attribution = new ContentValues();

        attribution.put(MeasurementTables.AttributionContract.ID, AttributionValues.ID);
        attribution.put(
                MeasurementTables.AttributionContract.SOURCE_SITE, AttributionValues.SOURCE_SITE);
        attribution.put(
                MeasurementTables.AttributionContract.SOURCE_ORIGIN,
                AttributionValues.SOURCE_ORIGIN);
        attribution.put(
                MeasurementTables.AttributionContract.DESTINATION_SITE,
                AttributionValues.DESTINATION_SITE);
        attribution.put(
                MeasurementTables.AttributionContract.DESTINATION_ORIGIN,
                AttributionValues.DESTINATION_ORIGIN);
        attribution.put(
                MeasurementTables.AttributionContract.ENROLLMENT_ID,
                AttributionValues.ENROLLMENT_ID);
        attribution.put(
                MeasurementTables.AttributionContract.TRIGGER_TIME, AttributionValues.TRIGGER_TIME);
        attribution.put(
                MeasurementTables.AttributionContract.REGISTRANT, AttributionValues.REGISTRANT);

        return attribution;
    }

    public static ContentValues generateEventReportContentValuesV1() {
        ContentValues eventReport = new ContentValues();

        eventReport.put(MeasurementTables.EventReportContract.ID, EventReportValues.ID);
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_ID,
                EventReportValues.SOURCE_ID_V2_AND_BELOW);
        eventReport.put(
                MeasurementTables.EventReportContract.ENROLLMENT_ID,
                EventReportValues.ENROLLMENT_ID);
        eventReport.put(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                EventReportValues.ATTRIBUTION_DESTINATION);
        eventReport.put(
                MeasurementTables.EventReportContract.REPORT_TIME, EventReportValues.REPORT_TIME);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DATA, EventReportValues.TRIGGER_DATA);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                EventReportValues.TRIGGER_PRIORITY);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                EventReportValues.TRIGGER_DEDUP_KEY);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_TIME, EventReportValues.TRIGGER_TIME);
        eventReport.put(MeasurementTables.EventReportContract.STATUS, EventReportValues.STATUS);
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_TYPE, EventReportValues.SOURCE_TYPE);
        eventReport.put(
                MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                EventReportValues.RANDOMIZED_TRIGGER_RATE);

        return eventReport;
    }

    public static ContentValues generateAggregateReportContentValuesV1() {
        ContentValues aggregateReport = new ContentValues();

        aggregateReport.put(MeasurementTables.AggregateReport.ID, AggregateReportValues.ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.PUBLISHER, AggregateReportValues.PUBLISHER);
        aggregateReport.put(
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                AggregateReportValues.ATTRIBUTION_DESTINATION);
        aggregateReport.put(
                MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                AggregateReportValues.SOURCE_REGISTRATION_TIME);
        aggregateReport.put(
                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                AggregateReportValues.SCHEDULED_REPORT_TIME);
        aggregateReport.put(
                MeasurementTables.AggregateReport.ENROLLMENT_ID,
                AggregateReportValues.ENROLLMENT_ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                AggregateReportValues.DEBUG_CLEARTEXT_PAYLOAD);
        aggregateReport.put(MeasurementTables.AggregateReport.STATUS, AggregateReportValues.STATUS);
        aggregateReport.put(
                MeasurementTables.AggregateReport.API_VERSION, AggregateReportValues.API_VERSION);

        return aggregateReport;
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV1() {
        ContentValues aggregateEncryptionKey = new ContentValues();

        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.ID, AggregateEncryptionKeyValues.ID);
        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.KEY_ID,
                AggregateEncryptionKeyValues.KEY_ID);
        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                AggregateEncryptionKeyValues.PUBLIC_KEY);
        aggregateEncryptionKey.put(
                MeasurementTables.AggregateEncryptionKey.EXPIRY,
                AggregateEncryptionKeyValues.EXPIRY);

        return aggregateEncryptionKey;
    }

    public static ContentValues generateAsyncRegistrationContentValuesV2() {
        // No differences in async registration fields between V1 and V2.
        return generateAsyncRegistrationContentValuesV1();
    }

    public static ContentValues generateSourceContentValuesV2() {
        // No differences in source fields between V1 and V2.
        return generateSourceContentValuesV1();
    }

    public static ContentValues generateTriggerContentValuesV2() {
        // No differences in trigger fields between V1 and V2.
        return generateTriggerContentValuesV1();
    }

    public static ContentValues generateAttributionContentValuesV2() {
        // No differences in attribution fields between V1 and V2.
        return generateAttributionContentValuesV1();
    }

    public static ContentValues generateEventReportContentValuesV2() {
        ContentValues eventReport = generateEventReportContentValuesV1();

        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY,
                EventReportValues.SOURCE_DEBUG_KEY);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY,
                EventReportValues.TRIGGER_DEBUG_KEY);

        return eventReport;
    }

    public static ContentValues generateAggregateReportContentValuesV2() {
        ContentValues aggregateReport = generateAggregateReportContentValuesV1();

        aggregateReport.put(
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                AggregateReportValues.SOURCE_DEBUG_KEY);
        aggregateReport.put(
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                AggregateReportValues.TRIGGER_DEBUG_KEY);

        return aggregateReport;
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV2() {
        // No differences in aggregate encryption key fields between V1 and V2.
        return generateAggregateEncryptionKeyContentValuesV1();
    }

    public static ContentValues generateAsyncRegistrationContentValuesV3() {
        ContentValues asyncRegistration = generateAsyncRegistrationContentValuesV2();

        // Remove columns.
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.REDIRECT);
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT);
        asyncRegistration.remove(MeasurementTablesDeprecated.AsyncRegistration.SCHEDULED_TIME);

        // Add columns.
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID,
                AsyncRegistrationValues.ENROLLMENT_ID);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REDIRECT_TYPE,
                AsyncRegistrationValues.REDIRECT_TYPE);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REDIRECT_COUNT,
                AsyncRegistrationValues.REDIRECT_COUNT);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                AsyncRegistrationValues.SOURCE_TYPE);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                AsyncRegistrationValues.REQUEST_TIME);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                AsyncRegistrationValues.DEBUG_KEY_ALLOWED);
        asyncRegistration.put(
                MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
                AsyncRegistrationValues.AD_ID_PERMISSION);

        return asyncRegistration;
    }

    public static ContentValues generateSourceContentValuesV3() {
        ContentValues source = generateSourceContentValuesV2();

        // Update columns.
        source.put(
                MeasurementTables.SourceContract.AGGREGATE_SOURCE,
                SourceValues.AGGREGATE_SOURCE_V3);

        // Add columns.
        source.put(MeasurementTables.SourceContract.DEBUG_REPORTING, SourceValues.DEBUG_REPORTING);
        source.put(
                MeasurementTables.SourceContract.AD_ID_PERMISSION, SourceValues.AD_ID_PERMISSION);
        source.put(
                MeasurementTables.SourceContract.AR_DEBUG_PERMISSION,
                SourceValues.AR_DEBUG_PERMISSION);

        return source;
    }

    public static ContentValues generateTriggerContentValuesV3() {
        // No differences in trigger fields between V1 and V2.
        ContentValues trigger = generateTriggerContentValuesV2();

        // Update values.
        trigger.put(
                MeasurementTables.TriggerContract.EVENT_TRIGGERS, TriggerValues.EVENT_TRIGGERS_V3);
        trigger.put(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                TriggerValues.AGGREGATE_TRIGGER_DATA_V3);
        trigger.put(MeasurementTables.TriggerContract.FILTERS, TriggerValues.FILTERS_V3);

        // Add columns.
        trigger.put(MeasurementTables.TriggerContract.NOT_FILTERS, TriggerValues.NOT_FILTERS);
        trigger.put(
                MeasurementTables.TriggerContract.DEBUG_REPORTING, TriggerValues.DEBUG_REPORTING);
        trigger.put(
                MeasurementTables.TriggerContract.AD_ID_PERMISSION, TriggerValues.AD_ID_PERMISSION);
        trigger.put(
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION,
                TriggerValues.AR_DEBUG_PERMISSION);

        return trigger;
    }

    public static ContentValues generateAttributionContentValuesV3() {
        ContentValues attribution = generateAttributionContentValuesV2();

        // Add columns.
        attribution.put(
                MeasurementTables.AttributionContract.SOURCE_ID, AttributionValues.SOURCE_ID);
        attribution.put(
                MeasurementTables.AttributionContract.TRIGGER_ID, AttributionValues.TRIGGER_ID);

        return attribution;
    }

    public static ContentValues generateEventReportContentValuesV3() {
        ContentValues eventReport = generateEventReportContentValuesV2();

        // Update column.
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_ID, EventReportValues.SOURCE_ID_V3);

        // Add columns.
        eventReport.put(
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
                EventReportValues.SOURCE_EVENT_ID);
        eventReport.put(
                MeasurementTables.EventReportContract.TRIGGER_ID, EventReportValues.TRIGGER_ID);
        eventReport.put(
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                EventReportValues.DEBUG_REPORT_STATUS);

        return eventReport;
    }

    public static ContentValues generateAggregateReportContentValuesV3() {
        ContentValues aggregateReport = generateAggregateReportContentValuesV2();

        // Add columns.
        aggregateReport.put(
                MeasurementTables.AggregateReport.SOURCE_ID, AggregateReportValues.SOURCE_ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.TRIGGER_ID, AggregateReportValues.TRIGGER_ID);
        aggregateReport.put(
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                AggregateReportValues.DEBUG_REPORT_STATUS);

        return aggregateReport;
    }

    public static ContentValues generateAggregateEncryptionKeyContentValuesV3() {
        // No differences in aggregate encryption key fields between V2 and V3.
        return generateAggregateEncryptionKeyContentValuesV2();
    }

    public static ContentValues generateDebugReportContentValuesV3() {
        ContentValues debugReport = new ContentValues();

        debugReport.put(MeasurementTables.DebugReportContract.ID, DebugReportValues.ID);
        debugReport.put(MeasurementTables.DebugReportContract.TYPE, DebugReportValues.TYPE);
        debugReport.put(MeasurementTables.DebugReportContract.BODY, DebugReportValues.BODY);
        debugReport.put(
                MeasurementTables.DebugReportContract.ENROLLMENT_ID,
                DebugReportValues.ENROLLMENT_ID);

        return debugReport;
    }
}