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

package com.android.adservices.data.measurement;

import static java.util.function.Predicate.not;

import android.database.Cursor;
import android.net.Uri;

import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.UnsignedLong;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Helper class for SQLite operations. */
public class SqliteObjectMapper {

    /**
     * Create {@link EventReport} object from SQLite datastore.
     */
    static EventReport constructEventReportFromCursor(Cursor cursor) {
        EventReport.Builder builder = new EventReport.Builder();
        setTextColumn(cursor, MeasurementTables.EventReportContract.ID,
                builder::setId);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
                builder::setSourceEventId);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                builder::setTriggerPriority);
        setIntColumn(cursor, MeasurementTables.EventReportContract.STATUS,
                builder::setStatus);
        setIntColumn(
                cursor,
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                builder::setDebugReportStatus);
        setUnsignedLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_DATA,
                builder::setTriggerData);
        setUnsignedLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                builder::setTriggerDedupKey);
        setUriColumn(cursor, MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setTextColumn(cursor, MeasurementTables.EventReportContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setLongColumn(cursor, MeasurementTables.EventReportContract.REPORT_TIME,
                builder::setReportTime);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_TIME,
                builder::setTriggerTime);
        setTextColumn(cursor, MeasurementTables.EventReportContract.SOURCE_TYPE,
                (enumValue) -> builder.setSourceType(Source.SourceType.valueOf(enumValue)));
        setDoubleColumn(cursor, MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                builder::setRandomizedTriggerRate);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY,
                builder::setSourceDebugKey);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY,
                builder::setTriggerDebugKey);
        setTextColumn(
                cursor, MeasurementTables.EventReportContract.SOURCE_ID, builder::setSourceId);
        setTextColumn(
                cursor, MeasurementTables.EventReportContract.TRIGGER_ID, builder::setTriggerId);
        return builder.build();
    }

    /** Create {@link Source} object from SQLite datastore. */
    static Source constructSourceFromCursor(Cursor cursor) {
        Source.Builder builder = new Source.Builder();
        setTextColumn(cursor, MeasurementTables.SourceContract.ID,
                builder::setId);
        setUnsignedLongColumn(cursor, MeasurementTables.SourceContract.EVENT_ID,
                builder::setEventId);
        setLongColumn(cursor, MeasurementTables.SourceContract.PRIORITY,
                builder::setPriority);
        setTextColumn(cursor, MeasurementTables.SourceContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setUriColumn(cursor, MeasurementTables.SourceContract.PUBLISHER,
                builder::setPublisher);
        setIntColumn(cursor, MeasurementTables.SourceContract.PUBLISHER_TYPE,
                builder::setPublisherType);
        setUriColumn(
                cursor,
                MeasurementTables.SourceContract.APP_DESTINATION,
                builder::setAppDestination);
        setUriColumn(
                cursor,
                MeasurementTables.SourceContract.WEB_DESTINATION,
                builder::setWebDestination);
        setTextColumn(cursor, MeasurementTables.SourceContract.SOURCE_TYPE,
                (enumValue) -> builder.setSourceType(Source.SourceType.valueOf(enumValue)));
        setLongColumn(cursor, MeasurementTables.SourceContract.EXPIRY_TIME,
                builder::setExpiryTime);
        setLongColumn(cursor, MeasurementTables.SourceContract.EVENT_TIME,
                builder::setEventTime);
        setTextColumn(cursor, MeasurementTables.SourceContract.DEDUP_KEYS,
                (concatArray) -> builder.setDedupKeys(Arrays.stream(concatArray.split(","))
                        .map(String::trim)
                        .filter(not(String::isEmpty))
                        .map(UnsignedLong::new)
                        .collect(Collectors.toList())));
        setIntColumn(cursor, MeasurementTables.SourceContract.STATUS,
                builder::setStatus);
        setUriColumn(cursor, MeasurementTables.SourceContract.REGISTRANT,
                builder::setRegistrant);
        setIntColumn(cursor, MeasurementTables.SourceContract.ATTRIBUTION_MODE,
                builder::setAttributionMode);
        setLongColumn(cursor, MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                builder::setInstallAttributionWindow);
        setLongColumn(cursor, MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                builder::setInstallCooldownWindow);
        setBooleanColumn(cursor, MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
                builder::setInstallAttributed);
        setTextColumn(cursor, MeasurementTables.SourceContract.FILTER_DATA,
                builder::setFilterData);
        setTextColumn(cursor, MeasurementTables.SourceContract.AGGREGATE_SOURCE,
                builder::setAggregateSource);
        setIntColumn(cursor, MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
                builder::setAggregateContributions);
        setUnsignedLongColumn(cursor, MeasurementTables.SourceContract.DEBUG_KEY,
                builder::setDebugKey);
        return builder.build();
    }

    /** Create {@link Trigger} object from SQLite datastore. */
    public static Trigger constructTriggerFromCursor(Cursor cursor) {
        Trigger.Builder builder = new Trigger.Builder();
        setTextColumn(cursor, MeasurementTables.TriggerContract.ID,
                builder::setId);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                builder::setEventTriggers);
        setUriColumn(cursor, MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setIntColumn(cursor, MeasurementTables.TriggerContract.DESTINATION_TYPE,
                builder::setDestinationType);
        setTextColumn(cursor, MeasurementTables.TriggerContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setIntColumn(cursor, MeasurementTables.TriggerContract.STATUS,
                builder::setStatus);
        setLongColumn(cursor, MeasurementTables.TriggerContract.TRIGGER_TIME,
                builder::setTriggerTime);
        setUriColumn(cursor, MeasurementTables.TriggerContract.REGISTRANT,
                builder::setRegistrant);
        setTextColumn(cursor, MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                builder::setAggregateTriggerData);
        setTextColumn(cursor, MeasurementTables.TriggerContract.AGGREGATE_VALUES,
                builder::setAggregateValues);
        setTextColumn(cursor, MeasurementTables.TriggerContract.FILTERS, builder::setFilters);
        setTextColumn(cursor, MeasurementTables.TriggerContract.NOT_FILTERS,
                builder::setNotFilters);
        setUnsignedLongColumn(cursor, MeasurementTables.TriggerContract.DEBUG_KEY,
                builder::setDebugKey);
        return builder.build();
    }

    /**
     * Create {@link AggregateReport} object from SQLite datastore.
     */
    static AggregateReport constructAggregateReport(Cursor cursor) {
        AggregateReport.Builder builder = new AggregateReport.Builder();
        setTextColumn(cursor, MeasurementTables.AggregateReport.ID,
                builder::setId);
        setUriColumn(cursor, MeasurementTables.AggregateReport.PUBLISHER,
                builder::setPublisher);
        setUriColumn(cursor, MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setLongColumn(cursor, MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                builder::setSourceRegistrationTime);
        setLongColumn(cursor, MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                builder::setScheduledReportTime);
        setTextColumn(cursor, MeasurementTables.AggregateReport.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setTextColumn(cursor, MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                builder::setDebugCleartextPayload);
        setIntColumn(cursor, MeasurementTables.AggregateReport.STATUS,
                builder::setStatus);
        setIntColumn(
                cursor,
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                builder::setDebugReportStatus);
        setTextColumn(cursor, MeasurementTables.AggregateReport.API_VERSION,
                builder::setApiVersion);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                builder::setSourceDebugKey);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                builder::setTriggerDebugKey);
        setTextColumn(cursor, MeasurementTables.AggregateReport.SOURCE_ID, builder::setSourceId);
        setTextColumn(cursor, MeasurementTables.AggregateReport.TRIGGER_ID, builder::setTriggerId);
        return builder.build();
    }

    /**
     * Create {@link AggregateEncryptionKey} object from SQLite datastore.
     */
    static AggregateEncryptionKey constructAggregateEncryptionKeyFromCursor(Cursor cursor) {
        AggregateEncryptionKey.Builder builder = new AggregateEncryptionKey.Builder();
        setTextColumn(cursor, MeasurementTables.AggregateEncryptionKey.ID,
                builder::setId);
        setTextColumn(cursor, MeasurementTables.AggregateEncryptionKey.KEY_ID,
                builder::setKeyId);
        setTextColumn(cursor, MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                builder::setPublicKey);
        setLongColumn(cursor, MeasurementTables.AggregateEncryptionKey.EXPIRY,
                builder::setExpiry);
        return builder.build();
    }

    /** Create {@link AsyncRegistration} object from SQLite datastore. */
    public static AsyncRegistration constructAsyncRegistration(Cursor cursor) {
        AsyncRegistration.Builder builder = new AsyncRegistration.Builder();
        setTextColumn(cursor, MeasurementTables.AsyncRegistrationContract.ID, builder::setId);
        setTextColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                builder::setWebDestination);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                builder::setOsDestination);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                builder::setRegistrationUri);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                builder::setVerifiedDestination);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                builder::setTopOrigin);
        setIntColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_TYPE,
                builder::setRedirectType);
        setIntColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_COUNT,
                builder::setRedirectCount);
        setIntColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                (enumValue) ->
                        builder.setSourceType(
                                enumValue == null ? null : Source.SourceType.values()[enumValue]));
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                builder::setRegistrant);
        setLongColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                builder::setRequestTime);
        setLongColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                builder::setRetryCount);
        setLongColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.LAST_PROCESSING_TIME,
                builder::setLastProcessingTime);
        setIntColumn(cursor, MeasurementTables.AsyncRegistrationContract.TYPE, builder::setType);
        setBooleanColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                builder::setDebugKeyAllowed);
        return builder.build();
    }

    private static <BuilderType> void setUriColumn(Cursor cursor, String column, Function<Uri,
            BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, (x) -> setter.apply(Uri.parse(x)));
    }

    private static <BuilderType> void setIntColumn(Cursor cursor, String column,
                                                   Function<Integer, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getInt, setter);
    }

    private static <BuilderType> void setDoubleColumn(Cursor cursor, String column,
            Function<Double, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getDouble, setter);
    }

    private static <BuilderType> void setLongColumn(Cursor cursor, String column,
                                                    Function<Long, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getLong, setter);
    }

    private static <BuilderType> void setUnsignedLongColumn(Cursor cursor, String column,
                                                    Function<UnsignedLong, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getLong, signedLong ->
                setter.apply(new UnsignedLong(signedLong)));
    }

    private static <BuilderType> void setTextColumn(Cursor cursor, String column,
                                                    Function<String, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, setter);
    }

    private static <BuilderType> void setBooleanColumn(Cursor cursor, String column,
            Function<Boolean, BuilderType> setter) {
        setIntColumn(cursor, column, (x) -> setter.apply(x == 1));
    }

    private static <BuilderType, DataType> void setColumnValue(
            Cursor cursor, String column, Function<Integer, DataType> getColVal,
            Function<DataType, BuilderType> setter) {
        int index = cursor.getColumnIndex(column);
        if (index > -1 && !cursor.isNull(index)) {
            setter.apply(getColVal.apply(index));
        }
    }
}
