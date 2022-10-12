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

package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.junit.Test;

import java.util.Set;
import java.util.UUID;

/** Unit tests for {@link AggregateReport} */
@SmallTest
public final class AggregateReportTest {

    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);
    private static final String SOURCE_ID = UUID.randomUUID().toString();
    private static final String TRIGGER_ID = UUID.randomUUID().toString();

    private AggregateReport createAttributionReport() {
        return new AggregateReport.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.com/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setEnrollmentId("enrollment-id")
                .setDebugCleartextPayload(" key: 1369, value: 32768; key: 3461, value: 1664;")
                .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                .setStatus(AggregateReport.Status.PENDING)
                .setApiVersion("1452")
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .build();
    }

    private AggregateReport createAttributionReportSingleTriggerDebugKey() {
        return new AggregateReport.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.com/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setEnrollmentId("enrollment-id")
                .setDebugCleartextPayload(" key: 1369, value: 32768; key: 3461, value: 1664;")
                .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                .setStatus(AggregateReport.Status.PENDING)
                .setApiVersion("1452")
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .build();
    }

    private AggregateReport createAttributionReportSingleSourceDebugKey() {
        return new AggregateReport.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.com/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setEnrollmentId("enrollment-id")
                .setDebugCleartextPayload(" key: 1369, value: 32768; key: 3461, value: 1664;")
                .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                .setStatus(AggregateReport.Status.PENDING)
                .setApiVersion("1452")
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateReport attributionReport = createAttributionReport();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(Uri.parse("https://example.com/aS"),
                attributionReport.getAttributionDestination());
        assertEquals(5L, attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("enrollment-id", attributionReport.getEnrollmentId());
        assertEquals(" key: 1369, value: 32768; key: 3461, value: 1664;",
                attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals("1452", attributionReport.getApiVersion());
        assertEquals(SOURCE_DEBUG_KEY, attributionReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, attributionReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, attributionReport.getSourceId());
        assertEquals(TRIGGER_ID, attributionReport.getTriggerId());
    }

    @Test
    public void testCreationSingleSourceDebugKey() {
        AggregateReport attributionReport = createAttributionReportSingleSourceDebugKey();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(
                Uri.parse("https://example.com/aS"), attributionReport.getAttributionDestination());
        assertEquals(5L, attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("enrollment-id", attributionReport.getEnrollmentId());
        assertEquals(
                " key: 1369, value: 32768; key: 3461, value: 1664;",
                attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals("1452", attributionReport.getApiVersion());
        assertEquals(SOURCE_DEBUG_KEY, attributionReport.getSourceDebugKey());
        assertNull(attributionReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, attributionReport.getSourceId());
        assertEquals(TRIGGER_ID, attributionReport.getTriggerId());
    }

    @Test
    public void testCreationSingleTriggerDebugKey() {
        AggregateReport attributionReport = createAttributionReportSingleTriggerDebugKey();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(
                Uri.parse("https://example.com/aS"), attributionReport.getAttributionDestination());
        assertEquals(5L, attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("enrollment-id", attributionReport.getEnrollmentId());
        assertEquals(
                " key: 1369, value: 32768; key: 3461, value: 1664;",
                attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertEquals("1452", attributionReport.getApiVersion());
        assertNull(attributionReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, attributionReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, attributionReport.getSourceId());
        assertEquals(TRIGGER_ID, attributionReport.getTriggerId());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateReport attributionReport =
                new AggregateReport.Builder().build();
        assertNull(attributionReport.getId());
        assertNull(attributionReport.getPublisher());
        assertNull(attributionReport.getAttributionDestination());
        assertEquals(0L, attributionReport.getSourceRegistrationTime());
        assertEquals(0L, attributionReport.getScheduledReportTime());
        assertNull(attributionReport.getEnrollmentId());
        assertNull(attributionReport.getDebugCleartextPayload());
        assertNull(attributionReport.getAggregateAttributionData());
        assertEquals(AggregateReport.Status.PENDING, attributionReport.getStatus());
        assertNull(attributionReport.getApiVersion());
        assertNull(attributionReport.getSourceDebugKey());
        assertNull(attributionReport.getTriggerDebugKey());
        assertNull(attributionReport.getSourceId());
        assertNull(attributionReport.getTriggerId());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        AggregateReport attributionReport1 = createAttributionReport();
        AggregateReport attributionReport2 = createAttributionReport();
        Set<AggregateReport> attributionReportSet1 = Set.of(attributionReport1);
        Set<AggregateReport> attributionReportSet2 = Set.of(attributionReport2);
        assertEquals(attributionReport1.hashCode(), attributionReport2.hashCode());
        assertEquals(attributionReport1, attributionReport2);
        assertEquals(attributionReportSet1, attributionReportSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        AggregateReport attributionReport1 = createAttributionReport();
        AggregateReport attributionReport2 =
                new AggregateReport.Builder()
                        .setId("1")
                        .setPublisher(Uri.parse("android-app://com.example.abc"))
                        .setAttributionDestination(Uri.parse("https://example.com/aS"))
                        .setSourceRegistrationTime(1L)
                        .setScheduledReportTime(1L)
                        .setEnrollmentId("another-enrollment-id")
                        .setDebugCleartextPayload(
                                " key: 1369, value: 32768; key: 3461, value: 1664;")
                        .setAggregateAttributionData(new AggregateAttributionData.Builder().build())
                        .setStatus(AggregateReport.Status.PENDING)
                        .setApiVersion("1452")
                        .setSourceId(SOURCE_ID)
                        .setTriggerId(TRIGGER_ID)
                        .build();
        Set<AggregateReport> attributionReportSet1 = Set.of(attributionReport1);
        Set<AggregateReport> attributionReportSet2 = Set.of(attributionReport2);
        assertNotEquals(attributionReport1.hashCode(), attributionReport2.hashCode());
        assertNotEquals(attributionReport1, attributionReport2);
        assertNotEquals(attributionReportSet1, attributionReportSet2);
    }
}
