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

import android.annotation.NonNull;
import android.os.Bundle;

/** The response returned from {@link SdkSandboxManager#loadSdk} on success. */
public final class LoadSdkResponse {
    private final Bundle mExtraInformation;

    /**
     * Initializes a {@link LoadSdkResponse}.
     *
     * @param extraInfo Extra information about the SDK loaded. This can later be retrieved using
     *     {@link #getExtraInformation}.
     */
    public LoadSdkResponse(@NonNull Bundle extraInfo) {
        mExtraInformation = extraInfo;
    }

    /** Returns extra information from the SDK in response to {@link SdkSandboxManager#loadSdk} */
    public @NonNull Bundle getExtraInformation() {
        return mExtraInformation;
    }
}
