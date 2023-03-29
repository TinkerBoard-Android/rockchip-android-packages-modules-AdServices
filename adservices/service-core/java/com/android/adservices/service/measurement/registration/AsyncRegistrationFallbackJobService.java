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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;

/** Fallback Job Service for servicing queued registration requests */
public class AsyncRegistrationFallbackJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        if (ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(this)) {
            LogUtil.d(
                    "Disabling AsyncRegistrationFallbackJobService job because it's running in"
                            + " ExtServices on T+");
            return skipAndCancelBackgroundJob(params);
        }

        if (FlagsFactory.getFlags().getAsyncRegistrationFallbackJobKillSwitch()) {
            LogUtil.e("AsyncRegistrationFallbackJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        Instant jobStartTime = Clock.systemUTC().instant();
        LogUtil.d(
                "AsyncRegistrationFallbackJobService.onStartJob " + "at %s",
                jobStartTime.toString());
        AsyncRegistrationQueueRunner asyncQueueRunner =
                AsyncRegistrationQueueRunner.getInstance(getApplicationContext());

        AdServicesExecutors.getBackgroundExecutor()
                .execute(
                        () -> {
                            asyncQueueRunner.runAsyncRegistrationQueueWorker();
                            jobFinished(params, false);
                        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("AsyncRegistrationFallbackJobService.onStopJob");
        return false;
    }

    @VisibleForTesting
    protected static void schedule(Context context, JobScheduler jobScheduler) {
        final JobInfo job =
                new JobInfo.Builder(
                                MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID,
                                new ComponentName(
                                        context, AsyncRegistrationFallbackJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setPeriodic(
                                FlagsFactory.getFlags().getAsyncRegistrationJobQueueIntervalMs())
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }
    /**
     * Schedule Fallback Async Registration Job Service if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getAsyncRegistrationFallbackJobKillSwitch()) {
            LogUtil.e("AsyncRegistrationFallbackJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo job =
                jobScheduler.getPendingJob(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (job == null || forceSchedule) {
            schedule(context, jobScheduler);
            LogUtil.d("Scheduled AsyncRegistrationFallbackJobService");
        } else {
            LogUtil.d("AsyncRegistrationFallbackJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID);
        }
        // Tell the JobScheduler that the job is done and does not need to be rescheduled
        jobFinished(params, false);

        // Returning false to reschedule this job.
        return false;
    }
}
