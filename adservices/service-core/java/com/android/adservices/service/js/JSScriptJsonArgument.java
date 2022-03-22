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

package com.android.adservices.service.js;

import java.util.Objects;

/**
 * An argument defined as a JSON string. If you want to define a record structure to be serialized
 * as JSON use {@link JSScriptRecordArgument}
 */
class JSScriptJsonArgument extends JSScriptArgument {
    private final String mValue;

    JSScriptJsonArgument(String name, String value) {
        super(name);
        mValue = value;
    }

    @Override
    public String initializationValue() {
        return mValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSScriptJsonArgument)) return false;
        JSScriptJsonArgument that = (JSScriptJsonArgument) o;
        return mValue.equals(that.mValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mValue);
    }

    @Override
    public String toString() {
        return "JSScriptJsonArgument{" + "mValue='" + mValue + '\'' + '}';
    }
}
