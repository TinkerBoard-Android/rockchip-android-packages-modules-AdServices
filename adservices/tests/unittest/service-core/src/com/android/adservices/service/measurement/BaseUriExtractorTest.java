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

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link BaseUriExtractor} */
@SmallTest
public class BaseUriExtractorTest {

    @Test
    public void testGetBaseUri() {
        assertEquals(BaseUriExtractor.getBaseUri(Uri.parse("https://www.example.com/abc")),
                "https://www.example.com");
        assertEquals(BaseUriExtractor.getBaseUri(Uri.parse("android-app://com.example.sample")),
                "android-app://com.example.sample");
        assertEquals(BaseUriExtractor.getBaseUri(Uri.parse("https://www.example.com:8080/abc")),
                "https://www.example.com:8080");
    }
}
