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

package android.adservices.common;

import android.annotation.SystemApi;

/** Permissions used by the AdServices APIs. */
public class AdServicesPermissions {
    private AdServicesPermissions() {}

    /** This permission needs to be declared by the caller of Topics APIs. */
    public static final String ACCESS_ADSERVICES_TOPICS =
            "android.permission.ACCESS_ADSERVICES_TOPICS";

    /** This permission needs to be declared by the caller of Attribution APIs. */
    public static final String ACCESS_ADSERVICES_ATTRIBUTION =
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION";

    /** This permission needs to be declared by the caller of Custom Audiences APIs. */
    public static final String ACCESS_ADSERVICES_CUSTOM_AUDIENCE =
            "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE";

    /**
     * This permission needs to be declared by the caller of App Install APIS
     *
     * @hide
     */
    public static final String ACCESS_ADSERVICES_APP_INSTALL =
            "android.permission.ACCESS_ADSERVICES_APP_INSTALL";

    /** This permission needs to be declared by the caller of Advertising ID APIs. */
    public static final String ACCESS_ADSERVICES_AD_ID =
            "android.permission.ACCESS_ADSERVICES_AD_ID";

    /**
     * This is a signature permission that needs to be declared by the AdServices apk to access API
     * for AdID provided by another provider service. The signature permission is required to make
     * sure that only AdServices is permitted to access this api.
     *
     * @hide
     */
    @SystemApi
    public static final String ACCESS_PRIVILEGED_AD_ID =
            "android.permission.ACCESS_PRIVILEGED_AD_ID";

    /**
     * This is a signature permission needs to be declared by the AdServices apk to access API for
     * AppSetId provided by another provider service. The signature permission is required to make
     * sure that only AdServices is permitted to access this api.
     *
     * @hide
     */
    @SystemApi
    public static final String ACCESS_PRIVILEGED_APP_SET_ID =
            "android.permission.ACCESS_PRIVILEGED_APP_SET_ID";

    /**
     * The permission that lets it modify AdService's enablement state modification API.
     *
     * @hide
     */
    @SystemApi
    public static final String MODIFY_ADSERVICES_STATE =
            "android.permission.MODIFY_ADSERVICES_STATE";

    /**
     * The permission that lets it access AdService's enablement state modification API.
     *
     * @hide
     */
    @SystemApi
    public static final String ACCESS_ADSERVICES_STATE =
            "android.permission.ACCESS_ADSERVICES_STATE";

    /**
     * The permission needed to call AdServicesManager APIs
     *
     * @hide
     */
    public static final String ACCESS_ADSERVICES_MANAGER =
            "android.permission.ACCESS_ADSERVICES_MANAGER";
}
