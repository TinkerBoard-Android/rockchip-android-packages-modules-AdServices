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
package android.adservices.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.time.Instant;

/** Unit tests for {@link DeletionParam} */
@SmallTest
public final class DeletionParamTest {
    private static final String TAG = "DeletionRequestTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    private DeletionParam createExample() {
        return new DeletionParam.Builder()
                .setOriginUri(Uri.parse("http://foo.com"))
                .setStart(Instant.ofEpochMilli(1642060000000L))
                .setEnd(Instant.ofEpochMilli(1642060538000L))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }

    private DeletionParam createDefaultExample() {
        return new DeletionParam.Builder()
                .setOriginUri(null)
                .setStart(null)
                .setEnd(null)
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }

    void verifyExample(DeletionParam request) {
        assertEquals("http://foo.com", request.getOriginUri().toString());
        assertEquals(1642060000000L, request.getStart().toEpochMilli());
        assertEquals(1642060538000L, request.getEnd().toEpochMilli());
        assertNotNull(request.getAttributionSource());
    }

    void verifyDefaultExample(DeletionParam request) {
        assertNull(request.getOriginUri());
        assertNull(request.getStart());
        assertNull(request.getEnd());
        assertNotNull(request.getAttributionSource());
    }

    @Test
    public void testNoAttributionSource() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DeletionParam.Builder().build();
                });
    }

    @Test
    public void testDefaults() throws Exception {
        verifyDefaultExample(createDefaultExample());
    }

    @Test
    public void testCreation() throws Exception {
        verifyExample(createExample());
    }

    @Test
    public void testParcelingDelete() throws Exception {
        Parcel p = Parcel.obtain();
        createExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExample(DeletionParam.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testParcelingDeleteDefaults() throws Exception {
        Parcel p = Parcel.obtain();
        createDefaultExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyDefaultExample(DeletionParam.CREATOR.createFromParcel(p));
        p.recycle();
    }
}
