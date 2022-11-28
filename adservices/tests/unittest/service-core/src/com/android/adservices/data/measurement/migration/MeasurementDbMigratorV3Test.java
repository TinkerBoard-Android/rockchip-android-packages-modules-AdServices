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

package com.android.adservices.data.measurement.migration;

import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.WebUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV3Test extends AbstractMeasurementDbMigratorTestBase {

    private static final String[][] INSERTED_EVENT_REPORT_DATA = {
        // id, destination, sourceId
        {"1", "https://example.com", "random one"},
        {"2", "http://will-be-trimmed.example.com", "random two"},
        {"3", "http://example.com/will-be-trimmed", "random three"},
        {"4", "android-app://com.android.app/will-be-trimmed", "random four"},
        {"5", "android-app://com.another.android.app", "random five"},
    };

    private static final String[][] INSERTED_TRIGGER = {
        // id, eventTriggers
        {"1", "[{\"trigger_data\":\"1\"},{},{}]"},
        {"2", "[{\"trigger_data\":\"1\"},{\"priority\":\"100\"}]"},
        {"3", null},
        {"4", "{[]}"},
        {"5", "[}]"}
    };

    private static final String[][] MIGRATED_EVENT_REPORT_DATA = {
        // id, destination, sourceEventId
        {"1", "https://example.com", "random one"},
        {"2", "http://example.com", "random two"},
        {"3", "http://example.com", "random three"},
        {"4", "android-app://com.android.app", "random four"},
        {"5", "android-app://com.another.android.app", "random five"},
    };

    private static final String[][] MIGRATED_TRIGGER = {
        // id, eventTriggers
        {"1", "[{\"trigger_data\":\"1\"},{\"trigger_data\":\"0\"},{\"trigger_data\":\"0\"}]"},
        {"2", "[{\"trigger_data\":\"1\"},{\"priority\":\"100\",\"trigger_data\":\"0\"}]"},
        {"3", "[]"},
        {"4", "[]"},
        {"5", "[]"}
    };

    @Test
    public void performMigration_success_v2ToV3() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);

        insertV2Sources(db);
        insertEventReports(db);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 16));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.AggregateReport.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AttributionContract.TABLE, 10));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
        assertSourceMigration(db);
        assertEventReportMigration(db);
        db.close();
    }

    @Test
    public void performMigration_twiceToSameVersion() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        insertV2Sources(db);
        insertTriggers(db);
        insertEventReports(db);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        // Perform migration again.
        new MeasurementDbMigratorV3().performMigration(db, 3, 3);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 16));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.AggregateReport.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AttributionContract.TABLE, 10));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
        assertSourceMigration(db);
        assertTriggerMigration(db);
        assertEventReportMigration(db);
        db.close();
    }

    @Test
    public void insertAndRetrieveAsyncData_afterV3Migration() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        insertV2Sources(db);
        insertEventReports(db);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Write a new asyncRegistrator row.
        db = dbHelper.getWritableDatabase();
        ContentValues asyncRegistrationRow = getAsyncRegistrationEntry();
        db.insert(MeasurementTables.AsyncRegistrationContract.TABLE, null, asyncRegistrationRow);
        db.close();

        db = dbHelper.getReadableDatabase();
        assertDbContainsAsyncRegistrationValues(db, asyncRegistrationRow);
    }

    @Override
    int getTargetVersion() {
        return 3;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV3();
    }

    private static void insertEventReports(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_EVENT_REPORT_DATA.length; i++) {
            insertEventReport(
                    db,
                    INSERTED_EVENT_REPORT_DATA[i][0],
                    INSERTED_EVENT_REPORT_DATA[i][1],
                    INSERTED_EVENT_REPORT_DATA[i][2]);
        }
    }

    private static void insertTriggers(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_TRIGGER.length; i++) {
            insertTrigger(db, INSERTED_TRIGGER[i][0], INSERTED_TRIGGER[i][1]);
        }
    }

    private static void insertV2Sources(SQLiteDatabase db) {
        // insert a source with valid aggregatable source
        String validAggregatableSourceV2 =
                "[\n"
                        + "              {\n"
                        + "                \"id\": \"campaignCounts\",\n"
                        + "                \"key_piece\": \"0x159\"\n"
                        + "              },\n"
                        + "              {\n"
                        + "                \"id\": \"geoValue\",\n"
                        + "                \"key_piece\": \"0x5\"\n"
                        + "              }\n"
                        + "            ]";
        insertSource(db, "1", validAggregatableSourceV2);

        // insert a source with invalid aggregatable source
        String invalidAggregatableSourceV2 =
                "[\n"
                        + "              {\n"
                        + "                \"id\": \"campaignCounts\",\n"
                        + "                \"key_piece\": \"0x159\"\n"
                        + "              ,\n" // missing closing brace making it invalid
                        + "              {\n"
                        + "                \"id\": \"geoValue\",\n"
                        + "                \"key_piece\": \"0x5\"\n"
                        + "              }\n"
                        + "            ]";
        insertSource(db, "2", invalidAggregatableSourceV2);
    }

    private static void insertSource(
            SQLiteDatabase db, String id, String invalidAggregatableSourceV2) {
        ContentValues invalidValues = new ContentValues();
        invalidValues.put(MeasurementTables.SourceContract.ID, id);
        invalidValues.put(
                MeasurementTables.SourceContract.AGGREGATE_SOURCE, invalidAggregatableSourceV2);

        db.insert(MeasurementTables.SourceContract.TABLE, null, invalidValues);
    }

    private static ContentValues getAsyncRegistrationEntry() {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AsyncRegistrationContract.ID, "id");
        values.put(MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID, "enrollment_id");
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                WebUtil.validUri("https://foo.test/bar?ad=134").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                WebUtil.validUri("https://web-destination.test").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                Uri.parse("android-app://com.ver-app_destination").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                Uri.parse("android-app://com.app_destination").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                Uri.parse("android-app://com.registrant").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                WebUtil.validUri("https://example.test").toString());
        values.put(MeasurementTables.AsyncRegistrationContract.REDIRECT_COUNT, 0);
        values.put(MeasurementTables.AsyncRegistrationContract.REQUEST_TIME, 0L);
        values.put(MeasurementTables.AsyncRegistrationContract.RETRY_COUNT, 0L);
        values.put(MeasurementTables.AsyncRegistrationContract.LAST_PROCESSING_TIME, 0L);
        values.put(
                MeasurementTables.AsyncRegistrationContract.TYPE,
                AsyncRegistration.RegistrationType.APP_SOURCE.ordinal());
        values.put(
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                AsyncRegistration.RegistrationType.APP_SOURCE.ordinal());
        values.put(MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED, 1);
        return values;
    }

    private static void assertDbContainsAsyncRegistrationValues(
            SQLiteDatabase db, ContentValues values) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        /* columns= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ null,
                        /* limit= */ null);
        assertThat(cursor.getCount()).isEqualTo(1);
        assertThat(cursor.moveToNext()).isTrue();
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.ENROLLMENT_ID));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .REGISTRATION_URI)))
                .isEqualTo(
                        values.get(MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .WEB_DESTINATION)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .VERIFIED_DESTINATION)))
                .isEqualTo(
                        values.get(
                                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .OS_DESTINATION)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.OS_DESTINATION));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.REGISTRANT)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.REGISTRANT));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .REDIRECT_COUNT)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.REDIRECT_COUNT));
        assertThat(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.REQUEST_TIME)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.REQUEST_TIME));
        assertThat(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.RETRY_COUNT)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.RETRY_COUNT));
        assertThat(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .LAST_PROCESSING_TIME)))
                .isEqualTo(
                        values.get(
                                MeasurementTables.AsyncRegistrationContract.LAST_PROCESSING_TIME));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.TYPE)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.TYPE));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .DEBUG_KEY_ALLOWED)))
                .isEqualTo(
                        values.get(MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED));
    }

    private static void assertEventReportMigration(SQLiteDatabase db) {
        Cursor cursor = db.query(
                MeasurementTables.EventReportContract.TABLE,
                new String[] {
                    MeasurementTables.EventReportContract.ID,
                    MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                    MeasurementTables.EventReportContract.SOURCE_EVENT_ID
                },
                null, null, null, null,
                /* orderBy */ MeasurementTables.EventReportContract.ID,
                null);
        while (cursor.moveToNext()) {
            assertEventReportMigrated(cursor);
        }
    }

    private static void assertTriggerMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {
                            MeasurementTables.TriggerContract.ID,
                            MeasurementTables.TriggerContract.EVENT_TRIGGERS
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.TriggerContract.ID,
                        null);
        while (cursor.moveToNext()) {
            assertTriggerMigrated(cursor);
        }
    }

    private static void assertSourceMigration(SQLiteDatabase db) throws JSONException {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.SourceContract.ID,
                        null);

        assertEquals(2, cursor.getCount());

        // valid aggregate source case
        assertTrue(cursor.moveToNext());
        String aggregateSourceString1 =
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.AGGREGATE_SOURCE));
        JSONObject aggregateSourceJsonObject = new JSONObject(aggregateSourceString1);

        assertEquals(2, aggregateSourceJsonObject.length());
        assertEquals("0x159", aggregateSourceJsonObject.getString("campaignCounts"));
        assertEquals("0x5", aggregateSourceJsonObject.getString("geoValue"));

        // invalid aggregate source case
        assertTrue(cursor.moveToNext());
        String aggregateSourceString2 =
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.AGGREGATE_SOURCE));
        assertNull(aggregateSourceString2);
    }

    private static void insertEventReport(
            SQLiteDatabase db, String id, String destination, String sourceId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID, id);
        values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION, destination);
        values.put(MeasurementTables.EventReportContract.SOURCE_ID, sourceId);
        db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
    }

    private static void insertTrigger(SQLiteDatabase db, String id, String eventTriggers) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, id);
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, eventTriggers);
        db.insert(MeasurementTables.TriggerContract.TABLE, null, values);
    }

    private static void assertEventReportMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(MIGRATED_EVENT_REPORT_DATA[i][0], cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ID)));
        assertEquals(MIGRATED_EVENT_REPORT_DATA[i][1], cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION)));
        assertEquals(MIGRATED_EVENT_REPORT_DATA[i][2], cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID)));
    }

    private static void assertTriggerMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_TRIGGER[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
        assertEquals(
                MIGRATED_TRIGGER[i][1],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.EVENT_TRIGGERS)));
    }
}
