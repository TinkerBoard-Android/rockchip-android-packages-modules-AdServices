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

package com.android.adservices.data.measurement;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;

/**
 * Tests for {@link MeasurementDao} browser deletion that affect the database.
 */
@RunWith(Parameterized.class)
public class DeleteApiE2ETest extends DatabaseE2ETest {
    private final JSONObject mParam;

    // The 'name' parameter is needed for the JUnit parameterized
    // test, although it's ostensibly unused by this constructor.
    @SuppressWarnings("unused")
    public DeleteApiE2ETest(
            DbState input, DbState output, JSONObject param, String name) {
        super(input, output);
        mParam = param;
    }

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> data() throws IOException, JSONException {
        InputStream inputStream = sContext.getAssets().open(
                "measurement_browser_deletion_test.json");
        return DatabaseE2ETest.getTestCasesFrom(inputStream, (testObj) ->
                ((JSONObject) testObj).getJSONObject("param"));
    }

    public void runActionToTest() {
        final String registrantValue = (String) get("registrant");
        final String originValue = (String) get("origin");
        final Long startValue = (Long) get("start");
        final Long endValue = (Long) get("end");
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
            dao.deleteMeasurementData(
                    Uri.parse(registrantValue),
                    null == originValue ? null : Uri.parse(originValue),
                    null == startValue ? null : Instant.ofEpochMilli(startValue),
                    null == endValue ? null : Instant.ofEpochMilli(endValue)
            );
        });
    }

    private Object get(String name) {
        try {
            return mParam.has(name) ? mParam.get(name) : null;
        } catch (JSONException e) {
            throw new IllegalArgumentException("error reading " + name);
        }
    }
}
