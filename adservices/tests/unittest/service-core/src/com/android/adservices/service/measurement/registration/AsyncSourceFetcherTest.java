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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_ATTRIBUTION_FILTERS;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_VALUES_PER_ATTRIBUTION_FILTER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;

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
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.content.Context;
import android.net.Uri;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Assert;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;

/** Unit tests for {@link AsyncSourceFetcher} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class AsyncSourceFetcherTest {
    private static final String DEFAULT_REGISTRATION = "https://foo.com";
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final EnrollmentData ENROLLMENT =
            new EnrollmentData.Builder().setEnrollmentId("enrollment-id").build();
    private static final String DEFAULT_TOP_ORIGIN =
            "android-app://com.android.adservices.servicecoretest";
    private static final String DEFAULT_DESTINATION = "android-app://com.myapps";
    private static final String DEFAULT_DESTINATION_WITHOUT_SCHEME = "com.myapps";
    private static final long DEFAULT_PRIORITY = 123;
    private static final long DEFAULT_EXPIRY = 456789;
    private static final UnsignedLong DEFAULT_EVENT_ID = new UnsignedLong(987654321L);
    private static final UnsignedLong EVENT_ID_1 = new UnsignedLong(987654321L);
    private static final UnsignedLong EVENT_ID_2 = new UnsignedLong(987654322L);
    private static final UnsignedLong DEBUG_KEY = new UnsignedLong(823523783L);
    private static final String ALT_REGISTRATION = "https://bar.com";
    private static final String ALT_DESTINATION = "android-app://com.yourapps";
    private static final long ALT_PRIORITY = 321;
    private static final UnsignedLong ALT_EVENT_ID = new UnsignedLong(123456789L);
    private static final long ALT_EXPIRY = 456790;
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os-destination");
    private static final Uri OS_DESTINATION_WITH_PATH =
            Uri.parse("android-app://com.os-destination/my/path");
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination.com");
    private static final Uri WEB_DESTINATION_WITH_SUBDOMAIN =
            Uri.parse("https://subdomain.web-destination.com");
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    AsyncSourceFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;

    @Mock HttpsURLConnection mUrlConnection1;
    @Mock HttpsURLConnection mUrlConnection2;
    @Mock EnrollmentDao mEnrollmentDao;
    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;

    @Before
    public void setup() {
        mFetcher = spy(new AsyncSourceFetcher(mEnrollmentDao, mFlags, mLogger));
        // For convenience, return the same enrollment-ID since we're using many arbitrary
        // registration URIs and not yet enforcing uniqueness of enrollment.
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(ENROLLMENT);
    }

    @Test
    public void testBasicSourceRequest() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_PRIORITY, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(DEBUG_KEY, result.get(0).getDebugKey());
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                                190)
                                        .setAdTechDomain(null)
                                        .build()));

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_failsWhenNotEnrolled() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void testBasicSourceRequestWithoutAdIdPermission() throws Exception {
        RegistrationRequest request = buildRequestWithoutAdIdPermission(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_PRIORITY, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertNull(result.get(0).getDebugKey());

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributes() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("https://foo.com"))
                        .setPackageName(sContext.getAttributionSource().getPackageName())
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "  \"install_attribution_window\": \"272800\",\n"
                                            + "  \"post_install_exclusivity_window\": \"987654\"\n"
                                            + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals("android-app://com.myapps", result.get(0).getAppDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(new UnsignedLong(987654321L), result.get(0).getSourceEventId());
        assertEquals(272800, result.get(0).getInstallAttributionWindow());
        assertEquals(987654L, result.get(0).getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributesReceivedAsNull() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("https://foo.com"))
                        .setPackageName(sContext.getAttributionSource().getPackageName())
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": null,\n"
                                                + "  \"post_install_exclusivity_window\": null\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals("android-app://com.myapps", result.get(0).getAppDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(new UnsignedLong(987654321L), result.get(0).getSourceEventId());
        // fallback to default value - 30 days
        assertEquals(2592000L, result.get(0).getInstallAttributionWindow());
        // fallback to default value - 0 days
        assertEquals(0L, result.get(0).getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithInstallAttributesOutofBounds() throws IOException {
        RegistrationRequest request =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("https://foo.com"))
                        .setPackageName(sContext.getAttributionSource().getPackageName())
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                // Min value of attribution is 1 day or 172800
                                                // seconds
                                                + "  \"install_attribution_window\": \"86300\",\n"
                                                // Max value of cooldown is 30 days or 2592000
                                                // seconds
                                                + "  \"post_install_exclusivity_window\":"
                                                + " \"9876543210\"\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals("android-app://com.myapps", result.get(0).getAppDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(new UnsignedLong(987654321L), result.get(0).getSourceEventId());
        // Adjusted to minimum allowed value
        assertEquals(86400, result.get(0).getInstallAttributionWindow());
        // Adjusted to maximum allowed value
        assertEquals(2592000L, result.get(0).getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceUrl() {
        RegistrationRequest request = buildRequest(/* registrationUri = */ "bad-schema://foo.com");
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceConnection() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher)
                .openUrl(new URL(DEFAULT_REGISTRATION));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceJson_missingSourceEventId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBadSourceJson_missingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Collections.emptyMap());
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBadSourceJson_missingDestination() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\"")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicSourceRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_sourceEventId_negative() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"-35\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicSourceRequest_sourceEventId_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551616\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicSourceRequest_sourceEventId_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"8l2\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicSourceRequest_sourceEventId_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551615\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(new UnsignedLong(-1L), result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_negative() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"-18\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertNull(result.get(0).getDebugKey());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"18446744073709551616\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertNull(result.get(0).getDebugKey());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"987fs\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertNull(result.get(0).getDebugKey());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"18446744073709551615\"}")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(new UnsignedLong(-1L), result.get(0).getDebugKey());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFieldsAndRestNull() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("https://foo.com"))
                        .setPackageName(sContext.getAttributionSource().getPackageName())
                        .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \"android-app://com.myapps\",\n"
                                                + "\"source_event_id\": \"123\",\n"
                                                + "\"priority\": null,\n"
                                                + "\"expiry\": null\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals("android-app://com.myapps", result.get(0).getAppDestination().toString());
        assertEquals(new UnsignedLong(123L), result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryLessThan2Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": 1"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryMoreThan30Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": 2678400"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = buildRequest("http://foo.com");
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + DEFAULT_EXPIRY
                                + ""
                                + "}\n"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of("https://bar.com"));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));

        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest);

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testFailedParsingButValidRedirect_returnFailure() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{}"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of("https://bar.com"));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));

        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest);

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testRedirectDifferentDestination_keepAllReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + DEFAULT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": "
                                + DEFAULT_EXPIRY
                                + ""
                                + "}\n"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(ALT_REGISTRATION));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + ALT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + ALT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));

        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest);

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(2, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(DEFAULT_PRIORITY, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(ENROLLMENT_ID, result.get(1).getEnrollmentId());
        assertEquals(ALT_DESTINATION, result.get(1).getAppDestination().toString());
        assertEquals(ALT_EVENT_ID, result.get(1).getSourceEventId());
        assertEquals(ALT_PRIORITY, result.get(1).getSourcePriority());
        assertEquals(ALT_EXPIRY, result.get(1).getExpiry());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectSameDestination_returnAllSuccessfully() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": 999,\n"
                                + "\"expiry\": "
                                + DEFAULT_EXPIRY
                                + ""
                                + "}\n"));
        headersFirstRequest.put(
                "Attribution-Reporting-Redirect", List.of(ALT_REGISTRATION, ALT_REGISTRATION));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": 888,\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));

        Map<String, List<String>> headersThirdRequest = new HashMap<>();
        headersThirdRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": 777,\n"
                                + "\"priority\": 777,\n"
                                + "\"expiry\": 456791"
                                + "}\n"));

        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest, headersThirdRequest);

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(3, result.size());
        result.sort((o1, o2) -> (int) (o2.getSourcePriority() - o1.getSourcePriority()));
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getAppDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(999, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(ENROLLMENT_ID, result.get(1).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(1).getAppDestination().toString());
        assertEquals(ALT_EVENT_ID, result.get(1).getSourceEventId());
        assertEquals(888, result.get(1).getSourcePriority());
        assertEquals(ALT_EXPIRY, result.get(1).getExpiry());
        assertEquals(ENROLLMENT_ID, result.get(2).getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.get(2).getAppDestination().toString());
        assertEquals(new UnsignedLong(777L), result.get(2).getSourceEventId());
        assertEquals(777, result.get(2).getSourcePriority());
        assertEquals(456791, result.get(2).getExpiry());
        verify(mUrlConnection, times(3)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectSameDestinationWithDelay_returnAllSuccessfully() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest =
                buildRegisterSourceDefaultHeader(
                        DEFAULT_DESTINATION, DEFAULT_EVENT_ID, /* priority = */ 1, DEFAULT_EXPIRY);
        headersFirstRequest.put(
                "Attribution-Reporting-Redirect",
                List.of(
                        "https://bar2.com",
                        "https://bar3.com",
                        "https://bar4.com",
                        "https://bar5.com",
                        "https://bar6.com"));

        Map<String, List<String>> headersSecondRequest =
                buildRegisterSourceDefaultHeader(
                        DEFAULT_DESTINATION, DEFAULT_EVENT_ID, /* priority = */ 2, DEFAULT_EXPIRY);

        Map<String, List<String>> headersThirdRequest =
                buildRegisterSourceDefaultHeader(
                        DEFAULT_DESTINATION, DEFAULT_EVENT_ID, /* priority = */ 3, DEFAULT_EXPIRY);

        Map<String, List<String>> headersFourthRequest =
                buildRegisterSourceDefaultHeader(
                        DEFAULT_DESTINATION, DEFAULT_EVENT_ID, /* priority = */ 4, DEFAULT_EXPIRY);

        Map<String, List<String>> headersFifthRequest =
                buildRegisterSourceDefaultHeader(
                        DEFAULT_DESTINATION, DEFAULT_EVENT_ID, /* priority = */ 5, DEFAULT_EXPIRY);

        Map<String, List<String>> headersSixthRequest =
                buildRegisterSourceDefaultHeader(
                        DEFAULT_DESTINATION, DEFAULT_EVENT_ID, /* priority = */ 6, DEFAULT_EXPIRY);

        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenAnswer(
                        invocation -> {
                            TimeUnit.MILLISECONDS.sleep(10);
                            return headersSecondRequest;
                        })
                .thenAnswer(
                        invocation -> {
                            TimeUnit.MILLISECONDS.sleep(10);
                            return headersThirdRequest;
                        })
                .thenAnswer(
                        invocation -> {
                            TimeUnit.MILLISECONDS.sleep(10);
                            return headersFourthRequest;
                        })
                .thenAnswer(
                        invocation -> {
                            TimeUnit.MILLISECONDS.sleep(10);
                            return headersFifthRequest;
                        })
                .thenAnswer(
                        invocation -> {
                            TimeUnit.MILLISECONDS.sleep(10);
                            return headersSixthRequest;
                        });

        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(6, result.size());
        long expected = 1;
        for (long priority :
                result.stream()
                        .map(SourceRegistration::getSourcePriority)
                        .sorted()
                        .collect(Collectors.toList())) {
            Assert.assertEquals(expected++, priority);
        }
    }

    @Test
    public void testBasicSourceRequestWithAggregateFilterData() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals("android-app://com.myapps", result.get(0).getAppDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(new UnsignedLong(987654321L), result.get(0).getSourceEventId());
        assertEquals(
                "{\"product\":[\"1234\",\"2345\"],\"ctid\":[\"id\"]}",
                result.get(0).getAggregateFilterData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequest_aggregateFilterData_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_aggregateFilterData_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String filterData = "  \"filter_data\": " + filters + "\n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_aggregateFilterData_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \""
                        + LONG_FILTER_STRING
                        + "\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_aggregateFilterData_tooManyValues() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
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
        String filterData = "  \"filter_data\": " + filters + " \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_aggregateFilterData_valueTooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\""
                        + LONG_FILTER_STRING
                        + "\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                                + filterData
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Redirect", List.of(ALT_REGISTRATION)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testBasicSourceRequestWithAggregateSource() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [{\"id\" :"
                                            + " \"campaignCounts\", \"key_piece\" :"
                                            + " \"0x159\"},{\"id\" : \"geoValue\", \"key_piece\" :"
                                            + " \"0x5\"}]\n"
                                            + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(ENROLLMENT_ID, result.get(0).getEnrollmentId());
        assertEquals(
                new JSONArray(
                                "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},{\"id\""
                                        + " : \"geoValue\", \"key_piece\" : \"0x5\"}]")
                        .toString(),
                result.get(0).getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [\"id\" : \"campaignCounts\","
                                            + " \"key_piece\" : \"0x159\"},{\"id\" : \"geoValue\","
                                            + " \"key_piece\" : \"0x5\"}]\n"
                                            + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_tooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("[");
        for (int i = 0; i < MAX_AGGREGATE_KEYS_PER_REGISTRATION + 1; i++) {
            tooManyKeys.append(
                    String.format("{\"id\": \"campaign-%1$s\", \"key_piece\": \"0x15%1$s\"}", i));
        }
        tooManyKeys.append("]");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\":"
                                            + " \"987654321\",'aggregation_keys': "
                                                + tooManyKeys)));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_keyIsNotAnObject() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [{\"id\" :"
                                            + " \"campaignCounts\", \"key_piece\" :"
                                            + " \"0x159\"},[\"id\", \"geoValue\", \"key_piece\" ,"
                                            + " \"0x5\"]\n"
                                            + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [{\"id\" : \""
                                                + LONG_AGGREGATE_KEY_ID
                                                + "\", \"key_piece\" : \"0x159\"},{\"id\" :"
                                                + " \"geoValue\", \"key_piece\" : \"0x5\"}]\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyPiece_missingPrefix()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [{\"id\" :"
                                            + " \"campaignCounts\", \"key_piece\" :"
                                            + " \"0159\"},{\"id\" : \"geoValue\", \"key_piece\" :"
                                            + " \"0x5\"}]\n"
                                            + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyPiece_tooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [{\"id\" :"
                                            + " \"campaignCounts\", \"key_piece\" :"
                                            + " \"0x159\"},{\"id\" : \"geoValue\", \"key_piece\" :"
                                            + " \""
                                                + LONG_AGGREGATE_KEY_PIECE
                                                + "\"}]\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_basic_success() throws IOException {
        // Setup
        SourceRegistration expectedResult1 =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceEventId(EVENT_ID_1)
                        .setSourcePriority(0)
                        .setDebugKey(DEBUG_KEY)
                        .build();
        SourceRegistration expectedResult2 =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceEventId(EVENT_ID_2)
                        .setSourcePriority(0)
                        .build();

        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        doReturn(mUrlConnection2).when(mFetcher).openUrl(new URL(REGISTRATION_URI_2.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection2.getResponseCode()).thenReturn(200);
        when(mUrlConnection1.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        when(mUrlConnection2.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_2
                                                + "\"\n"
                                                + "}\n")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(2, result.size());
        assertEquals(
                new HashSet<>(Arrays.asList(expectedResult1, expectedResult2)),
                new HashSet<>(result));
        verify(mUrlConnection1).setRequestMethod("POST");
        verify(mUrlConnection2).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSourcesSuccessWithoutAdIdPermission() throws IOException {
        // Setup
        SourceRegistration expectedResult1 =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceEventId(EVENT_ID_1)
                        .setSourcePriority(0)
                        .build();
        SourceRegistration expectedResult2 =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceEventId(EVENT_ID_2)
                        .setSourcePriority(0)
                        .build();

        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        doReturn(mUrlConnection2).when(mFetcher).openUrl(new URL(REGISTRATION_URI_2.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection2.getResponseCode()).thenReturn(200);
        when(mUrlConnection1.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        when(mUrlConnection2.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_2
                                                + "\"\n"
                                                + "}\n")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, false);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(2, result.size());
        assertEquals(
                new HashSet<>(Arrays.asList(expectedResult1, expectedResult2)),
                new HashSet<>(result));
        verify(mUrlConnection1).setRequestMethod("POST");
        verify(mUrlConnection2).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_oneSuccessAndOneFailure_resultsIntoOneSourceFetched()
            throws IOException {
        // Setup
        SourceRegistration expectedResult2 =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceEventId(EVENT_ID_2)
                        .setSourcePriority(0)
                        .build();

        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        doReturn(mUrlConnection2).when(mFetcher).openUrl(new URL(REGISTRATION_URI_2.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection2.getResponseCode()).thenReturn(200);
        // Its validation will fail due to destination mismatch
        when(mUrlConnection1.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                /* wrong destination */
                                                + "android-app://com.wrongapp"
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));

        when(mUrlConnection2.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_2
                                                + "\"\n"
                                                + "}\n")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedResult2, result.iterator().next());
        verify(mUrlConnection1).setRequestMethod("POST");
        verify(mUrlConnection2).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withExtendedHeaders_success() throws IOException, JSONException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregateSource =
                "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},"
                        + "{\"id\" : \"geoValue\", \"key_piece\" : \"0x5\"}]";
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + ", "
                                                + " \"aggregation_keys\": "
                                                + aggregateSource
                                                + "}")));
        SourceRegistration expectedSourceRegistration =
                new SourceRegistration.Builder()
                        .setAppDestination(OS_DESTINATION)
                        .setSourcePriority(123)
                        .setExpiry(456789)
                        .setSourceEventId(new UnsignedLong(987654321L))
                        .setAggregateFilterData(filterData)
                        .setAggregateSource(new JSONArray(aggregateSource).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedSourceRegistration, result.get(0));

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withRedirects_ignoresRedirects() throws IOException, JSONException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregateSource =
                "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},"
                        + "{\"id\" : \"geoValue\", \"key_piece\" : \"0x5\"}]";
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + " , "
                                                + " \"aggregation_keys\": "
                                                + aggregateSource
                                                + "}"),
                                "Attribution-Reporting-Redirect",
                                List.of(ALT_REGISTRATION)));
        SourceRegistration expectedSourceRegistration =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setSourcePriority(123)
                        .setExpiry(456789)
                        .setSourceEventId(new UnsignedLong(987654321L))
                        .setAggregateFilterData(filterData)
                        .setAggregateSource(new JSONArray(aggregateSource).toString())
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedSourceRegistration, result.get(0));

        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_appDestinationDoNotMatch_failsDropsSource() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\":"
                                                + " android-app://wrong.os-destination,\n"
                                                + "  \"web_destination\": "
                                                + WEB_DESTINATION
                                                + ",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + "}")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertFalse(fetch.isPresent());

        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_webDestinationDoNotMatch_failsDropsSource() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\":  "
                                                + OS_DESTINATION
                                                + ",\n"
                                                + "  \"web_destination\": "
                                                + " https://wrong-web-destination.com,\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + "}")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertFalse(fetch.isPresent());

        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_osAndWebDestinationMatch_recordSourceSuccess() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION
                                                + "\""
                                                + "}")));
        SourceRegistration expectedSourceRegistration =
                new SourceRegistration.Builder()
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setSourcePriority(123)
                        .setExpiry(456789)
                        .setSourceEventId(new UnsignedLong(987654321L))
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedSourceRegistration, result.get(0));

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_extractsTopPrivateDomain() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION_WITH_SUBDOMAIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION_WITH_SUBDOMAIN
                                                + "\""
                                                + "}")));
        SourceRegistration expectedSourceRegistration =
                new SourceRegistration.Builder()
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setSourcePriority(123)
                        .setExpiry(456789)
                        .setSourceEventId(new UnsignedLong(987654321L))
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedSourceRegistration, result.get(0));

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_extractsDestinationBaseUri() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION_WITH_PATH,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION_WITH_PATH
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION
                                                + "\""
                                                + "}")));
        SourceRegistration expectedSourceRegistration =
                new SourceRegistration.Builder()
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setSourcePriority(123)
                        .setExpiry(456789)
                        .setSourceEventId(new UnsignedLong(987654321L))
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(expectedSourceRegistration, result.get(0));

        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_missingDestinations_dropsSource() throws Exception {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDestinationUriNotHavingScheme_attachesAppScheme()
            throws IOException {
        // Setup
        SourceRegistration expectedResult =
                new SourceRegistration.Builder()
                        .setAppDestination(Uri.parse(DEFAULT_DESTINATION))
                        .setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .setSourceEventId(EVENT_ID_1)
                        .setSourcePriority(0)
                        .build();

        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION_WITHOUT_SCHEME),
                        null);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection1.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\"\n"
                                                + "}\n")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertTrue(fetch.isPresent());
        List<SourceRegistration> result = fetch.get();
        assertEquals(1, result.size());
        assertEquals(
                new HashSet<>(Collections.singletonList(expectedResult)), new HashSet<>(result));
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDestinationUriHavingHttpsScheme_dropsSource()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION_WITHOUT_SCHEME),
                        null);
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection1.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                // Invalid (https) URI for app destination
                                                + WEB_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\"\n"
                                                + "}\n")));

        // Execution
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchWebSources(request, true);

        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection1).setRequestMethod("POST");
    }

    @Test
    public void basicSourceRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(5L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        Optional<List<SourceRegistration>> fetch = mFetcher.fetchSource(request);
        assertTrue(fetch.isPresent());
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                                115)
                                        .setAdTechDomain(DEFAULT_REGISTRATION)
                                        .build()));
    }

    private RegistrationRequest buildRequest(String registrationUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse(registrationUri))
                .setPackageName(sContext.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(true)
                .build();
    }

    private RegistrationRequest buildRequestWithoutAdIdPermission(String registrationUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse(registrationUri))
                .setPackageName(sContext.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(false)
                .build();
    }

    private WebSourceRegistrationRequest buildWebSourceRegistrationRequest(
            List<WebSourceParams> sourceParamsList,
            String topOrigin,
            Uri appDestination,
            Uri webDestination) {
        WebSourceRegistrationRequest.Builder webSourceRegistrationRequestBuilder =
                new WebSourceRegistrationRequest.Builder(sourceParamsList, Uri.parse(topOrigin))
                        .setAppDestination(appDestination);

        if (webDestination != null) {
            webSourceRegistrationRequestBuilder.setWebDestination(webDestination);
        }

        return webSourceRegistrationRequestBuilder.build();
    }

    private Map<String, List<String>> buildRegisterSourceDefaultHeader(
            String destination, UnsignedLong eventId, long priority, long expiry) {
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + destination
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + eventId
                                + "\",\n"
                                + "\"priority\": \""
                                + priority
                                + "\",\n"
                                + "\"expiry\": "
                                + expiry
                                + ""
                                + "}\n"));
        return headersFirstRequest;
    }
}
