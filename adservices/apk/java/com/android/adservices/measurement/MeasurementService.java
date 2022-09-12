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
package com.android.adservices.measurement;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.MeasurementServiceImpl;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.stats.Clock;

import java.util.Objects;

/** Measurement Service */
public class MeasurementService extends Service {

    /** The binder service. This field must only be accessed on the main thread. */
    private MeasurementServiceImpl mMeasurementService;

    @Override
    public void onCreate() {
        super.onCreate();
        Flags flags = FlagsFactory.getFlags();
        if (flags.getMeasurementKillSwitch()) {
            LogUtil.e("Measurement API is disabled");
            return;
        }

        if (mMeasurementService == null) {
            final AppImportanceFilter appImportanceFilter =
                    AppImportanceFilter.create(
                            this,
                            AD_SERVICES_API_CALLED__API_CLASS__MEASUREMENT,
                            () -> FlagsFactory.getFlags().getForegroundStatuslLevelForValidation());

            mMeasurementService =
                    new MeasurementServiceImpl(
                            this,
                            Clock.SYSTEM_CLOCK,
                            ConsentManager.getInstance(this),
                            EnrollmentDao.getInstance(this),
                            flags,
                            appImportanceFilter);
        }

        if (hasUserConsent()) {
            schedulePeriodicJobsIfNeeded();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (FlagsFactory.getFlags().getMeasurementKillSwitch()) {
            LogUtil.e("Measurement API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }
        return Objects.requireNonNull(mMeasurementService);
    }

    private boolean hasUserConsent() {
        return ConsentManager.getInstance(this).getConsent(this.getPackageManager()).isGiven();
    }

    private void schedulePeriodicJobsIfNeeded() {
        AggregateReportingJobService.scheduleIfNeeded(this, false);
        AggregateFallbackReportingJobService.scheduleIfNeeded(this, false);
        AttributionJobService.scheduleIfNeeded(this, false);
        EventReportingJobService.scheduleIfNeeded(this, false);
        EventFallbackReportingJobService.scheduleIfNeeded(this, false);
        DeleteExpiredJobService.scheduleIfNeeded(this, false);
        MddJobService.scheduleIfNeeded(this, false);
    }
}
