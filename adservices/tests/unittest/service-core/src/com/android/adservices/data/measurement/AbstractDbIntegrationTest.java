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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Abstract class for parameterized tests that
 * check the database state of measurement
 * tables against expected output after an action
 * is run.
 *
 * Consider @RunWith(Parameterized.class)
 */
public abstract class AbstractDbIntegrationTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    public final DbState mInput;
    public final DbState mOutput;

    @Before
    public void before() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        emptyTables(db);
        seedTables(db, mInput);
    }

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        emptyTables(db);
    }

    public AbstractDbIntegrationTest(DbState input, DbState output) {
        this.mInput = input;
        this.mOutput = output;
    }

    /**
     * Runs the action we want to test.
     */
    public abstract void runActionToTest() throws DatastoreException;

    @Test
    public void runTest() throws DatastoreException, JSONException {
        runActionToTest();
        SQLiteDatabase readerDb = DbHelper.getInstance(sContext).getReadableDatabase();
        DbState dbState = new DbState(readerDb);
        mOutput.sortAll();
        dbState.sortAll();
        Assert.assertTrue("Source mismatch",
                areEqual(mOutput.mSourceList, dbState.mSourceList));
        Assert.assertTrue("Trigger mismatch",
                areEqual(mOutput.mTriggerList, dbState.mTriggerList));
        Assert.assertTrue(
                "Report mismatch", areEqual(mOutput.mEventReportList, dbState.mEventReportList));
        Assert.assertTrue("Attribution mismatch",
                areEqual(mOutput.mAttrRateLimitList, dbState.mAttrRateLimitList));
        Assert.assertTrue(
                "AggregateReport mismatch",
                areEqual(mOutput.mAggregateReportList, dbState.mAggregateReportList));
        Assert.assertTrue("AggregateEncryptionKey mismatch",
                areEqual(mOutput.mAggregateEncryptionKeyList, dbState.mAggregateEncryptionKeyList));
    }

    /**
     * Generic function type that handles JSON objects,
     * used to type lambdas provided to {@link getTestCasesFrom}
     * to prepare test-specific data.
     */
    @FunctionalInterface
    public interface CheckedJsonFunction {
        Object apply(JSONObject jsonObject) throws JSONException;
    }

    /**
     * Builds and returns test cases from a JSON InputStream
     * to be used by JUnit parameterized tests.
     *
     * @return A collection of Object arrays, each with
     * {@code [DbState input, DbState output, (any) additionalData, String name]}
     */
    public static Collection<Object[]> getTestCasesFrom(InputStream inputStream,
            CheckedJsonFunction prepareAdditionalData) throws IOException, JSONException {
        int size = inputStream.available();
        byte[] buffer = new byte[size];
        inputStream.read(buffer);
        inputStream.close();
        String json = new String(buffer, StandardCharsets.UTF_8);
        if (json.length() <= 10) {
            throw new IOException("json length less than 11 characters.");
        }
        JSONObject obj = new JSONObject(json);
        JSONArray testArray = obj.getJSONArray("testCases");

        List<Object[]> testCases = new ArrayList<>();
        for (int i = 0; i < testArray.length(); i++) {
            JSONObject testObj = testArray.getJSONObject(i);
            String name = testObj.getString("name");
            JSONObject input = testObj.getJSONObject("input");
            JSONObject output = testObj.getJSONObject("output");
            DbState inputState = new DbState(input);
            DbState outputState = new DbState(output);
            if (prepareAdditionalData != null) {
                testCases.add(new Object[]{inputState, outputState,
                        prepareAdditionalData.apply(testObj), name});
            } else {
                testCases.add(new Object[]{inputState, outputState, name});
            }
        }
        return testCases;
    }

    public static Collection<Object[]> getTestCasesFromMultipleStreams(
            List<InputStream> inputStreams, CheckedJsonFunction prepareAdditionalData)
            throws IOException, JSONException {
        List<Object[]> testCases = new ArrayList<>();
        for (InputStream inputStream : inputStreams) {
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject testObj = new JSONObject(json);
            String name = testObj.getString("name");
            JSONObject input = testObj.getJSONObject("input");
            JSONObject output = testObj.getJSONObject("output");
            DbState inputState = new DbState(input);
            DbState outputState = new DbState(output);
            if (prepareAdditionalData != null) {
                testCases.add(
                        new Object[] {
                            inputState, outputState, prepareAdditionalData.apply(testObj), name
                        });
            } else {
                testCases.add(new Object[] {inputState, outputState, name});
            }
        }
        return testCases;
    }

    /**
     * Compares two lists of the same measurement record type.
     * (Caller enforces the element types.)
     */
    public static <T> boolean areEqual(List<T> list1, List<T> list2) {
        return Arrays.equals(list1.toArray(), list2.toArray());
    }

    /**
     * Empties measurement database tables,
     * used for test cleanup.
     */
    private static void emptyTables(SQLiteDatabase db) {
        db.delete(MeasurementTables.SourceContract.TABLE, null, null);
        db.delete(MeasurementTables.TriggerContract.TABLE, null, null);
        db.delete(MeasurementTables.EventReportContract.TABLE, null, null);
        db.delete(MeasurementTables.AttributionContract.TABLE, null, null);
        db.delete(MeasurementTables.AggregateReport.TABLE, null, null);
        db.delete(MeasurementTables.AggregateEncryptionKey.TABLE, null, null);
    }

    /**
     * Seeds measurement database tables for testing.
     */
    private static void seedTables(SQLiteDatabase db, DbState input) throws SQLiteException {
        for (Source source : input.mSourceList) {
            insertToDb(source, db);
        }
        for (Trigger trigger : input.mTriggerList) {
            insertToDb(trigger, db);
        }
        for (EventReport eventReport : input.mEventReportList) {
            insertToDb(eventReport, db);
        }
        for (com.android.adservices.service.measurement.Attribution attr :
                input.mAttrRateLimitList) {
            insertToDb(attr, db);
        }
        for (AggregateReport aggregateReport : input.mAggregateReportList) {
            insertToDb(aggregateReport, db);
        }
        for (AggregateEncryptionKey key : input.mAggregateEncryptionKeyList) {
            insertToDb(key, db);
        }
    }

    /**
     * Inserts a Source record into the given database.
     */
    public static void insertToDb(Source source, SQLiteDatabase db) throws SQLiteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, source.getId());
        values.put(MeasurementTables.SourceContract.EVENT_ID, source.getEventId());
        values.put(MeasurementTables.SourceContract.SOURCE_TYPE, source.getSourceType().toString());
        values.put(MeasurementTables.SourceContract.PUBLISHER,
                source.getPublisher().toString());
        values.put(MeasurementTables.SourceContract.PUBLISHER_TYPE,
                source.getPublisherType());
        values.put(
                MeasurementTables.SourceContract.APP_DESTINATION,
                source.getAppDestination().toString());
        values.put(MeasurementTables.SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
        values.put(MeasurementTables.SourceContract.STATUS, source.getStatus());
        values.put(MeasurementTables.SourceContract.EVENT_TIME, source.getEventTime());
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(MeasurementTables.SourceContract.PRIORITY, source.getPriority());
        values.put(MeasurementTables.SourceContract.REGISTRANT, source.getRegistrant().toString());
        values.put(MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                source.getInstallAttributionWindow());
        values.put(MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                source.getInstallCooldownWindow());
        values.put(MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
                source.isInstallAttributed() ? 1 : 0);
        values.put(MeasurementTables.SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
        values.put(MeasurementTables.SourceContract.FILTER_DATA, source.getAggregateFilterData());
        long row = db.insert(MeasurementTables.SourceContract.TABLE, null, values);
        if (row == -1) {
            throw new SQLiteException("Source insertion failed");
        }
    }

    /**
     * Inserts a Trigger record into the given database.
     */
    private static void insertToDb(Trigger trigger, SQLiteDatabase db)
            throws SQLiteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, trigger.getId());
        values.put(MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                trigger.getAttributionDestination().toString());
        values.put(MeasurementTables.TriggerContract.DESTINATION_TYPE,
                trigger.getDestinationType());
        values.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
        values.put(MeasurementTables.TriggerContract.STATUS, trigger.getStatus());
        values.put(MeasurementTables.TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, trigger.getEventTriggers());
        values.put(MeasurementTables.TriggerContract.REGISTRANT,
                trigger.getRegistrant().toString());
        values.put(MeasurementTables.TriggerContract.FILTERS, trigger.getFilters());
        long row = db.insert(MeasurementTables.TriggerContract.TABLE, null, values);
        if (row == -1) {
            throw new SQLiteException("Trigger insertion failed");
        }
    }

    /**
     * Inserts an EventReport record into the given database.
     */
    private static void insertToDb(EventReport report, SQLiteDatabase db)
            throws SQLiteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID, report.getId());
        values.put(MeasurementTables.EventReportContract.SOURCE_ID, report.getSourceId());
        values.put(MeasurementTables.EventReportContract.ENROLLMENT_ID, report.getEnrollmentId());
        values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                report.getAttributionDestination().toString());
        values.put(MeasurementTables.EventReportContract.REPORT_TIME, report.getReportTime());
        values.put(MeasurementTables.EventReportContract.TRIGGER_DATA, report.getTriggerData());
        values.put(MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                report.getTriggerPriority());
        values.put(MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                report.getTriggerDedupKey());
        values.put(MeasurementTables.EventReportContract.TRIGGER_TIME, report.getTriggerTime());
        values.put(MeasurementTables.EventReportContract.STATUS, report.getStatus());
        values.put(MeasurementTables.EventReportContract.SOURCE_TYPE,
                report.getSourceType().toString());
        values.put(MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                report.getRandomizedTriggerRate());
        long row = db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
        if (row == -1) {
            throw new SQLiteException("EventReport insertion failed");
        }
    }

    /**
     * Inserts an Attribution record into the given database.
     */
    private static void insertToDb(Attribution attribution, SQLiteDatabase db)
            throws SQLiteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AttributionContract.ID, attribution.getId());
        values.put(MeasurementTables.AttributionContract.SOURCE_SITE, attribution.getSourceSite());
        values.put(
                MeasurementTables.AttributionContract.SOURCE_ORIGIN, attribution.getSourceOrigin());
        values.put(MeasurementTables.AttributionContract.DESTINATION_SITE,
                attribution.getDestinationSite());
        values.put(MeasurementTables.AttributionContract.DESTINATION_ORIGIN,
                attribution.getDestinationOrigin());
        values.put(MeasurementTables.AttributionContract.ENROLLMENT_ID,
                attribution.getEnrollmentId());
        values.put(MeasurementTables.AttributionContract.TRIGGER_TIME,
                attribution.getTriggerTime());
        values.put(MeasurementTables.AttributionContract.REGISTRANT,
                attribution.getRegistrant());
        long row = db.insert(MeasurementTables.AttributionContract.TABLE, null, values);
        if (row == -1) {
            throw new SQLiteException("Attribution insertion failed");
        }
    }

    /** Inserts an AggregateReport record into the given database. */
    private static void insertToDb(AggregateReport aggregateReport, SQLiteDatabase db)
            throws SQLiteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
        values.put(
                MeasurementTables.AggregateReport.PUBLISHER,
                aggregateReport.getPublisher().toString());
        values.put(
                MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                aggregateReport.getAttributionDestination().toString());
        values.put(
                MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                aggregateReport.getSourceRegistrationTime());
        values.put(
                MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                aggregateReport.getScheduledReportTime());
        values.put(
                MeasurementTables.AggregateReport.ENROLLMENT_ID,
                aggregateReport.getEnrollmentId());
        values.put(
                MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                aggregateReport.getDebugCleartextPayload());
        values.put(MeasurementTables.AggregateReport.STATUS, aggregateReport.getStatus());
        long row = db.insert(MeasurementTables.AggregateReport.TABLE, null, values);
        if (row == -1) {
            throw new SQLiteException("AggregateReport insertion failed");
        }
    }

    /**
     * Inserts an AggregateEncryptionKey record into the given database.
     */
    private static void insertToDb(AggregateEncryptionKey aggregateEncryptionKey, SQLiteDatabase db)
            throws SQLiteException {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AggregateEncryptionKey.ID, aggregateEncryptionKey.getId());
        values.put(MeasurementTables.AggregateEncryptionKey.KEY_ID,
                aggregateEncryptionKey.getKeyId());
        values.put(MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                aggregateEncryptionKey.getPublicKey());
        values.put(MeasurementTables.AggregateEncryptionKey.EXPIRY,
                aggregateEncryptionKey.getExpiry());
        long row = db.insert(MeasurementTables.AggregateEncryptionKey.TABLE, null, values);
        if (row == -1) {
            throw new SQLiteException("AggregateEncryptionKey insertion failed.");
        }
    }
}
