/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.ui.util;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

/** Util class for APK tests. */
public class ApkTestUtil {

    public static final String ADEXTSERVICES_PACKAGE_NAME = "com.google.android.ext.adservices.api";
    private static final String PRIVACY_SANDBOX_UI = "android.adservices.ui.SETTINGS";
    private static final String PRIVACY_SANDBOX_TEST_UI = "android.test.adservices.ui.MAIN";

    /**
     * Check whether the device is supported. Adservices doesn't support non-phone device.
     *
     * @return if the device is supported.
     */
    public static boolean isDeviceSupported() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = inst.getContext().getPackageManager();
        return !pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /** Returns the UiObject corresponding to a resource ID. */
    public static UiObject getElement(UiDevice device, int resId) {
        UiObject obj = device.findObject(new UiSelector().text(getString(resId)));
        if (!obj.exists()) {
            obj = device.findObject(new UiSelector().text(getString(resId).toUpperCase()));
        }
        return obj;
    }

    /** Returns the string corresponding to a resource ID. */
    public static String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    /** Returns the UiObject corresponding to a resource ID after scrolling. */
    public static void scrollToAndClick(UiDevice device, int resId)
            throws UiObjectNotFoundException {
        UiObject obj = scrollTo(device, resId);
        // objects may be partially hidden by the status bar and nav bars.
        obj.clickTopLeft();
    }

    public static UiObject scrollTo(UiDevice device, int resId) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));

        UiObject obj = device.findObject(new UiSelector().text(getString(resId)));
        scrollView.scrollIntoView(obj);
        return obj;
    }

    /** Returns the string corresponding to a resource ID and index. */
    public static UiObject getElement(UiDevice device, int resId, int index) {
        UiObject obj = device.findObject(new UiSelector().text(getString(resId)).instance(index));
        if (!obj.exists()) {
            obj =
                    device.findObject(
                            new UiSelector().text(getString(resId).toUpperCase()).instance(index));
        }
        return obj;
    }

    /** Returns the UiObject corresponding to a resource ID. */
    public static UiObject getPageElement(UiDevice device, int resId) {
        return device.findObject(new UiSelector().text(getString(resId)));
    }

    /** Launch Privacy Sandbox Setting View. */
    public static void launchSettingView(Context context, UiDevice device, int launchTimeout) {
        // Uses test package on S- since AdServices Activities in PRIVACY_SANDBOX_PACKAGE are
        // disabled by default on S-
        String privacySandboxUi;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            privacySandboxUi = PRIVACY_SANDBOX_TEST_UI;
        } else {
            privacySandboxUi = PRIVACY_SANDBOX_UI;
        }
        // Launch the setting view.
        Intent intent = new Intent(privacySandboxUi);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the view to appear
        device.wait(Until.hasObject(By.pkg(privacySandboxUi).depth(0)), launchTimeout);
    }
}
