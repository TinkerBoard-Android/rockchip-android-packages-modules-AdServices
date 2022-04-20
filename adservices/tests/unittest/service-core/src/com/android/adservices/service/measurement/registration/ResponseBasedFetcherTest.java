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
package com.android.adservices.service.measurement.registration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Unit tests for {@link ResponseBasedFetcher}
 */
@SmallTest
public final class ResponseBasedFetcherTest {
    private static final String TAG = "ResponseBasedFetcherTest";

    @Test
    public void testIsSuccess() throws Exception {
        assertTrue(ResponseBasedFetcher.isSuccess(200));
        assertFalse(ResponseBasedFetcher.isSuccess(404));
        assertFalse(ResponseBasedFetcher.isSuccess(500));
        assertFalse(ResponseBasedFetcher.isSuccess(0));
    }

    @Test
    public void testIsRedirect() throws Exception {
        assertTrue(ResponseBasedFetcher.isRedirect(301));
        assertTrue(ResponseBasedFetcher.isRedirect(302));
        assertTrue(ResponseBasedFetcher.isRedirect(303));
        assertTrue(ResponseBasedFetcher.isRedirect(307));
        assertTrue(ResponseBasedFetcher.isRedirect(308));
        assertFalse(ResponseBasedFetcher.isRedirect(200));
        assertFalse(ResponseBasedFetcher.isRedirect(404));
        assertFalse(ResponseBasedFetcher.isRedirect(500));
        assertFalse(ResponseBasedFetcher.isRedirect(0));
    }

    @Test
    public void testParseRedirectsNothingInitial() throws Exception {
        ArrayList<Uri> redirs = new ArrayList();
        ResponseBasedFetcher.parseRedirects(true, Map.of(), redirs);
        assertEquals(0, redirs.size());
    }

    @Test
    public void testParseRedirectsNothingLater() throws Exception {
        ArrayList<Uri> redirs = new ArrayList();
        ResponseBasedFetcher.parseRedirects(false, Map.of(), redirs);
        assertEquals(0, redirs.size());
    }

    @Test
    public void testParseRedirectsARR() throws Exception {
        ArrayList<Uri> redirs = new ArrayList();
        ResponseBasedFetcher.parseRedirects(
                true, Map.of("Attribution-Reporting-Redirect",
                    List.of("foo.com", "bar.com")), redirs);
        assertEquals(2, redirs.size());
        assertEquals(Uri.parse("foo.com"), redirs.get(0));
        assertEquals(Uri.parse("bar.com"), redirs.get(1));
    }

    @Test
    public void testParseRedirectsSingleElementARR() throws Exception {
        ArrayList<Uri> redirs = new ArrayList();
        ResponseBasedFetcher.parseRedirects(
                true, Map.of("Attribution-Reporting-Redirect",
                    List.of("foo.com")), redirs);
        assertEquals(1, redirs.size());
    }

    @Test
    public void testParseRedirectsARRLater() throws Exception {
        ArrayList<Uri> redirs = new ArrayList();
        ResponseBasedFetcher.parseRedirects(
                false, Map.of("Attribution-Reporting-Redirect",
                    List.of("foo.com", "bar.com")), redirs);
        assertEquals(0, redirs.size());
    }
}
