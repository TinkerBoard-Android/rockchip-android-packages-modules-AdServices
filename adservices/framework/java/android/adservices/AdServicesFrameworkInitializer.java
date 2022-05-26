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

package android.adservices;

import static android.adservices.adselection.AdSelectionManager.AD_SELECTION_SERVICE;
import static android.adservices.common.AdServicesCommonManager.AD_SERVICES_COMMON_SERVICE;
import static android.adservices.customaudience.CustomAudienceManager.CUSTOM_AUDIENCE_SERVICE;
import static android.adservices.measurement.MeasurementManager.MEASUREMENT_SERVICE;
import static android.adservices.topics.TopicsManager.TOPICS_SERVICE;

import android.adservices.adselection.AdSelectionManager;
import android.adservices.common.AdServicesCommonManager;
import android.adservices.customaudience.CustomAudienceManager;
import android.adservices.measurement.MeasurementManager;
import android.adservices.topics.TopicsManager;
import android.annotation.SystemApi;
import android.app.SystemServiceRegistry;
import android.content.Context;

import com.android.adservices.LogUtil;

/**
 * Class holding initialization code for the AdServices module.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class AdServicesFrameworkInitializer {
    private AdServicesFrameworkInitializer() {
    }

    /**
     * Called by {@link SystemServiceRegistry}'s static initializer and registers all
     * AdServices services to {@link Context}, so that
     * {@link Context#getSystemService} can return them.
     *
     * @throws IllegalStateException if this is called from anywhere besides
     *     {@link SystemServiceRegistry}
     */
    public static void registerServiceWrappers() {
        LogUtil.d("Registering AdServices's TopicsManager.");
        SystemServiceRegistry.registerContextAwareService(
                TOPICS_SERVICE, TopicsManager.class,
                (c) -> new TopicsManager(c));

        LogUtil.d("Registering AdServices's CustomAudienceManager.");
        SystemServiceRegistry.registerContextAwareService(
                CUSTOM_AUDIENCE_SERVICE, CustomAudienceManager.class,
                (c) -> new CustomAudienceManager(c));

        LogUtil.d("Registering AdServices's AdSelectionManager.");
        SystemServiceRegistry.registerContextAwareService(
                AD_SELECTION_SERVICE, AdSelectionManager.class,
                (c) -> new AdSelectionManager(c));

        LogUtil.d("Registering AdServices's MeasurementManager.");
        SystemServiceRegistry.registerContextAwareService(
                MEASUREMENT_SERVICE, MeasurementManager.class,
                (c) -> new MeasurementManager(c));

        LogUtil.d("Registering AdServices's AdServicesCommonManager.");
        SystemServiceRegistry.registerContextAwareService(AD_SERVICES_COMMON_SERVICE,
                AdServicesCommonManager.class,
                (c) -> new AdServicesCommonManager(c));
    }
}
