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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link android.adservices.adselection.ReportImpressionRequest} */
@SmallTest
public final class ReportImpressionResponseTest {

    @Test
    public void testWriteToParcel() throws Exception {
        String notImplementedMessage = "Not Implemented!";
        ReportImpressionResponse response =
                new ReportImpressionResponse.Builder()
                        .setResultCode(ReportImpressionResponse.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportImpressionResponse fromParcel = ReportImpressionResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getResultCode())
                .isEqualTo(ReportImpressionResponse.STATUS_INTERNAL_ERROR);
        assertThat(fromParcel.getErrorMessage()).isEqualTo(notImplementedMessage);
    }

    @Test
    public void testWriteToParcelEmptyMessage() throws Exception {
        ReportImpressionResponse response =
                new ReportImpressionResponse.Builder()
                        .setResultCode(ReportImpressionResponse.STATUS_OK)
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportImpressionResponse fromParcel = ReportImpressionResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.isSuccess()).isTrue();
        assertThat(fromParcel.getErrorMessage()).isNull();
    }

    @Test
    public void testFailsForEmptyMessageWithNotOkStatus() {

        assertThrows(
                NullPointerException.class,
                () -> {
                        new ReportImpressionResponse.Builder()
                                .setResultCode(ReportImpressionResponse.STATUS_INTERNAL_ERROR)
                                // Not setting error message making it null.
                                .build();
                });
    }

    @Test
    public void testFailsForNotSetStatus() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                        new ReportImpressionResponse.Builder()
                                .setErrorMessage("Status not set!")
                                // Not setting status code making it -1.
                                .build();
                });
    }
}
