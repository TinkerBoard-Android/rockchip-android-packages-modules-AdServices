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

package android.adservices.test.scenario.adservices.fledge;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.test.scenario.adservices.utils.MockWebServerRule;
import android.adservices.test.scenario.adservices.utils.MockWebServerRuleFactory;
import android.content.Context;
import android.net.Uri;
import android.platform.test.scenario.annotation.Scenario;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class LimitPerfTests {

    // The number of ms to sleep after killing the adservices process so it has time to recover
    public static final long SLEEP_MS_AFTER_KILL = 2000L;
    // Command to kill the adservices process
    public static final String KILL_ADSERVICES_CMD =
            "su 0 killall -9 com.google.android.adservices.api";
    // Command prevent activity manager from backing off on restarting the adservices process
    public static final String DISABLE_ADSERVICES_BACKOFF_CMD =
            "am service-restart-backoff disable com.google.android.adservices.api";

    public static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    public static final Instant VALID_ACTIVATION_TIME = Instant.now();
    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    public static final String VALID_NAME = "testCustomAudienceName";
    public static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    public static final String VALID_TRUSTED_BIDDING_URI_PATH = "/trusted/bidding/";
    public static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS =
            new ArrayList<>(Arrays.asList("example", "valid", "list", "of", "keys"));
    public static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("localhost");
    // Uri Constants
    public static final String DECISION_LOGIC_PATH = "/decisionFragment";
    public static final String TRUSTED_SCORING_SIGNAL_PATH = "/trustedScoringSignalsFragment";
    public static final String CUSTOM_AUDIENCE_SHIRT = "ca_shirt";
    public static final String CUSTOM_AUDIENCE_SHOES = "ca_shoe";
    // TODO(b/244530379) Make compatible with multiple buyers
    public static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("localhost");
    public static final List<AdTechIdentifier> CUSTOM_AUDIENCE_BUYERS =
            Collections.singletonList(BUYER_1);
    public static final AdSelectionSignals AD_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}");
    public static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");
    public static final Map<AdTechIdentifier, AdSelectionSignals> PER_BUYER_SIGNALS =
            Map.of(BUYER_1, AdSelectionSignals.fromString("{\"buyer_signals\":1}"));
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    // Time allowed by current test setup for APIs to respond
    // setting a large value for perf testing, to avoid failing for large datasets
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";
    private static final String DEFAULT_DECISION_LOGIC_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                    + SELLER_REPORTING_PATH
                    + "' } };\n"
                    + "}";
    private static final String DEFAULT_BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, user_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_REPORTING_PATH
                    + "' } };\n"
                    + "}";
    private static final String CALCULATION_INTENSE_JS =
            "for (let i = 1; i < 1000000000; i++) {\n" + "  Math.sqrt(i);\n" + "}";
    private static final String MEMORY_INTENSE_JS =
            "var a = []\n" + "for (let i = 0; i < 10000; i++) {\n" + " a.push(i);" + "}";

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    private static final String AD_URI_PREFIX = "/adverts/123/";
    private static final int DELAY_TO_AVOID_THROTTLE_MS = 1001;
    protected final Context mContext = ApplicationProvider.getApplicationContext();
    private final AdSelectionClient mAdSelectionClient =
            new AdSelectionClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    private final AdvertisingCustomAudienceClient mCustomAudienceClient =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private Dispatcher mDefaultDispatcher;

    @BeforeClass
    public static void setupBeforeClass() {
        // Disable backoff since we will be killing the process between tests
        ShellUtils.runShellCommand(DISABLE_ADSERVICES_BACKOFF_CMD);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        // TODO(b/245585645) Mark true for the heap size enforcement after installing M105 library
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_js_isolate_enforce_max_heap_size",
                "false",
                true);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, "disable_fledge_enrollment_check", "true", true);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES, "ppapi_app_allow_list", "*", true);
    }

    public static Uri getUri(String name, String path) {
        return Uri.parse("https://" + name + path);
    }

    @Before
    public void setup() throws InterruptedException {
        ShellUtils.runShellCommand(KILL_ADSERVICES_CMD);
        Thread.sleep(SLEEP_MS_AFTER_KILL);
        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_DECISION_LOGIC_JS);
                        } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_BIDDING_LOGIC_JS);
                        } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                                || SELLER_REPORTING_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath().startsWith(VALID_TRUSTED_BIDDING_URI_PATH)) {
                            return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
    }

    @Test
    public void test_joinCustomAudience_success() throws Exception {
        CustomAudience ca =
                createCustomAudience(
                        BUYER_1, CUSTOM_AUDIENCE_SHOES, Collections.singletonList(1.0));
        addDelayToAvoidThrottle();
        mCustomAudienceClient
                .joinCustomAudience(ca)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        addDelayToAvoidThrottle();
        mCustomAudienceClient
                .leaveCustomAudience(ca.getBuyer(), ca.getName())
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelectionAndReporting_normalFlow_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        // TODO(b/244530379) Make compatible with multiple buyers
        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle();
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        addDelayToAvoidThrottle();
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        addDelayToAvoidThrottle();
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdSelectionAndReporting_executionHeavyJS_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        // TODO(b/244530379) Make compatible with multiple buyers
        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        String calculation_intense_logic_js =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + CALCULATION_INTENSE_JS
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(calculation_intense_logic_js);
                        } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_BIDDING_LOGIC_JS);
                        } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                                || SELLER_REPORTING_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath().startsWith(VALID_TRUSTED_BIDDING_URI_PATH)) {
                            return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle();
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        addDelayToAvoidThrottle();
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        addDelayToAvoidThrottle();
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdSelectionAndReporting_memoryHeavyJS_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        // TODO(b/244530379) Make compatible with multiple buyers
        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        String memory_intense_logic_js =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + MEMORY_INTENSE_JS
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(memory_intense_logic_js);
                        } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_BIDDING_LOGIC_JS);
                        } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                                || SELLER_REPORTING_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath().startsWith(VALID_TRUSTED_BIDDING_URI_PATH)) {
                            return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle();
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        addDelayToAvoidThrottle();
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        addDelayToAvoidThrottle();
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdSelectionAndReporting_multipleCustomAudienceList_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        // TODO(b/244530379) Make compatible with multiple buyers
        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = new ArrayList<>();
        customAudienceList.add(customAudience1);
        customAudienceList.add(customAudience2);

        // Create multiple generic custom audience entries
        for (int i = 1; i <= 48; i++) {
            CustomAudience customAudience =
                    createCustomAudience(BUYER_1, "GENERIC_CA_" + i, bidsForBuyer1);
            customAudienceList.add(customAudience);
        }
        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle();
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        addDelayToAvoidThrottle();
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        addDelayToAvoidThrottle();
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void addDelayToAvoidThrottle() throws InterruptedException {
        addDelayToAvoidThrottle(DELAY_TO_AVOID_THROTTLE_MS);
    }

    private void addDelayToAvoidThrottle(int delayValueMs) throws InterruptedException {
        if (delayValueMs > 0) {
            Thread.sleep(delayValueMs);
        }
    }

    private CustomAudience createCustomAudience(
            final AdTechIdentifier buyer,
            String name,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the custom audience name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    getUri(
                                            buyer.toString(),
                                            AD_URI_PREFIX + name + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    private CustomAudience createCustomAudience(
            final AdTechIdentifier buyer, String name, List<Double> bids) {
        return createCustomAudience(
                buyer, name, bids, VALID_ACTIVATION_TIME, VALID_EXPIRATION_TIME);
    }

    private AdSelectionConfig createAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(mMockWebServerRule.uriForPath(DECISION_LOGIC_PATH))
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(
                        mMockWebServerRule.uriForPath(TRUSTED_SCORING_SIGNAL_PATH))
                // TODO(b/244530379) Make compatible with multiple buyers
                .setCustomAudienceBuyers(Collections.singletonList(BUYER_1))
                .build();
    }

    private Uri createExpectedWinningUri(
            AdTechIdentifier buyer, String customAudienceName, int adNumber) {
        return getUri(buyer.toString(), AD_URI_PREFIX + customAudienceName + "/ad" + adNumber);
    }

    // TODO(b/244530379) Make compatible with multiple buyers
    public Uri getValidDailyUpdateUriByBuyer(AdTechIdentifier buyer) {
        return mMockWebServerRule.uriForPath("/update");
    }

    public TrustedBiddingData getValidTrustedBiddingDataByBuyer(AdTechIdentifier buyer) {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(VALID_TRUSTED_BIDDING_KEYS)
                .setTrustedBiddingUri(getValidTrustedBiddingUriByBuyer(buyer))
                .build();
    }

    // TODO(b/244530379) Make compatible with multiple buyers
    public Uri getValidTrustedBiddingUriByBuyer(AdTechIdentifier buyer) {
        return mMockWebServerRule.uriForPath(VALID_TRUSTED_BIDDING_URI_PATH);
    }
}
