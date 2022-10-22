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

package com.android.adservices.service.measurement;

import static com.android.adservices.ResultCode.RESULT_OK;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.HpkeJni;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.AggregateReportingJob;
import com.android.adservices.service.measurement.actions.EventReportingJob;
import com.android.adservices.service.measurement.actions.InstallApp;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.RegisterWebSource;
import com.android.adservices.service.measurement.actions.RegisterWebTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.UninstallApp;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobHandlerWrapper;
import com.android.adservices.service.measurement.reporting.EventReportingJobHandlerWrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * <p>Consider @RunWith(Parameterized.class)
 */
public abstract class E2EMockTest extends E2ETest {

    static final EnrollmentDao sEnrollmentDao = EnrollmentDao.getInstance(
            ApplicationProvider.getApplicationContext());
    static final DatastoreManager sDatastoreManager = DatastoreManagerFactory.getDatastoreManager(
            ApplicationProvider.getApplicationContext());

    // Class extensions may choose to disable or enable added noise.
    AttributionJobHandlerWrapper mAttributionHelper;
    MeasurementImpl mMeasurementImpl;
    SourceFetcher mSourceFetcher;
    TriggerFetcher mTriggerFetcher;
    ClickVerifier mClickVerifier;
    Flags mFlags;

    private final AtomicInteger mEnrollmentCount = new AtomicInteger();
    private final Set<String> mSeenUris = new HashSet<>();
    private final Map<String, String> mUriToEnrollmentId = new HashMap<>();

    @Rule
    public final E2EMockStatic.E2EMockStaticRule mE2EMockStaticRule;

    E2EMockTest(Collection<Action> actions, ReportObjects expectedOutput,
            PrivacyParamsProvider privacyParamsProvider, String name) {
        super(actions, expectedOutput, name);
        mSourceFetcher = Mockito.spy(new SourceFetcher(sContext));
        mTriggerFetcher = Mockito.spy(new TriggerFetcher(sContext));
        mClickVerifier = Mockito.mock(ClickVerifier.class);
        mFlags = FlagsFactory.getFlagsForTest();
        when(mClickVerifier.isInputEventVerifiable(any(), anyLong())).thenReturn(true);
        mE2EMockStaticRule = new E2EMockStatic.E2EMockStaticRule(privacyParamsProvider);
    }

