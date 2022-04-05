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

package com.android.adservices.data.customaudience;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;

import com.android.internal.util.Preconditions;

import java.time.Instant;
import java.util.Objects;

/**
 * POJO represents a Custom Audience.
 * TODO: Align on the class naming strategy. (b/228095626)
 */
@Entity(
        tableName = DBCustomAudience.TABLE_NAME,
        primaryKeys = {"owner", "buyer", "name"}
)
public class DBCustomAudience {
    public static final String TABLE_NAME = "custom_audience";

    @ColumnInfo(name = "owner", index = true)
    @NonNull
    private final String mOwner;

    @ColumnInfo(name = "buyer", index = true)
    @NonNull
    private final String mBuyer;

    @ColumnInfo(name = "name")
    @NonNull
    private final String mName;

    @ColumnInfo(name = "expiration_time", index = true)
    @NonNull
    private final Instant mExpirationTime;

    @ColumnInfo(name = "activation_time")
    @Nullable
    private final Instant mActivationTime;

    @ColumnInfo(name = "creation_time")
    @NonNull
    private final Instant mCreationTime;

    @ColumnInfo(name = "last_updated_time", index = true)
    @NonNull
    private final Instant mLastUpdatedTime;

    @ColumnInfo(name = "daily_update_url")
    @NonNull
    private final Uri mDailyUpdateUrl;

    @ColumnInfo(name = "user_bidding_signals")
    @Nullable
    private final String mUserBiddingSignals;

    @Embedded(prefix = "trusted_bidding_data_")
    @Nullable
    private final DBTrustedBiddingData mTrustedBiddingData;

    @ColumnInfo(name = "bidding_logic_url")
    @Nullable
    private final Uri mBiddingLogicUrl;

    @ColumnInfo(name = "ads")
    @Nullable
    private final String mAds;

    public DBCustomAudience(@NonNull String owner, @NonNull String buyer,
            @NonNull String name, @NonNull Instant expirationTime, @Nullable Instant activationTime,
            @NonNull Instant creationTime, @NonNull Instant lastUpdatedTime,
            @NonNull Uri dailyUpdateUrl, @Nullable String userBiddingSignals,
            @Nullable DBTrustedBiddingData trustedBiddingData, @Nullable Uri biddingLogicUrl,
            @Nullable String ads) {
        Preconditions.checkStringNotEmpty(owner, "Owner must be provided");
        Preconditions.checkStringNotEmpty(buyer, "Buyer must be provided.");
        Preconditions.checkStringNotEmpty(name, "Name must be provided");
        Objects.requireNonNull(expirationTime, "Expiration time must be provided.");
        Objects.requireNonNull(creationTime, "Creation time must be provided.");
        Objects.requireNonNull(lastUpdatedTime, "Last updated time must be provided.");
        Objects.requireNonNull(dailyUpdateUrl, "Daily update url must be provided.");

        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mExpirationTime = expirationTime;
        mActivationTime = activationTime;
        mCreationTime = creationTime;
        mLastUpdatedTime = lastUpdatedTime;
        mDailyUpdateUrl = dailyUpdateUrl;
        mUserBiddingSignals = userBiddingSignals;
        mTrustedBiddingData = trustedBiddingData;
        mBiddingLogicUrl = biddingLogicUrl;
        mAds = ads;
    }

    /**
     * The App that adds the user to this CustomAudience.
     * Value must be <App UID>-<package name>.
     */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /**
     * The ad-tech who can read this custom audience information and return
     * back relevant ad information. This is expected to be the domain’s name
     * used in biddingLogicUrl and dailyUpdateUrl.
     * Max length: 200 bytes
     */
    @NonNull
    public String getBuyer() {
        return mBuyer;
    }


    /**
     * Identifies the CustomAudience within the set of ones created
     * for this combination of owner and buyer.
     * Max length: 200 bytes
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Defines until when the CA end to be effective, this can be used to
     * remove a user from this CA only after a defined period of time.
     * Default to be 60 days(Pending product confirm).
     * Should be within 1 year since creation.
     */
    @NonNull
    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    /**
     * Defines when the CA starts to be effective, this can be used to
     * enroll a user to this CA only after a defined interval (for example
     * to track the fact that the user has not been using the app in the last
     * n days).
     * Should be within 1 year since creation.
     */
    @Nullable
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /**
     * Returns the time the CA was created.
     */
    @NonNull
    public Instant getCreationTime() {
        return mCreationTime;
    }

    /**
     * Returns the time the CA was last updated.
     */
    @NonNull
    public Instant getLastUpdatedTime() {
        return mLastUpdatedTime;
    }

    /**
     * Specify where Ads and other metadata (like user_bidding_signals) are
     * fetched from. Encodes custom audience name, coarse geo, time on list
     * and any other information needed for fetching Ads.
     */
    @NonNull
    public Uri getDailyUpdateUrl() {
        return mDailyUpdateUrl;
    }

