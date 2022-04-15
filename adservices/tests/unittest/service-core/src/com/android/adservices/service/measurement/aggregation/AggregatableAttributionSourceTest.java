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

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link AggregatableAttributionSource} */
@SmallTest
public final class AggregatableAttributionSourceTest {

    @Test
    public void testCreation() throws Exception {
        Map<String, AttributionAggregatableKey> aggregatableSource = new HashMap<>();
        aggregatableSource.put("campaignCounts",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(159L).build());
        aggregatableSource.put("geoValue",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(5L).build());

        AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregatableSource).build();

        assertEquals(attributionSource.getAggregatableSource().size(), 2);
        assertEquals(attributionSource.getAggregatableSource().get("campaignCounts")
                .getHighBits().longValue(), 0L);
        assertEquals(attributionSource.getAggregatableSource().get("campaignCounts")
                .getLowBits().longValue(), 159L);
        assertEquals(attributionSource.getAggregatableSource().get("geoValue")
                .getHighBits().longValue(), 0L);
        assertEquals(attributionSource.getAggregatableSource().get("geoValue")
                .getLowBits().longValue(), 5L);
    }

    @Test
    public void testDefaults() throws Exception {
        AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder().build();
        assertEquals(attributionSource.getAggregatableSource().size(), 0);
    }
}
