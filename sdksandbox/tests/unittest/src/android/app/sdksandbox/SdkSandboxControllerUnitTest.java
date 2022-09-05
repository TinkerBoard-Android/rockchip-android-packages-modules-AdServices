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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxControllerUnitTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testCreateInstance() throws Exception {
        SdkSandboxController controller = new SdkSandboxController(mContext);
        assertThat(controller).isNotNull();
    }

    @Test
    public void testGetInstance() throws Exception {
        assertThat(mContext.getSystemService(SdkSandboxController.class)).isNotNull();
    }
}
