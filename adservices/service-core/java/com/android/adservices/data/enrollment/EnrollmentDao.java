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

package com.android.adservices.data.enrollment;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Data Access Object for the EnrollmentData. */
public class EnrollmentDao implements IEnrollmentDao {

    private static EnrollmentDao sSingleton;
    private final DbHelper mDbHelper;
    private final Context mContext;
    @VisibleForTesting static final String ENROLLMENT_SHARED_PREF = "adservices_enrollment";
    @VisibleForTesting static final String IS_SEEDED = "is_seeded";

    @VisibleForTesting
    public EnrollmentDao(Context context, DbHelper dbHelper) {
        mContext = context;
        mDbHelper = dbHelper;
        // TODO: move this to be called when the enrollment download job is scheduled.
        seed();
    }

    /** Returns an instance of the EnrollmentDao given a context. */
    @NonNull
    public static EnrollmentDao getInstance(@NonNull Context context) {
        synchronized (EnrollmentDao.class) {
            if (sSingleton == null) {
                sSingleton = new EnrollmentDao(context, DbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    @VisibleForTesting
    boolean isSeeded() {
        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        return prefs.getBoolean(IS_SEEDED, false);
    }

    private void seed() {
        if (!isSeeded()) {
            boolean success = true;
            for (EnrollmentData enrollment : PreEnrolledAdTechForTest.getList()) {
                success = success && insert(enrollment);
            }

            if (success) {
                SharedPreferences prefs =
                        mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean(IS_SEEDED, true);
                edit.apply();
            }
        }
    }

    private void unSeed() {
        SharedPreferences prefs =
                mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(IS_SEEDED, false);
        edit.apply();
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentData(String enrollmentId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID + " = ? ",
                        new String[] {enrollmentId},
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataFromMeasurementUrl(String url) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL
                                + " LIKE '%"
                                + url
                                + "%' OR "
                                + EnrollmentTables.EnrollmentDataContract
                                        .ATTRIBUTION_TRIGGER_REGISTRATION_URL
                                + " LIKE '%"
                                + url
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataForFledgeByAdTechIdentifier(
            AdTechIdentifier adTechIdentifier) {
        String adTechIdentifierString = adTechIdentifier.toString();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract
                                        .REMARKETING_RESPONSE_BASED_REGISTRATION_URL
                                + " LIKE '%"
                                + adTechIdentifierString
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() != 1) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    @Nullable
    public EnrollmentData getEnrollmentDataFromSdkName(String sdkName) {
        if (sdkName.contains(" ") || sdkName.contains(",")) {
            return null;
        }
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        EnrollmentTables.EnrollmentDataContract.SDK_NAMES
                                + " LIKE '%"
                                + sdkName
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    @Override
    public boolean insert(EnrollmentData enrollmentData) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put(
                EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID,
                enrollmentData.getEnrollmentId());
        values.put(
                EnrollmentTables.EnrollmentDataContract.COMPANY_ID, enrollmentData.getCompanyId());
        values.put(
                EnrollmentTables.EnrollmentDataContract.SDK_NAMES,
                String.join(" ", enrollmentData.getSdkNames()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionSourceRegistrationUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionTriggerRegistrationUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                String.join(" ", enrollmentData.getAttributionReportingUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                String.join(" ", enrollmentData.getRemarketingResponseBasedRegistrationUrl()));
        values.put(
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                String.join(" ", enrollmentData.getEncryptionKeyUrl()));
        try {
            db.insert(
                    EnrollmentTables.EnrollmentDataContract.TABLE,
                    /*nullColumnHack=*/ null,
                    values);
        } catch (SQLException e) {
            LogUtil.e("Failed to insert EnrollmentData. Exception : " + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean delete(String enrollmentId) {
        Objects.requireNonNull(enrollmentId);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        try {
            db.delete(
                    EnrollmentTables.EnrollmentDataContract.TABLE,
                    EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID + " = ?",
                    new String[] {enrollmentId});
        } catch (SQLException e) {
            LogUtil.e("Failed to delete EnrollmentData." + e.getMessage());
            return false;
        }
        return true;
    }

    /** Deletes the whole EnrollmentData table. */
    @Override
    public boolean deleteAll() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }

        boolean success = false;
        // Handle this in a transaction.
        db.beginTransaction();
        try {
            db.delete(EnrollmentTables.EnrollmentDataContract.TABLE, null, null);
            success = true;
            unSeed();
            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return success;
    }
}
