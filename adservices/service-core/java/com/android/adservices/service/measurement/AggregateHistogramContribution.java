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

import java.math.BigInteger;
import java.util.Objects;

/**
 * POJO for AggregateReportPayload, the result for Aggregate API.
 */
public class AggregateHistogramContribution {
    private BigInteger mKey;  // Equivalent to uint128 in C++.
    private long mValue;

    private AggregateHistogramContribution() {
        mKey = BigInteger.valueOf(0L);
        mValue = 0L;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateHistogramContribution)) {
            return false;
        }
        AggregateHistogramContribution aggregateHistogramContribution =
                (AggregateHistogramContribution) obj;
        return Objects.equals(mKey, aggregateHistogramContribution.mKey)
                && Objects.equals(mValue, aggregateHistogramContribution.mValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mValue);
    }

    /**
     * Encrypted Key for the aggregate histogram contribution.
     */
    public BigInteger getKey() {
        return mKey;
    }

    /**
     * Value for the aggregate histogram contribution.
     */
    public long getValue() {
        return mValue;
    }

    /**
     * Builder for {@link AggregateHistogramContribution}.
     */
    public static final class Builder {
        private final AggregateHistogramContribution mAggregateHistogramContribution;

        public Builder() {
            mAggregateHistogramContribution = new AggregateHistogramContribution();
        }

        /**
         * See {@link AggregateHistogramContribution#getKey()}.
         */
        public Builder setKey(BigInteger key) {
            mAggregateHistogramContribution.mKey = key;
            return this;
        }

        /**
         * See {@link AggregateHistogramContribution#getValue()}.
         */
        public Builder setValue(long value) {
            mAggregateHistogramContribution.mValue = value;
            return this;
        }

        /**
         * Build the {@link AggregateHistogramContribution}.
         */
        public AggregateHistogramContribution build() {
            return mAggregateHistogramContribution;
        }
    }
}
