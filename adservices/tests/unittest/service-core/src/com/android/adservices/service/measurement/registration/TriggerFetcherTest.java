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
package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATABLE_TRIGGER_DATA;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_ATTRIBUTION_EVENT_TRIGGER_DATA;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_ATTRIBUTION_FILTERS;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_VALUES_PER_ATTRIBUTION_FILTER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.Context;
import android.net.Uri;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;

/** Unit tests for {@link TriggerFetcher} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class TriggerFetcherTest {
    private static final String TRIGGER_URI = "https://foo.com";
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final EnrollmentData ENROLLMENT = new EnrollmentData.Builder()
            .setEnrollmentId("enrollment-id")
            .build();
    private static final String TOP_ORIGIN = "https://baz.com";
    private static final long TRIGGER_DATA = 7;
    private static final long PRIORITY = 1;
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
    private static final long DEDUP_KEY = 100;
    private static final Long DEBUG_KEY = 34787843L;

    private static final String DEFAULT_REDIRECT = "https://bar.com";

    private static final String EVENT_TRIGGERS_1 =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + TRIGGER_DATA
                    + "\",\n"
                    + "  \"priority\": \""
                    + PRIORITY
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + DEDUP_KEY
                    + "\",\n"
                    + "  \"filters\": {\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }\n"
                    + "}"
                    + "]\n";
    private static final String EVENT_TRIGGERS_2 =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + 11
                    + "\",\n"
                    + "  \"priority\": \""
                    + 21
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + 31
                    + "\"}"
                    + "]\n";

    private static final String ALT_REGISTRATION = "https://bar.com";
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final WebTriggerParams TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebTriggerParams TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();

    TriggerFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;
    @Mock HttpsURLConnection mUrlConnection1;
    @Mock HttpsURLConnection mUrlConnection2;
    @Mock EnrollmentDao mEnrollmentDao;
    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;

    @Before
    public void setup() {
        mFetcher = spy(new TriggerFetcher(mEnrollmentDao, mFlags, mLogger));
        // For convenience, return the same enrollment-ID since we're using many arbitrary
        // registration URIs and not yet enforcing uniqueness of enrollment.
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(ENROLLMENT);
    }

    @Test
    public void testBasicTriggerRequest() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        MeasurementRegistrationResponseStats expectedStats =
                new MeasurementRegistrationResponseStats.Builder(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                                221)
                        .setAdTechDomain(null)
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.get(0).getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }

    @Test
    public void testTriggerRequest_eventTriggerData_tooManyEntries() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        StringBuilder tooManyEntries = new StringBuilder("[");
        for (int i = 0; i < MAX_ATTRIBUTION_EVENT_TRIGGER_DATA + 1; i++) {
            tooManyEntries.append("{\"trigger_data\": \"2\",\"priority\": \"101\"}");
        }
        tooManyEntries.append("]");
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + tooManyEntries + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequest_failsWhenNotEnrolled() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithDebugKey() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "'event_trigger_data': "
                                + EVENT_TRIGGERS_1
                                + ", 'debug_key': '"
                                + DEBUG_KEY
                                + "'"
                                + "}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.get(0).getEventTriggers());
        assertEquals(DEBUG_KEY, result.get(0).getDebugKey());

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithoutAdIdPermission() throws Exception {
        RegistrationRequest request = buildRequestWithoutAdIdPermission(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "'event_trigger_data': "
                                + EVENT_TRIGGERS_1
                                + ", 'debug_key': '"
                                + DEBUG_KEY
                                + "'"
                                + "}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.get(0).getEventTriggers());
        assertNull(result.get(0).getDebugKey());

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadTriggerUrl() throws Exception {
        RegistrationRequest request =
                buildRequest("bad-schema://foo.com", TOP_ORIGIN);
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadTriggerConnection() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher).openUrl(new URL(TRIGGER_URI));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, never()).setRequestMethod("POST");
    }

    @Test
    public void testBadRequestReturnFailure() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(400);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data': " + "[{}]" + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals("[{}]", result.get(0).getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = buildRequest("http://foo.com", TOP_ORIGIN);
        // Non-https should fail.
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(DEFAULT_REDIRECT));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);

        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.get(0).getEventTriggers());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Redirect", List.of(DEFAULT_REDIRECT)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(
                new JSONArray(aggregatable_trigger_data).toString(),
                result.get(0).getAggregateTriggerData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_tooManyEntries() throws Exception {
        StringBuilder tooManyEntries = new StringBuilder("[");
        for (int i = 0; i < MAX_AGGREGATABLE_TRIGGER_DATA + 1; i++) {
            tooManyEntries.append(String.format(
                    "{\"key_piece\": \"0x15%1$s\",\"source_keys\":[\"campaign-%1$s\"]}", i));
        }
        tooManyEntries.append("]");
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + tooManyEntries
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_missingPrefix()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_tooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"" + LONG_AGGREGATE_KEY_PIECE
                        + "\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_notAnArray()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":{\"campaignCounts\": true},"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_tooManyKeys()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        StringBuilder tooManyKeys = new StringBuilder("[");
        tooManyKeys.append(IntStream.range(0, MAX_AGGREGATE_KEYS_PER_REGISTRATION + 1)
                .mapToObj(i -> "aggregate-key-" + i)
                .collect(Collectors.joining(",")));
        tooManyKeys.append("]");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\": " + tooManyKeys + ","
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_invalidKeyId()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\", \""
                        + LONG_AGGREGATE_KEY_ID + "\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_tooManyFilters()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        StringBuilder filters = new StringBuilder("{");
        filters.append(IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                .collect(Collectors.joining(",")));
        filters.append("}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": " + filters + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": " + filters + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_tooManyValues()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        StringBuilder filters = new StringBuilder("{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [");
        filters.append(IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                .mapToObj(i -> "\"filter-value-" + i + "\"")
                .collect(Collectors.joining(",")));
        filters.append("]}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": " + filters + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_valueTooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": " + filters + ","
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyFilters()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        StringBuilder filters = new StringBuilder("{");
        filters.append(IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                .collect(Collectors.joining(",")));
        filters.append("}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": " + filters + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_keyTooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": " + filters + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyValues()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        StringBuilder filters = new StringBuilder("{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [");
        filters.append(IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                .mapToObj(i -> "\"filter-value-" + i + "\"")
                .collect(Collectors.joining(",")));
        filters.append("]}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": " + filters + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_valueTooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": " + filters + ","
                        + "\"filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + aggregatable_trigger_data
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithAggregatableValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_values = "{\"campaignCounts\":32768,\"geoValue\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_values': "
                                                + aggregatable_values
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(
                new JSONObject(aggregatable_values).toString(), result.get(0).getAggregateValues());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testTriggerRequestWithAggregatableValues_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_values = "{\"campaignCounts\":32768\"geoValue\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_values': "
                                                + aggregatable_values
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregatableValues_tooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        tooManyKeys.append(IntStream.range(0, MAX_AGGREGATE_KEYS_PER_REGISTRATION + 1)
                .mapToObj(i -> String.format("\"key-%s\": 12345,", i))
                .collect(Collectors.joining(",")));
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'aggregatable_values': " + tooManyKeys + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregatableValues_invalidKeyId() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        String aggregatable_values = "{\"campaignCounts\":32768, \"" + LONG_AGGREGATE_KEY_ID
                + "\":1644}";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_values': "
                                                + aggregatable_values
                                                + "}")));
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchTrigger_withReportingFilters_success() throws IOException, JSONException {
        // Setup
        String filters =
                "{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}";
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'filters': " + filters + "}")));

        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(new JSONObject(filters).toString(), result.get(0).getFilters());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_basic_success() throws IOException, JSONException {
        // Setup
        TriggerRegistration expectedResult1 =
                new TriggerRegistration.Builder()
                        .setTopOrigin(Uri.parse(TOP_ORIGIN))
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setDebugKey(DEBUG_KEY)
                        .build();
        TriggerRegistration expectedResult2 =
                new TriggerRegistration.Builder()
                        .setTopOrigin(Uri.parse(TOP_ORIGIN))
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_2).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1, TRIGGER_REGISTRATION_2), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        doReturn(mUrlConnection2).when(mFetcher).openUrl(new URL(REGISTRATION_URI_2.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection2.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "'event_trigger_data': "
                                + EVENT_TRIGGERS_1
                                + ", 'debug_key': '"
                                + DEBUG_KEY
                                + "'"
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        when(mUrlConnection2.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data': " + EVENT_TRIGGERS_2 + "}")));

        // Execution
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchWebTriggers(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(2, result.size());
        assertEquals(
                new HashSet<>(Arrays.asList(expectedResult1, expectedResult2)),
                new HashSet<>(result));
        verify(mUrlConnection1).setRequestMethod("POST");
        verify(mUrlConnection2).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggerSuccessWithoutAdIdPermission() throws IOException, JSONException {
        // Setup
        TriggerRegistration expectedResult1 =
                new TriggerRegistration.Builder()
                        .setTopOrigin(Uri.parse(TOP_ORIGIN))
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();
        TriggerRegistration expectedResult2 =
                new TriggerRegistration.Builder()
                        .setTopOrigin(Uri.parse(TOP_ORIGIN))
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_2).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1, TRIGGER_REGISTRATION_2), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        doReturn(mUrlConnection2).when(mFetcher).openUrl(new URL(REGISTRATION_URI_2.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection2.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{"
                                + "'event_trigger_data': "
                                + EVENT_TRIGGERS_1
                                + ", 'debug_key': '"
                                + DEBUG_KEY
                                + "'"
                                + "}"));
        when(mUrlConnection1.getHeaderFields()).thenReturn(headersRequest);
        when(mUrlConnection2.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data': " + EVENT_TRIGGERS_2 + "}")));

        // Execution
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchWebTriggers(request, false);

        // Assertion
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(2, result.size());
        assertEquals(
                new HashSet<>(Arrays.asList(expectedResult1, expectedResult2)),
                new HashSet<>(result));
        verify(mUrlConnection1).setRequestMethod("POST");
        verify(mUrlConnection2).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withExtendedHeaders_success() throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Collections.singletonList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregatableValues = "{\"campaignCounts\":32768,\"geoValue\":1644}";
        String filters =
                "{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}";
        String aggregatableTriggerData =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'event_trigger_data': "
                                                + EVENT_TRIGGERS_1
                                                + ", 'filters': "
                                                + filters
                                                + ", 'aggregatable_values': "
                                                + aggregatableValues
                                                + ", 'aggregatable_trigger_data': "
                                                + aggregatableTriggerData
                                                + "}")));
        TriggerRegistration expectedResult =
                new TriggerRegistration.Builder()
                        .setTopOrigin(Uri.parse(TOP_ORIGIN))
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setFilters(new JSONObject(filters).toString())
                        .setAggregateTriggerData(new JSONArray(aggregatableTriggerData).toString())
                        .setAggregateValues(new JSONObject(aggregatableValues).toString())
                        .build();

        // Execution
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchWebTriggers(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedResult, result.get(0));
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_withRedirects_ignoresRedirects()
            throws IOException, JSONException {
        // Setup
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Collections.singletonList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data': " + EVENT_TRIGGERS_1 + "}"),
                                "Attribution-Reporting-Redirect",
                                List.of(ALT_REGISTRATION)));
        TriggerRegistration expectedResult =
                new TriggerRegistration.Builder()
                        .setTopOrigin(Uri.parse(TOP_ORIGIN))
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        // Execution
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchWebTriggers(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedResult, result.get(0));
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void basicTriggerRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        MeasurementRegistrationResponseStats expectedStats =
                new MeasurementRegistrationResponseStats.Builder(
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                                221)
                        .setAdTechDomain(TRIGGER_URI)
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        doReturn(5L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        Optional<List<TriggerRegistration>> fetch = mFetcher.fetchTrigger(request);
        assertTrue(fetch.isPresent());
        List<TriggerRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.get(0).getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }

    private RegistrationRequest buildRequest(String triggerUri, String topOriginUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse(triggerUri))
                .setTopOriginUri(Uri.parse(topOriginUri))
                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(true)
                .build();
    }

    private RegistrationRequest buildRequestWithoutAdIdPermission(
            String triggerUri, String topOriginUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse(triggerUri))
                .setTopOriginUri(Uri.parse(topOriginUri))
                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(false)
                .build();
    }

    private WebTriggerRegistrationRequest buildWebTriggerRegistrationRequest(
            List<WebTriggerParams> triggerParams, String topOrigin) {
        return new WebTriggerRegistrationRequest.Builder(triggerParams, Uri.parse(topOrigin))
                .build();
    }
}
