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

package com.android.adservices.service.adselection;

import android.content.Context;

import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;

import java.time.Clock;
import java.util.Objects;

/** Factory for implementations of the {@link AdFilterer} interface */
public final class AdFilteringFeatureFactory {
    private final boolean mIsFledgeAdSelectionFilteringEnabled;
    private final AppInstallDao mAppInstallDao;
    private final FrequencyCapDao mFrequencyCapDao;

    public AdFilteringFeatureFactory(Context context, Flags flags) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        mIsFledgeAdSelectionFilteringEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAdSelectionFilteringEnabled);

        if (mIsFledgeAdSelectionFilteringEnabled) {
            mAppInstallDao = SharedStorageDatabase.getInstance(context).appInstallDao();
            mFrequencyCapDao = SharedStorageDatabase.getInstance(context).frequencyCapDao();
        } else {
            mAppInstallDao = null;
            mFrequencyCapDao = null;
        }
    }

    /**
     * Returns the correct {@link AdFilterer} implementation to use based on the given {@link
     * Flags}.
     *
     * @return an instance of {@link AdFiltererImpl} if ad selection filtering is enabled and an
     *     instance of {@link AdFiltererNoOpImpl} otherwise
     */
    public AdFilterer getAdFilterer() {
        if (mIsFledgeAdSelectionFilteringEnabled) {
            return new AdFiltererImpl(mAppInstallDao, mFrequencyCapDao, Clock.systemUTC());
        } else {
            return new AdFiltererNoOpImpl();
        }
    }
}