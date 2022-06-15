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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;

public abstract class AbstractMeasurementDbMigratorTestBase {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String DATABASE_NAME_FOR_MIGRATION = "adservices_migration.db";

    private static DbHelper sDbHelper;

    @Mock private SQLiteDatabase mDb;

    @Before
    public void setup() {
        // To force create a fresh db, delete the existing DB file
        File databaseFile = sContext.getDatabasePath(DATABASE_NAME_FOR_MIGRATION);
        if (databaseFile.exists()) {
            databaseFile.delete();
        }

        sDbHelper = null;
    }

    @Test
    public void performMigration_alreadyOnHigherVersion_skipMigration() {
        // Execution
        getTestSubject().performMigration(mDb, (getTargetVersion() + 1), (getTargetVersion() + 2));

        // Verify
        verify(mDb, never()).execSQL(any());
    }

    @Test
    public void performMigration_lowerRequestedVersion_skipMigration() {
        // Execution
        getTestSubject().performMigration(mDb, (getTargetVersion() - 2), (getTargetVersion() - 1));

        // Verify
        verify(mDb, never()).execSQL(any());
    }

    protected DbHelper getDbHelper(int version) {
        synchronized (DbHelper.class) {
            if (sDbHelper == null) {
                sDbHelper = new DbHelper(sContext, DATABASE_NAME_FOR_MIGRATION, version);
            }
            return sDbHelper;
        }
    }

    abstract int getTargetVersion();

    abstract AbstractMeasurementDbMigrator getTestSubject();
}
