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

package com.android.adservices.data.adselection;

import android.adservices.customaudience.CustomAudience;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.util.Objects;

/**
 * This class represents the custom_audience_signals passed into generateBid and scoreAd javascript.
 * It contains fields from the {@link CustomAudience}.
 */
public class CustomAudienceSignals {
    @NonNull
    private final String mOwner;
    @NonNull
    private final String mBuyer;
    @NonNull
    private final String mName;
    @NonNull
    private final Instant mActivationTime;
    @NonNull
    private final Instant mExpirationTime;
    @NonNull
    private final String mUserBiddingSignals;

    public CustomAudienceSignals(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull Instant activationTime,
            @NonNull Instant expirationTime,
            @NonNull String userBiddingSignals) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(activationTime);
        Objects.requireNonNull(expirationTime);
        Objects.requireNonNull(userBiddingSignals);

        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mActivationTime = activationTime;
        mExpirationTime = expirationTime;
        mUserBiddingSignals = userBiddingSignals;
    }

    /**
     * @return a String representing the custom audience's owner application.
     */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /**
     * @return a String representing the custom audience's buyer's domain.
     */
    @NonNull
    public String getBuyer() {
        return mBuyer;
    }

    /**
     * @return a String representing the custom audience's name.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * @return the custom audience's time, truncated to whole seconds, after which the custom
     * audience is active.
     */
    @NonNull
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /**
     * @return the custom audience's time, truncated to whole seconds, after which the custom
     * audience should be removed.
     */
    @NonNull
    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    /**
     * @return a JSON String representing the opaque user bidding signals for the custom audience.
     */
    @NonNull
    public String getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    @Override
    public boolean equals(@NonNull Object o) {
        if (o instanceof CustomAudienceSignals) {
            CustomAudienceSignals customAudienceSignals = (CustomAudienceSignals) o;
            return mOwner.equals(customAudienceSignals.mOwner)
                    && mBuyer.equals(customAudienceSignals.mBuyer)
                    && mName.equals(customAudienceSignals.mName)
                    && mActivationTime.equals(customAudienceSignals.mActivationTime)
                    && mExpirationTime.equals(customAudienceSignals.mExpirationTime)
                    && mUserBiddingSignals.equals(customAudienceSignals.mUserBiddingSignals);
        }
        return false;
    }

    /**
     * @return the hash of the {@link CustomAudienceSignals} object data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mOwner, mBuyer, mName, mActivationTime, mExpirationTime,
                mUserBiddingSignals);
    }

    /**
     * Builder for @link CustomAudienceSignals} object.
     */
    public static final class Builder {
        @NonNull
        private String mOwner;
        @NonNull
        private String mBuyer;
        @NonNull
        private String mName;
        @NonNull
        private Instant mActivationTime;
        @NonNull
        private Instant mExpirationTime;
        @NonNull
        private String mUserBiddingSignals;

        public Builder() {}

        /**
         * Sets the owner application.
         *
         * See {@link #getOwner()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setOwner(@NonNull String owner) {
            Objects.requireNonNull(owner);
            mOwner = owner;
            return this;
        }

        /**
         * Sets the buyer domain URL.
         *
         * See {@link #getBuyer()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setBuyer(String buyer) {
            Objects.requireNonNull(buyer);
            mBuyer = buyer;
            return this;
        }

        /**
         * Sets the {@link CustomAudience} name.
         *
         * See {@link #getName()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setName(String name) {
            Objects.requireNonNull(name);
            mName = name;
            return this;
        }

        /**
         * Sets the {@link CustomAudience} activation time.
         *
         * See {@link #getActivationTime()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setActivationTime(Instant activationTime) {
            Objects.requireNonNull(activationTime);
            mActivationTime = activationTime;
            return this;
        }

        /**
         * Sets the {@link CustomAudience} expiration time.
         *
         * See {@link #getExpirationTime()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setExpirationTime(Instant expirationTime) {
            Objects.requireNonNull(expirationTime);
            mExpirationTime = expirationTime;
            return this;
        }

        /**
         * Sets the user bidding signals used in the ad selection.
         *
         * See {@link #getUserBiddingSignals()} for more information.
         */
        @NonNull
        public CustomAudienceSignals.Builder setUserBiddingSignals(String userBiddingSignals) {
            Objects.requireNonNull(userBiddingSignals);
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /**
         * Builds an instance of {@link CustomAudienceSignals}.
         *
         * @throws NullPointerException if any non-null parameter is null.
         */
        @NonNull
        public CustomAudienceSignals build() {
            Objects.requireNonNull(mOwner);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);
            Objects.requireNonNull(mActivationTime);
            Objects.requireNonNull(mExpirationTime);
            Objects.requireNonNull(mUserBiddingSignals);

            return new CustomAudienceSignals(mOwner, mBuyer, mName, mActivationTime,
                    mExpirationTime, mUserBiddingSignals);
        }
    }
}
