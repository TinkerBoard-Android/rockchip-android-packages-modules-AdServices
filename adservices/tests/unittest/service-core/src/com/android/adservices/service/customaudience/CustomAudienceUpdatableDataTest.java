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

package com.android.adservices.service.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class CustomAudienceUpdatableDataTest {
    @Test
    public void testBuildUpdatableDataSuccess() throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                CustomAudienceUpdatableDataFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(DBAdDataFixture.VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                DBTrustedBiddingDataFixture.VALID_DB_TRUSTED_BIDDING_DATA,
                updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(DBAdDataFixture.VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString,
                        DBTrustedBiddingDataFixture.VALID_DB_TRUSTED_BIDDING_DATA,
                        DBAdDataFixture.VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildEmptyUpdatableDataSuccess() throws JSONException {
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(null)
                        .setTrustedBiddingData(null)
                        .setAds(null)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertNull(updatableDataFromBuilder.getUserBiddingSignals());
        assertNull(updatableDataFromBuilder.getTrustedBiddingData());
        assertNull(updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse = CustomAudienceUpdatableDataFixture.getEmptyJsonResponseString();
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);

        CustomAudienceUpdatableData updatableDataFromEmptyString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        "");

        assertEquals(
                "Updatable data created with empty string does not match built from response"
                        + " string \""
                        + jsonResponse
                        + '"',
                updatableDataFromEmptyString,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildEmptyUpdatableDataWithNonEmptyResponseSuccess() throws JSONException {
        boolean expectedContainsSuccessfulUpdate = false;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(null)
                        .setTrustedBiddingData(null)
                        .setAds(null)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertNull(updatableDataFromBuilder.getUserBiddingSignals());
        assertNull(updatableDataFromBuilder.getTrustedBiddingData());
        assertNull(updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        // In this case, a non-empty response was parsed, but the units of data found were malformed
        // and not updatable
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.getMalformedJsonResponseString();
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testHarmlessJunkIgnoredInUpdatableDataCreateFromResponse() throws JSONException {
        // In this case, a regular full response was parsed without any extra fields
        final String jsonResponseWithoutHarmlessJunk =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                        null,
                        DBAdDataFixture.VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataWithoutHarmlessJunk =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponseWithoutHarmlessJunk);

        // Harmless junk was added to the same response
        final String jsonResponseWithHarmlessJunk =
                CustomAudienceUpdatableDataFixture.toJsonResponseStringWithHarmlessJunk(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                        null,
                        DBAdDataFixture.VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataWithHarmlessJunk =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponseWithHarmlessJunk);

        assertNotEquals(
                "Harmless junk was not added to the response JSON",
                jsonResponseWithoutHarmlessJunk,
                jsonResponseWithHarmlessJunk);
        assertEquals(
                "Updatable data created without harmless junk \""
                        + jsonResponseWithoutHarmlessJunk
                        + "\" does not match created with harmless junk \""
                        + jsonResponseWithHarmlessJunk
                        + '"',
                updatableDataWithoutHarmlessJunk,
                updatableDataWithHarmlessJunk);
    }

    @Test
    public void testBuildNonEmptyUpdatableDataWithUnsuccessfulUpdateFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CustomAudienceUpdatableData.builder()
                                .setUserBiddingSignals(
                                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                                .setTrustedBiddingData(
                                        DBTrustedBiddingDataFixture.VALID_DB_TRUSTED_BIDDING_DATA)
                                .setAds(DBAdDataFixture.VALID_DB_AD_DATA_LIST)
                                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                                .setInitialUpdateResult(
                                        BackgroundFetchRunner.UpdateResultType.SUCCESS)
                                .setContainsSuccessfulUpdate(false)
                                .build());
    }

    @Test
    public void testUnsuccessfulInitialUpdateResultCausesUnsuccessfulUpdate() throws JSONException {
        // If the initial update result is anything except for SUCCESS, the resulting updatableData
        // should not contain a successful update
        for (BackgroundFetchRunner.UpdateResultType initialUpdateResult :
                BackgroundFetchRunner.UpdateResultType.values()) {
            CustomAudienceUpdatableData updatableData =
                    CustomAudienceUpdatableData.createFromResponseString(
                            CommonFixture.FIXED_NOW,
                            initialUpdateResult,
                            CustomAudienceUpdatableDataFixture.getEmptyJsonResponseString());
            assertEquals(
                    "Incorrect update success when initial result is "
                            + initialUpdateResult.toString(),
                    initialUpdateResult == BackgroundFetchRunner.UpdateResultType.SUCCESS,
                    updatableData.getContainsSuccessfulUpdate());
        }
    }

    @Test
    public void testCreateFromNonJsonResponseStringCausesUnsuccessfulUpdate() {
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        "this (input ,string .is -not real json'");

        assertNull(updatableData.getUserBiddingSignals());
        assertNull(updatableData.getTrustedBiddingData());
        assertNull(updatableData.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableData.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableData.getInitialUpdateResult());
        assertFalse(updatableData.getContainsSuccessfulUpdate());
    }

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectSuccess() throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                CustomAudienceUpdatableDataFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                CustomAudienceUpdatableData.getUserBiddingSignalsFromJsonObject(responseObject));
    }

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectWithHarmlessJunkSuccess()
            throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                CustomAudienceUpdatableDataFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, true);

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                CustomAudienceUpdatableData.getUserBiddingSignalsFromJsonObject(responseObject));
    }

    @Test
    public void testGetUserBiddingSignalsFromEmptyJsonObject() throws JSONException {
        String missingUserBiddingSignalsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingUserBiddingSignalsAsJsonObjectString, false);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CustomAudienceUpdatableData.getUserBiddingSignalsFromJsonObject(
                                responseObject));
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedSchema() {
        assertThrows(
                JSONException.class,
                () ->
                        CustomAudienceUpdatableData.getUserBiddingSignalsFromJsonObject(
                                CustomAudienceUpdatableDataFixture.getMalformedJsonObject()));
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedNullSchema() {
        assertThrows(
                JSONException.class,
                () ->
                        CustomAudienceUpdatableData.getUserBiddingSignalsFromJsonObject(
                                CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject()));
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectSuccess() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData =
                DBTrustedBiddingDataFixture.getValidBuilder().build();

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);

        assertEquals(
                expectedTrustedBiddingData,
                CustomAudienceUpdatableData.getTrustedBiddingDataFromJsonObject(
                        responseObject, "[1]"));
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectWithHarmlessJunkSuccess()
            throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData =
                DBTrustedBiddingDataFixture.getValidBuilder().build();

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, true);

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedTrustedBiddingData,
                CustomAudienceUpdatableData.getTrustedBiddingDataFromJsonObject(
                        responseObject, "[1]"));
    }

    @Test
    public void testGetTrustedBiddingDataFromEmptyJsonObject() throws JSONException {
        String missingTrustedBiddingDataAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingTrustedBiddingDataAsJsonObjectString, false);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CustomAudienceUpdatableData.getTrustedBiddingDataFromJsonObject(
                                responseObject, "[1]"));
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedSchema() {
        assertThrows(
                JSONException.class,
                () ->
                        CustomAudienceUpdatableData.getTrustedBiddingDataFromJsonObject(
                                CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                                "[1]"));
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedNullSchema() {
        assertThrows(
                JSONException.class,
                () ->
                        CustomAudienceUpdatableData.getTrustedBiddingDataFromJsonObject(
                                CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                                "[1]"));
    }

    @Test
    public void testGetAdsFromFullJsonObjectSuccess() throws JSONException {
        List<DBAdData> expectedAds = DBAdDataFixture.VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);

        assertEquals(
                expectedAds,
                CustomAudienceUpdatableData.getAdsFromJsonObject(responseObject, "[1]"));
    }

    @Test
    public void testGetAdsFromFullJsonObjectWithHarmlessJunkSuccess() throws JSONException {
        List<DBAdData> expectedAds = DBAdDataFixture.VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, true);

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedAds,
                CustomAudienceUpdatableData.getAdsFromJsonObject(responseObject, "[1]"));
    }

    @Test
    public void testGetAdsFromEmptyJsonObject() throws JSONException {
        String missingAdsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingAdsAsJsonObjectString, false);

        assertThrows(
                IllegalArgumentException.class,
                () -> CustomAudienceUpdatableData.getAdsFromJsonObject(responseObject, "[1]"));
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedSchema() {
        assertThrows(
                JSONException.class,
                () ->
                        CustomAudienceUpdatableData.getAdsFromJsonObject(
                                CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                                "[1]"));
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedNullSchema() {
        assertThrows(
                JSONException.class,
                () ->
                        CustomAudienceUpdatableData.getAdsFromJsonObject(
                                CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                                "[1]"));
    }
}
