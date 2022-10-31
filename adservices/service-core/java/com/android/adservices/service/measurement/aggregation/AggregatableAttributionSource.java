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

import com.android.adservices.service.measurement.FilterData;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * POJO for AggregatableAttributionSource.
 */
public class AggregatableAttributionSource {

    private Map<String, BigInteger> mAggregatableSource;
    private FilterData mFilterData;

    private AggregatableAttributionSource() {
        mAggregatableSource = new HashMap<>();
        mFilterData = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregatableAttributionSource)) {
            return false;
        }
        AggregatableAttributionSource attributionSource = (AggregatableAttributionSource) obj;
        return Objects.equals(mAggregatableSource, attributionSource.mAggregatableSource)
                && Objects.equals(mFilterData, attributionSource.mFilterData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAggregatableSource);
    }

    /**
     * Returns aggregatable_source map with key represents the name field in JSON, value represents
     * the id field in JSON.
     */
    public Map<String, BigInteger> getAggregatableSource() {
        return mAggregatableSource;
    }

    /**
     * Returns aggregate filter data which represents a map in JSONObject.
     */
    public FilterData getFilterData() {
        return mFilterData;
    }

    /**
     * Builder for {@link AggregatableAttributionSource}.
     */
    public static final class Builder {
        private final AggregatableAttributionSource mBuilding;

        public Builder() {
            mBuilding = new AggregatableAttributionSource();
        }

        /**
         * See {@link AggregatableAttributionSource#getAggregatableSource()}.
         */
        public Builder setAggregatableSource(
                Map<String, BigInteger> aggregatableSource) {
            mBuilding.mAggregatableSource = aggregatableSource;
            return this;
        }

        /**
         * See {@link AggregatableAttributionSource#getFilterData()}.
         */
        public Builder setFilterData(FilterData filterData) {
            mBuilding.mFilterData = filterData;
            return this;
        }

        /**
         * Build the  {@link AggregatableAttributionSource}.
         */
        public AggregatableAttributionSource build() {
            return mBuilding;
        }
    }
}