    /**
     * Signals needed for any on-device bidding for remarketing Ads.
     * For instance, an App might decide to store an embedding from their
     * User features a model here while creating the custom audience.
     */
    @Nullable
    public String getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    /**
     * An ad-tech can define what data needs to be fetched from a trusted
     * server (trusted_bidding_keys) and where it should be fetched from
     * (trusted_bidding_url).
     */
    @Nullable
    public DBTrustedBiddingData getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    /**
     * Returns the URL to fetch bidding logic js.
     */
    @Nullable
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /**
     * Returns Ads metadata that used to render an ad.
     */
    @Nullable
    public String getAds() {
        return mAds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DBCustomAudience)) return false;
        DBCustomAudience that = (DBCustomAudience) o;
        return mOwner.equals(that.mOwner) && mBuyer.equals(that.mBuyer) && mName.equals(that.mName)
                && mExpirationTime.equals(that.mExpirationTime) && Objects.equals(
                mActivationTime, that.mActivationTime) && mCreationTime.equals(that.mCreationTime)
                && mLastUpdatedTime.equals(that.mLastUpdatedTime) && mDailyUpdateUrl.equals(
                that.mDailyUpdateUrl) && Objects.equals(mUserBiddingSignals,
                that.mUserBiddingSignals) && Objects.equals(mTrustedBiddingData,
                that.mTrustedBiddingData) && Objects.equals(mBiddingLogicUrl,
                that.mBiddingLogicUrl) && Objects.equals(mAds, that.mAds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOwner, mBuyer, mName, mExpirationTime, mActivationTime, mCreationTime,
                mLastUpdatedTime, mDailyUpdateUrl, mUserBiddingSignals, mTrustedBiddingData,
                mBiddingLogicUrl, mAds);
    }

    @Override
    public String toString() {
        return "DBCustomAudience{"
                + "mOwner='" + mOwner + '\''
                + ", mBuyer='" + mBuyer + '\''
                + ", mName='" + mName + '\''
                + ", mExpirationTime=" + mExpirationTime
                + ", mActivationTime=" + mActivationTime
                + ", mCreationTime=" + mCreationTime
                + ", mLastUpdatedTime=" + mLastUpdatedTime
                + ", mDailyUpdateUrl=" + mDailyUpdateUrl
                + ", mUserBiddingSignals='" + mUserBiddingSignals + '\''
                + ", mTrustedBiddingData=" + mTrustedBiddingData
                + ", mBiddingLogicUrl=" + mBiddingLogicUrl
                + ", mAds='" + mAds + '\''
                + '}';
    }

    /**
     * Builder to construct a {@link DBCustomAudience}.
     */
    public static final class Builder {
        private String mOwner;
        private String mBuyer;
        private String mName;
        private Instant mExpirationTime;
        private Instant mActivationTime;
        private Instant mCreationTime;
        private Instant mLastUpdatedTime;
        private Uri mDailyUpdateUrl;
        private String mUserBiddingSignals;
        private DBTrustedBiddingData mTrustedBiddingData;
        private Uri mBiddingLogicUrl;
        private String mAds;

        public Builder() {
        }

        public Builder(@NonNull DBCustomAudience customAudience) {
            Objects.requireNonNull(customAudience, "Custom audience must not be null.");

            mOwner = customAudience.getOwner();
            mBuyer = customAudience.getBuyer();
            mName = customAudience.getName();
            mExpirationTime = customAudience.getExpirationTime();
            mActivationTime = customAudience.getActivationTime();
            mCreationTime = customAudience.getCreationTime();
            mDailyUpdateUrl = customAudience.getDailyUpdateUrl();
            mUserBiddingSignals = customAudience.getUserBiddingSignals();
            mTrustedBiddingData = customAudience.getTrustedBiddingData();
            mBiddingLogicUrl = customAudience.getBiddingLogicUrl();
            mAds = customAudience.getAds();
        }

        /**
         * See {@link #getOwner()} for detail.
         */
        public Builder setOwner(String owner) {
            mOwner = owner;
            return this;
        }

        /**
         * See {@link #getBuyer()} for detail.
         */
        public Builder setBuyer(String buyer) {
            mBuyer = buyer;
            return this;
        }

        /**
         * See {@link #getName()} for detail.
         */
        public Builder setName(String name) {
            mName = name;
            return this;
        }

        /**
         * See {@link #getExpirationTime()} for detail.
         */
        public Builder setExpirationTime(Instant expirationTime) {
            mExpirationTime = expirationTime;
            return this;
        }

        /**
         * See {@link #getActivationTime()} for detail.
         */
        public Builder setActivationTime(Instant activationTime) {
            mActivationTime = activationTime;
            return this;
        }

        /**
         * See {@link #getCreationTime()} for detail.
         */
        public Builder setCreationTime(Instant creationTime) {
            mCreationTime = creationTime;
            return this;
        }

        /**
         * See {@link #getLastUpdatedTime()} for detail.
         */
        public Builder setLastUpdatedTime(Instant lastUpdatedTime) {
            mLastUpdatedTime = lastUpdatedTime;
            return this;
        }

        /**
         * See {@link #getDailyUpdateUrl()} for detail.
         */
        public Builder setDailyUpdateUrl(Uri dailyUpdateUrl) {
            mDailyUpdateUrl = dailyUpdateUrl;
            return this;
        }

        /**
         * See {@link #getUserBiddingSignals()} for detail.
         */
        public Builder setUserBiddingSignals(String userBiddingSignals) {
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /**
         * See {@link #getTrustedBiddingData()} for detail.
         */
        public Builder setTrustedBiddingData(DBTrustedBiddingData trustedBiddingData) {
            mTrustedBiddingData = trustedBiddingData;
            return this;
        }

        /**
         * See {@link #getBiddingLogicUrl()} for detail.
         */
        public Builder setBiddingLogicUrl(Uri biddingLogicUrl) {
            mBiddingLogicUrl = biddingLogicUrl;
            return this;
        }

        /**
         * See {@link #getAds()} for detail.
         */
        public Builder setAds(String ads) {
            mAds = ads;
            return this;
        }

        /**
         * Build the {@link DBCustomAudience}.
         *
         * @return the built {@link DBCustomAudience}.
         */
        public DBCustomAudience build() {
            return new DBCustomAudience(mOwner, mBuyer, mName, mExpirationTime, mActivationTime,
                    mCreationTime, mLastUpdatedTime, mDailyUpdateUrl, mUserBiddingSignals,
                    mTrustedBiddingData, mBiddingLogicUrl, mAds);
        }
    }
}
