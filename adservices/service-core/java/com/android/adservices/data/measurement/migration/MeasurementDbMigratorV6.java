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

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;

/** Migrates Measurement DB from user version 5 to 6. */
public class MeasurementDbMigratorV6 extends AbstractMeasurementDbMigrator {

    private static final String[] ALTER_STATEMENTS_VER_6 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEBUG_REPORTING),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.DEBUG_REPORTING),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AD_ID_PERMISSION),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AR_DEBUG_PERMISSION),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AD_ID_PERMISSION),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION),
    };

    public MeasurementDbMigratorV6() {
        super(6);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        if (!MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.DEBUG_REPORTING)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.DEBUG_REPORTING)) {
            for (String sql : ALTER_STATEMENTS_VER_6) {
                db.execSQL(sql);
            }
        }
        db.execSQL(MeasurementTables.CREATE_TABLE_DEBUG_REPORT_LATEST);
    }
}
