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

package com.android.adservices.service.measurement.enrollment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** POJO for Adtech EnrollmentData, store the data download using MDD. */
public class EnrollmentData {

    private String mEnrollmentId;
    private String mCompanyId;
    private List<String> mAttributionSourceRegistrationUrl;
    private List<String> mAttributionTriggerRegistrationUrl;
    private List<String> mAttributionReportingUrl;
    private List<String> mRemarketingResponseBasedRegistrationUrl;
    private List<String> mEncryptionKeyUrl;

    private EnrollmentData() {
        mEnrollmentId = null;
        mCompanyId = null;
        mAttributionSourceRegistrationUrl = new ArrayList<>();
        mAttributionTriggerRegistrationUrl = new ArrayList<>();
        mAttributionReportingUrl = new ArrayList<>();
        mRemarketingResponseBasedRegistrationUrl = new ArrayList<>();
        mEncryptionKeyUrl = new ArrayList<>();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EnrollmentData)) {
            return false;
        }
        EnrollmentData enrollmentData = (EnrollmentData) obj;
        return Objects.equals(mEnrollmentId, enrollmentData.mEnrollmentId)
                && Objects.equals(mCompanyId, enrollmentData.mCompanyId)
                && Objects.equals(
                        mAttributionSourceRegistrationUrl,
                        enrollmentData.mAttributionSourceRegistrationUrl)
                && Objects.equals(
                        mAttributionTriggerRegistrationUrl,
                        enrollmentData.mAttributionTriggerRegistrationUrl)
                && Objects.equals(mAttributionReportingUrl, enrollmentData.mAttributionReportingUrl)
                && Objects.equals(
                        mRemarketingResponseBasedRegistrationUrl,
                        enrollmentData.mRemarketingResponseBasedRegistrationUrl)
                && Objects.equals(mEncryptionKeyUrl, enrollmentData.mEncryptionKeyUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mEnrollmentId,
                mCompanyId,
                mAttributionSourceRegistrationUrl,
                mAttributionTriggerRegistrationUrl,
                mAttributionReportingUrl,
                mRemarketingResponseBasedRegistrationUrl,
                mEncryptionKeyUrl);
    }

    /** Returns ID provided to the Adtech at the end of the enrollment process. */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** Returns ID assigned to the Parent Company. */
    public String getCompanyId() {
        return mCompanyId;
    }

    /** Returns URLs used to register attribution sources for measurement. */
    public List<String> getAttributionSourceRegistrationUrl() {
        return mAttributionSourceRegistrationUrl;
    }

    /** Returns URLs used to register triggers for measurement. */
    public List<String> getAttributionTriggerRegistrationUrl() {
        return mAttributionTriggerRegistrationUrl;
    }

    /** Returns URLs that the Measurement module will send Attribution reports to. */
    public List<String> getAttributionReportingUrl() {
        return mAttributionReportingUrl;
    }

    /** Returns URLs used for response-based-registration for joinCustomAudience. */
    public List<String> getRemarketingResponseBasedRegistrationUrl() {
        return mRemarketingResponseBasedRegistrationUrl;
    }

    /** Returns URLs used to fetch public/private keys for encrypting API requests. */
    public List<String> getEncryptionKeyUrl() {
        return mEncryptionKeyUrl;
    }

    /** Builder for {@link EnrollmentData}. */
    public static final class Builder {
        private final EnrollmentData mBuilding;

        public Builder() {
            mBuilding = new EnrollmentData();
        }

        /** See {@link EnrollmentData#getEnrollmentId()}. */
        public Builder setEnrollmentId(String enrollmentId) {
            mBuilding.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link EnrollmentData#getCompanyId()}. */
        public Builder setCompanyId(String companyId) {
            mBuilding.mCompanyId = companyId;
            return this;
        }

        /** See {@link EnrollmentData#getAttributionSourceRegistrationUrl()}. */
        public Builder setAttributionSourceRegistrationUrl(
                List<String> attributionSourceRegistrationUrl) {
            mBuilding.mAttributionSourceRegistrationUrl = attributionSourceRegistrationUrl;
            return this;
        }

        /** See {@link EnrollmentData#getAttributionTriggerRegistrationUrl()}. */
        public Builder setAttributionTriggerRegistrationUrl(
                List<String> attributionTriggerRegistrationUrl) {
            mBuilding.mAttributionTriggerRegistrationUrl = attributionTriggerRegistrationUrl;
            return this;
        }

        /** See {@link EnrollmentData#getAttributionReportingUrl()}. */
        public Builder setAttributionReportingUrl(List<String> attributionReportingUrl) {
            mBuilding.mAttributionReportingUrl = attributionReportingUrl;
            return this;
        }

        /** See {@link EnrollmentData#getRemarketingResponseBasedRegistrationUrl()}. */
        public Builder setRemarketingResponseBasedRegistrationUrl(
                List<String> remarketingResponseBasedRegistrationUrl) {
            mBuilding.mRemarketingResponseBasedRegistrationUrl =
                    remarketingResponseBasedRegistrationUrl;
            return this;
        }

        /** See {@link EnrollmentData#getEncryptionKeyUrl()}. */
        public Builder setEncryptionKeyUrl(List<String> encryptionKeyUrl) {
            mBuilding.mEncryptionKeyUrl = encryptionKeyUrl;
            return this;
        }

        /** Builder the {@link EnrollmentData}. */
        public EnrollmentData build() {
            return mBuilding;
        }
    }
}
