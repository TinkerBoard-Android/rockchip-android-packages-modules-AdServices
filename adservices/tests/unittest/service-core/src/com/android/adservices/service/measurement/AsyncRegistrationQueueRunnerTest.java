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

import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private AsyncSourceFetcher mAsyncSourceFetcher;
    private AsyncTriggerFetcher mAsyncTriggerFetcher;

    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private Source mMockedSource;
    @Mock private Trigger mMockedTrigger;
    @Mock private ITransaction mTransaction;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private ContentResolver mContentResolver;
    @Mock private ContentProviderClient mMockContentProviderClient;

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
        SQLiteDatabase db = DbHelper.getInstance(sDefaultContext).safeGetWritableDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }
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
    //// HERE
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
}
