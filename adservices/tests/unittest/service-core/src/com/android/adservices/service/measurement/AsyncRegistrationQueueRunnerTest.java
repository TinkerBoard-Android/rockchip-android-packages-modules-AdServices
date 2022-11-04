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
package com.android.adservices.service.measurement;

import static com.android.adservices.service.measurement.SystemHealthParams.MAX_TRIGGER_REGISTERS_PER_DESTINATION;
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.SqliteObjectMapperAccessor;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.registration.EnqueueAsyncRegistration;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;

/** Unit tests for {@link AsyncRegistrationQueueRunnerTest} */
public class AsyncRegistrationQueueRunnerTest {
    private static final Context sDefaultContext = ApplicationProvider.getApplicationContext();
    private static final String DEFAULT_ENROLLMENT_ID = "enrollment_id";
    private static final Uri DEFAULT_REGISTRANT = Uri.parse("android-app://com.registrant");
    private static final Uri DEFAULT_VERIFIED_DESTINATION = Uri.parse("android-app://com.example");
    private static final Uri APP_TOP_ORIGIN =
            Uri.parse("android-app://" + sDefaultContext.getPackageName());
    private static final Uri WEB_TOP_ORIGIN = Uri.parse("https://example.com");
    private static final Uri REGISTRATION_URI = Uri.parse("https://foo.com/bar?ad=134");
    private static final String LIST_TYPE_REDIRECT_URI_1 = "https://foo.com";
    private static final String LIST_TYPE_REDIRECT_URI_2 = "https://bar.com";
    private static final String LOCATION_TYPE_REDIRECT_URI = "https://baz.com";
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination.com");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");
    private static final Source SOURCE_1 =
            SourceFixture.getValidSourceBuilder()
                    .setEventId(new UnsignedLong(1L))
                    .setPublisher(APP_TOP_ORIGIN)
                    .setAppDestination(Uri.parse("android-app://com.destination1"))
                    .setWebDestination(Uri.parse("https://web-destination1.com"))
                    .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                    .setRegistrant(Uri.parse("android-app://com.example"))
                    .setEventTime(new Random().nextLong())
                    .setExpiryTime(8640000010L)
                    .setPriority(100L)
                    .setSourceType(Source.SourceType.EVENT)
                    .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                    .setDebugKey(new UnsignedLong(47823478789L))
                    .build();
    private static final Uri DEFAULT_WEB_DESTINATION = Uri.parse("https://def-web-destination.com");
    private static final Uri ALT_WEB_DESTINATION = Uri.parse("https://alt-web-destination.com");
    private static final Uri ALT_APP_DESTINATION =
            Uri.parse("android-app://com.alt-app_destination");
    private static final String DEFAULT_REGISTRATION = "https://foo.com";
    private static final Uri DEFAULT_OS_DESTINATION =
            Uri.parse("android-app://com.def-os-destination");
    private static final WebSourceParams DEFAULT_REGISTRATION_PARAM_LIST =
            new WebSourceParams.Builder(Uri.parse(DEFAULT_REGISTRATION))
                    .setDebugKeyAllowed(true)
                    .build();

    private static final Trigger TRIGGER =
            TriggerFixture.getValidTriggerBuilder()
                    .setAttributionDestination(APP_DESTINATION)
                    .setDestinationType(EventSurfaceType.APP)
                    .build();

    private AsyncSourceFetcher mAsyncSourceFetcher;
    private AsyncTriggerFetcher mAsyncTriggerFetcher;

    @Mock HttpsURLConnection mUrlConnection1;
    @Mock HttpsURLConnection mUrlConnection2;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private Source mMockedSource;
    @Mock private Trigger mMockedTrigger;
    @Mock private ITransaction mTransaction;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private ContentResolver mContentResolver;
    @Mock private ContentProviderClient mMockContentProviderClient;
    @Mock HttpsURLConnection mUrlConnection;
    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;

    private MockitoSession mStaticMockSession;

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    class FakeDatastoreManager extends DatastoreManager {

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbTestUtil.getDbHelperForTest().getWritableDatabase();
        emptyTables(db);
        mStaticMockSession.finishMocking();
    }

    @Before
    public void before() throws RemoteException {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mAsyncSourceFetcher = spy(new AsyncSourceFetcher(sDefaultContext));
        mAsyncTriggerFetcher = spy(new AsyncTriggerFetcher(sDefaultContext));
        MockitoAnnotations.initMocks(this);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT_ID));
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_success() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    // Tests for redirect types

    @Test
    public void runAsyncRegistrationQueueWorker_appSource_defaultRegistration_redirectTypeList()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse(LIST_TYPE_REDIRECT_URI_1),
                            Uri.parse(LIST_TYPE_REDIRECT_URI_2)));
                    asyncRedirect.setRedirectType(AsyncRegistration.RedirectType.NONE);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        List<Source.FakeReport> eventReportList = Collections.singletonList(
                new Source.FakeReport(new UnsignedLong(1L), 1L, APP_DESTINATION));
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(
                asyncRegistrationArgumentCaptor.capture());

        // Assert 'none' type redirect and values
        Assert.assertEquals(2, asyncRegistrationArgumentCaptor.getAllValues().size());

        AsyncRegistration asyncReg1 = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_1), asyncReg1.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.NONE, asyncReg1.getRedirectType());
        Assert.assertEquals(1, asyncReg1.getRedirectCount());

        AsyncRegistration asyncReg2 = asyncRegistrationArgumentCaptor.getAllValues().get(1);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_2), asyncReg2.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.NONE, asyncReg2.getRedirectType());
        Assert.assertEquals(1, asyncReg2.getRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSource_defaultRegistration_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse(LOCATION_TYPE_REDIRECT_URI)));
                    asyncRedirect.setRedirectType(AsyncRegistration.RedirectType.DAISY_CHAIN);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        List<Source.FakeReport> eventReportList = Collections.singletonList(
                new Source.FakeReport(new UnsignedLong(1L), 1L, APP_DESTINATION));
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1)).insertAsyncRegistration(
                asyncRegistrationArgumentCaptor.capture());

        // Assert 'location' type redirect and value
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncReg.getRedirectType());
        Assert.assertEquals(1, asyncReg.getRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appSource_middleRegistration_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource(
                AsyncRegistration.RedirectType.DAISY_CHAIN, 3);
        Answer<Optional<Source>> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse(LOCATION_TYPE_REDIRECT_URI)));
                    asyncRedirect.setRedirectType(AsyncRegistration.RedirectType.DAISY_CHAIN);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        List<Source.FakeReport> eventReportList = Collections.singletonList(
                new Source.FakeReport(new UnsignedLong(1L), 1L, APP_DESTINATION));
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(1)).insertAsyncRegistration(
                asyncRegistrationArgumentCaptor.capture());

        // Assert 'location' type redirect and value
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncReg.getRedirectType());
        Assert.assertEquals(4, asyncReg.getRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_defaultRegistration_redirectTypeList()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse(LIST_TYPE_REDIRECT_URI_1),
                            Uri.parse(LIST_TYPE_REDIRECT_URI_2)));
                    asyncRedirect.setRedirectType(AsyncRegistration.RedirectType.NONE);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(
                asyncRegistrationArgumentCaptor.capture());

        // Assert 'none' type redirect and values
        Assert.assertEquals(2, asyncRegistrationArgumentCaptor.getAllValues().size());

        AsyncRegistration asyncReg1 = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_1), asyncReg1.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.NONE, asyncReg1.getRedirectType());
        Assert.assertEquals(1, asyncReg1.getRedirectCount());

        AsyncRegistration asyncReg2 = asyncRegistrationArgumentCaptor.getAllValues().get(1);
        Assert.assertEquals(Uri.parse(LIST_TYPE_REDIRECT_URI_2), asyncReg2.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.NONE, asyncReg2.getRedirectType());
        Assert.assertEquals(1, asyncReg2.getRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_defaultReg_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse(LOCATION_TYPE_REDIRECT_URI)));
                    asyncRedirect.setRedirectType(AsyncRegistration.RedirectType.DAISY_CHAIN);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(1)).insertAsyncRegistration(
                asyncRegistrationArgumentCaptor.capture());

        // Assert 'location' type redirect and values
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());

        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncReg.getRedirectType());
        Assert.assertEquals(1, asyncReg.getRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void runAsyncRegistrationQueueWorker_appTrigger_middleRegistration_redirectTypeLocation()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger(
                AsyncRegistration.RedirectType.DAISY_CHAIN, 4);

        Answer<Optional<Trigger>> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse(LOCATION_TYPE_REDIRECT_URI)));
                    asyncRedirect.setRedirectType(AsyncRegistration.RedirectType.DAISY_CHAIN);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(1)).insertAsyncRegistration(
                asyncRegistrationArgumentCaptor.capture());

        // Assert 'location' type redirect and values
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());

        AsyncRegistration asyncReg = asyncRegistrationArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(Uri.parse(LOCATION_TYPE_REDIRECT_URI), asyncReg.getRegistrationUri());
        Assert.assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncReg.getRedirectType());
        Assert.assertEquals(5, asyncReg.getRedirectCount());

        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    // End tests for redirect types

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_noRedirects_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_NetworkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_noRedirects_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForAppTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    AsyncRedirect asyncRedirect = invocation.getArgument(2);
                    asyncRedirect.addToRedirects(List.of(
                            Uri.parse("https://example.com/sF1"),
                            Uri.parse("https://example.com/sF2")));
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_success() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_NetworkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebSource();

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration =  createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        AsyncRegistration validAsyncRegistration = createAsyncRegistrationForWebTrigger();

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus = invocation.getArgument(1);
                    asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void insertSource_withFakeReportsFalseAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION);
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getAppDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void insertSource_withFakeReportsFalseWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(null)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source, fakeReportsCount, SourceFixture.ValidSourceParams.WEB_DESTINATION);
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getWebDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void insertSource_withFalseAppAndWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build());
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();

        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION);

        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao, times(2))
                .insertAttribution(attributionRateLimitArgCaptor.capture());
        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getAppDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getAllValues().get(0));

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getWebDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getAllValues().get(1));
    }

    @Test
    public void insertSource_withFakeReportsNeverAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .build());
        List<Source.FakeReport> fakeReports = Collections.emptyList();
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                getSpyAsyncRegistrationQueueRunner();
        Answer<?> neverAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.NEVER);
                    return fakeReports;
                };
        doAnswer(neverAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getAppDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchSuccess() throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        // Execution
        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assertions
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsPrivacyParam_destination()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        // Execution
        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsMaxSourcesLimit()
            throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        // Execution
        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1)).getNumSourcesPerPublisher(any(), anyInt());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsPrivacyParam_adTech()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));
        // Execution
        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegisterWebSource_exceedsPrivacyParam_destination()
            throws RemoteException, DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        // Execution
        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assert
        assertFalse(status);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegisterWebSource_exceedsPrivacyParam_adTech() throws DatastoreException {
        // setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        // Execution
        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));

        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assert
        assertFalse(status);
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testRegisterWebSource_exceedsMaxSourcesLimit() throws DatastoreException {
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));
        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());

        // Execution
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assertions
        assertFalse(status);
    }

    @Test
    public void testRegisterWebSource_LimitsMaxSources_ForWebPublisher_WitheTLDMatch()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());

        // Execution
        boolean status =
                asyncRegistrationQueueRunner.isSourceAllowedToInsert(
                        SOURCE_1, SOURCE_1.getPublisher(), EventSurfaceType.APP, mMeasurementDao);

        // Assertions
        assertFalse(status);
    }

    @Test
    public void testRegisterTrigger_belowSystemHealthLimits_success() throws Exception {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        when(mMeasurementDao.getNumTriggersPerDestination(APP_DESTINATION, EventSurfaceType.APP))
                .thenReturn(0L);

        Truth.assertThat(
                        AsyncRegistrationQueueRunner.isTriggerAllowedToInsert(
                                mMeasurementDao, TRIGGER))
                .isTrue();
    }

    @Test
    public void testRegisterTrigger_atSystemHealthLimits_success() throws Exception {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        when(mMeasurementDao.getNumTriggersPerDestination(APP_DESTINATION, EventSurfaceType.APP))
                .thenReturn(MAX_TRIGGER_REGISTERS_PER_DESTINATION - 1L);

        Truth.assertThat(
                        AsyncRegistrationQueueRunner.isTriggerAllowedToInsert(
                                mMeasurementDao, TRIGGER))
                .isTrue();
    }

    @Test
    public void testRegisterTrigger_overSystemHealthLimits_failure() throws Exception {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        when(mMeasurementDao.getNumTriggersPerDestination(APP_DESTINATION, EventSurfaceType.APP))
                .thenReturn(MAX_TRIGGER_REGISTERS_PER_DESTINATION);

        Truth.assertThat(
                        AsyncRegistrationQueueRunner.isTriggerAllowedToInsert(
                                mMeasurementDao, TRIGGER))
                .isFalse();
    }

    @Test
    public void testRegisterAppSource_redirectOverridesWebAndOsDestinations()
            throws DatastoreException, IOException {
        // Setup
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        AsyncSourceFetcher mFetcher = spy(new AsyncSourceFetcher(mEnrollmentDao, mFlags, mLogger));
        doReturn(mUrlConnection1).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(mUrlConnection2).when(mFetcher).openUrl(new URL("https://foo-redirect.com"));
        when(mUrlConnection1.getResponseCode()).thenReturn(200);
        when(mUrlConnection2.getResponseCode()).thenReturn(200);
        when(mUrlConnection1.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + DEFAULT_WEB_DESTINATION
                                                + "\""
                                                + "}"),
                                "Attribution-Reporting-Redirect",
                                List.of("https://foo-redirect.com")));
        when(mUrlConnection2.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"priority\": \"321\",\n"
                                                + "  \"expiry\": \"987654\",\n"
                                                + "  \"source_event_id\": \"123456789\",\n"
                                                + "  \"destination\": \""
                                                + ALT_APP_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + ALT_WEB_DESTINATION
                                                + "\""
                                                + "}")));
        DatastoreManager datastoreManager =
                spy(new SQLDatastoreManager(DbTestUtil.getDbHelperForTest()));
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                datastoreManager));
        ArgumentCaptor<DatastoreManager.ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(DatastoreManager.ThrowingCheckedConsumer.class);
        EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                request, APP_TOP_ORIGIN, 100, mEnrollmentDao, datastoreManager);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(2L, (short) 5);

        // Assertion
        verify(datastoreManager, times(3)).runInTransaction(consumerArgCaptor.capture());
        consumerArgCaptor.getValue().accept(mMeasurementDao);
        try (Cursor cursor =
                DbTestUtil.getDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            Source source = SqliteObjectMapperAccessor.constructSourceFromCursor(cursor);
            assertEquals(new UnsignedLong(987654321L), source.getEventId());
            assertEquals(DEFAULT_WEB_DESTINATION, source.getWebDestination());
            assertEquals(DEFAULT_OS_DESTINATION, source.getAppDestination());
            Assert.assertTrue(cursor.moveToNext());
            source = SqliteObjectMapperAccessor.constructSourceFromCursor(cursor);
            assertEquals(new UnsignedLong(123456789L), source.getEventId());
            assertEquals(DEFAULT_WEB_DESTINATION, source.getWebDestination());
            assertEquals(DEFAULT_OS_DESTINATION, source.getAppDestination());
        }
    }

    @Test
    public void testRegisterWebSource_failsWebAndOsDestinationVerification()
            throws DatastoreException, IOException {
        // Setup
        AsyncSourceFetcher mFetcher = spy(new AsyncSourceFetcher(mEnrollmentDao, mFlags, mLogger));
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(DEFAULT_REGISTRATION_PARAM_LIST),
                        WEB_TOP_ORIGIN.toString(),
                        DEFAULT_OS_DESTINATION,
                        DEFAULT_WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + ALT_APP_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"web_destination\": \""
                                                + ALT_WEB_DESTINATION
                                                + "\""
                                                + "}")));
        DatastoreManager datastoreManager =
                spy(new SQLDatastoreManager(DbTestUtil.getDbHelperForTest()));
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                datastoreManager));
        ArgumentCaptor<DatastoreManager.ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(DatastoreManager.ThrowingCheckedConsumer.class);
        EnqueueAsyncRegistration.webSourceRegistrationRequest(
                request, APP_TOP_ORIGIN, 100, mEnrollmentDao, datastoreManager);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(2L, (short) 5);

        // Assertion
        verify(datastoreManager, times(2)).runInTransaction(consumerArgCaptor.capture());
        consumerArgCaptor.getValue().accept(mMeasurementDao);
        try (Cursor cursor =
                DbTestUtil.getDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.SourceContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertFalse(cursor.moveToNext());
        }
    }

    private RegistrationRequest buildRequest(String registrationUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse(registrationUri))
                .setPackageName(sDefaultContext.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(true)
                .build();
    }

    private WebSourceRegistrationRequest buildWebSourceRegistrationRequest(
            List<WebSourceParams> sourceParamsList,
            String topOrigin,
            Uri appDestination,
            Uri webDestination) {
        WebSourceRegistrationRequest.Builder webSourceRegistrationRequestBuilder =
                new WebSourceRegistrationRequest.Builder(sourceParamsList, Uri.parse(topOrigin))
                        .setAppDestination(appDestination);
        if (webDestination != null) {
            webSourceRegistrationRequestBuilder.setWebDestination(webDestination);
        }
        return webSourceRegistrationRequestBuilder.build();
    }

    private List<Source.FakeReport> createFakeReports(Source source, int count, Uri destination) {
        return IntStream.range(0, count)
                .mapToObj(
                        x ->
                                new Source.FakeReport(
                                        new UnsignedLong(0L),
                                        source.getReportingTimeForNoising(0),
                                        destination))
                .collect(Collectors.toList());
    }

    private static AsyncRegistration createAsyncRegistrationForAppSource() {
        return createAsyncRegistrationForAppSource(AsyncRegistration.RedirectType.ANY, 0);
    }

    private static AsyncRegistration createAsyncRegistrationForAppSource(
            @AsyncRegistration.RedirectType int redirectType, int redirectCount) {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                .setRegistrationUri(REGISTRATION_URI)
                // null .setWebDestination(webDestination)
                // null .setOsDestination(osDestination)
                .setRegistrant(DEFAULT_REGISTRANT)
                // null .setVerifiedDestination(null)
                .setTopOrigin(APP_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.APP_SOURCE.ordinal())
                .setSourceType(Source.SourceType.EVENT)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setLastProcessingTime(System.currentTimeMillis())
                .setRedirectType(redirectType)
                .setRedirectCount(redirectCount)
                .setDebugKeyAllowed(true)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForAppTrigger() {
        return createAsyncRegistrationForAppTrigger(AsyncRegistration.RedirectType.ANY, 0);
    }

    private static AsyncRegistration createAsyncRegistrationForAppTrigger(
            @AsyncRegistration.RedirectType int redirectType, int redirectCount) {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                .setRegistrationUri(REGISTRATION_URI)
                // null .setWebDestination(webDestination)
                // null .setOsDestination(osDestination)
                .setRegistrant(DEFAULT_REGISTRANT)
                // null .setVerifiedDestination(null)
                .setTopOrigin(APP_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.APP_TRIGGER.ordinal())
                // null .setSourceType(null)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setLastProcessingTime(System.currentTimeMillis())
                .setRedirectType(redirectType)
                .setRedirectCount(redirectCount)
                .setDebugKeyAllowed(true)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForWebSource() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                .setRegistrationUri(REGISTRATION_URI)
                .setWebDestination(WEB_DESTINATION)
                .setOsDestination(APP_DESTINATION)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setVerifiedDestination(DEFAULT_VERIFIED_DESTINATION)
                .setTopOrigin(WEB_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.WEB_SOURCE.ordinal())
                .setSourceType(Source.SourceType.EVENT)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setLastProcessingTime(System.currentTimeMillis())
                .setRedirectType(AsyncRegistration.RedirectType.NONE)
                .setDebugKeyAllowed(true)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForWebTrigger() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                .setRegistrationUri(REGISTRATION_URI)
                // null .setWebDestination(webDestination)
                // null .setOsDestination(osDestination)
                .setRegistrant(DEFAULT_REGISTRANT)
                // null .setVerifiedDestination(null)
                .setTopOrigin(WEB_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.WEB_TRIGGER.ordinal())
                // null .setSourceType(null)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setLastProcessingTime(System.currentTimeMillis())
                .setRedirectType(AsyncRegistration.RedirectType.NONE)
                .setDebugKeyAllowed(true)
                .build();
    }

    private AsyncRegistrationQueueRunner getSpyAsyncRegistrationQueueRunner() {
        return spy(new AsyncRegistrationQueueRunner(
                mContentResolver,
                mAsyncSourceFetcher,
                mAsyncTriggerFetcher,
                mEnrollmentDao,
                new FakeDatastoreManager()));
    }

    private static void emptyTables(SQLiteDatabase db) {
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
        db.delete("msmt_event_report", null, null);
        db.delete("msmt_attribution", null, null);
        db.delete("msmt_aggregate_report", null, null);
        db.delete("enrollment_data", null, null);
        db.delete("msmt_async_registration_contract", null, null);
    }
}
