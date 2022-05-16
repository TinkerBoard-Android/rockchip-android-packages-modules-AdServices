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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CustomAudienceCtsTest {

    private AdvertisingCustomAudienceClient mClient;

    private static final String OWNER = "owner";
    private static final String BUYER = "buyer";
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final String TRUSTED_BIDDING_DATA = "{\"trusted_bidding_data\":1}";

    private boolean mIsDebugMode;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        mClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(context)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        DevContext devContext = DevContextFilter.create(context).createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDevOptionsEnabled();
    }

    @Test
    public void testJoinCustomAudience_validCustomAudience_success()
            throws ExecutionException, InterruptedException {
        mClient.joinCustomAudience(CustomAudienceFixture.getValidBuilder().build()).get();
    }

    @Test
    public void testJoinCustomAudience_illegalExpirationTime_fail() {
        Exception exception = assertThrows(ExecutionException.class,
                () -> mClient.joinCustomAudience(CustomAudienceFixture.getValidBuilder()
                        .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                        .build()).get());
        assertTrue(exception.getCause() instanceof AdServicesException);
        assertTrue(exception.getCause().getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testLeaveCustomAudience_joinedCustomAudience_success()
            throws ExecutionException, InterruptedException {
        mClient.joinCustomAudience(CustomAudienceFixture.getValidBuilder().build()).get();
        mClient.leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER,
                        CustomAudienceFixture.VALID_NAME)
                .get();
    }

    @Test
    public void testLeaveCustomAudience_notJoinedCustomAudience_doesNotFail()
            throws ExecutionException, InterruptedException {
        mClient.leaveCustomAudience(
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_BUYER,
                        "not_exist_name")
                .get();
    }

    @Test
    public void testAddOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build();

        ListenableFuture<Void> result = mClient.overrideCustomAudienceRemoteInfo(request);

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testAddOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build();

        ListenableFuture<Void> result = mClient.overrideCustomAudienceRemoteInfo(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testRemoveNotExistingOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> result = mClient.removeCustomAudienceRemoteInfoOverride(request);

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testRemoveExistingOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        AddCustomAudienceOverrideRequest addRequest =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build();

        ListenableFuture<Void> addResult = mClient.overrideCustomAudienceRemoteInfo(addRequest);

        // Asserting no exception since there is no returned value
        addResult.get(10, TimeUnit.SECONDS);

        RemoveCustomAudienceOverrideRequest removeRequest =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> removeResult =
                mClient.removeCustomAudienceRemoteInfoOverride(removeRequest);

        // Asserting no exception since there is no returned value
        removeResult.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testRemoveOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> result = mClient.removeCustomAudienceRemoteInfoOverride(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testResetAllOverridesSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        ListenableFuture<Void> result = mClient.resetAllCustomAudienceOverrides();

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testResetAllOverridesFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        ListenableFuture<Void> result = mClient.resetAllCustomAudienceOverrides();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }
}
