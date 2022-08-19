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

package android.app.sdksandbox.testutils;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.os.OutcomeReceiver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FakeLoadSdkCallback implements OutcomeReceiver<SandboxedSdk, LoadSdkException> {
    private final CountDownLatch mLoadSdkLatch = new CountDownLatch(1);

    private boolean mLoadSdkSuccess;

    private int mErrorCode;
    private String mErrorMsg;
    private SandboxedSdk mSandboxedSdk;

    @Override
    public void onResult(SandboxedSdk sandboxedSdk) {
        mLoadSdkSuccess = true;
        mLoadSdkLatch.countDown();
        mSandboxedSdk = sandboxedSdk;
    }

    @Override
    public void onError(LoadSdkException exception) {
        mLoadSdkSuccess = false;
        mErrorCode = exception.getLoadSdkErrorCode();
        mErrorMsg = exception.getMessage();
        mLoadSdkLatch.countDown();
    }

    public boolean isLoadSdkSuccessful() {
        return isLoadSdkSuccessful(false);
    }

    public boolean isLoadSdkSuccessful(boolean ignoreSdkAlreadyLoadedError) {
        waitForLatch(mLoadSdkLatch);
        if (ignoreSdkAlreadyLoadedError
                && mErrorCode == SdkSandboxManager.LOAD_SDK_ALREADY_LOADED) {
            mLoadSdkSuccess = true;
        }
        return mLoadSdkSuccess;
    }

    public int getLoadSdkErrorCode() {
        waitForLatch(mLoadSdkLatch);
        assertThat(mLoadSdkSuccess).isFalse();
        return mErrorCode;
    }

    public String getLoadSdkErrorMsg() {
        waitForLatch(mLoadSdkLatch);
        return mErrorMsg;
    }

    public SandboxedSdk getSandboxedSdk() {
        waitForLatch(mLoadSdkLatch);
        return mSandboxedSdk;
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            // Wait for callback to be called
            final int waitTime = 5;
            if (!latch.await(waitTime, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Callback not called within " + waitTime + " seconds");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(
                    "Interrupted while waiting on callback: " + e.getMessage());
        }
    }
}
