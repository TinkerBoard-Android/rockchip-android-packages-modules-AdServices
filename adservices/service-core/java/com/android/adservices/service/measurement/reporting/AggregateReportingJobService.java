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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Main service for scheduling aggregate reporting jobs. The actual job execution logic is part of
 * {@link AggregateReportingJobHandler}
 */
public final class AggregateReportingJobService extends JobService {

    private static final Executor sBlockingExecutor = AdServicesExecutors.getBlockingExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AggregateReportingJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params);
        }

        if (FlagsFactory.getFlags().getMeasurementJobAggregateReportingKillSwitch()) {
            LogUtil.e("AggregateReportingJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        LogUtil.d("AggregateReportingJobService.onStartJob");
        sBlockingExecutor.execute(
                () -> {
                    long maxAggregateReportUploadRetryWindowMs =
                            SystemHealthParams.MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;
                    boolean success =
                            new AggregateReportingJobHandler(
                                            EnrollmentDao.getInstance(getApplicationContext()),
                                            DatastoreManagerFactory.getDatastoreManager(
                                                    getApplicationContext()),
                                            ReportingStatus.UploadMethod.REGULAR)
                                    .performScheduledPendingReportsInWindow(
                                            System.currentTimeMillis()
                                                    - maxAggregateReportUploadRetryWindowMs,
                                            System.currentTimeMillis());
                    jobFinished(params, !success);
                });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("AggregateReportingJobService.onStopJob");
        return false;
    }

    /** Schedules {@link AggregateReportingJobService} */
    @VisibleForTesting
    static void schedule(Context context, JobScheduler jobScheduler) {
        final JobInfo job =
                new JobInfo.Builder(
                                AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID,
                                new ComponentName(context, AggregateReportingJobService.class))
                        .setRequiresDeviceIdle(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPeriodic(
                                AdServicesConfig.getMeasurementAggregateMainReportingJobPeriodMs())
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Aggregate Reporting Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getMeasurementJobAggregateReportingKillSwitch()) {
            LogUtil.d("AggregateReportingJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo job = jobScheduler.getPendingJob(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (job == null || forceSchedule) {
            schedule(context, jobScheduler);
            LogUtil.d("Scheduled AggregateReportingJobService");
        } else {
            LogUtil.d("AggregateReportingJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }
}
