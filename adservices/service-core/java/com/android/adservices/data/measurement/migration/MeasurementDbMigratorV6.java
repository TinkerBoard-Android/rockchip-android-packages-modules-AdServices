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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;

/** Migrates Measurement DB from user version 3 to 6. */
public class MeasurementDbMigratorV6 extends AbstractMeasurementDbMigrator {
    private static final String[] ALTER_STATEMENTS_VER_6 = {
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEDUP_KEYS,
                MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOW),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW)
    };

    public static final String UPDATE_SOURCE_STATEMENT = String.format(
            "UPDATE %1$s SET %2$s = %3$s, %4$s = %3$s",
            MeasurementTables.SourceContract.TABLE,
            MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
            MeasurementTables.SourceContract.EXPIRY_TIME,
            MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW);

    public MeasurementDbMigratorV6() {
        super(6);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        if (!MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.EVENT_REPORT_WINDOW)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW)) {
            for (String sql : ALTER_STATEMENTS_VER_6) {
                db.execSQL(sql);
            }
            migrateSourceData(db);
        }
    }

    private static void migrateSourceData(SQLiteDatabase db) {
        db.execSQL(UPDATE_SOURCE_STATEMENT);
    }
}