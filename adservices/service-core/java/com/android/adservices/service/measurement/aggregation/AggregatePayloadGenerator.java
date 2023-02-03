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

import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.XNetworkData;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class used to generate AggregateReport using AggregatableAttributionSource and
 * AggregatableAttributionTrigger.
 */
public class AggregatePayloadGenerator {

    private AggregatePayloadGenerator() {}

    /**
     * Generates the {@link AggregateReport} from given AggregatableAttributionSource and
     * AggregatableAttributionTrigger.
     *
     * @param source the aggregate attribution source used for aggregation.
     * @param trigger the aggregate attribution trigger used for aggregation.
     * @return the aggregate report generated by the given aggregate attribution source and
     *     aggregate attribution trigger.
     */
    public static Optional<List<AggregateHistogramContribution>> generateAttributionReport(
            Source source, Trigger trigger) throws JSONException {
        Optional<AggregatableAttributionSource> aggregateAttributionSource =
                source.getAggregatableAttributionSource();
        Optional<AggregatableAttributionTrigger> aggregateAttributionTrigger =
                trigger.getAggregatableAttributionTrigger();
        if (!aggregateAttributionSource.isPresent() || !aggregateAttributionTrigger.isPresent()) {
            return Optional.empty();
        }

        AggregatableAttributionSource attributionSource = aggregateAttributionSource.get();
        AggregatableAttributionTrigger attributionTrigger = aggregateAttributionTrigger.get();

        FilterMap sourceFilterMap = attributionSource.getFilterMap();
        Map<String, BigInteger> aggregateSourceMap =
                attributionSource.getAggregatableSource();
        for (AggregateTriggerData triggerData : attributionTrigger.getTriggerData()) {
            Optional<List<FilterMap>> filterSet = triggerData.getFilterSet();
            Optional<List<FilterMap>> notFilterSet = triggerData.getNotFilterSet();
            // Skip this trigger data when filter doesn't match.
            if (filterSet.isPresent()
                    && !Filter.isFilterMatch(sourceFilterMap, filterSet.get(), true)) {
                continue;
            }
            // Skip this trigger data when not_filters doesn't match.
            if (notFilterSet.isPresent()
                    && !Filter.isFilterMatch(
                            sourceFilterMap, notFilterSet.get(), false)) {
                continue;
            }

            final BigInteger triggerDataKey;
            Optional<BigInteger> keyMappingForAdtech =
                    Optional.ofNullable(trigger.parseAdtechKeyMapping())
                            .map(map -> map.get(source.getEnrollmentId()));
            if (source.getParentId() != null && keyMappingForAdtech.isPresent()) {
                int keyOffset =
                        triggerData
                                .getXNetworkData()
                                .flatMap(XNetworkData::getKeyOffset)
                                .map(UnsignedLong::getValue)
                                .map(Long::intValue)
                                .orElse(0);

                BigInteger orValue = keyMappingForAdtech.get().shiftLeft(keyOffset);
                triggerDataKey = triggerData.getKey().or(orValue);
            } else {
                triggerDataKey = triggerData.getKey();
            }

            for (String sourceKey : triggerData.getSourceKeys()) {
                aggregateSourceMap.computeIfPresent(sourceKey, (k, v) -> v.or(triggerDataKey));
            }
        }

        List<AggregateHistogramContribution> contributions = new ArrayList<>();
        for (String id : aggregateSourceMap.keySet()) {
            if (!attributionTrigger.getValues().containsKey(id)) {
                continue;
            }
            AggregateHistogramContribution contribution =
                    new AggregateHistogramContribution.Builder()
                            .setKey(aggregateSourceMap.get(id))
                            .setValue(attributionTrigger.getValues().get(id)).build();
            contributions.add(contribution);
        }
        if (contributions.size() > 0) {
            return Optional.of(contributions);
        }
        return Optional.empty();
    }
}
