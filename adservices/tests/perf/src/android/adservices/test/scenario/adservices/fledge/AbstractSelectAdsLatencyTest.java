/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.test.scenario.adservices.utils.StaticAdTechServerUtils;
import android.content.Context;
import android.net.Uri;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class AbstractSelectAdsLatencyTest {
    protected static final String TAG = "SelectAds";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String CACHE_DB =
            "/data/data/com.google.android.adservices.api/databases/fledgehttpcache.db";
    private static final String CUSTOM_AUDIENCE_DB =
            "/data/data/com.google.android.adservices.api/databases/customaudience.db";
    protected static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    protected static final AdSelectionClient AD_SELECTION_CLIENT =
            new AdSelectionClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    protected static final AdvertisingCustomAudienceClient CUSTOM_AUDIENCE_CLIENT =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    protected final Ticker mTicker =
            new Ticker() {
                public long read() {
                    return android.os.SystemClock.elapsedRealtimeNanos();
                }
            };
    protected static List<CustomAudience> sCustomAudiences;

    @BeforeClass
    public static void setupBeforeClass() {
        StaticAdTechServerUtils.warmupServers();
        sCustomAudiences = new ArrayList<>();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        extendAuctionTimeouts();
        disableApiThrottling();
        disablePhenotypeFlagUpdates();
        enableAdservicesApi();
    }

    protected void runSelectAds(
            String customAudienceJson,
            String adSelectionConfig,
            String testClassName,
            String testName)
            throws Exception {
        // TODO(b/266194876): Clean up CA db entries before starting a test run. Cleaning up CAs
        // would ensure we run select ads only the CAs added by the test.
        sCustomAudiences.addAll(readCustomAudiences(customAudienceJson));
        joinCustomAudiences(sCustomAudiences);
        AdSelectionConfig config = readAdSelectionConfig(adSelectionConfig);

        Stopwatch timer = Stopwatch.createStarted(mTicker);
        AdSelectionOutcome outcome =
                AD_SELECTION_CLIENT
                        .selectAds(config)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        timer.stop();

        Log.i(TAG, generateLogLabel(testClassName, testName, timer.elapsed(TimeUnit.MILLISECONDS)));
        Assert.assertFalse(outcome.getRenderUri().toString().isEmpty());
    }

    protected void disableJsCache() throws Exception {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_http_cache_enable_js_caching false");
        // TODO(b/266194876): Clean up cache db entries when cache is disabled. Cleaning up cache
        // would ensure we are not occuping memory unnecessarily.
    }

    protected void enableJsCache() throws Exception {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_http_cache_enable_js_caching true");
    }

    protected void warmupSingleBuyerProcess() throws Exception {
        joinCustomAudiences(readCustomAudiences("CustomAudiencesOneBuyerOneCAOneAd.json"));
        AD_SELECTION_CLIENT
                .selectAds(readAdSelectionConfig("AdSelectionConfigOneBuyerOneCAOneAd.json"))
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    protected void warmupFiveBuyersProcess() throws Exception {
        joinCustomAudiences(readCustomAudiences("CustomAudiencesFiveBuyersOneCAFiveAds.json"));
        AD_SELECTION_CLIENT
                .selectAds(readAdSelectionConfig("AdSelectionConfigFiveBuyersOneCAFiveAds.json"))
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void extendAuctionTimeouts() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_ca_ms "
                        + "120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_scoring_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_overall_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_buyer_ms "
                        + "120000");
    }

    private static void disableApiThrottling() {
        ShellUtils.runShellCommand(
                "device_config put adservices sdk_request_permits_per_second 1000");
    }

    private static void disablePhenotypeFlagUpdates() {
        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests persistent");
    }

    private static void enableAdservicesApi() {
        ShellUtils.runShellCommand("setprop debug.adservices.disable_fledge_enrollment_check true");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices adservice_system_service_enabled true");
    }

    private ImmutableList<CustomAudience> readCustomAudiences(String fileName) throws Exception {
        ImmutableList.Builder<CustomAudience> customAudienceBuilder = ImmutableList.builder();
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        JSONArray customAudiencesJson = new JSONArray(new String(is.readAllBytes()));
        is.close();

        for (int i = 0; i < customAudiencesJson.length(); i++) {
            JSONObject caJson = customAudiencesJson.getJSONObject(i);
            JSONObject trustedBiddingDataJson = caJson.getJSONObject("trustedBiddingData");
            JSONArray trustedBiddingKeysJson =
                    trustedBiddingDataJson.getJSONArray("trustedBiddingKeys");
            JSONArray adsJson = caJson.getJSONArray("ads");

            ImmutableList.Builder<String> biddingKeys = ImmutableList.builder();
            for (int index = 0; index < trustedBiddingKeysJson.length(); index++) {
                biddingKeys.add(trustedBiddingKeysJson.getString(index));
            }

            ImmutableList.Builder<AdData> adDatas = ImmutableList.builder();
            for (int index = 0; index < adsJson.length(); index++) {
                JSONObject adJson = adsJson.getJSONObject(index);
                adDatas.add(
                        new AdData.Builder()
                                .setRenderUri(Uri.parse(adJson.getString("render_uri")))
                                .setMetadata(adJson.getString("metadata"))
                                .build());
            }

            customAudienceBuilder.add(
                    new CustomAudience.Builder()
                            .setBuyer(AdTechIdentifier.fromString(caJson.getString("buyer")))
                            .setName(caJson.getString("name"))
                            .setActivationTime(Instant.now())
                            .setExpirationTime(Instant.now().plus(90000, ChronoUnit.SECONDS))
                            .setDailyUpdateUri(Uri.parse(caJson.getString("dailyUpdateUri")))
                            .setUserBiddingSignals(
                                    AdSelectionSignals.fromString(
                                            caJson.getString("userBiddingSignals")))
                            .setTrustedBiddingData(
                                    new TrustedBiddingData.Builder()
                                            .setTrustedBiddingKeys(biddingKeys.build())
                                            .setTrustedBiddingUri(
                                                    Uri.parse(
                                                            trustedBiddingDataJson.getString(
                                                                    "trustedBiddingUri")))
                                            .build())
                            .setBiddingLogicUri(Uri.parse(caJson.getString("biddingLogicUri")))
                            .setAds(adDatas.build())
                            .build());
        }
        return customAudienceBuilder.build();
    }

    private AdSelectionConfig readAdSelectionConfig(String fileName) throws Exception {
        InputStream is = ApplicationProvider.getApplicationContext().getAssets().open(fileName);
        JSONObject adSelectionConfigJson = new JSONObject(new String(is.readAllBytes()));
        JSONArray buyersJson = adSelectionConfigJson.getJSONArray("custom_audience_buyers");
        JSONObject perBuyerSignalsJson = adSelectionConfigJson.getJSONObject("per_buyer_signals");
        is.close();

        ImmutableList.Builder<AdTechIdentifier> buyersBuilder = ImmutableList.builder();
        ImmutableMap.Builder<AdTechIdentifier, AdSelectionSignals> perBuyerSignals =
                ImmutableMap.builder();
        for (int i = 0; i < buyersJson.length(); i++) {
            AdTechIdentifier buyer = AdTechIdentifier.fromString(buyersJson.getString(i));
            buyersBuilder.add(buyer);
            perBuyerSignals.put(
                    buyer,
                    AdSelectionSignals.fromString(perBuyerSignalsJson.getString(buyer.toString())));
        }

        return new AdSelectionConfig.Builder()
                .setSeller(AdTechIdentifier.fromString(adSelectionConfigJson.getString("seller")))
                .setDecisionLogicUri(
                        Uri.parse(adSelectionConfigJson.getString("decision_logic_uri")))
                .setAdSelectionSignals(
                        AdSelectionSignals.fromString(
                                adSelectionConfigJson.getString("auction_signals")))
                .setSellerSignals(
                        AdSelectionSignals.fromString(
                                adSelectionConfigJson.getString("seller_signals")))
                .setTrustedScoringSignalsUri(
                        Uri.parse(adSelectionConfigJson.getString("trusted_scoring_signal_uri")))
                .setPerBuyerSignals(perBuyerSignals.build())
                .setCustomAudienceBuyers(buyersBuilder.build())
                .build();
    }

    private void joinCustomAudiences(List<CustomAudience> customAudiences) throws Exception {
        for (CustomAudience ca : customAudiences) {
            CUSTOM_AUDIENCE_CLIENT
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private String generateLogLabel(String classSimpleName, String testName, long elapsedMs) {
        return "("
                + "SELECT_ADS_LATENCY_"
                + classSimpleName
                + "#"
                + testName
                + ": "
                + elapsedMs
                + " ms)";
    }
}
