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

package android.adservices.adselection;

import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LogUtil;
import com.android.adservices.ServiceBinder;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * AdSelection Manager.
 *
 * @hide
 */
public class AdSelectionManager {
    public static final String AD_SELECTION_SERVICE = "ad_selection_service";

    /**
     * This field will be used once full implementation is ready.
     *
     * <p>TODO(b/212300065) remove the warning suppression once the service is implemented.
     */
    @SuppressWarnings("unused")
    private final Context mContext;

    private final ServiceBinder<AdSelectionService> mServiceBinder;

    /**
     * Create AdSelectionManager
     *
     * @hide
     */
    public AdSelectionManager(@NonNull Context context) {
        Objects.requireNonNull(context);

        mContext = context;
        mServiceBinder =
                ServiceBinder.getServiceBinder(
                        context,
                        AdServicesCommon.ACTION_AD_SELECTION_SERVICE,
                        AdSelectionService.Stub::asInterface);
    }

    @NonNull
    private AdSelectionService getService() {
        AdSelectionService service = mServiceBinder.getService();
        if (service == null) {
            throw new IllegalStateException("Unable to find the service");
        }
        return service;
    }

    /**
     * This method runs an asynchronous call to get the result of an on-device Ad selection. The
     * input {@code adSelectionConfig} is provided by the Ads SDK. The receiver either returns an
     * {@link AdSelectionOutcome} for a successful run, or an {@link AdServicesException} indicates
     * the error.
     */
    public void runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<AdSelectionOutcome, AdServicesException> receiver) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.runAdSelection(
                    adSelectionConfig,
                    new AdSelectionCallback.Stub() {
                        @Override
                        public void onSuccess(AdSelectionResponse resultParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(
                                                new AdSelectionOutcome.Builder()
                                                        .setAdSelectionId(
                                                                resultParcel.getAdSelectionId())
                                                        .setRenderUrl(resultParcel.getRenderUrl())
                                                        .build());
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Failure of AdSelection service.", e);
            receiver.onError(new AdServicesException("Failure of AdSelection service."));
        }
    }

    /**
     * Report the given impression. The {@link ReportImpressionInput} is provided by the Ads SDK.
     * The receiver either returns a {@code void} for a successful run, or an {@link
     * AdServicesException} indicates the error.
     */
    @NonNull
    public void reportImpression(
            @NonNull ReportImpressionInput input,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, AdServicesException> receiver) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(receiver);

        try {
            final AdSelectionService service = getService();
            service.reportImpression(
                    new ReportImpressionRequest.Builder()
                            .setAdSelectionId(input.getAdSelectionId())
                            .setAdSelectionConfig(input.getAdSelectionConfig())
                            .build(),
                    new ReportImpressionCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            executor.execute(
                                    () -> {
                                        receiver.onResult(null);
                                    });
                        }

                        @Override
                        public void onFailure(FledgeErrorResponse failureParcel) {
                            executor.execute(
                                    () -> {
                                        receiver.onError(failureParcel.asException());
                                    });
                        }
                    });
        } catch (RemoteException e) {
            LogUtil.e("Exception", e);
            receiver.onError(new AdServicesException("Internal Error!"));
        }
    }
}
