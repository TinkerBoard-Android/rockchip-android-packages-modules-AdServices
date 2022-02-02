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

package com.android.supplementalprocess.app;

import android.app.Activity;
import android.os.Bundle;
import android.supplementalprocess.SupplementalProcessManager;
import android.supplementalprocess.testutils.FakeRemoteCodeCallback;

public class SupplementalProcessTestActivity extends Activity {

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        SupplementalProcessManager supplementalProcessManager =
                getApplicationContext().getSystemService(
                        SupplementalProcessManager.class);

        Bundle params = new Bundle();
        params.putString("code-provider-class", "com.android.testcode.TestCodeProvider");
        FakeRemoteCodeCallback callback = new FakeRemoteCodeCallback();
        supplementalProcessManager.loadCode(
                "com.android.testcode", "1", params, callback);
    }
}
