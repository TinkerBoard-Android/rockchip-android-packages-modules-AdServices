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

import android.database.Cursor;

import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.function.Function;

/** Helper class for SQLite operations. */
public class SqliteObjectMapper {
    /** Create {@link EnrollmentData} object from SQLite datastore. */
    public static EnrollmentData constructEnrollmentDataFromCursor(Cursor cursor) {
        EnrollmentData.Builder builder = new EnrollmentData.Builder();
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setTextColumn(
                cursor, EnrollmentTables.EnrollmentDataContract.COMPANY_ID, builder::setCompanyId);
        setTextColumn(
                cursor, EnrollmentTables.EnrollmentDataContract.SDK_NAMES, builder::setSdkNames);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                builder::setAttributionSourceRegistrationUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                builder::setAttributionTriggerRegistrationUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                builder::setAttributionReportingUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                builder::setRemarketingResponseBasedRegistrationUrl);
        setTextColumn(
                cursor,
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                builder::setEncryptionKeyUrl);
        return builder.build();
    }

    private static <BuilderType> void setTextColumn(
            Cursor cursor, String column, Function<String, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, setter);
    }

    @SuppressWarnings("ReturnValueIgnored")
    private static <BuilderType, DataType> void setColumnValue(
            Cursor cursor,
            String column,
            Function<Integer, DataType> getColVal,
            Function<DataType, BuilderType> setter) {
        int index = cursor.getColumnIndex(column);
        if (index > -1 && !cursor.isNull(index)) {
            setter.apply(getColVal.apply(index));
        }
    }
}
