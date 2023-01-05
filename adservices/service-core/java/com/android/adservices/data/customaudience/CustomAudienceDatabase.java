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

package com.android.adservices.data.customaudience;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RenameColumn;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.AutoMigrationSpec;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.internal.annotations.GuardedBy;


import java.util.Objects;

/** Room based database for custom audience. */
@Database(
        entities = {
            DBCustomAudience.class,
            DBCustomAudienceBackgroundFetchData.class,
            DBCustomAudienceOverride.class
        },
        version = CustomAudienceDatabase.DATABASE_VERSION,
        autoMigrations = {
            @AutoMigration(from = 1, to = 2, spec = CustomAudienceDatabase.AutoMigration1To2.class)
        })
@TypeConverters({FledgeRoomConverters.class})
public abstract class CustomAudienceDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 2;
    // TODO(b/230653780): Should we separate the DB.
    public static final String DATABASE_NAME = "customaudience.db";

    @RenameColumn(
            tableName = DBCustomAudience.TABLE_NAME,
            fromColumnName = "bidding_logic_url",
            toColumnName = "bidding_logic_uri")
    @RenameColumn(
            tableName = DBCustomAudience.TABLE_NAME,
            fromColumnName = "trusted_bidding_data_url",
            toColumnName = "trusted_bidding_data_uri")
    @RenameColumn(
            tableName = DBCustomAudienceBackgroundFetchData.TABLE_NAME,
            fromColumnName = "daily_update_url",
            toColumnName = "daily_update_uri")
    static class AutoMigration1To2 implements AutoMigrationSpec {}

    @GuardedBy("SINGLETON_LOCK")
    private static CustomAudienceDatabase sSingleton;

    // TODO: How we want handle synchronized situation (b/228101878).

    /** Returns an instance of the AdServiceDatabase given a context. */
    public static CustomAudienceDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        Room.databaseBuilder(context, CustomAudienceDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * Custom Audience Dao.
     *
     * @return Dao to access custom audience storage.
     */
    public abstract CustomAudienceDao customAudienceDao();
}
