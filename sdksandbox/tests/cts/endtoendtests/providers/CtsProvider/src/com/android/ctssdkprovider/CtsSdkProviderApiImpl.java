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

package com.android.ctssdkprovider;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import com.android.sdksandbox.SdkSandboxServiceImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CtsSdkProviderApiImpl extends ICtsSdkProviderApi.Stub {
    private Context mContext;

    private static final String STRING_RESOURCE = "Test String";
    private static final int INTEGER_RESOURCE = 1234;
    private static final String STRING_ASSET = "This is a test asset";
    private static final String ASSET_FILE = "test-asset.txt";

    CtsSdkProviderApiImpl(Context context) {
        mContext = context;
    }

    @Override
    public void checkClassloaders() {
        final ClassLoader ownClassloader = getClass().getClassLoader();
        if (ownClassloader == null) {
            throw new RuntimeException("SdkProvider loaded in top-level classloader");
        }

        final ClassLoader contextClassloader = mContext.getClassLoader();
        if (!ownClassloader.equals(contextClassloader)) {
            throw new RuntimeException("Different SdkProvider and Context classloaders");
        }

        try {
            Class<?> loadedClazz = ownClassloader.loadClass(SdkSandboxServiceImpl.class.getName());
            if (!ownClassloader.equals(loadedClazz.getClassLoader())) {
                throw new RuntimeException("SdkSandboxServiceImpl loaded with wrong classloader");
            }
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Couldn't find class bundled with SdkProvider", ex);
        }
    }

    @Override
    public void checkResourcesAndAssets() {
        Resources resources = mContext.getResources();
        String stringRes = resources.getString(R.string.test_string);
        int integerRes = resources.getInteger(R.integer.test_integer);
        if (!stringRes.equals(STRING_RESOURCE)) {
            throw new RuntimeException(createErrorMessage(STRING_RESOURCE, stringRes));
        }
        if (integerRes != INTEGER_RESOURCE) {
            throw new RuntimeException(
                    createErrorMessage(
                            String.valueOf(INTEGER_RESOURCE), String.valueOf(integerRes)));
        }

        AssetManager assets = mContext.getAssets();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(assets.open(ASSET_FILE)))) {
            String readAsset = reader.readLine();
            if (!readAsset.equals(STRING_ASSET)) {
                throw new RuntimeException(createErrorMessage(STRING_ASSET, readAsset));
            }
        } catch (IOException e) {
            throw new RuntimeException("File not found: " + ASSET_FILE);
        }
    }

    /* Sends an error if the expected resource/asset does not match the read value. */
    private String createErrorMessage(String expected, String actual) {
        return new String("Expected " + expected + ", actual " + actual);
    }
}