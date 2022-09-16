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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class SdkSandboxControllerUnitTest {
    private static final String RESOURCES_PACKAGE = "com.android.codeproviderresources_1";

    private Context mContext;
    private SandboxedSdkContext mSandboxedSdkContext;
    private SdkSandboxLocalSingleton mSdkSandboxLocalSingleton;
    private StaticMockitoSession mStaticMockSession;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        ApplicationInfo info =
                mContext.getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        mSandboxedSdkContext =
                new SandboxedSdkContext(
                        androidx.test.InstrumentationRegistry.getContext(),
                        getClass().getClassLoader(),
                        "com.test.app",
                        info,
                        "com.test.sdk",
                        "testCe",
                        "testDe");

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkSandboxLocalSingleton.class)
                        .startMocking();
        mSdkSandboxLocalSingleton = Mockito.mock(SdkSandboxLocalSingleton.class);
        // Populate mSdkSandboxLocalSingleton
        ExtendedMockito.doReturn(mSdkSandboxLocalSingleton)
                .when(() -> SdkSandboxLocalSingleton.getExistingInstance());
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testCreateInstance() throws Exception {
        SdkSandboxController controller = new SdkSandboxController(mContext);
        assertThat(controller).isNotNull();
    }

    @Test
    public void testInitWithAnyContext() throws Exception {
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);
        assertThat(controller).isNotNull();
        // Does not fail on initialising with same context
        controller.initialize(mContext);
        assertThat(controller).isNotNull();
    }

    @Test
    public void testGetSandboxedSdks() throws RemoteException {
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);
        controller.initialize(mSandboxedSdkContext);

        // Mock singleton methods
        ISdkToServiceCallback serviceCallback = Mockito.mock(ISdkToServiceCallback.class);
        ArrayList<SandboxedSdk> sandboxedSdksMock = new ArrayList<>();
        sandboxedSdksMock.add(new SandboxedSdk(new Binder()));
        Mockito.when(serviceCallback.getSandboxedSdks(Mockito.anyString()))
                .thenReturn(sandboxedSdksMock);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(serviceCallback);

        List<SandboxedSdk> sandboxedSdks = controller.getSandboxedSdks();
        assertThat(sandboxedSdks).isEqualTo(sandboxedSdksMock);
    }

    @Test
    public void testGetSandboxedSdksFailsWithIncorrectContext() {
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);

        assertThrows(
                "Only available from the context obtained by calling android.app.sdksandbox"
                        + ".SandboxedSdkProvider#getContext()",
                UnsupportedOperationException.class,
                () -> controller.getSandboxedSdks());
    }
}
