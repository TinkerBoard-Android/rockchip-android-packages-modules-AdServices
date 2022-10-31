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

import static org.junit.Assert.*;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Set;

public class EventTriggerTest {
    private static JSONObject sFilterData1;
    private static JSONObject sFilterData2;
    private static JSONObject sNotFilterData1;
    private static JSONObject sNotFilterData2;

    static {
        try {
            sFilterData1 =
                    new JSONObject(
                            "{\n"
                                    + "    \"source_type\": [\"navigation\"],\n"
                                    + "    \"key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sFilterData2 =
                    new JSONObject(
                            "{\n"
                                    + "    \"source_type\": [\"EVENT\"],\n"
                                    + "    \"key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sNotFilterData1 =
                    new JSONObject(
                            "{\n"
                                    + "    \"not_source_type\": [\"EVENT\"],\n"
                                    + "    \"not_key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sNotFilterData2 =
                    new JSONObject(
                            "{\n"
                                    + "    \"not_source_type\": [\"navigation\"],\n"
                                    + "    \"not_key_1\": [\"value_1\"] \n"
                                    + "   }\n");
        } catch (JSONException e) {
            fail();
        }
    }

    @Test
    public void testDefaults() throws Exception {
        EventTrigger eventTrigger = new EventTrigger.Builder().build();
        assertEquals(0L, eventTrigger.getTriggerPriority());
        assertNull(eventTrigger.getTriggerData());
        assertNull(eventTrigger.getDedupKey());
        assertFalse(eventTrigger.getFilterData().isPresent());
        assertFalse(eventTrigger.getNotFilterData().isPresent());
    }

    @Test
    public void test_equals_pass() throws Exception {
        EventTrigger eventTrigger1 =
                new EventTrigger.Builder()
                        .setTriggerPriority(1L)
                        .setTriggerData(new UnsignedLong(101L))
                        .setDedupKey(new UnsignedLong(1001L))
                        .setFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sFilterData1)
                                        .build())
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sNotFilterData1)
                                        .build())
                        .build();
        EventTrigger eventTrigger2 =
                new EventTrigger.Builder()
                        .setTriggerPriority(1L)
                        .setTriggerData(new UnsignedLong(101L))
                        .setDedupKey(new UnsignedLong(1001L))
                        .setFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sFilterData1)
                                        .build())
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sNotFilterData1)
                                        .build())
                        .build();

        assertEquals(eventTrigger1, eventTrigger2);
    }

    @Test
    public void test_equals_fail() throws Exception {
        assertNotEquals(
                new EventTrigger.Builder().setTriggerPriority(1L).build(),
                new EventTrigger.Builder().setTriggerPriority(2L).build());
        assertNotEquals(
                new EventTrigger.Builder().setTriggerData(new UnsignedLong(1L)).build(),
                new EventTrigger.Builder().setTriggerData(new UnsignedLong(2L)).build());
        assertNotEquals(
                new EventTrigger.Builder().setDedupKey(new UnsignedLong(1L)).build(),
                new EventTrigger.Builder().setDedupKey(new UnsignedLong(2L)).build());
        assertNotEquals(
                new EventTrigger.Builder()
                        .setFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sFilterData1)
                                        .build())
                        .build(),
                new EventTrigger.Builder()
                        .setFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sFilterData2)
                                        .build())
                        .build());
        assertNotEquals(
                new EventTrigger.Builder()
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sNotFilterData1)
                                        .build())
                        .build(),
                new EventTrigger.Builder()
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sNotFilterData2)
                                        .build())
                        .build());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        EventTrigger eventTrigger1 = createExample();
        EventTrigger eventTrigger2 = createExample();
        Set<EventTrigger> eventTriggerSet1 = Set.of(eventTrigger1);
        Set<EventTrigger> eventTriggerSet2 = Set.of(eventTrigger2);
        assertEquals(eventTrigger1.hashCode(), eventTrigger2.hashCode());
        assertEquals(eventTrigger1, eventTrigger2);
        assertEquals(eventTriggerSet1, eventTriggerSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        EventTrigger eventTrigger1 = createExample();
        EventTrigger eventTrigger2 =
                new EventTrigger.Builder()
                        .setTriggerPriority(2L)
                        .setTriggerData(new UnsignedLong(101L))
                        .setDedupKey(new UnsignedLong(1001L))
                        .setFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sFilterData1)
                                        .build())
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(sNotFilterData1)
                                        .build())
                        .build();
        Set<EventTrigger> eventTriggerSet1 = Set.of(eventTrigger1);
        Set<EventTrigger> eventTriggerSet2 = Set.of(eventTrigger2);
        assertNotEquals(eventTrigger1.hashCode(), eventTrigger2.hashCode());
        assertNotEquals(eventTrigger1, eventTrigger2);
        assertNotEquals(eventTriggerSet1, eventTriggerSet2);
    }

    private EventTrigger createExample() throws JSONException {
        return new EventTrigger.Builder()
                .setTriggerPriority(1L)
                .setTriggerData(new UnsignedLong(101L))
                .setDedupKey(new UnsignedLong(1001L))
                .setFilter(
                        new FilterData.Builder()
                                .buildFilterData(sFilterData1)
                                .build())
                .setNotFilter(
                        new FilterData.Builder()
                                .buildFilterData(sNotFilterData1)
                                .build())
                .build();
    }
}
