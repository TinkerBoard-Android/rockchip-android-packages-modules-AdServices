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

package com.android.adservices.service.consent;

import com.android.internal.annotations.VisibleForTesting;

/** ConsentManager related Constants. */
public class ConsentConstants {

    @VisibleForTesting
    static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";

    @VisibleForTesting
    static final String GA_UX_NOTIFICATION_DISPLAYED_ONCE = "GA-UX-NOTIFICATION-DISPLAYED-ONCE";

    @VisibleForTesting
    static final String TOPICS_CONSENT_PAGE_DISPLAYED = "TOPICS-CONSENT-PAGE-DISPLAYED";

    @VisibleForTesting
    static final String FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED =
            "FLEDGE-AND-MSMT-CONSENT-PAGE-DISPLAYED";

    @VisibleForTesting static final String DEFAULT_CONSENT = "DEFAULT_CONSENT";

    @VisibleForTesting static final String CONSENT_KEY = "CONSENT";

    // Internal datastore version
    @VisibleForTesting static final int STORAGE_VERSION = 1;

    // Internal datastore filename. The name should be unique to avoid multiple threads or processes
    // to update the same file.
    @VisibleForTesting
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    // The name of shared preferences file to store status of one-time migrations.
    // Once a migration has happened, it marks corresponding shared preferences to prevent it
    // happens again.
    @VisibleForTesting static final String SHARED_PREFS_CONSENT = "PPAPI_Consent";

    // Shared preferences to mark whether PPAPI consent has been migrated to system server
    @VisibleForTesting
    static final String SHARED_PREFS_KEY_HAS_MIGRATED = "CONSENT_HAS_MIGRATED_TO_SYSTEM_SERVER";

    // Shared preferences to mark whether PPAPI consent has been cleared.
    @VisibleForTesting
    static final String SHARED_PREFS_KEY_PPAPI_HAS_CLEARED = "CONSENT_HAS_CLEARED_IN_PPAPI";

    static final String ERROR_MESSAGE_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";

    static final String ERROR_MESSAGE_WHILE_SET_CONTENT = "setConsent method failed.";

    static final String ERROR_MESSAGE_INVALID_CONSENT_SOURCE_OF_TRUTH =
            "Invalid type of consent source of truth.";
}