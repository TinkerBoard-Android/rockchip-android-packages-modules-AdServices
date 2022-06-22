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

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Download public encryption keys for aggregatable reports.
 *
 * @hide
 */
final class AggregateEncryptionKeyFetcher {
    /**
     * Provides a testing hook.
     */
    public @NonNull URLConnection openUrl(@NonNull URL url) throws IOException {
        return url.openConnection();
    }

    private static long getMaxAgeInSeconds(@NonNull Map<String, List<String>> headers) {
        String cacheControl = null;
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase("cache-control")) {
                List<String> field = headers.get(key);
                if (field != null && field.size() > 0) {
                    cacheControl = field.get(0).toLowerCase(Locale.ENGLISH);
                    break;
                }
            }
        }
        if (cacheControl == null) {
            LogUtil.d("Cache-Control header or value is missing");
            return 0;
        }
        String[] tokens = cacheControl.split(",", 0);
        long maxAge = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.startsWith("max-age=")) {
                try {
                    maxAge = Long.parseLong(token.substring(8));
                } catch (NumberFormatException e) {
                    LogUtil.d("Failed to parse max-age value %s", e);
                    return 0;
                }
            }
        }
        if (maxAge == 0) {
            LogUtil.d("max-age directive is missing");
            return 0;
        }
        return maxAge;
    }

    private static Optional<List<AggregateEncryptionKey>> parseResponse(
            @NonNull String responseBody,
            @NonNull Map<String, List<String>> headers,
            @NonNull long eventTime) {
        long maxAge = getMaxAgeInSeconds(headers);
        if (maxAge == 0) {
            return Optional.empty();
        }
        try {
            JSONObject responseObj = new JSONObject(responseBody);
            JSONArray keys = responseObj.getJSONArray(ResponseContract.KEYS);
            long expiry = eventTime + TimeUnit.SECONDS.toMillis(maxAge);
            List<AggregateEncryptionKey> aggregateEncryptionKeys = new ArrayList<>();
            for (int i = 0; i < keys.length(); i++) {
                JSONObject keyObj = keys.getJSONObject(i);
                AggregateEncryptionKey.Builder keyBuilder = new AggregateEncryptionKey.Builder();
                keyBuilder.setKeyId(keyObj.getString(ResponseContract.KEYS_KEY_ID));
                keyBuilder.setPublicKey(keyObj.getString(ResponseContract.KEYS_PUBLIC_KEY));
                keyBuilder.setExpiry(expiry);
                aggregateEncryptionKeys.add(keyBuilder.build());
            }
            return Optional.of(aggregateEncryptionKeys);
        } catch (JSONException e) {
            LogUtil.d("Invalid JSON %s", e);
            return Optional.empty();
        }
    }

    /**
     * Fetch public encryption keys for aggregatable reports.
     */
    public Optional<List<AggregateEncryptionKey>> fetch(
            @NonNull Uri target, @NonNull long eventTime) {
        // Require https.
        if (!target.getScheme().equals("https")) {
            return Optional.empty();
        }
        URL url;
        try {
            url = new URL(target.toString());
        } catch (MalformedURLException e) {
            LogUtil.d("Malformed coordinator target URL %s", e);
            return Optional.empty();
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            LogUtil.e("Failed to open coordinator target URL %s", e);
            return Optional.empty();
        }
        try {
            urlConnection.setRequestMethod("GET");
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            if (responseCode != 200) {
                return Optional.empty();
            }

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream()));

            String line;
            StringBuilder responseBody = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                responseBody.append(line);
            }
            bufferedReader.close();

            return parseResponse(responseBody.toString(), headers, eventTime);
        } catch (IOException e) {
            LogUtil.e("Failed to get coordinator response %s", e);
            return Optional.empty();
        } finally {
            urlConnection.disconnect();
        }
    }

    private interface ResponseContract {
        String KEYS = "keys";
        String KEYS_PUBLIC_KEY = "key";
        String KEYS_KEY_ID = "id";
    }
}
