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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Class used to generate CleartextAggregatePayload using AggregatableAttributionSource and
 * AggregatableAttributionTrigger.
 */
public class AggregatePayloadGenerator {

    private AggregatePayloadGenerator() {}

    /**
     * Generates the {@link CleartextAggregatePayload} from given AggregatableAttributionSource and
     * AggregatableAttributionTrigger.
     *
     * @param attributionSource the aggregate attribution source used for aggregation.
     * @param attributionTrigger the aggregate attribution trigger used for aggregation.
     * @return the aggregate report generated by the given aggregate attribution source and
     * aggregate attribution trigger.
     */
    public static Optional<CleartextAggregatePayload> generateAttributionReport(
            AggregatableAttributionSource attributionSource,
            AggregatableAttributionTrigger attributionTrigger) {
        AggregateFilterData sourceFilterData = attributionSource.getAggregateFilterData();
        Map<String, BigInteger> aggregateKeys = new HashMap<>();
        Map<String, AttributionAggregatableKey> aggregateSourceMap =
                attributionSource.getAggregatableSource();
        for (String sourceKey : aggregateSourceMap.keySet()) {
            for (AggregateTriggerData triggerData : attributionTrigger.getTriggerData()) {
                Optional<AggregateFilterData> filterData = triggerData.getFilter();
                Optional<AggregateFilterData> notFilterData = triggerData.getNotFilter();
                // Skip this trigger data when filter doesn't match.
                if (filterData.isPresent()
                        && !isFilterMatch(sourceFilterData, filterData.get(), true)) {
                    continue;
                }
                // Skip this trigger data when not_filters doesn't match.
                if (notFilterData.isPresent()
                        && !isFilterMatch(sourceFilterData, notFilterData.get(), false)) {
                    continue;
                }
                if (triggerData.getSourceKeys().contains(sourceKey)) {
                    AttributionAggregatableKey currentKey = aggregateSourceMap.get(sourceKey);
                    AttributionAggregatableKey triggerKey = triggerData.getKey();
                    BigInteger currentInt;
                    if (aggregateKeys.containsKey(sourceKey)) {
                        currentInt = aggregateKeys.get(sourceKey);
                    } else {
                        currentInt = BigInteger.valueOf(
                                (long) (Math.pow(2, 63) * currentKey.getHighBits()
                                        + currentKey.getLowBits()));
                    }
                    BigInteger triggerInt = BigInteger.valueOf(
                            (long) (Math.pow(2, 63) * triggerKey.getHighBits()
                                    + triggerKey.getLowBits()));
                    aggregateKeys.put(sourceKey, currentInt.add(triggerInt));
                }
            }
        }

        List<AggregateHistogramContribution> contributions = new ArrayList<>();
        for (String key : attributionTrigger.getValues().keySet()) {
            if (aggregateKeys.containsKey(key)) {
                AggregateHistogramContribution contribution =
                        new AggregateHistogramContribution.Builder()
                                .setKey(aggregateKeys.get(key))
                                .setValue(attributionTrigger.getValues().get(key)).build();
                contributions.add(contribution);
            }
        }
        if (contributions.size() > 0) {
            return Optional.of(new CleartextAggregatePayload.Builder()
                    .setAggregateAttributionData(
                            new AggregateAttributionData.Builder()
                                    .setContributions(contributions).build()).build());
        }
        return Optional.empty();
    }

    /**
     * Checks whether source filter and trigger filter are matched.
     * When a key is only present in source or trigger, ignore that key.
     * When a key is present both in source and trigger, the key matches if the intersection of
     * values is not empty.
     * @param sourceFilter the filter_data field in attribution source.
     * @param triggerFilter the AttributionTriggerData in attribution trigger.
     * @param isFilter true for filters, false for not_filters.
     * @return return true when all keys in source filter and trigger filter are matched.
     */
    public static boolean isFilterMatch(AggregateFilterData sourceFilter,
            AggregateFilterData triggerFilter, boolean isFilter) {
        for (String key : triggerFilter.getAttributionFilterMap().keySet()) {
            if (!sourceFilter.getAttributionFilterMap().containsKey(key)) {
                continue;
            }
            // Finds the intersection of two value lists.
            List<String> sourceValues = sourceFilter.getAttributionFilterMap().get(key);
            List<String> triggerValues = triggerFilter.getAttributionFilterMap().get(key);
            Set<String> common = new HashSet<>(sourceValues);
            common.retainAll(triggerValues);
            // For filters, return false when one key doesn't have intersection.
            if (isFilter && common.size() == 0) {
                return false;
            }
            // For not_filters, return false when one key has intersection.
            if (!isFilter && common.size() != 0) {
                return false;
            }
        }
        return true;
    }
}


