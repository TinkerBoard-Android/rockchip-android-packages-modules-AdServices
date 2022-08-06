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
package com.android.adservices.topics;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJobService;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsServiceImpl;
import com.android.adservices.service.topics.TopicsWorker;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Objects;

/** Topics Service */
public class TopicsService extends Service {

    /** The binder service. This field must only be accessed on the main thread. */
    private TopicsServiceImpl mTopicsService;

    @Override
    public void onCreate() {
        super.onCreate();

        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LogUtil.e("Topics API is disabled");
            return;
        }

        if (mTopicsService == null) {
            mTopicsService =
                    new TopicsServiceImpl(
                            this,
                            TopicsWorker.getInstance(this),
                            ConsentManager.getInstance(this),
                            AdServicesLoggerImpl.getInstance(),
                            Clock.SYSTEM_CLOCK,
                            FlagsFactory.getFlags(),
                            Throttler.getInstance(
                                    FlagsFactory.getFlags().getSdkRequestPermitsPerSecond()),
                            EnrollmentDao.getInstance(this));
            mTopicsService.init();
        }

        schedulePeriodicJobs();
    }

    private void schedulePeriodicJobs() {
        MaintenanceJobService.schedule(this);
        EpochJobService.schedule(this);

        // TODO(b/238674236): Schedule this after the boot complete.
        MddJobService.schedule(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (FlagsFactory.getFlags().getTopicsKillSwitch()) {
            LogUtil.e("Topics API is disabled");
            // Return null so that clients can not bind to the service.
            return null;
        }
        return Objects.requireNonNull(mTopicsService);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
        FlagsFactory.getFlags().dump(writer, args);
        if (Build.isDebuggable()) {
            writer.println("Build is Debuggable, dumping information for TopicsService");
            EpochManager.getInstance(this).dump(writer, args);
            CacheManager.getInstance(this).dump(writer, args);
        } else {
            writer.println("Build is not Debuggable");
        }
    }
}
