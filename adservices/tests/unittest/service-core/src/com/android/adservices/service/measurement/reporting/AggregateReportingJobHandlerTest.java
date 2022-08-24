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

package com.android.adservices.service.measurement.reporting;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.aggregation.AggregateReport;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit test for {@link AggregateReportingJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class AggregateReportingJobHandlerTest {

    private static final String CLEARTEXT_PAYLOAD =
            "{\"operation\":\"histogram\",\"data\":[{\"bucket\":1,\"value\":2}]}";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    DatastoreManager mDatastoreManager;

    @Mock IMeasurementDao mMeasurementDao;

    @Mock ITransaction mTransaction;

    AggregateReportingJobHandler mAggregateReportingJobHandler;
    AggregateReportingJobHandler mSpyAggregateReportingJobHandler;

    class FakeDatasoreManager extends DatastoreManager {

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }
    }

    @Before
    public void setUp() {
        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        ArgumentCaptor<Integer> captorNumberOfKeys = ArgumentCaptor.forClass(Integer.class);
        when(mockKeyManager.getAggregateEncryptionKeys(captorNumberOfKeys.capture()))
                .thenAnswer(
                        invocation -> {
                            List<AggregateEncryptionKey> keys = new ArrayList<>();
                            for (int i = 0; i < captorNumberOfKeys.getValue(); i++) {
                                keys.add(AggregateCryptoFixture.getKey());
                            }
                            return keys;
                        });
        mDatastoreManager = new FakeDatasoreManager();
        mAggregateReportingJobHandler =
                new AggregateReportingJobHandler(mDatastoreManager, mockKeyManager);
        mSpyAggregateReportingJobHandler = Mockito.spy(mAggregateReportingJobHandler);
    }

    @Test
    public void testSendReportForPendingReportSuccess()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();
        JSONObject aggregateReportBody =
                new AggregateReportBody.Builder()
                        .setReportId(aggregateReport.getId())
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .build()
                        .toJson(AggregateCryptoFixture.getKey());

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), Mockito.any());

        doNothing().when(mMeasurementDao).markAggregateReportDelivered(aggregateReport.getId());
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_SUCCESS,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(), AggregateCryptoFixture.getKey()));

        verify(mMeasurementDao, times(1)).markAggregateReportDelivered(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testSendReportForPendingReportFailure()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .build();
        JSONObject aggregateReportBody =
                new AggregateReportBody.Builder()
                        .setReportId(aggregateReport.getId())
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .build()
                        .toJson(AggregateCryptoFixture.getKey());

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_BAD_REQUEST)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(Mockito.any(), Mockito.any());

        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_IO_ERROR,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(), AggregateCryptoFixture.getKey()));

        verify(mMeasurementDao, never()).markAggregateReportDelivered(any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testSendReportForAlreadyDeliveredReport() throws DatastoreException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId")
                        .setStatus(AggregateReport.Status.DELIVERED)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .build();

        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        Assert.assertEquals(
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT,
                mSpyAggregateReportingJobHandler.performReport(
                        aggregateReport.getId(), AggregateCryptoFixture.getKey()));

        verify(mMeasurementDao, never()).markAggregateReportDelivered(any());
        verify(mTransaction, times(1)).begin();
        verify(mTransaction, times(1)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsForMultipleReports()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport1 =
                new AggregateReport.Builder()
                        .setId("aggregateReportId1")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .setScheduledReportTime(1000L)
                        .build();
        JSONObject aggregateReportBody1 =
                new AggregateReportBody.Builder()
                        .setReportId(aggregateReport1.getId())
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .build()
                        .toJson(AggregateCryptoFixture.getKey());
        AggregateReport aggregateReport2 =
                new AggregateReport.Builder()
                        .setId("aggregateReportId2")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .setScheduledReportTime(1100L)
                        .build();
        JSONObject aggregateReportBody2 =
                new AggregateReportBody.Builder()
                        .setReportId(aggregateReport2.getId())
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .build()
                        .toJson(AggregateCryptoFixture.getKey());

        when(mMeasurementDao.getPendingAggregateReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(aggregateReport1.getId(), aggregateReport2.getId()));
        when(mMeasurementDao.getAggregateReport(aggregateReport1.getId()))
                .thenReturn(aggregateReport1);
        when(mMeasurementDao.getAggregateReport(aggregateReport2.getId()))
                .thenReturn(aggregateReport2);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        doReturn(aggregateReportBody1)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(aggregateReport1, AggregateCryptoFixture.getKey());
        doReturn(aggregateReportBody2)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(aggregateReport2, AggregateCryptoFixture.getKey());

        Assert.assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        verify(mMeasurementDao, times(2)).markAggregateReportDelivered(any());
        verify(mTransaction, times(5)).begin();
        verify(mTransaction, times(5)).end();
    }

    @Test
    public void testPerformScheduledPendingReportsInWindow_noKeys()
            throws DatastoreException, IOException, JSONException {
        AggregateReport aggregateReport =
                new AggregateReport.Builder()
                        .setId("aggregateReportId1")
                        .setStatus(AggregateReport.Status.PENDING)
                        .setAdTechDomain(Uri.parse("https://adtech.domain"))
                        .setScheduledReportTime(1000L)
                        .build();
        JSONObject aggregateReportBody =
                new AggregateReportBody.Builder()
                        .setReportId(aggregateReport.getId())
                        .setDebugCleartextPayload(CLEARTEXT_PAYLOAD)
                        .build()
                        .toJson(AggregateCryptoFixture.getKey());

        when(mMeasurementDao.getPendingAggregateReportIdsInWindow(1000, 1100))
                .thenReturn(List.of(aggregateReport.getId()));
        when(mMeasurementDao.getAggregateReport(aggregateReport.getId()))
                .thenReturn(aggregateReport);
        doReturn(HttpURLConnection.HTTP_OK)
                .when(mSpyAggregateReportingJobHandler)
                .makeHttpPostRequest(Mockito.any(), Mockito.any());
        doReturn(aggregateReportBody)
                .when(mSpyAggregateReportingJobHandler)
                .createReportJsonPayload(aggregateReport, AggregateCryptoFixture.getKey());

        AggregateEncryptionKeyManager mockKeyManager = mock(AggregateEncryptionKeyManager.class);
        when(mockKeyManager.getAggregateEncryptionKeys(anyInt()))
                .thenReturn(Collections.emptyList());
        mAggregateReportingJobHandler =
                new AggregateReportingJobHandler(new FakeDatasoreManager(), mockKeyManager);
        mSpyAggregateReportingJobHandler = Mockito.spy(mAggregateReportingJobHandler);

        Assert.assertTrue(
                mSpyAggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                        1000, 1100));

        verify(mMeasurementDao, never()).markAggregateReportDelivered(any());
    }
}
