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

package android.adservices.measurement;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Class to hold parameters needed for getting the Measurement API status. This is an internal class
 * for communication between the {@link MeasurementManager} and {@link IMeasurementService} impl.
 *
 * @hide
 */
public final class StatusParam implements Parcelable {
    private final String mAppPackageName;

    private StatusParam(@NonNull Builder builder) {
        mAppPackageName = builder.mAppPackageName;
    }

    /** Unpack an StatusParam from a Parcel. */
    private StatusParam(Parcel in) {
        mAppPackageName = in.readString();
    }

    /** Creator for Parcelable (via reflection). */
    @NonNull
    public static final Creator<StatusParam> CREATOR =
            new Creator<StatusParam>() {
                @Override
                public StatusParam createFromParcel(Parcel in) {
                    return new StatusParam(in);
                }

                @Override
                public StatusParam[] newArray(int size) {
                    return new StatusParam[size];
                }
            };

    /** For Parcelable, no special marshalled objects. */
    public int describeContents() {
        return 0;
    }

    /** For Parcelable, write out to a Parcel in particular order. */
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        out.writeString(mAppPackageName);
    }

    /** Caller's package name used for getting the status. */
    @NonNull
    public String getAppPackageName() {
        return mAppPackageName;
    }

    /** A builder for {@link StatusParam}. */
    public static final class Builder {
        private String mAppPackageName;

        /**
         * Builder constructor for {@link StatusParam}.
         *
         * @param appPackageName caller's package name
         */
        public Builder(@NonNull String appPackageName) {
            Objects.requireNonNull(appPackageName);
            mAppPackageName = appPackageName;
        }

        /** Build the StatusParam. */
        @NonNull
        public StatusParam build() {
            return new StatusParam(this);
        }
    }
}
