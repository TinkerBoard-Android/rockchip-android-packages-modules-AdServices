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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

/** Unit tests for {@link android.adservices.customaudience.TrustedBiddingData} */
@SmallTest
public final class TrustedBiddingDataTest {
    @Test
    public void testBuildValidTrustedBiddingDataSuccess() {
        TrustedBiddingData validTrustedBiddingData = new TrustedBiddingData.Builder()
                .setTrustedBiddingUrl(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL)
                .setTrustedBiddingKeys(CustomAudienceUtils.VALID_TRUSTED_BIDDING_KEYS).build();

        assertThat(validTrustedBiddingData.getTrustedBiddingUrl())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL);
        assertThat(validTrustedBiddingData.getTrustedBiddingKeys())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_KEYS);
    }

    @Test
    public void testParcelValidTrustedBiddingDataSuccess() {
        TrustedBiddingData validTrustedBiddingData = new TrustedBiddingData.Builder()
                .setTrustedBiddingUrl(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL)
                .setTrustedBiddingKeys(CustomAudienceUtils.VALID_TRUSTED_BIDDING_KEYS).build();

        Parcel p = Parcel.obtain();
        validTrustedBiddingData.writeToParcel(p, 0);
        p.setDataPosition(0);
        TrustedBiddingData fromParcel = TrustedBiddingData.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getTrustedBiddingUrl())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL);
        assertThat(fromParcel.getTrustedBiddingKeys())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_KEYS);
    }

    @Test
    public void testBuildNullUrlTrustedBiddingDataFails() {
        assertThrows(NullPointerException.class, () -> {
            // TrustedBiddingUrl is not set, so it gets built as null
            new TrustedBiddingData.Builder()
                    .setTrustedBiddingKeys(CustomAudienceUtils.VALID_TRUSTED_BIDDING_KEYS).build();
        });
    }

    @Test
    public void testBuildNullKeysTrustedBiddingDataFails() {
        assertThrows(NullPointerException.class, () -> {
            // TrustedBiddingKeys is not set, so it gets built as null
            new TrustedBiddingData.Builder()
                    .setTrustedBiddingUrl(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL).build();
        });
    }

    @Test
    public void testBuildEmptyKeysTrustedBiddingData() {
        // An empty list is allowed and should not throw any exceptions
        ArrayList<String> emptyTrustedBiddingKeys = new ArrayList<String>(Collections.emptyList());

        TrustedBiddingData emptyKeysTrustedBiddingData = new TrustedBiddingData.Builder()
                .setTrustedBiddingUrl(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL)
                .setTrustedBiddingKeys(emptyTrustedBiddingKeys).build();

        assertThat(emptyKeysTrustedBiddingData.getTrustedBiddingUrl())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_URL);
        assertThat(emptyKeysTrustedBiddingData.getTrustedBiddingKeys())
                .isEqualTo(emptyTrustedBiddingKeys);
    }
}
