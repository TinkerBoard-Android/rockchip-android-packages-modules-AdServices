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
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.UnsignedLong;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;
/** Unit tests for {@link AsyncTriggerFetcher} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class AsyncTriggerFetcherTest {
    private static final String TRIGGER_URI = "https://foo.com";
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final EnrollmentData ENROLLMENT =
            new EnrollmentData.Builder().setEnrollmentId("enrollment-id").build();
    private static final String TOP_ORIGIN = "https://baz.com";
    private static final long TRIGGER_DATA = 7;
    private static final long PRIORITY = 1;
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
    private static final long DEDUP_KEY = 100;
    private static final UnsignedLong DEBUG_KEY = new UnsignedLong(34787843L);
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
    private static final String ALT_REGISTRATION = "https://bar.com";
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final WebTriggerParams TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebTriggerParams TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();
    AsyncTriggerFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;
    @Mock HttpsURLConnection mUrlConnection1;
    @Mock EnrollmentDao mEnrollmentDao;
    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;
    @Before
    public void setup() {
        mFetcher = spy(new AsyncTriggerFetcher(mEnrollmentDao, mFlags, mLogger));
        // For convenience, return the same enrollment-ID since we're using many arbitrary
        // registration URIs and not yet enforcing uniqueness of enrollment.
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(ENROLLMENT);
    }
    @Test
    public void testBasicTriggerRequest() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                AsyncTriggerFetcher.getAttributionDestination(asyncRegistration).toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }
    @Test
    public void testTriggerRequest_eventTriggerData_tooManyEntries() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_triggerData_negative() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"-2\",\"priority\":\"101\"}]";
        String expectedResult = "[{\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_triggerData_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"18446744073709551616\"," + "\"priority\":\"101\"}]";
        String expectedResult = "[{\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_triggerData_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"101z\",\"priority\":\"101\"}]";
        String expectedResult = "[{\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_triggerData_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"18446744073709551615\"," + "\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priority_negative() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"-101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priority_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":" + "\"18446744073709551615\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_priority_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData = "[{\"trigger_data\":\"2\",\"priority\":\"a101\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKey_negative() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"-34\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKey_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"18446744073709551616\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKey_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"145l\"}]";
        String expectedResult = "[{\"trigger_data\":\"2\",\"priority\":\"101\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(expectedResult, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData_deduplicationKey_uses64thBit()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"deduplication_key\":\"18446744073709551615\"}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{\"event_trigger_data\":" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(eventTriggerData, result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__filters_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__filters_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__filters_tooManyValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__filters_valueTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\"," + "\"filters\":" + filters + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__notFilters_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters = new StringBuilder("{");
        notFilters.append(
                IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        notFilters.append("}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__notFilters_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__notFilters_tooManyValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder notFilters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        notFilters.append(
                IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        notFilters.append("]}");
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequest_eventTriggerData__notFilters_valueTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String notFilters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String eventTriggerData =
                "[{\"trigger_data\":\"2\",\"priority\":\"101\","
                        + "\"not_filters\":"
                        + notFilters
                        + "}]";
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + eventTriggerData + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequest_failsWhenNotEnrolled() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.INVALID_ENROLLMENT, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void testBasicTriggerRequestWithDebugKey() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                AsyncTriggerFetcher.getAttributionDestination(asyncRegistration).toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_negative() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"-376\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"18446744073709551616\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"65g43\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequest_debugKey_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersRequest = new HashMap<>();
        headersRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of(
                        "{\"event_trigger_data\":"
                                + EVENT_TRIGGERS_1
                                + ",\"debug_key\":\"18446744073709551615\"}"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersRequest);

        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertEquals(new UnsignedLong(-1L), result.getDebugKey());
    }

    @Test
    public void testBasicTriggerRequestWithoutAdIdPermission() throws Exception {
        RegistrationRequest request = buildRequestWithoutAdIdPermission(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                AsyncTriggerFetcher.getAttributionDestination(asyncRegistration).toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        assertNull(result.getDebugKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadTriggerUrl() throws Exception {
        RegistrationRequest request = buildRequest("bad-schema://foo.com");
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadTriggerConnection() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher)
                .openUrl(new URL(TRIGGER_URI));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, never()).setRequestMethod("POST");
    }

    @Test
    public void testBadRequestReturnFailure() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(400);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data': " + "[{}]" + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                AsyncTriggerFetcher.getAttributionDestination(asyncRegistration).toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("[{}]", result.getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = buildRequest("http://foo.com");
        // Non-https should fail.
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Trigger",
                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(DEFAULT_REDIRECT));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                AsyncTriggerFetcher.getAttributionDestination(asyncRegistration).toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Redirect", List.of(DEFAULT_REDIRECT)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'event_trigger_data':" + EVENT_TRIGGERS_1 + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        AsyncRegistration asyncRegistration = appTriggerRegistrationRequest(request);
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(asyncRegistration, asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(
                AsyncTriggerFetcher.getAttributionDestination(asyncRegistration).toString(),
                result.getAttributionDestination().toString());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(
                new JSONArray(aggregatable_trigger_data).toString(),
                result.getAggregateTriggerData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData_rejectsTooManyDataKeys()
            throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("[");
        for (int i = 0; i < 51; i++) {
            tooManyKeys.append(
                    String.format(
                            "{\"key_piece\": \"0x15%1$s\",\"source_keys\":[\"campaign-%1$s\"]}",
                            i));
        }
        tooManyKeys.append("]");
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of(
                                        "{"
                                                + "'aggregatable_trigger_data': "
                                                + tooManyKeys
                                                + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONObject(aggregatable_values).toString(), result.getAggregateValues());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData_rejectsTooManyValueKeys()
            throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        int i = 0;
        for (; i < 50; i++) {
            tooManyKeys.append(String.format("\"key-%s\": 12345,", i));
        }
        tooManyKeys.append(String.format("\"key-%s\": 12345}", i));
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'aggregatable_values': " + tooManyKeys + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchTrigger_withReportingFilters_success() throws IOException, JSONException {
        // Setup
        String filters =
                "{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}";
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'filters': " + filters + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(new JSONObject(filters).toString(), result.getFilters());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebTriggers_basic_success() throws IOException, JSONException {
        // Setup
        TriggerRegistration expectedResult1 =
                new TriggerRegistration.Builder()
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setDebugKey(DEBUG_KEY)
                        .build();
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedResult1.getEventTriggers(), result.getEventTriggers());
        verify(mUrlConnection1).setRequestMethod("POST");
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
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setFilters(new JSONObject(filters).toString())
                        .setAggregateTriggerData(new JSONArray(aggregatableTriggerData).toString())
                        .setAggregateValues(new JSONObject(aggregatableValues).toString())
                        .build();
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(expectedResult.getEventTriggers(), result.getEventTriggers());
        assertEquals(expectedResult.getAggregateTriggerData(), result.getAggregateTriggerData());
        assertEquals(expectedResult.getAggregateValues(), result.getAggregateValues());
        assertEquals(expectedResult.getFilters(), result.getFilters());
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
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, true), asyncFetchStatus, redirects);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(0, redirects.size());
        assertEquals(expectedResult.getEventTriggers(), result.getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_tooManyEntries() throws Exception {
        StringBuilder tooManyEntries = new StringBuilder("[");
        for (int i = 0; i < MAX_AGGREGATABLE_TRIGGER_DATA + 1; i++) {
            tooManyEntries.append(
                    String.format(
                            "{\"key_piece\": \"0x15%1$s\",\"source_keys\":[\"campaign-%1$s\"]}",
                            i));
        }
        tooManyEntries.append("]");
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_missingPrefix()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_invalidKeyPiece_tooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":"
                        + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\""
                        + LONG_AGGREGATE_KEY_PIECE
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_notAnArray()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_tooManyKeys()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder tooManyKeys = new StringBuilder("[");
        tooManyKeys.append(
                IntStream.range(0, MAX_AGGREGATE_KEYS_PER_REGISTRATION + 1)
                        .mapToObj(i -> "aggregate-key-" + i)
                        .collect(Collectors.joining(",")));
        tooManyKeys.append("]");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\": "
                        + tooManyKeys
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testTriggerRequestWithAggregateTriggerData_sourceKeys_invalidKeyId()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\", \""
                        + LONG_AGGREGATE_KEY_ID
                        + "\"],"
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_tooManyFilters()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_tooManyValues()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_filters_valueTooLong() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyFilters()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_keyTooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"2345\"], \"" + LONG_FILTER_STRING + "\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_tooManyValues()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregateTriggerData_notFilters_valueTooLong()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String filters =
                "{\"product\":[\"1234\",\"" + LONG_FILTER_STRING + "\"], \"ctid\":[\"id\"]}";
        String aggregatable_trigger_data =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"not_filters\": "
                        + filters
                        + ","
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testBasicTriggerRequestWithAggregatableValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONObject(aggregatable_values).toString(), result.getAggregateValues());
        verify(mUrlConnection).setRequestMethod("POST");
    }
    @Test
    public void testTriggerRequestWithAggregatableValues_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregatableValues_tooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        tooManyKeys.append(
                IntStream.range(0, MAX_AGGREGATE_KEYS_PER_REGISTRATION + 1)
                        .mapToObj(i -> String.format("\"key-%s\": 12345,", i))
                        .collect(Collectors.joining(",")));
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Trigger",
                                List.of("{" + "'aggregatable_values': " + tooManyKeys + "}")));
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void testTriggerRequestWithAggregatableValues_invalidKeyId() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
        String aggregatable_values =
                "{\"campaignCounts\":32768, \"" + LONG_AGGREGATE_KEY_ID + "\":1644}";
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }
    @Test
    public void fetchWebTriggerSuccessWithoutAdIdPermission() throws IOException, JSONException {
        // Setup
        TriggerRegistration expectedResult1 =
                new TriggerRegistration.Builder()
                        .setEventTriggers(new JSONArray(EVENT_TRIGGERS_1).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();
        WebTriggerRegistrationRequest request =
                buildWebTriggerRegistrationRequest(
                        Arrays.asList(TRIGGER_REGISTRATION_1, TRIGGER_REGISTRATION_2), TOP_ORIGIN);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        webTriggerRegistrationRequest(request, false), asyncFetchStatus, redirects);
        // Assertion
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(TOP_ORIGIN, result.getAttributionDestination().toString());
        assertEquals(expectedResult1.getEnrollmentId(), result.getEnrollmentId());
        assertEquals(expectedResult1.getEventTriggers(), result.getEventTriggers());
        verify(mUrlConnection1).setRequestMethod("POST");
    }
    @Test
    public void basicTriggerRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
            throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI);
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
        List<Uri> redirects = new ArrayList<>();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Trigger> fetch =
                mFetcher.fetchTrigger(
                        appTriggerRegistrationRequest(request), asyncFetchStatus, redirects);
        assertTrue(fetch.isPresent());
        Trigger result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(new JSONArray(EVENT_TRIGGERS_1).toString(), result.getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mLogger).logMeasurementRegistrationsResponseSize(eq(expectedStats));
    }
    private RegistrationRequest buildRequest(String triggerUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse(triggerUri))
                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(true)
                .build();
    }
    private RegistrationRequest buildRequestWithoutAdIdPermission(String triggerUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse(triggerUri))
                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(false)
                .build();
    }
    private WebTriggerRegistrationRequest buildWebTriggerRegistrationRequest(
            List<WebTriggerParams> triggerParams, String topOrigin) {
        return new WebTriggerRegistrationRequest.Builder(triggerParams, Uri.parse(topOrigin))
                .build();
    }

    public static AsyncRegistration appTriggerRegistrationRequest(
            RegistrationRequest registrationRequest) {
        // Necessary for testing
        String enrollmentId = "";
        if (EnrollmentDao.getInstance(CONTEXT)
                        .getEnrollmentDataFromMeasurementUrl(
                                registrationRequest
                                        .getRegistrationUri()
                                        .buildUpon()
                                        .clearQuery()
                                        .toString())
                != null) {
            enrollmentId =
                    EnrollmentDao.getInstance(CONTEXT)
                            .getEnrollmentDataFromMeasurementUrl(
                                    registrationRequest
                                            .getRegistrationUri()
                                            .buildUpon()
                                            .clearQuery()
                                            .toString())
                            .getEnrollmentId();
        }
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                enrollmentId,
                registrationRequest.getRegistrationUri(),
                null,
                null,
                Uri.parse("android-app://" + CONTEXT.getPackageName()),
                null,
                null,
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                null,
                System.currentTimeMillis(),
                0,
                System.currentTimeMillis(),
                true,
                registrationRequest.isAdIdPermissionGranted());
    }

    private static AsyncRegistration webTriggerRegistrationRequest(
            WebTriggerRegistrationRequest webTriggerRegistrationRequest,
            boolean adIdPermissionGranted) {
        if (webTriggerRegistrationRequest.getTriggerParams().size() > 0) {
            WebTriggerParams webTriggerParams =
                    webTriggerRegistrationRequest.getTriggerParams().get(0);
            // Necessary for testing
            String enrollmentId = "";
            if (EnrollmentDao.getInstance(CONTEXT)
                            .getEnrollmentDataFromMeasurementUrl(
                                    webTriggerRegistrationRequest
                                            .getTriggerParams()
                                            .get(0)
                                            .getRegistrationUri()
                                            .buildUpon()
                                            .clearQuery()
                                            .toString())
                    != null) {
                enrollmentId =
                        EnrollmentDao.getInstance(CONTEXT)
                                .getEnrollmentDataFromMeasurementUrl(
                                        webTriggerParams
                                                .getRegistrationUri()
                                                .buildUpon()
                                                .clearQuery()
                                                .toString())
                                .getEnrollmentId();
            }
            return createAsyncRegistration(
                    UUID.randomUUID().toString(),
                    enrollmentId,
                    webTriggerParams.getRegistrationUri(),
                    null,
                    null,
                    Uri.parse("android-app://" + CONTEXT.getPackageName()),
                    null,
                    webTriggerRegistrationRequest.getDestination(),
                    AsyncRegistration.RegistrationType.WEB_TRIGGER,
                    null,
                    System.currentTimeMillis(),
                    0,
                    System.currentTimeMillis(),
                    false,
                    adIdPermissionGranted);
        }
        return null;
    }

    private static AsyncRegistration createAsyncRegistration(
            String iD,
            String enrollmentId,
            Uri registrationUri,
            Uri webDestination,
            Uri osDestination,
            Uri registrant,
            Uri verifiedDestination,
            Uri topOrigin,
            AsyncRegistration.RegistrationType registrationType,
            Source.SourceType sourceType,
            long mRequestTime,
            long mRetryCount,
            long mLastProcessingTime,
            boolean redirect,
            boolean debugKeyAllowed) {
        return new AsyncRegistration.Builder()
                .setId(iD)
                .setEnrollmentId(enrollmentId)
                .setRegistrationUri(registrationUri)
                .setWebDestination(webDestination)
                .setOsDestination(osDestination)
                .setRegistrant(registrant)
                .setVerifiedDestination(verifiedDestination)
                .setTopOrigin(topOrigin)
                .setType(registrationType.ordinal())
                .setSourceType(sourceType)
                .setRequestTime(mRequestTime)
                .setRetryCount(mRetryCount)
                .setLastProcessingTime(mLastProcessingTime)
                .setRedirect(redirect)
                .setDebugKeyAllowed(debugKeyAllowed)
                .build();
    }
}