    @Override
    void prepareRegistrationServer(RegisterSource sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mSourceFetcher).openUrl(new URL(uri));
        }
    }

    @Override
    void prepareRegistrationServer(RegisterTrigger triggerRegistration)
            throws IOException {
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation ->
                            getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mTriggerFetcher).openUrl(new URL(uri));
        }
    }

    @Override
    void prepareRegistrationServer(RegisterWebSource sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mSourceFetcher).openUrl(new URL(uri));
        }
    }

    @Override
    void prepareRegistrationServer(RegisterWebTrigger triggerRegistration) throws IOException {
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            updateEnrollment(uri);
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation ->
                            getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mTriggerFetcher).openUrl(new URL(uri));
        }
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws IOException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.register source failed",
                RESULT_OK,
                mMeasurementImpl.register(
                        sourceRegistration.mRegistrationRequest, sourceRegistration.mTimestamp));
    }

    @Override
    void processAction(RegisterWebSource sourceRegistration) throws IOException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebSource failed",
                RESULT_OK,
                mMeasurementImpl.registerWebSource(
                        sourceRegistration.mRegistrationRequest, sourceRegistration.mTimestamp));
    }

    @Override
    void processAction(RegisterTrigger triggerRegistration) throws IOException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertEquals(
                "MeasurementImpl.register trigger failed",
                RESULT_OK,
                mMeasurementImpl.register(
                        triggerRegistration.mRegistrationRequest, triggerRegistration.mTimestamp));
        Assert.assertTrue("AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
    }

    @Override
    void processAction(RegisterWebTrigger triggerRegistration) throws IOException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebTrigger failed",
                RESULT_OK,
                mMeasurementImpl.registerWebTrigger(
                        triggerRegistration.mRegistrationRequest, triggerRegistration.mTimestamp));
        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
    }

    @Override
    void processAction(InstallApp installApp) {
        Assert.assertTrue(
                "measurementDao.doInstallAttribution failed",
                sDatastoreManager.runInTransaction(
                        measurementDao ->
                                measurementDao.doInstallAttribution(
                                        installApp.mUri, installApp.mTimestamp)));
    }

    @Override
    void processAction(UninstallApp uninstallApp) {
        Assert.assertTrue("measurementDao.undoInstallAttribution failed",
                sDatastoreManager.runInTransaction(
                    measurementDao -> {
                        measurementDao.deleteAppRecords(uninstallApp.mUri);
                        measurementDao.undoInstallAttribution(uninstallApp.mUri);
                    }));
    }

    @Override
    void processAction(EventReportingJob reportingJob) throws IOException, JSONException {
        Object[] eventCaptures = EventReportingJobHandlerWrapper
                .spyPerformScheduledPendingReportsInWindow(
                        sEnrollmentDao,
                        sDatastoreManager,
                        reportingJob.mTimestamp
                                - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                        reportingJob.mTimestamp);

        processEventReports(
                (List<EventReport>) eventCaptures[0],
                (List<Uri>) eventCaptures[1],
                (List<JSONObject>) eventCaptures[2]);
    }

    @Override
    void processAction(AggregateReportingJob reportingJob) throws IOException, JSONException {
        Object[] aggregateCaptures = AggregateReportingJobHandlerWrapper
                .spyPerformScheduledPendingReportsInWindow(
                        sEnrollmentDao,
                        sDatastoreManager,
                        reportingJob.mTimestamp
                                - SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                        reportingJob.mTimestamp);

        processAggregateReports(
                (List<Uri>) aggregateCaptures[0],
                (List<JSONObject>) aggregateCaptures[1]);
    }

    // Class extensions may need different processing to prepare for result evaluation.
    void processEventReports(List<EventReport> eventReports, List<Uri> destinations,
            List<JSONObject> payloads) throws JSONException {
        List<JSONObject> eventReportObjects =
                getEventReportObjects(eventReports, destinations, payloads);
        mActualOutput.mEventReportObjects.addAll(eventReportObjects);
    }

    private List<JSONObject> getEventReportObjects(
            List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            Map<String, Object> map = new HashMap<>();
            map.put(TestFormatJsonMapping.REPORT_TIME_KEY, eventReports.get(i).getReportTime());
            map.put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString());
            map.put(TestFormatJsonMapping.PAYLOAD_KEY, payloads.get(i));
            result.add(new JSONObject(map));
        }
        return result;
    }

    // Class extensions may need different processing to prepare for result evaluation.
    void processAggregateReports(List<Uri> destinations, List<JSONObject> payloads)
            throws JSONException {
        List<JSONObject> aggregateReportObjects = getAggregateReportObjects(destinations, payloads);
        mActualOutput.mAggregateReportObjects.addAll(aggregateReportObjects);
    }

    private List<JSONObject> getAggregateReportObjects(List<Uri> destinations,
            List<JSONObject> payloads) throws JSONException {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            JSONObject sharedInfo = new JSONObject(payloads.get(i).getString("shared_info"));
            result.add(new JSONObject()
                    .put(TestFormatJsonMapping.REPORT_TIME_KEY, String.valueOf(
                            sharedInfo.getLong("scheduled_report_time") * 1000))
                    .put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString())
                    .put(TestFormatJsonMapping.PAYLOAD_KEY,
                            getAggregatablePayloadForTest(sharedInfo, payloads.get(i))));
        }
        return result;
    }

    private static JSONObject getAggregatablePayloadForTest(
            JSONObject sharedInfo, JSONObject data) throws JSONException {
        String payload =
                data.getJSONArray("aggregation_service_payloads")
                        .getJSONObject(0)
                        .getString("payload");

        final byte[] decryptedPayload =
                HpkeJni.decrypt(
                        decode(AggregateCryptoFixture.getPrivateKeyBase64()),
                        decode(payload),
                        (AggregateCryptoFixture.getSharedInfoPrefix() + sharedInfo.toString())
                                .getBytes());

        String sourceDebugKey = data.optString(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY);
        String triggerDebugKey = data.optString(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY);
        JSONObject aggregateJson =
                new JSONObject()
                        .put(
                                AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION,
                                sharedInfo.getString("attribution_destination"))
                        .put(
                                AggregateReportPayloadKeys.HISTOGRAMS,
                                getAggregateHistograms(decryptedPayload));
        if (!sourceDebugKey.isEmpty()) {
            aggregateJson.put(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY, sourceDebugKey);
        }
        if (!triggerDebugKey.isEmpty()) {
            aggregateJson.put(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY, triggerDebugKey);
        }
        return aggregateJson;
    }

    private static JSONArray getAggregateHistograms(byte[] encodedCborPayload)
            throws JSONException {
        List<JSONObject> result = new ArrayList<>();

        try {
            final List<DataItem> dataItems =
                    new CborDecoder(new ByteArrayInputStream(encodedCborPayload)).decode();
            final co.nstant.in.cbor.model.Map payload =
                    (co.nstant.in.cbor.model.Map) dataItems.get(0);
            final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
            for (DataItem i : payloadArray.getDataItems()) {
                co.nstant.in.cbor.model.Map m = (co.nstant.in.cbor.model.Map) i;
                result.add(
                        new JSONObject()
                                .put(
                                        AggregateHistogramKeys.BUCKET,
                                        "0x" + new BigInteger(
                                                        ((ByteString)
                                                                        m.get(
                                                                                new UnicodeString(
                                                                                        "bucket")))
                                                                .getBytes())
                                                .toString(16))
                                .put(
                                        AggregateHistogramKeys.VALUE,
                                        new BigInteger(
                                                        ((ByteString)
                                                                        m.get(
                                                                                new UnicodeString(
                                                                                        "value")))
                                                                .getBytes())
                                                .intValue()));
            }
        } catch (CborException e) {
            throw new JSONException(e);
        }

        return new JSONArray(result);
    }

    protected static Map<String, List<String>> getNextResponse(
            Map<String, List<Map<String, List<String>>>> uriToResponseHeadersMap, String uri) {
        List<Map<String, List<String>>> responseList = uriToResponseHeadersMap.get(uri);
        return responseList.remove(0);
    }

    void updateEnrollment(String uri) {
        if (mSeenUris.contains(uri)) {
            return;
        }
        mSeenUris.add(uri);
        String enrollmentId = getEnrollmentId(uri);
        Set<String> attributionRegistrationUrls;
        EnrollmentData enrollmentData = sEnrollmentDao.getEnrollmentData(enrollmentId);
        if (enrollmentData != null) {
            sEnrollmentDao.delete(enrollmentId);
            attributionRegistrationUrls = new HashSet<>(
                    enrollmentData.getAttributionSourceRegistrationUrl());
            attributionRegistrationUrls.addAll(
                    enrollmentData.getAttributionTriggerRegistrationUrl());
            attributionRegistrationUrls.add(uri);
        } else {
            attributionRegistrationUrls = Set.of(uri);
        }
        Uri registrationUri = Uri.parse(uri);
        String reportingUrl = registrationUri.getScheme() + "://" + registrationUri.getAuthority();
        insertEnrollment(enrollmentId, reportingUrl, new ArrayList<>(attributionRegistrationUrls));
    }

    private void insertEnrollment(String enrollmentId, String reportingUrl,
            List<String> attributionRegistrationUrls) {
        EnrollmentData enrollmentData = new EnrollmentData.Builder()
                .setEnrollmentId(enrollmentId)
                .setAttributionSourceRegistrationUrl(attributionRegistrationUrls)
                .setAttributionTriggerRegistrationUrl(attributionRegistrationUrls)
                .setAttributionReportingUrl(List.of(reportingUrl))
                .build();
        Assert.assertTrue(sEnrollmentDao.insert(enrollmentData));
    }

    private String getEnrollmentId(String uri) {
        String authority = Uri.parse(uri).getAuthority();
        return mUriToEnrollmentId.computeIfAbsent(authority, k ->
                "enrollment-id-" + mEnrollmentCount.incrementAndGet());
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value.getBytes());
    }
}
