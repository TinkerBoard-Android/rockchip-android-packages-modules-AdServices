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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.CleartextAggregatePayload;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

public class MeasurementDaoTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = "MeasurementDaoTest";
    private static final String PAYLOAD =
            "{\"operation\":\"histogram\","
                    + "\"data\":[{\"bucket\":1369,\"value\":32768},"
                    + "{\"bucket\":3461,\"value\":1664}]}";
    private static final long MIN_TIME_MS = TimeUnit.MINUTES.toMillis(10L);
    private static final long MAX_TIME_MS = TimeUnit.MINUTES.toMillis(60L);
    private static final Uri APP_TWO_SOURCES = Uri.parse("android-app://com.example1.two-sources");
    private static final Uri APP_ONE_SOURCE = Uri.parse("android-app://com.example2.one-source");
    private static final Uri APP_NO_SOURCE = Uri.parse("android-app://com.example3.no-sources");
    private static final Uri APP_TWO_TRIGGERS =
            Uri.parse("android-app://com.example1.two-triggers");
    private static final Uri APP_ONE_TRIGGER = Uri.parse("android-app://com.example1.one-trigger");
    private static final Uri APP_NO_TRIGGERS = Uri.parse("android-app://com.example1.no-triggers");
    private static final Uri INSTALLED_PACKAGE = Uri.parse("android-app://com.example.installed");

    @After
    public void cleanup() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
    }

    @Test
    public void testInsertSource() {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertSource(
                        ValidSourceParams.SOURCE_EVENT_ID,
                        ValidSourceParams.sPublisher,
                        ValidSourceParams.sAttributionDestination,
                        ValidSourceParams.sAdTechDomain,
                        ValidSourceParams.sRegistrant,
                        ValidSourceParams.SOURCE_EVENT_TIME,
                        ValidSourceParams.EXPIRY_TIME,
                        ValidSourceParams.PRIORITY,
                        ValidSourceParams.SOURCE_TYPE,
                        ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                        ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                        ValidSourceParams.ATTRIBUTION_MODE,
                        ValidSourceParams.buildAggregateSource(),
                        ValidSourceParams.buildAggregateFilterData()
                )
        );

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.SourceContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Source source = SqliteObjectMapper.constructSourceFromCursor(sourceCursor);
            Assert.assertNotNull(source);
            Assert.assertNotNull(source.getId());
            assertEquals(ValidSourceParams.sPublisher, source.getPublisher());
            assertEquals(ValidSourceParams.sAttributionDestination,
                    source.getAttributionDestination());
            assertEquals(ValidSourceParams.sAdTechDomain, source.getAdTechDomain());
            assertEquals(ValidSourceParams.sRegistrant, source.getRegistrant());
            assertEquals(ValidSourceParams.SOURCE_EVENT_TIME.longValue(), source.getEventTime());
            assertEquals(ValidSourceParams.EXPIRY_TIME.longValue(), source.getExpiryTime());
            assertEquals(ValidSourceParams.PRIORITY.longValue(), source.getPriority());
            assertEquals(ValidSourceParams.SOURCE_TYPE, source.getSourceType());
            assertEquals(ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW.longValue(),
                    source.getInstallAttributionWindow());
            assertEquals(ValidSourceParams.INSTALL_COOLDOWN_WINDOW.longValue(),
                    source.getInstallCooldownWindow());
            assertEquals(ValidSourceParams.ATTRIBUTION_MODE, source.getAttributionMode());
            assertEquals(ValidSourceParams.buildAggregateSource(), source.getAggregateSource());
            assertEquals(ValidSourceParams.buildAggregateFilterData(),
                    source.getAggregateFilterData());
            assertEquals(ValidSourceParams.AGGREGATE_CONTRIBUTIONS,
                    source.getAggregateContributions());
        }
    }

    @Test
    public void testInsertSource_validateArgumentSourceEventId() {
        assertInvalidSourceArguments(
                null,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentPublisher() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                null,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                Uri.parse("com.source"),
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentAttributionDestination() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                null,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                Uri.parse("com.destination"),
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentAdTechDomain() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                null,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                Uri.parse("com.adTechDomain"),
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentRegistrant() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                null,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                Uri.parse("com.registrant"),
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentSourceEventTime() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                null,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentSourceExpiryTime() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                null,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentPriority() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                null,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentSourceType() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                null,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentInstallAttributionWindow() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                null,
                ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData());
    }

    @Test
    public void testInsertSource_validateArgumentInstallCooldownWindow() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sPublisher,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sAdTechDomain,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.SOURCE_TYPE,
                ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                null,
                ValidSourceParams.ATTRIBUTION_MODE,
                ValidSourceParams.buildAggregateSource(),
                ValidSourceParams.buildAggregateFilterData()
        );
    }

    private void assertInvalidSourceArguments(Long sourceEventId, Uri publisher,
            Uri attributionDestination, Uri adTechDomain, Uri registrant, Long sourceEventTime,
            Long expiryTime, Long priority, Source.SourceType sourceType,
            Long installAttributionWindow, Long installCooldownWindow,
            @Source.AttributionMode int attributionMode, @Nullable String aggregateSource,
            @Nullable String aggregateFilterData) {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
                    try {
                        dao.insertSource(
                                sourceEventId,
                                publisher,
                                attributionDestination,
                                adTechDomain,
                                registrant,
                                sourceEventTime,
                                expiryTime,
                                priority,
                                sourceType,
                                installAttributionWindow,
                                installCooldownWindow,
                                attributionMode,
                                aggregateSource,
                                aggregateFilterData);
                        fail();
                    } catch (DatastoreException e) {
                        // Valid Exception
                    }
                }
        );

    }

    @Test
    public void testInsertTrigger() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) ->
                                dao.insertTrigger(
                                        ValidTriggerParams.sAttributionDestination,
                                        ValidTriggerParams.sAdTechDomain,
                                        ValidTriggerParams.sRegistrant,
                                        ValidTriggerParams.TRIGGER_TIME,
                                        ValidTriggerParams.TRIGGER_DATA,
                                        ValidTriggerParams.DEDUP_KEY,
                                        ValidTriggerParams.PRIORITY,
                                        ValidTriggerParams.buildAggregateTriggerData(),
                                        ValidTriggerParams.buildAggregateValues(),
                                        ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING));

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.TriggerContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(sourceCursor);
            Assert.assertNotNull(trigger);
            Assert.assertNotNull(trigger.getId());
            assertEquals(ValidTriggerParams.sAttributionDestination,
                    trigger.getAttributionDestination());
            assertEquals(ValidTriggerParams.sAdTechDomain, trigger.getAdTechDomain());
            assertEquals(ValidTriggerParams.sRegistrant, trigger.getRegistrant());
            assertEquals(ValidTriggerParams.TRIGGER_TIME.longValue(), trigger.getTriggerTime());
            assertEquals(ValidTriggerParams.TRIGGER_DATA.longValue(),
                    trigger.getEventTriggerData());
            assertEquals(ValidTriggerParams.DEDUP_KEY, trigger.getDedupKey());
            assertEquals(ValidTriggerParams.PRIORITY.longValue(), trigger.getPriority());
        }
    }

    @Test
    public void testInsertTrigger_validateArgumentAttributionDestination() {
        assertInvalidTriggerArguments(
                null,
                ValidTriggerParams.sAdTechDomain,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);

        assertInvalidTriggerArguments(
                Uri.parse("com.destination"),
                ValidTriggerParams.sAdTechDomain,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);
    }

    @Test
    public void testInsertTrigger_validateArgumentAdTechDomain() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                null,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);

        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                Uri.parse("com.adTechDomain"),
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);
    }

    @Test
    public void testInsertTrigger_validateArgumentRegistrant() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sAdTechDomain,
                null,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);

        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sAdTechDomain,
                Uri.parse("com.registrant"),
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);
    }

    @Test
    public void testInsertTrigger_validateArgumentTriggerTime() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sAdTechDomain,
                ValidTriggerParams.sRegistrant,
                null,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);
    }

    @Test
    public void testInsertTrigger_validateArgumentTriggerData() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sAdTechDomain,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                null,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);
    }

    @Test
    public void testInsertTrigger_validateArgumentDedupKey() {
        cleanup();

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) ->
                                dao.insertTrigger(
                                        ValidTriggerParams.sAttributionDestination,
                                        ValidTriggerParams.sAdTechDomain,
                                        ValidTriggerParams.sRegistrant,
                                        ValidTriggerParams.TRIGGER_TIME,
                                        ValidTriggerParams.TRIGGER_DATA,
                                        null,
                                        ValidTriggerParams.PRIORITY,
                                        ValidTriggerParams.buildAggregateTriggerData(),
                                        ValidTriggerParams.buildAggregateValues(),
                                        ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING));

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.TriggerContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(sourceCursor);
            Assert.assertNotNull(trigger);
            Assert.assertNotNull(trigger.getId());
            assertEquals(ValidTriggerParams.sAttributionDestination,
                    trigger.getAttributionDestination());
            assertEquals(ValidTriggerParams.sAdTechDomain, trigger.getAdTechDomain());
            assertEquals(ValidTriggerParams.sRegistrant, trigger.getRegistrant());
            assertEquals(ValidTriggerParams.TRIGGER_TIME.longValue(), trigger.getTriggerTime());
            assertEquals(ValidTriggerParams.TRIGGER_DATA.longValue(),
                    trigger.getEventTriggerData());
            assertNull(trigger.getDedupKey());
            assertEquals(ValidTriggerParams.PRIORITY.longValue(), trigger.getPriority());
        }
    }

    @Test
    public void testInsertTrigger_validateArgumentPriority() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sAdTechDomain,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                null,
                ValidTriggerParams.buildAggregateTriggerData(),
                ValidTriggerParams.buildAggregateValues(),
                ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING);
    }

    public void assertInvalidTriggerArguments(
            Uri attributionDestination,
            Uri adTechDomain,
            Uri registrant,
            Long triggerTime,
            Long triggerData,
            Long dedupKey,
            Long priority,
            String aggregateTriggerData,
            String aggregateValues,
            String filters) {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            try {
                                dao.insertTrigger(
                                        attributionDestination,
                                        adTechDomain,
                                        registrant,
                                        triggerTime,
                                        triggerData,
                                        dedupKey,
                                        priority,
                                        aggregateTriggerData,
                                        aggregateValues,
                                        filters);
                                fail();
                            } catch (DatastoreException e) {
                                // Valid Exception
                            }
                        });
    }

    @Test
    public void testGetNumSourcesPerRegistrant() {
        setupSourceAndTriggerData();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(2, measurementDao.getNumSourcesPerRegistrant(APP_TWO_SOURCES));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(1, measurementDao.getNumSourcesPerRegistrant(APP_ONE_SOURCE));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(0, measurementDao.getNumSourcesPerRegistrant(APP_NO_SOURCE));
                });
    }

    @Test
    public void testGetNumTriggersPerRegistrant() {
        setupSourceAndTriggerData();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(2, measurementDao.getNumTriggersPerRegistrant(APP_TWO_TRIGGERS));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(1, measurementDao.getNumTriggersPerRegistrant(APP_ONE_TRIGGER));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(0, measurementDao.getNumTriggersPerRegistrant(APP_NO_TRIGGERS));
                });
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteMeasurementData_requiredRegistrantAsNull() {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
            dao.deleteMeasurementData(
                    null /* registrant */, null /* origin */,
                    null /* start */, null /* end */);
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeNoStartDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    APP_ONE_SOURCE,
                                    null /* origin */,
                                    null /* start */,
                                    Instant.now());
                        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeNoEndDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    APP_ONE_SOURCE,
                                    null /* origin */,
                                    Instant.now(),
                                    null /* end */);
                        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeStartAfterEndDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    APP_ONE_SOURCE,
                                    null /* origin */,
                                    Instant.now().plusMillis(1),
                                    Instant.now());
                        });
    }

    @Test
    public void testInstallAttribution_selectHighestPriority() {
        long currentTimestamp = System.currentTimeMillis();

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 100, -1, false),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 50, -1, false),
                db);
        // Should select id IA1 because it has higher priority
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE, currentTimestamp);
                                }));
        Assert.assertTrue(getInstallAttributionStatus("IA1", db));
        Assert.assertFalse(getInstallAttributionStatus("IA2", db));
        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_selectLatest() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false),
                db);
        // Should select id=IA2 as it is latest
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE, currentTimestamp);
                                }));
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
        Assert.assertTrue(getInstallAttributionStatus("IA2", db));

        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_ignoreNewerSources() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false),
                db);
        // Should select id=IA1 as it is the only valid choice.
        // id=IA2 is newer than the evenTimestamp of install event.
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE,
                                            currentTimestamp - TimeUnit.DAYS.toMillis(7));
                                }));
        Assert.assertTrue(getInstallAttributionStatus("IA1", db));
        Assert.assertFalse(getInstallAttributionStatus("IA2", db));

        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_noValidSource() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 10, 10, true),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 10, 11, true),
                db);
        // Should not update any sources.
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.doInstallAttribution(
                                                INSTALLED_PACKAGE, currentTimestamp)));
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
        Assert.assertFalse(getInstallAttributionStatus("IA2", db));
        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testUndoInstallAttribution_noMarkedSource() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Source source = createSourceForIATest("IA1", currentTimestamp, 10, 10, false);
        source.setInstallAttributed(true);
        AbstractDbIntegrationTest.insertToDb(source, db);
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.undoInstallAttribution(INSTALLED_PACKAGE)));
        // Should set installAttributed = false for id=IA1
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
    }

    @Test
    public void testGetSourceEventReports() {
        List<Source> sourceList = new ArrayList<>();
        sourceList.add(new Source.Builder().setId("1").setEventId(3).build());
        sourceList.add(new Source.Builder().setId("2").setEventId(4).build());

        // Should match with source 1
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(new EventReport.Builder().setId("1").setSourceId(3).build());
        reportList1.add(new EventReport.Builder().setId("7").setSourceId(3).build());

        // Should match with source 2
        List<EventReport> reportList2 = new ArrayList<>();
        reportList2.add(new EventReport.Builder().setId("3").setSourceId(4).build());
        reportList2.add(new EventReport.Builder().setId("8").setSourceId(4).build());

        List<EventReport> reportList3 = new ArrayList<>();
        // Should not match with any source
        reportList3.add(new EventReport.Builder().setId("2").setSourceId(5).build());
        reportList3.add(new EventReport.Builder().setId("4").setSourceId(6).build());
        reportList3.add(new EventReport.Builder().setId("5").setSourceId(1).build());
        reportList3.add(new EventReport.Builder().setId("6").setSourceId(2).build());

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        sourceList.forEach(source -> {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.SourceContract.ID, source.getId());
            values.put(MeasurementTables.SourceContract.EVENT_ID, source.getEventId());
            db.insert(MeasurementTables.SourceContract.TABLE, null, values);
        });
        Stream.of(reportList1, reportList2, reportList3)
                .flatMap(Collection::stream)
                .forEach(eventReport -> {
                    ContentValues values = new ContentValues();
                    values.put(MeasurementTables.EventReportContract.ID, eventReport.getId());
                    values.put(MeasurementTables.EventReportContract.SOURCE_ID,
                            eventReport.getSourceId());
                    db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
                });

        Assert.assertEquals(
                reportList1,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao -> measurementDao.getSourceEventReports(
                                        sourceList.get(0)))
                        .orElseThrow());

        Assert.assertEquals(
                reportList2,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao -> measurementDao.getSourceEventReports(
                                        sourceList.get(1)))
                        .orElseThrow());
    }

    @Test
    public void testUpdateSourceStatus() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);

        List<Source> sourceList = new ArrayList<>();
        sourceList.add(new Source.Builder().setId("1").build());
        sourceList.add(new Source.Builder().setId("2").build());
        sourceList.add(new Source.Builder().setId("3").build());
        sourceList.forEach(source -> {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.SourceContract.ID, source.getId());
            values.put(MeasurementTables.SourceContract.STATUS, 1);
            db.insert(MeasurementTables.SourceContract.TABLE, null, values);
        });

        // Multiple Elements
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        measurementDao -> measurementDao.updateSourceStatus(
                                sourceList, Source.Status.IGNORED)
                ));

        // Single Element
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        measurementDao -> measurementDao.updateSourceStatus(
                                sourceList.subList(0, 1), Source.Status.IGNORED)
                ));
    }

    @Test
    public void testGetMatchingActiveSources() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Uri adTechDomain = Uri.parse("https://www.example.xyz");
        Uri attributionDestination = Uri.parse("android-app://com.example.abc");
        Source s1 = new Source.Builder().setId("1").setEventTime(10).setExpiryTime(20).build();
        Source s2 = new Source.Builder().setId("2").setEventTime(10).setExpiryTime(50).build();
        Source s3 = new Source.Builder().setId("3").setEventTime(20).setExpiryTime(50).build();
        Source s4 = new Source.Builder().setId("4").setEventTime(30).setExpiryTime(50).build();
        List<Source> sources = Arrays.asList(s1, s2, s3, s4);
        sources.forEach(source -> {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.SourceContract.ID, source.getId());
            values.put(MeasurementTables.SourceContract.STATUS, Source.Status.ACTIVE);
            values.put(MeasurementTables.SourceContract.EVENT_TIME, source.getEventTime());
            values.put(MeasurementTables.SourceContract.EXPIRY_TIME, source.getExpiryTime());
            values.put(MeasurementTables.SourceContract.AD_TECH_DOMAIN, adTechDomain.toString());
            values.put(MeasurementTables.SourceContract.ATTRIBUTION_DESTINATION,
                    attributionDestination.toString());
            db.insert(MeasurementTables.SourceContract.TABLE, null, values);
        });

        Function<Trigger, List<Source>> runFunc = trigger -> {
            List<Source> result = DatastoreManagerFactory.getDatastoreManager(sContext)
                    .runInTransactionWithResult(
                            measurementDao -> measurementDao.getMatchingActiveSources(trigger)
                    ).orElseThrow();
            result.sort(Comparator.comparing(Source::getId));
            return result;
        };

        // Trigger Time > s1's eventTime and < s1's expiryTime
        // Trigger Time > s2's eventTime and < s2's expiryTime
        // Trigger Time < s3's eventTime
        // Trigger Time < s4's eventTime
        // Expected: Match with s1 and s2
        Trigger trigger1MatchSource1And2 = new Trigger.Builder()
                .setTriggerTime(12)
                .setAdTechDomain(adTechDomain)
                .setAttributionDestination(attributionDestination)
                .build();
        List<Source> result1 = runFunc.apply(trigger1MatchSource1And2);
        Assert.assertEquals(2, result1.size());
        Assert.assertEquals(s1.getId(), result1.get(0).getId());
        Assert.assertEquals(s2.getId(), result1.get(1).getId());


        // Trigger Time > s1's eventTime and = s1's expiryTime
        // Trigger Time > s2's eventTime and < s2's expiryTime
        // Trigger Time = s3's eventTime
        // Trigger Time < s4's eventTime
        // Expected: Match with s1 and s2
        Trigger trigger2MatchSource1And2 = new Trigger.Builder()
                .setTriggerTime(20)
                .setAdTechDomain(adTechDomain)
                .setAttributionDestination(attributionDestination)
                .build();

        List<Source> result2 = runFunc.apply(trigger2MatchSource1And2);
        Assert.assertEquals(2, result2.size());
        Assert.assertEquals(s1.getId(), result2.get(0).getId());
        Assert.assertEquals(s2.getId(), result2.get(1).getId());

        // Trigger Time > s1's expiryTime
        // Trigger Time > s2's eventTime and < s2's expiryTime
        // Trigger Time > s3's eventTime and < s3's expiryTime
        // Trigger Time < s4's eventTime
        // Expected: Match with s2 and s3
        Trigger trigger3MatchSource2And3 = new Trigger.Builder()
                .setTriggerTime(21)
                .setAdTechDomain(adTechDomain)
                .setAttributionDestination(attributionDestination)
                .build();

        List<Source> result3 = runFunc.apply(trigger3MatchSource2And3);
        Assert.assertEquals(2, result3.size());
        Assert.assertEquals(s2.getId(), result3.get(0).getId());
        Assert.assertEquals(s3.getId(), result3.get(1).getId());

        // Trigger Time > s1's expiryTime
        // Trigger Time > s2's eventTime and < s2's expiryTime
        // Trigger Time > s3's eventTime and < s3's expiryTime
        // Trigger Time > s4's eventTime and < s4's expiryTime
        // Expected: Match with s2, s3 and s4
        Trigger trigger4MatchSource1And2And3 = new Trigger.Builder()
                .setTriggerTime(31)
                .setAdTechDomain(adTechDomain)
                .setAttributionDestination(attributionDestination)
                .build();

        List<Source> result4 = runFunc.apply(trigger4MatchSource1And2And3);
        Assert.assertEquals(3, result4.size());
        Assert.assertEquals(s2.getId(), result4.get(0).getId());
        Assert.assertEquals(s3.getId(), result4.get(1).getId());
        Assert.assertEquals(s4.getId(), result4.get(2).getId());
    }

    @Test
    public void testInsertAggregateEncryptionKey() {
        String keyId = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
        String publicKey = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
        long expiry = 1653620135831L;

        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertAggregateEncryptionKey(
                        new AggregateEncryptionKey.Builder()
                                .setKeyId(keyId)
                                .setPublicKey(publicKey)
                                .setExpiry(expiry).build())
        );

        try (Cursor cursor = DbHelper.getInstance(sContext).getReadableDatabase()
                .query(MeasurementTables.AggregateEncryptionKey.TABLE,
                    null, null, null, null, null, null)) {
            Assert.assertTrue(cursor.moveToNext());
            AggregateEncryptionKey aggregateEncryptionKey =
                    SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor);
            Assert.assertNotNull(aggregateEncryptionKey);
            Assert.assertNotNull(aggregateEncryptionKey.getId());
            assertEquals(keyId, aggregateEncryptionKey.getKeyId());
            assertEquals(publicKey, aggregateEncryptionKey.getPublicKey());
            assertEquals(expiry, aggregateEncryptionKey.getExpiry());
        }
    }

    @Test
    public void testInsertUnencryptedAggregatePayload() {
        long randomTime = (long) ((Math.random() * (MAX_TIME_MS - MIN_TIME_MS)) + MIN_TIME_MS);
        List<AggregateHistogramContribution> contributions = new ArrayList<>();
        AggregateHistogramContribution contribution1 =
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(1369L)).setValue(32768).build();
        AggregateHistogramContribution contribution2 =
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(3461L)).setValue(1664).build();
        contributions.add(contribution1);
        contributions.add(contribution2);
        String debugPayload = null;
        try {
            debugPayload = CleartextAggregatePayload.generateDebugPayload(contributions);
        } catch (JSONException e) {
            LogUtil.e("JSONException when generating debug payload.");
        }
        String finalDebugPayload = debugPayload;
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertAggregateReport(
                        new CleartextAggregatePayload.Builder()
                                .setPublisher(ValidSourceParams.sRegistrant)
                                .setAttributionDestination(
                                        ValidSourceParams.sAttributionDestination)
                                .setSourceRegistrationTime(ValidSourceParams.SOURCE_EVENT_TIME)
                                .setScheduledReportTime(
                                        ValidTriggerParams.TRIGGER_TIME + randomTime)
                                .setReportingOrigin(ValidSourceParams.sAdTechDomain)
                                .setDebugCleartextPayload(finalDebugPayload)
                                .setStatus(EventReport.Status.PENDING).build())
        );

        try (Cursor cursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.AggregateReport.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(cursor.moveToNext());
            CleartextAggregatePayload aggregateReport =
                    SqliteObjectMapper.constructCleartextAggregatePayload(cursor);
            Assert.assertNotNull(aggregateReport);
            Assert.assertNotNull(aggregateReport.getId());
            assertEquals(ValidSourceParams.sRegistrant, aggregateReport.getPublisher());
            assertEquals(ValidSourceParams.sAttributionDestination,
                    aggregateReport.getAttributionDestination());
            assertEquals(ValidSourceParams.SOURCE_EVENT_TIME.longValue(),
                    aggregateReport.getSourceRegistrationTime());
            assertEquals(ValidTriggerParams.TRIGGER_TIME + randomTime,
                    aggregateReport.getScheduledReportTime());
            assertEquals(ValidSourceParams.sAdTechDomain, aggregateReport.getReportingOrigin());
            assertEquals(PAYLOAD, aggregateReport.getDebugCleartextPayload());
            assertEquals(EventReport.Status.PENDING, aggregateReport.getStatus());
        }
    }

    private void setupSourceAndTriggerData() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Source> sourcesList = new ArrayList<>();
        sourcesList.add(new Source.Builder().setId("S1").setRegistrant(APP_TWO_SOURCES).build());
        sourcesList.add(new Source.Builder().setId("S2").setRegistrant(APP_TWO_SOURCES).build());
        sourcesList.add(new Source.Builder().setId("S3").setRegistrant(APP_ONE_SOURCE).build());
        for (Source source : sourcesList) {
            ContentValues values = new ContentValues();
            values.put("_id", source.getId());
            values.put("registrant", source.getRegistrant().toString());
            long row = db.insert("msmt_source", null, values);
            Assert.assertNotEquals("Source insertion failed", -1, row);
        }
        List<Trigger> triggersList = new ArrayList<>();
        triggersList.add(new Trigger.Builder().setId("T1").setRegistrant(APP_TWO_TRIGGERS).build());
        triggersList.add(new Trigger.Builder().setId("T2").setRegistrant(APP_TWO_TRIGGERS).build());
        triggersList.add(new Trigger.Builder().setId("T3").setRegistrant(APP_ONE_TRIGGER).build());
        for (Trigger trigger : triggersList) {
            ContentValues values = new ContentValues();
            values.put("_id", trigger.getId());
            values.put("registrant", trigger.getRegistrant().toString());
            long row = db.insert("msmt_trigger", null, values);
            Assert.assertNotEquals("Trigger insertion failed", -1, row);
        }
    }

    private Source createSourceForIATest(String id, long currentTime, long priority,
            int eventTimePastDays, boolean expiredIAWindow) {
        return new Source.Builder()
                .setId(id)
                .setPublisher(Uri.parse("android-app://com.example.sample"))
                .setRegistrant(Uri.parse("android-app://com.example.sample"))
                .setAdTechDomain(Uri.parse("https://example.com"))
                .setExpiryTime(currentTime + TimeUnit.DAYS.toMillis(30))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(expiredIAWindow ? 0 : 30))
                .setAttributionDestination(INSTALLED_PACKAGE)
                .setEventTime(
                        currentTime
                                - TimeUnit.DAYS.toMillis(
                                        eventTimePastDays == -1 ? 10 : eventTimePastDays))
                .setPriority(priority == -1 ? 100 : priority)
                .build();
    }

    private boolean getInstallAttributionStatus(String sourceDbId, SQLiteDatabase db) {
        Cursor cursor = db.query(MeasurementTables.SourceContract.TABLE,
                new String[]{MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED},
                MeasurementTables.SourceContract.ID + " = ? ", new String[]{sourceDbId},
                null, null,
                null, null);
        Assert.assertTrue(cursor.moveToFirst());
        return cursor.getInt(0) == 1;
    }

    private void removeSources(List<String> dbIds, SQLiteDatabase db) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.ID + " IN ( ? )",
                new String[]{String.join(",", dbIds)});
    }

    private static class ValidSourceParams {
        static final Long EXPIRY_TIME = 8640000010L;
        static final Long PRIORITY = 100L;
        static final Long SOURCE_EVENT_ID = 1L;
        static final Long SOURCE_EVENT_TIME = 8640000000L;
        static final Uri sAttributionDestination = Uri.parse("android-app://com.destination");
        static final Uri sPublisher = Uri.parse("android-app://com.publisher");
        static final Uri sRegistrant = Uri.parse("android-app://com.registrant");
        static final Uri sAdTechDomain = Uri.parse("https://com.example");
        static final Source.SourceType SOURCE_TYPE = Source.SourceType.EVENT;
        static final Long INSTALL_ATTRIBUTION_WINDOW = 841839879274L;
        static final Long INSTALL_COOLDOWN_WINDOW = 8418398274L;
        static final @Source.AttributionMode int ATTRIBUTION_MODE =
                Source.AttributionMode.TRUTHFULLY;
        static final int AGGREGATE_CONTRIBUTIONS = 0;

        static String buildAggregateSource() {
            try {
                JSONArray aggregatableSource = new JSONArray();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", "campaignCounts");
                jsonObject.put("key_piece", "0x159");
                aggregatableSource.put(jsonObject);
                return aggregatableSource.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        static String buildAggregateFilterData() {
            try {
                JSONObject filterData = new JSONObject();
                filterData.put("conversion_subdomain",
                        new JSONArray(Collections.singletonList("electronics.megastore")));
                filterData.put("product", new JSONArray(Arrays.asList("1234", "2345")));
                return filterData.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static class ValidTriggerParams {
        static final Long DEDUP_KEY = 200L;
        static final Long PRIORITY = 100L;
        static final Long TRIGGER_TIME = 8640000000L;
        static final Long TRIGGER_DATA = 3L;
        static final Uri sAttributionDestination = Uri.parse("android-app://com.destination");
        static final Uri sRegistrant = Uri.parse("android-app://com.registrant");
        static final Uri sAdTechDomain = Uri.parse("https://com.example");
        private static final String TOP_LEVEL_FILTERS_JSON_STRING =
                "{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}\n";

        static String buildAggregateTriggerData() {
            try {
                JSONArray triggerData = new JSONArray();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("key_piece", "0xA80");
                jsonObject.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
                triggerData.put(jsonObject);
                return triggerData.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        static String buildAggregateValues() {
            try {
                JSONObject values = new JSONObject();
                values.put("campaignCounts", 32768);
                values.put("geoValue", 1664);
                return values.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
