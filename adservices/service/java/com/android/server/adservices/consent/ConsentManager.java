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
package com.android.server.adservices.consent;

import android.annotation.NonNull;
import android.app.adservices.consent.ConsentParcel;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Manager to handle user's consent. We will have one ConsentManager instance per user.
 *
 * @hide
 */
public final class ConsentManager {
    // The Data Schema Version of this binary.
    static final String DATA_SCHEMA_VERSION = "1";

    private static final String CONSENT_DIR = "consent";

    public static final String ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";

    @VisibleForTesting
    static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";

    private static final String CONSENT_KEY = "CONSENT";
    private static final String CONSENT_API_TYPE_PREFIX = "CONSENT_API_TYPE_";

    // Deprecate this since we store each version in its own folder.
    static final int STORAGE_VERSION = 1;
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    private final BooleanFileDatastore mDatastore;

    private ConsentManager(@NonNull BooleanFileDatastore datastore) {
        Objects.requireNonNull(datastore);

        mDatastore = datastore;
    }

    /** Create a ConsentManager with base directory and for userIdentifier */
    public static ConsentManager createConsentManager(String baseDir, int userIdentifier)
            throws IOException {
        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/data_schema_version/
        // Create the consent directory if needed.
        String consentDataStoreDir = getConsentDataStoreDir(baseDir, userIdentifier);
        final Path packageDir = Paths.get(consentDataStoreDir);
        if (!Files.exists(packageDir)) {
            Files.createDirectories(packageDir);
        }

        BooleanFileDatastore datastore = createAndInitBooleanFileDatastore(consentDataStoreDir);

        return new ConsentManager(datastore);
    }

    @NonNull
    @VisibleForTesting
    static BooleanFileDatastore createAndInitBooleanFileDatastore(String consentDataStoreDir)
            throws IOException {
        // Create the DataStore and initialize it.
        BooleanFileDatastore datastore =
                new BooleanFileDatastore(
                        consentDataStoreDir, STORAGE_XML_IDENTIFIER, STORAGE_VERSION);
        datastore.initialize();
        // TODO(b/259607624): implement a method in the datastore which would support
        // this exact scenario - if the value is null, return default value provided
        // in the parameter (similar to SP apply etc.)
        if (datastore.get(NOTIFICATION_DISPLAYED_ONCE) == null) {
            datastore.put(NOTIFICATION_DISPLAYED_ONCE, false);
        }
        return datastore;
    }

    @VisibleForTesting
    @NonNull
    static String getConsentDataStoreDir(String baseDir, int userIdentifier) {
        return baseDir + "/" + userIdentifier + "/" + CONSENT_DIR;
    }

    /** Retrieves the consent for all PP API services. */
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        LogUtil.d("ConsentManager.getConsent() is invoked for consentApiType = " + consentApiType);

        synchronized (this) {
            try {
                return new ConsentParcel.Builder()
                        .setConsentApiType(consentApiType)
                        .setIsGiven(mDatastore.get(getConsentApiTypeKey(consentApiType)))
                        .build();
            } catch (NullPointerException | IllegalArgumentException e) {
                LogUtil.e(e, ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT);
                return ConsentParcel.createRevokedConsent(consentApiType);
            }
        }
    }

    /** Set Consent */
    public void setConsent(ConsentParcel consentParcel) throws IOException {
        synchronized (this) {
            mDatastore.put(
                    getConsentApiTypeKey(consentParcel.getConsentApiType()),
                    consentParcel.isIsGiven());
            if (consentParcel.getConsentApiType() == ConsentParcel.ALL_API) {
                // Convert from 1 to 3 consents.
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.TOPICS), consentParcel.isIsGiven());
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.FLEDGE), consentParcel.isIsGiven());
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.MEASUREMENT), consentParcel.isIsGiven());
            } else {
                // Convert from 3 consents to 1 consent.
                if (mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.TOPICS), /* defaultValue */
                                false)
                        && mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.FLEDGE), /* defaultValue */
                                false)
                        && mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.MEASUREMENT), /* defaultValue */
                                false)) {
                    mDatastore.put(getConsentApiTypeKey(ConsentParcel.ALL_API), true);
                } else {
                    mDatastore.put(getConsentApiTypeKey(ConsentParcel.ALL_API), false);
                }
            }
        }
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed() throws IOException {
        synchronized (this) {
            try {
                // TODO(b/229725886): add metrics / logging
                mDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
            } catch (IOException e) {
                LogUtil.e(e, "Record notification failed due to IOException thrown by Datastore.");
            }
        }
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public boolean wasNotificationDisplayed() {
        synchronized (this) {
            return mDatastore.get(NOTIFICATION_DISPLAYED_ONCE);
        }
    }

    @VisibleForTesting
    String getConsentApiTypeKey(@ConsentParcel.ConsentApiType int consentApiType) {
        return CONSENT_API_TYPE_PREFIX + consentApiType;
    }

    /** tearDown method used for Testing only. */
    @VisibleForTesting
    public void tearDownForTesting() {
        synchronized (this) {
            mDatastore.tearDownForTesting();
        }
    }
}