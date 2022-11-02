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

package com.android.adservices.data.measurement;

import static com.android.adservices.data.measurement.MeasurementTables.ALL_MSMT_TABLES;
import static com.android.adservices.data.measurement.MeasurementTables.AttributionContract;
import static com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import static com.android.adservices.data.measurement.MeasurementTables.MSMT_TABLE_PREFIX;
import static com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import static com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.measurement.DeletionRequest;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.AsyncRegistrationFixture;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MeasurementDaoTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Uri APP_TWO_SOURCES = Uri.parse("android-app://com.example1.two-sources");
    private static final Uri APP_ONE_SOURCE = Uri.parse("android-app://com.example2.one-source");
    private static final String DEFAULT_ENROLLMENT_ID = "enrollment-id";
    private static final Uri APP_TWO_PUBLISHER =
            Uri.parse("android-app://com.publisher2.two-sources");
    private static final Uri APP_ONE_PUBLISHER =
            Uri.parse("android-app://com.publisher1.one-source");
    private static final Uri APP_NO_PUBLISHER =
            Uri.parse("android-app://com.publisher3.no-sources");
    private static final Uri APP_BROWSER = Uri.parse("android-app://com.example1.browser");
    private static final Uri WEB_ONE_DESTINATION = Uri.parse("https://www.example1.com");
    private static final Uri WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN =
            Uri.parse("https://store.example1.com");
    private static final Uri WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2 =
            Uri.parse("https://foo.example1.com");
    private static final Uri WEB_TWO_DESTINATION = Uri.parse("https://www.example2.com");
    private static final Uri WEB_TWO_DESTINATION_WITH_PATH =
            Uri.parse("https://www.example2.com/ad/foo");
    private static final Uri APP_ONE_DESTINATION =
            Uri.parse("android-app://com.example1.one-trigger");
    private static final Uri APP_TWO_DESTINATION =
            Uri.parse("android-app://com.example1.two-triggers");
    private static final Uri APP_THREE_DESTINATION =
            Uri.parse("android-app://com.example1.three-triggers");
    private static final Uri APP_THREE_DESTINATION_PATH1 =
            Uri.parse("android-app://com.example1.three-triggers/path1");
    private static final Uri APP_THREE_DESTINATION_PATH2 =
            Uri.parse("android-app://com.example1.three-triggers/path2");
    private static final Uri APP_NO_TRIGGERS = Uri.parse("android-app://com.example1.no-triggers");
    private static final Uri INSTALLED_PACKAGE = Uri.parse("android-app://com.example.installed");
    private static final Uri WEB_PUBLISHER_ONE = Uri.parse("https://not.example.com");
    private static final Uri WEB_PUBLISHER_TWO = Uri.parse("https://notexample.com");
    private static final Uri WEB_PUBLISHER_THREE = Uri.parse("http://not.example.com");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.destination.example");

    // Fake ID count for initializing triggers.
    private int mValueId = 1;
    private MockitoSession mStaticMockSession;

    @Before
    public void before() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        for (String table : ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }

        mStaticMockSession.finishMocking();
    }

    @Test
    public void testInsertSource() {
        Source validSource = SourceFixture.getValidSource();
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertSource(validSource));

        try (Cursor sourceCursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(SourceContract.TABLE, null, null, null, null, null, null)) {
            assertTrue(sourceCursor.moveToNext());
            Source source = SqliteObjectMapper.constructSourceFromCursor(sourceCursor);
            assertNotNull(source);
            assertNotNull(source.getId());
            assertEquals(validSource.getPublisher(), source.getPublisher());
            assertEquals(validSource.getAppDestination(), source.getAppDestination());
            assertEquals(validSource.getWebDestination(), source.getWebDestination());
            assertEquals(validSource.getEnrollmentId(), source.getEnrollmentId());
            assertEquals(validSource.getRegistrant(), source.getRegistrant());
            assertEquals(validSource.getEventTime(), source.getEventTime());
            assertEquals(validSource.getExpiryTime(), source.getExpiryTime());
            assertEquals(validSource.getPriority(), source.getPriority());
            assertEquals(validSource.getSourceType(), source.getSourceType());
            assertEquals(validSource.getInstallAttributionWindow(),
                    source.getInstallAttributionWindow());
            assertEquals(validSource.getInstallCooldownWindow(), source.getInstallCooldownWindow());
            assertEquals(validSource.getAttributionMode(), source.getAttributionMode());
            assertEquals(validSource.getAggregateSource(), source.getAggregateSource());
            assertEquals(validSource.getFilterData(), source.getFilterData());
            assertEquals(validSource.getAggregateContributions(),
                    source.getAggregateContributions());
        }
    }

    @Test
    public void testInsertSource_reachedDbSizeLimitOnEdgeCase_doNotInsert() {
        insertSourceReachingDbSizeLimit(/* dbSize = */ 100L, /* dbSizeMaxLimit = */ 100L);
    }

    @Test
    public void testInsertSource_reachedDbSizeLimitUpperEdgeCase_doNotInsert() {
        insertSourceReachingDbSizeLimit(/* dbSize = */ 101L, /* dbSizeMaxLimit = */ 100L);
    }

    private void insertSourceReachingDbSizeLimit(long dbSize, long dbSizeMaxLimit) {
        final Source validSource = SourceFixture.getValidSource();

        final MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DbHelper.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            // Mocking that the DB file has a size of 100 bytes
            final DbHelper spyDbHelper = Mockito.spy(DbHelper.getInstance(sContext));
            ExtendedMockito.doReturn(spyDbHelper)
                    .when(() -> DbHelper.getInstance(ArgumentMatchers.any()));
            ExtendedMockito.doReturn(dbSize).when(spyDbHelper).getDbFileSize();

            // Mocking that the flags return a max limit size of 100 bytes
            Flags mockFlags = Mockito.mock(Flags.class);
            ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
            ExtendedMockito.doReturn(dbSizeMaxLimit).when(mockFlags).getMeasurementDbSizeLimit();

            DatastoreManagerFactory.getDatastoreManager(sContext)
                    .runInTransaction((dao) -> dao.insertSource(validSource));

            try (Cursor sourceCursor =
                    DbHelper.getInstance(sContext)
                            .getReadableDatabase()
                            .query(SourceContract.TABLE, null, null, null, null, null, null)) {
                assertFalse(sourceCursor.moveToNext());
            }
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testInsertTrigger() {
        Trigger validTrigger = TriggerFixture.getValidTrigger();
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertTrigger(validTrigger));

        try (Cursor triggerCursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(TriggerContract.TABLE, null, null, null, null, null, null)) {
            assertTrue(triggerCursor.moveToNext());
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(triggerCursor);
            assertNotNull(trigger);
            assertNotNull(trigger.getId());
            assertEquals(
                    validTrigger.getAttributionDestination(), trigger.getAttributionDestination());
            assertEquals(validTrigger.getDestinationType(), trigger.getDestinationType());
            assertEquals(validTrigger.getEnrollmentId(), trigger.getEnrollmentId());
            assertEquals(validTrigger.getRegistrant(), trigger.getRegistrant());
            assertEquals(validTrigger.getTriggerTime(), trigger.getTriggerTime());
            assertEquals(validTrigger.getEventTriggers(), trigger.getEventTriggers());
        }
    }

    @Test
    public void testInsertTrigger_reachedDbSizeLimitOnEdgeCase_doNotInsert() {
        insertTriggerReachingDbSizeLimit(/* dbSize = */ 100L, /* dbSizeMaxLimit = */ 100L);
    }

    @Test
    public void testInsertTrigger_reachedDbSizeLimitUpperEdgeCase_doNotInsert() {
        insertTriggerReachingDbSizeLimit(/* dbSize = */ 101L, /* dbSizeMaxLimit = */ 100L);
    }

    private void insertTriggerReachingDbSizeLimit(long dbSize, long dbSizeMaxLimit) {
        final Trigger validTrigger = TriggerFixture.getValidTrigger();

        final MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DbHelper.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        try {
            // Mocking that the DB file has a size of 100 bytes
            final DbHelper spyDbHelper = Mockito.spy(DbHelper.getInstance(sContext));
            ExtendedMockito.doReturn(spyDbHelper)
                    .when(() -> DbHelper.getInstance(ArgumentMatchers.any()));
            ExtendedMockito.doReturn(dbSize).when(spyDbHelper).getDbFileSize();

            // Mocking that the flags return a max limit size of 100 bytes
            Flags mockFlags = Mockito.mock(Flags.class);
            ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
            ExtendedMockito.doReturn(dbSizeMaxLimit).when(mockFlags).getMeasurementDbSizeLimit();

            DatastoreManagerFactory.getDatastoreManager(sContext)
                    .runInTransaction((dao) -> dao.insertTrigger(validTrigger));

            try (Cursor sourceCursor =
                    DbHelper.getInstance(sContext)
                            .getReadableDatabase()
                            .query(TriggerContract.TABLE, null, null, null, null, null, null)) {
                assertFalse(sourceCursor.moveToNext());
            }
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testGetNumSourcesPerPublisher_publisherTypeApp() {
        setupSourceAndTriggerData();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            2,
                            measurementDao.getNumSourcesPerPublisher(
                                    APP_TWO_PUBLISHER, EventSurfaceType.APP));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            1,
                            measurementDao.getNumSourcesPerPublisher(
                                    APP_ONE_PUBLISHER, EventSurfaceType.APP));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            0,
                            measurementDao.getNumSourcesPerPublisher(
                                    APP_NO_PUBLISHER, EventSurfaceType.APP));
                });
    }

    @Test
    public void testGetNumSourcesPerPublisher_publisherTypeWeb() {
        setupSourceDataForPublisherTypeWeb();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            1,
                            measurementDao.getNumSourcesPerPublisher(
                                    WEB_PUBLISHER_ONE, EventSurfaceType.WEB));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            1,
                            measurementDao.getNumSourcesPerPublisher(
                                    WEB_PUBLISHER_TWO, EventSurfaceType.WEB));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            1,
                            measurementDao.getNumSourcesPerPublisher(
                                    WEB_PUBLISHER_THREE, EventSurfaceType.WEB));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_atWindow() {
        Uri sourceSite = Uri.parse("android-app://publisher.app");
        Uri appDestination = Uri.parse("android-app://destination.app");
        String registrant = "android-app://registrant.app";
        List<Attribution> attributionsWithAppDestinations =
                getAttributionsWithDifferentEnrollments(
                        4, appDestination, 5000000001L, sourceSite, registrant);
        for (Attribution attribution : attributionsWithAppDestinations) {
            insertAttribution(attribution);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-0";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(Integer.valueOf(3), measurementDao
                            .countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
                                    sourceSite, appDestination, excludedEnrollmentId,
                                    5000000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_beyondWindow() {
        Uri sourceSite = Uri.parse("android-app://publisher.app");
        Uri appDestination = Uri.parse("android-app://destination.app");
        String registrant = "android-app://registrant.app";
        List<Attribution> attributionsWithAppDestinations =
                getAttributionsWithDifferentEnrollments(
                        4, appDestination, 5000000000L, sourceSite, registrant);
        for (Attribution attribution : attributionsWithAppDestinations) {
            insertAttribution(attribution);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-0";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(0),
                            measurementDao
                                    .countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
                                            sourceSite,
                                            appDestination,
                                            excludedEnrollmentId,
                                            5000000000L,
                                            6000000000L));
                });
    }

    @Test
    public void testGetNumTriggersPerRegistrant() {
        setupSourceAndTriggerData();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            2, measurementDao.getNumTriggersPerRegistrant(APP_TWO_DESTINATION));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            1, measurementDao.getNumTriggersPerRegistrant(APP_ONE_DESTINATION));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(0, measurementDao.getNumTriggersPerRegistrant(APP_NO_TRIGGERS));
                });
    }

    @Test
    public void singleAppTrigger_triggersPerDestination_returnsOne() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createAppTrigger(APP_ONE_DESTINATION, APP_ONE_DESTINATION));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao ->
                        assertThat(
                                        measurementDao.getNumTriggersPerDestination(
                                                APP_ONE_DESTINATION, EventSurfaceType.APP))
                                .isEqualTo(1));
    }

    @Test
    public void multipleAppTriggers_similarUris_triggersPerDestination() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createAppTrigger(APP_TWO_DESTINATION, APP_TWO_DESTINATION));
        triggerList.add(createAppTrigger(APP_TWO_DESTINATION, APP_TWO_DESTINATION));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao ->
                        assertThat(
                                        measurementDao.getNumTriggersPerDestination(
                                                APP_TWO_DESTINATION, EventSurfaceType.APP))
                                .isEqualTo(2));
    }

    @Test
    public void noAppTriggers_triggersPerDestination_returnsNone() {
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao ->
                        assertThat(
                                        measurementDao.getNumTriggersPerDestination(
                                                APP_NO_TRIGGERS, EventSurfaceType.APP))
                                .isEqualTo(0));
    }

    @Test
    public void multipleAppTriggers_differentPaths_returnsAllMatching() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION));
        triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION_PATH1));
        triggerList.add(createAppTrigger(APP_THREE_DESTINATION, APP_THREE_DESTINATION_PATH2));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            APP_THREE_DESTINATION, EventSurfaceType.APP))
                            .isEqualTo(3);
                    // Try the same thing, but use the app uri with path to find number of triggers.
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            APP_THREE_DESTINATION_PATH1, EventSurfaceType.APP))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            APP_THREE_DESTINATION_PATH2, EventSurfaceType.APP))
                            .isEqualTo(3);
                    Uri unseenAppThreePath =
                            Uri.parse("android-app://com.example1.three-triggers/path3");
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            unseenAppThreePath, EventSurfaceType.APP))
                            .isEqualTo(3);
                });
    }

    @Test
    public void singleWebTrigger_triggersPerDestination_returnsOne() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            WEB_ONE_DESTINATION, EventSurfaceType.WEB))
                            .isEqualTo(1);
                });
    }

    @Test
    public void webTriggerMultipleSubDomains_triggersPerDestination_returnsAllMatching() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
        triggerList.add(createWebTrigger(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN));
        triggerList.add(createWebTrigger(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            WEB_ONE_DESTINATION, EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN,
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2,
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            Uri.parse("https://new-subdomain.example1.com"),
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            Uri.parse("https://example1.com"),
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                });
    }

    @Test
    public void webTriggerWithoutSubdomains_triggersPerDestination_returnsAllMatching() {
        List<Trigger> triggerList = new ArrayList<>();
        Uri webDestinationWithoutSubdomain = Uri.parse("https://example1.com");
        Uri webDestinationWithoutSubdomainPath1 = Uri.parse("https://example1.com/path1");
        Uri webDestinationWithoutSubdomainPath2 = Uri.parse("https://example1.com/path2");
        Uri webDestinationWithoutSubdomainPath3 = Uri.parse("https://example1.com/path3");
        triggerList.add(createWebTrigger(webDestinationWithoutSubdomain));
        triggerList.add(createWebTrigger(webDestinationWithoutSubdomainPath1));
        triggerList.add(createWebTrigger(webDestinationWithoutSubdomainPath2));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            webDestinationWithoutSubdomain, EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            webDestinationWithoutSubdomainPath1,
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            webDestinationWithoutSubdomainPath2,
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            webDestinationWithoutSubdomainPath3,
                                            EventSurfaceType.WEB))
                            .isEqualTo(3);
                });
    }

    @Test
    public void webTriggerDifferentPaths_triggersPerDestination_returnsAllMatching() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createWebTrigger(WEB_TWO_DESTINATION));
        triggerList.add(createWebTrigger(WEB_TWO_DESTINATION_WITH_PATH));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            WEB_TWO_DESTINATION, EventSurfaceType.WEB))
                            .isEqualTo(2);
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            WEB_TWO_DESTINATION_WITH_PATH, EventSurfaceType.WEB))
                            .isEqualTo(2);
                });
    }

    @Test
    public void noMathingWebTriggers_triggersPerDestination_returnsZero() {
        List<Trigger> triggerList = new ArrayList<>();
        triggerList.add(createWebTrigger(WEB_ONE_DESTINATION));
        addTriggersToDatabase(triggerList);

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    Uri differentScheme = Uri.parse("http://www.example1.com");
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            differentScheme, EventSurfaceType.WEB))
                            .isEqualTo(0);

                    Uri notMatchingUrl2 = Uri.parse("https://www.not-example1.com");
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            notMatchingUrl2, EventSurfaceType.WEB))
                            .isEqualTo(0);

                    Uri notMatchingUrl = Uri.parse("https://www.not-example-1.com");
                    assertThat(
                                    measurementDao.getNumTriggersPerDestination(
                                            notMatchingUrl, EventSurfaceType.WEB))
                            .isEqualTo(0);
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_appDestination() {
        Uri sourceSite = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        String registrant = "android-app://registrant.app";
        List<Attribution> attributionsWithAppDestinations1 =
                getAttributionsWithDifferentEnrollments(
                        4, appDestination, 5000000000L, sourceSite, registrant);
        List<Attribution> attributionsWithAppDestinations2 =
                getAttributionsWithDifferentEnrollments(
                        2, appDestination, 5000000000L, sourceSite, registrant);
        List<Attribution> attributionsWithWebDestinations =
                getAttributionsWithDifferentEnrollments(
                        2, webDestination, 5500000000L, sourceSite, registrant);
        List<Attribution> attributionsOutOfWindow =
                getAttributionsWithDifferentEnrollments(
                        10, appDestination, 50000000000L, sourceSite, registrant);
        for (Attribution attribution : attributionsWithAppDestinations1) {
            insertAttribution(attribution);
        }
        for (Attribution attribution : attributionsWithAppDestinations2) {
            insertAttribution(attribution);
        }
        for (Attribution attribution : attributionsWithWebDestinations) {
            insertAttribution(attribution);
        }
        for (Attribution attribution : attributionsOutOfWindow) {
            insertAttribution(attribution);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-0";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(Integer.valueOf(3), measurementDao
                            .countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
                                    sourceSite, appDestination, excludedEnrollmentId,
                                    4000000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInAttribution_webDestination() {
        Uri sourceSite = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        String registrant = "android-app://registrant.app";
        List<Attribution> attributionsWithAppDestinations =
                getAttributionsWithDifferentEnrollments(
                        2, appDestination, 5000000000L, sourceSite, registrant);
        List<Attribution> attributionsWithWebDestinations1 =
                getAttributionsWithDifferentEnrollments(
                        4, webDestination, 5000000000L, sourceSite, registrant);
        List<Attribution> attributionsWithWebDestinations2 =
                getAttributionsWithDifferentEnrollments(
                        2, webDestination, 5500000000L, sourceSite, registrant);
        List<Attribution> attributionsOutOfWindow =
                getAttributionsWithDifferentEnrollments(
                        10, webDestination, 50000000000L, sourceSite, registrant);
        for (Attribution attribution : attributionsWithAppDestinations) {
            insertAttribution(attribution);
        }
        for (Attribution attribution : attributionsWithWebDestinations1) {
            insertAttribution(attribution);
        }
        for (Attribution attribution : attributionsWithWebDestinations2) {
            insertAttribution(attribution);
        }
        for (Attribution attribution : attributionsOutOfWindow) {
            insertAttribution(attribution);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-3";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(Integer.valueOf(3), measurementDao
                            .countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
                                    sourceSite, webDestination, excludedEnrollmentId,
                                    4000000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctDestinationsPerPublisherInActiveSource_atWindow() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000001L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(3),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.APP,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4500000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctDestinationsPerPublisherInActiveSource_expiredSource() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000001L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> expiredSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        6, true, true, 4500000001L, 6000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : expiredSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(3),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.APP,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4500000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctDestinationsPerPublisherInActiveSource_beyondWindow() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(0),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.APP,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4500000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctDestinationsPerPublisherInActiveSource_appPublisher() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentDestinations(
                        2, true, false, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentDestinations(
                        2, false, true, 5500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentDestinations(
                        10, true, true, 50000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentDestinations(
                        10, true, true, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(3),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.APP,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4000000000L, 6000000000L));
                });
    }

    // (Testing countDistinctDestinationsPerPublisherInActiveSource)
    @Test
    public void testCountDistinctDestinations_appPublisher_enrollmentMismatch() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentDestinations(
                        2, true, false, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentDestinations(
                        2, false, true, 5500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentDestinations(
                        10, true, true, 50000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentDestinations(
                        10, true, true, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(0),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.APP,
                                            "unmatched-enrollment-id", excludedDestination,
                                            EventSurfaceType.WEB, 4000000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctDestinationsPerPublisherInActiveSource_webPublisher_exactMatch() {
        Uri publisher = Uri.parse("https://publisher.com");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentDestinations(
                        2, true, false, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentDestinations(
                        2, false, true, 5500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentDestinations(
                        10, true, true, 50000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentDestinations(
                        10, true, true, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(3),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.WEB,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4000000000L, 6000000000L));
                });
    }

    // (Testing countDistinctDestinationsPerPublisherXEnrollmentInActiveSource)
    @Test
    public void testCountDistinctDestinations_webPublisher_doesNotMatchDomainAsSuffix() {
        Uri publisher = Uri.parse("https://publisher.com");
        Uri publisherAsSuffix = Uri.parse("https://prefix-publisher.com");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000000L, publisherAsSuffix,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentDestinations(
                        2, true, false, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentDestinations(
                        2, false, true, 5500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentDestinations(
                        10, true, true, 50000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentDestinations(
                        10, true, true, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(2),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.WEB,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4000000000L, 6000000000L));
                });
    }

    // (Testing countDistinctDestinationsPerPublisherXEnrollmentInActiveSource)
    @Test
    public void testCountDistinctDestinations_webPublisher_doesNotMatchDifferentScheme() {
        Uri publisher = Uri.parse("https://publisher.com");
        Uri publisherWithDifferentScheme = Uri.parse("http://publisher.com");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentDestinations(
                        4, true, true, 4500000000L, publisherWithDifferentScheme,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentDestinations(
                        2, true, false, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentDestinations(
                        2, false, true, 5500000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentDestinations(
                        10, true, true, 50000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentDestinations(
                        10, true, true, 5000000000L, publisher,
                        SourceFixture.ValidSourceParams.ENROLLMENT_ID, Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        Uri excludedDestination = Uri.parse("https://web-destination-2.com");
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(2),
                            measurementDao
                                    .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                                            publisher, EventSurfaceType.WEB,
                                            SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                                            excludedDestination, EventSurfaceType.WEB,
                                            4000000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_atWindow() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, webDestination, 4500000001L, publisher,
                        Source.Status.ACTIVE);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-1";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(1),
                            measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                                    publisher, EventSurfaceType.APP, appDestination,
                                    excludedEnrollmentId, 4500000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_beyondWindow() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, webDestination, 4500000000L, publisher,
                        Source.Status.ACTIVE);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-1";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(0),
                            measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                                    publisher, EventSurfaceType.APP, appDestination,
                                    excludedEnrollmentId, 4500000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_expiredSource() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, webDestination, 4500000001L, publisher,
                        Source.Status.ACTIVE);
        List<Source> expiredSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentEnrollments(
                        4, appDestination, webDestination, 4500000000L, 6000000000L,
                        publisher, Source.Status.ACTIVE);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : expiredSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-1";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(1),
                            measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                                    publisher, EventSurfaceType.APP, appDestination,
                                    excludedEnrollmentId, 4500000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_appDestination() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, webDestination, 4500000000L, publisher,
                        Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, null, 5000000000L, publisher, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, null, webDestination, 5500000000L, publisher, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentEnrollments(
                        10, appDestination, webDestination, 50000000000L, publisher,
                        Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentEnrollments(
                        3, appDestination, webDestination, 5000000000L, publisher,
                        Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-1";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(2),
                            measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                                    publisher, EventSurfaceType.APP, appDestination,
                                    excludedEnrollmentId, 4000000000L, 6000000000L));
                });
    }

    @Test
    public void testCountDistinctEnrollmentsPerPublisherXDestinationInSource_webDestination() {
        Uri publisher = Uri.parse("android-app://publisher.app");
        Uri webDestination = Uri.parse("https://web-destination.com");
        Uri appDestination = Uri.parse("android-app://destination.app");
        List<Source> activeSourcesWithAppAndWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, webDestination, 4500000000L, publisher,
                        Source.Status.ACTIVE);
        List<Source> activeSourcesWithAppDestinations =
                getSourcesWithDifferentEnrollments(
                        2, appDestination, null, 5000000000L, publisher, Source.Status.ACTIVE);
        List<Source> activeSourcesWithWebDestinations =
                getSourcesWithDifferentEnrollments(
                        2, null, webDestination, 5500000000L, publisher, Source.Status.ACTIVE);
        List<Source> activeSourcesOutOfWindow =
                getSourcesWithDifferentEnrollments(
                        10, appDestination, webDestination, 50000000000L, publisher,
                        Source.Status.ACTIVE);
        List<Source> ignoredSources =
                getSourcesWithDifferentEnrollments(
                        3, appDestination, webDestination, 5000000000L, publisher,
                        Source.Status.IGNORED);
        for (Source source : activeSourcesWithAppAndWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithAppDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesWithWebDestinations) {
            insertSource(source);
        }
        for (Source source : activeSourcesOutOfWindow) {
            insertSource(source);
        }
        for (Source source : ignoredSources) {
            insertSource(source);
        }
        DatastoreManager datastoreManager = DatastoreManagerFactory.getDatastoreManager(sContext);
        String excludedEnrollmentId = "enrollment-id-22";
        datastoreManager.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Integer.valueOf(3),
                            measurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                                    publisher, EventSurfaceType.APP, appDestination,
                                    excludedEnrollmentId, 4000000000L, 6000000000L));
                });
    }

    @Test
    public void testInstallAttribution_selectHighestPriority() {
        long currentTimestamp = System.currentTimeMillis();

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA1", currentTimestamp, 100, -1, false, DEFAULT_ENROLLMENT_ID),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA2", currentTimestamp, 50, -1, false, DEFAULT_ENROLLMENT_ID),
                db);
        // Should select id IA1 because it has higher priority
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE, currentTimestamp);
                                }));
        assertTrue(getInstallAttributionStatus("IA1", db));
        assertFalse(getInstallAttributionStatus("IA2", db));
        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_selectLatest() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false, DEFAULT_ENROLLMENT_ID),
                db);
        // Should select id=IA2 as it is latest
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE, currentTimestamp);
                                }));
        assertFalse(getInstallAttributionStatus("IA1", db));
        assertTrue(getInstallAttributionStatus("IA2", db));

        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_ignoreNewerSources() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false, DEFAULT_ENROLLMENT_ID),
                db);
        // Should select id=IA1 as it is the only valid choice.
        // id=IA2 is newer than the evenTimestamp of install event.
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE,
                                            currentTimestamp - TimeUnit.DAYS.toMillis(7));
                                }));
        assertTrue(getInstallAttributionStatus("IA1", db));
        assertFalse(getInstallAttributionStatus("IA2", db));

        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_noValidSource() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 10, 10, true, DEFAULT_ENROLLMENT_ID),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 10, 11, true, DEFAULT_ENROLLMENT_ID),
                db);
        // Should not update any sources.
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.doInstallAttribution(
                                                INSTALLED_PACKAGE, currentTimestamp)));
        assertFalse(getInstallAttributionStatus("IA1", db));
        assertFalse(getInstallAttributionStatus("IA2", db));
        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void doInstallAttribution_noValidSourceStatus_IgnoresSources() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Source source =
                createSourceForIATest(
                        "IA1", currentTimestamp, 100, -1, false, DEFAULT_ENROLLMENT_ID);

        // Execution
        // Active source should get install attributed
        source.setStatus(Source.Status.ACTIVE);
        AbstractDbIntegrationTest.insertToDb(source, db);
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.doInstallAttribution(
                                                INSTALLED_PACKAGE, currentTimestamp)));
        assertTrue(getInstallAttributionStatus("IA1", db));
        removeSources(Collections.singletonList("IA1"), db);

        // Active source should not get install attributed
        source.setStatus(Source.Status.IGNORED);
        AbstractDbIntegrationTest.insertToDb(source, db);
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.doInstallAttribution(
                                                INSTALLED_PACKAGE, currentTimestamp)));
        assertFalse(getInstallAttributionStatus("IA1", db));
        removeSources(Collections.singletonList("IA1"), db);

        // MARKED_TO_DELETE source should not get install attributed
        source.setStatus(Source.Status.MARKED_TO_DELETE);
        AbstractDbIntegrationTest.insertToDb(source, db);
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.doInstallAttribution(
                                                INSTALLED_PACKAGE, currentTimestamp)));
        assertFalse(getInstallAttributionStatus("IA1", db));
        removeSources(Collections.singletonList("IA1"), db);
    }

    @Test
    public void doInstallAttribution_withSourcesAcrossEnrollments_marksOneInstallFromEachAdTech() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);

        // Enrollment1: Choose IA2 because that's newer and still occurred before install
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA1", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID + "_1"),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA2", currentTimestamp, -1, 9, false, DEFAULT_ENROLLMENT_ID + "_1"),
                db);

        // Enrollment2: Choose IA4 because IA3's install attribution window has expired
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA3", currentTimestamp, -1, 10, true, DEFAULT_ENROLLMENT_ID + "_2"),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA4", currentTimestamp, -1, 9, false, DEFAULT_ENROLLMENT_ID + "_2"),
                db);

        // Enrollment3: Choose IA5 because IA6 was registered after install event
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA5", currentTimestamp, -1, 10, false, DEFAULT_ENROLLMENT_ID + "_3"),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA6", currentTimestamp, -1, 5, false, DEFAULT_ENROLLMENT_ID + "_3"),
                db);

        // Enrollment4: Choose IA8 due to higher priority
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA7", currentTimestamp, 5, 10, false, DEFAULT_ENROLLMENT_ID + "_4"),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA8", currentTimestamp, 10, 10, false, DEFAULT_ENROLLMENT_ID + "_4"),
                db);

        // Enrollment5: Choose none because both sources are ineligible
        // Expired install attribution window
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA9", currentTimestamp, 5, 31, true, DEFAULT_ENROLLMENT_ID + "_5"),
                db);
        // Registered after install attribution
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest(
                        "IA10", currentTimestamp, 10, 3, false, DEFAULT_ENROLLMENT_ID + "_5"),
                db);

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE,
                                            currentTimestamp - TimeUnit.DAYS.toMillis(7));
                                }));
        assertTrue(getInstallAttributionStatus("IA2", db));
        assertTrue(getInstallAttributionStatus("IA4", db));
        assertTrue(getInstallAttributionStatus("IA5", db));
        assertTrue(getInstallAttributionStatus("IA8", db));

        assertFalse(getInstallAttributionStatus("IA1", db));
        assertFalse(getInstallAttributionStatus("IA3", db));
        assertFalse(getInstallAttributionStatus("IA6", db));
        assertFalse(getInstallAttributionStatus("IA7", db));
        assertFalse(getInstallAttributionStatus("IA9", db));
        assertFalse(getInstallAttributionStatus("IA10", db));

        removeSources(
                Arrays.asList(
                        "IA1", "IA2", "IA3", "IA4", "IA5", "IA6", "IA7", "IA8", "IA8", "IA10"),
                db);
    }

    @Test
    public void deleteSources_providedIds_deletesMatchingSourcesAndRelatedData()
            throws JSONException {
        // Setup - Creates the following -
        // source - S1, S2, S3, S4
        // trigger - T1, T2, T3, T4
        // event reports - E11, E12, E21, E22, E23, E33, E44
        // aggregate reports - AR11, AR12, AR21, AR34
        // attributions - ATT11, ATT12, ATT21, ATT22, ATT33, ATT44
        prepareDataForSourceAndTriggerDeletion();

        // Execution
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        measurementDao -> {
                            measurementDao.deleteSources(List.of("S1", "S2"));

                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getSource("S1");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getSource("S2");
                                    });

                            assertNotNull(measurementDao.getSource("S3"));
                            assertNotNull(measurementDao.getSource("S4"));
                            assertNotNull(measurementDao.getTrigger("T1"));
                            assertNotNull(measurementDao.getTrigger("T2"));
                            assertNotNull(measurementDao.getTrigger("T3"));
                            assertNotNull(measurementDao.getTrigger("T4"));

                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getEventReport("E11");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getEventReport("E12");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getEventReport("E21");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getEventReport("E22");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getEventReport("E23");
                                    });
                            assertNotNull(measurementDao.getEventReport("E33"));
                            assertNotNull(measurementDao.getEventReport("E44"));

                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getAggregateReport("AR11");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getAggregateReport("AR12");
                                    });
                            assertThrows(
                                    DatastoreException.class,
                                    () -> {
                                        measurementDao.getAggregateReport("AR21");
                                    });
                            assertNotNull(measurementDao.getAggregateReport("AR34"));
                        });

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        assertEquals(2, DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE));
    }

    @Test
    public void deleteTriggers_providedIds_deletesMatchingTriggersAndRelatedData()
            throws JSONException {
        // Setup - Creates the following -
        // source - S1, S2, S3, S4
        // trigger - T1, T2, T3, T4
        // event reports - E11, E12, E21, E22, E23, E33, E44
        // aggregate reports - AR11, AR12, AR21, AR34
        // attributions - ATT11, ATT12, ATT21, ATT22, ATT33, ATT44
        prepareDataForSourceAndTriggerDeletion();

        // Execution
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        measurementDao -> {
                            measurementDao.deleteTriggers(List.of("T1", "T2"));

                            assertNotNull(measurementDao.getSource("S1"));
                            assertNotNull(measurementDao.getSource("S2"));
                            assertNotNull(measurementDao.getSource("S3"));
                            assertNotNull(measurementDao.getSource("S4"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getTrigger("T1"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getTrigger("T2"));
                            assertNotNull(measurementDao.getTrigger("T3"));
                            assertNotNull(measurementDao.getTrigger("T4"));

                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getEventReport("E11"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getEventReport("E12"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getEventReport("E21"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getEventReport("E22"));
                            assertNotNull(measurementDao.getEventReport("E23"));
                            assertNotNull(measurementDao.getEventReport("E33"));
                            assertNotNull(measurementDao.getEventReport("E44"));

                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getAggregateReport("AR11"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getAggregateReport("AR12"));
                            assertThrows(
                                    DatastoreException.class,
                                    () -> measurementDao.getAggregateReport("AR21"));

                            assertNotNull(measurementDao.getAggregateReport("AR34"));
                        });

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        assertEquals(2, DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE));
    }

    private void prepareDataForSourceAndTriggerDeletion() throws JSONException {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Source s1 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setId("S1")
                        .build(); // deleted
        Source s2 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(2L))
                        .setId("S2")
                        .build(); // deleted
        Source s3 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(3L))
                        .setId("S3")
                        .build();
        Source s4 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(4L))
                        .setId("S4")
                        .build();
        Trigger t1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                        .setId("T1")
                        .build();
        Trigger t2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                        .setId("T2")
                        .build();
        Trigger t3 =
                TriggerFixture.getValidTriggerBuilder()
                        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                        .setId("T3")
                        .build();
        Trigger t4 =
                TriggerFixture.getValidTriggerBuilder()
                        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                        .setId("T4")
                        .build();
        EventReport e11 = createEventReportForSourceAndTrigger("E11", s1, t1);
        EventReport e12 = createEventReportForSourceAndTrigger("E12", s1, t2);
        EventReport e21 = createEventReportForSourceAndTrigger("E21", s2, t1);
        EventReport e22 = createEventReportForSourceAndTrigger("E22", s2, t2);
        EventReport e23 = createEventReportForSourceAndTrigger("E23", s2, t3);
        EventReport e33 = createEventReportForSourceAndTrigger("E33", s3, t3);
        EventReport e44 = createEventReportForSourceAndTrigger("E44", s4, t4);
        AggregateReport ar11 = createAggregateReportForSourceAndTrigger("AR11", s1, t1);
        AggregateReport ar12 = createAggregateReportForSourceAndTrigger("AR12", s1, t2);
        AggregateReport ar21 = createAggregateReportForSourceAndTrigger("AR21", s2, t1);
        AggregateReport ar34 = createAggregateReportForSourceAndTrigger("AR34", s3, t4);
        Attribution att11 =
                createAttributionWithSourceAndTriggerIds(
                        "ATT11", s1.getId(), t1.getId()); // deleted
        Attribution att12 =
                createAttributionWithSourceAndTriggerIds(
                        "ATT12", s1.getId(), t2.getId()); // deleted
        Attribution att21 =
                createAttributionWithSourceAndTriggerIds(
                        "ATT21", s2.getId(), t1.getId()); // deleted
        Attribution att22 =
                createAttributionWithSourceAndTriggerIds(
                        "ATT22", s2.getId(), t2.getId()); // deleted
        Attribution att33 =
                createAttributionWithSourceAndTriggerIds("ATT33", s3.getId(), t3.getId());
        Attribution att44 =
                createAttributionWithSourceAndTriggerIds("ATT44", s4.getId(), t4.getId());

        AbstractDbIntegrationTest.insertToDb(s1, db);
        AbstractDbIntegrationTest.insertToDb(s2, db);
        AbstractDbIntegrationTest.insertToDb(s3, db);
        AbstractDbIntegrationTest.insertToDb(s4, db);

        AbstractDbIntegrationTest.insertToDb(t1, db);
        AbstractDbIntegrationTest.insertToDb(t2, db);
        AbstractDbIntegrationTest.insertToDb(t3, db);
        AbstractDbIntegrationTest.insertToDb(t4, db);

        AbstractDbIntegrationTest.insertToDb(e11, db);
        AbstractDbIntegrationTest.insertToDb(e12, db);
        AbstractDbIntegrationTest.insertToDb(e21, db);
        AbstractDbIntegrationTest.insertToDb(e22, db);
        AbstractDbIntegrationTest.insertToDb(e23, db);
        AbstractDbIntegrationTest.insertToDb(e33, db);
        AbstractDbIntegrationTest.insertToDb(e44, db);

        AbstractDbIntegrationTest.insertToDb(ar11, db);
        AbstractDbIntegrationTest.insertToDb(ar12, db);
        AbstractDbIntegrationTest.insertToDb(ar21, db);
        AbstractDbIntegrationTest.insertToDb(ar34, db);

        AbstractDbIntegrationTest.insertToDb(att11, db);
        AbstractDbIntegrationTest.insertToDb(att12, db);
        AbstractDbIntegrationTest.insertToDb(att21, db);
        AbstractDbIntegrationTest.insertToDb(att22, db);
        AbstractDbIntegrationTest.insertToDb(att33, db);
        AbstractDbIntegrationTest.insertToDb(att44, db);
    }

    @Test
    public void testUndoInstallAttribution_noMarkedSource() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Source source =
                createSourceForIATest(
                        "IA1", currentTimestamp, 10, 10, false, DEFAULT_ENROLLMENT_ID);
        source.setInstallAttributed(true);
        AbstractDbIntegrationTest.insertToDb(source, db);
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.undoInstallAttribution(INSTALLED_PACKAGE)));
        // Should set installAttributed = false for id=IA1
        assertFalse(getInstallAttributionStatus("IA1", db));
    }

    @Test
    public void getNumAggregateReportsPerDestination_returnsExpected() {
        List<AggregateReport> reportsWithPlainDestination =
                Arrays.asList(generateMockAggregateReport("https://destination-1.com", 1));
        List<AggregateReport> reportsWithPlainAndSubDomainDestination =
                Arrays.asList(
                        generateMockAggregateReport("https://destination-2.com", 2),
                        generateMockAggregateReport("https://subdomain.destination-2.com", 3));
        List<AggregateReport> reportsWithPlainAndPathDestination =
                Arrays.asList(
                        generateMockAggregateReport("https://subdomain.destination-3.com", 4),
                        generateMockAggregateReport("https://subdomain.destination-3.com/abcd", 5));
        List<AggregateReport> reportsWithAll3Types =
                Arrays.asList(
                        generateMockAggregateReport("https://destination-4.com", 6),
                        generateMockAggregateReport("https://subdomain.destination-4.com", 7),
                        generateMockAggregateReport("https://subdomain.destination-4.com/abcd", 8));
        List<AggregateReport> reportsWithAndroidAppDestination =
                Arrays.asList(generateMockAggregateReport("android-app://destination-5.app", 9));

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Stream.of(
                        reportsWithPlainDestination,
                        reportsWithPlainAndSubDomainDestination,
                        reportsWithPlainAndPathDestination,
                        reportsWithAll3Types,
                        reportsWithAndroidAppDestination)
                .flatMap(Collection::stream)
                .forEach(
                        aggregateReport -> {
                            ContentValues values = new ContentValues();
                            values.put(
                                    MeasurementTables.AggregateReport.ID, aggregateReport.getId());
                            values.put(
                                    MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                                    aggregateReport.getAttributionDestination().toString());
                            db.insert(MeasurementTables.AggregateReport.TABLE, null, values);
                        });

        List<String> attributionDestinations1 = createWebDestinationVariants(1);
        List<String> attributionDestinations2 = createWebDestinationVariants(2);
        List<String> attributionDestinations3 = createWebDestinationVariants(3);
        List<String> attributionDestinations4 = createWebDestinationVariants(4);
        List<String> attributionDestinations5 = createAppDestinationVariants(5);

        // expected query return values for attribution destination variants
        List<Integer> destination1ExpectedCounts = Arrays.asList(1, 1, 1, 1, 0);
        List<Integer> destination2ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
        List<Integer> destination3ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
        List<Integer> destination4ExpectedCounts = Arrays.asList(3, 3, 3, 3, 0);
        List<Integer> destination5ExpectedCounts = Arrays.asList(0, 0, 1, 1, 0);
        assertAggregateReportCount(
                attributionDestinations1, EventSurfaceType.WEB, destination1ExpectedCounts);
        assertAggregateReportCount(
                attributionDestinations2, EventSurfaceType.WEB, destination2ExpectedCounts);
        assertAggregateReportCount(
                attributionDestinations3, EventSurfaceType.WEB, destination3ExpectedCounts);
        assertAggregateReportCount(
                attributionDestinations4, EventSurfaceType.WEB, destination4ExpectedCounts);
        assertAggregateReportCount(
                attributionDestinations5, EventSurfaceType.APP, destination5ExpectedCounts);
    }

    @Test
    public void getNumEventReportsPerDestination_returnsExpected() {
        List<EventReport> reportsWithPlainDestination =
                Arrays.asList(generateMockEventReport("https://destination-1.com", 1));
        List<EventReport> reportsWithPlainAndSubDomainDestination =
                Arrays.asList(
                        generateMockEventReport("https://destination-2.com", 2),
                        generateMockEventReport("https://subdomain.destination-2.com", 3));
        List<EventReport> reportsWithPlainAndPathDestination =
                Arrays.asList(
                        generateMockEventReport("https://subdomain.destination-3.com", 4),
                        generateMockEventReport("https://subdomain.destination-3.com/abcd", 5));
        List<EventReport> reportsWithAll3Types =
                Arrays.asList(
                        generateMockEventReport("https://destination-4.com", 6),
                        generateMockEventReport("https://subdomain.destination-4.com", 7),
                        generateMockEventReport("https://subdomain.destination-4.com/abcd", 8));
        List<EventReport> reportsWithAndroidAppDestination =
                Arrays.asList(generateMockEventReport("android-app://destination-5.app", 9));

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Stream.of(
                        reportsWithPlainDestination,
                        reportsWithPlainAndSubDomainDestination,
                        reportsWithPlainAndPathDestination,
                        reportsWithAll3Types,
                        reportsWithAndroidAppDestination)
                .flatMap(Collection::stream)
                .forEach(
                        eventReport -> {
                            ContentValues values = new ContentValues();
                            values.put(
                                    MeasurementTables.EventReportContract.ID, eventReport.getId());
                            values.put(
                                    MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                                    eventReport.getAttributionDestination().toString());
                            db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
                        });

        List<String> attributionDestinations1 = createWebDestinationVariants(1);
        List<String> attributionDestinations2 = createWebDestinationVariants(2);
        List<String> attributionDestinations3 = createWebDestinationVariants(3);
        List<String> attributionDestinations4 = createWebDestinationVariants(4);
        List<String> attributionDestinations5 = createAppDestinationVariants(5);

        // expected query return values for attribution destination variants
        List<Integer> destination1ExpectedCounts = Arrays.asList(1, 1, 1, 1, 0);
        List<Integer> destination2ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
        List<Integer> destination3ExpectedCounts = Arrays.asList(2, 2, 2, 2, 0);
        List<Integer> destination4ExpectedCounts = Arrays.asList(3, 3, 3, 3, 0);
        List<Integer> destination5ExpectedCounts = Arrays.asList(0, 0, 1, 1, 0);
        assertEventReportCount(
                attributionDestinations1, EventSurfaceType.WEB, destination1ExpectedCounts);
        assertEventReportCount(
                attributionDestinations2, EventSurfaceType.WEB, destination2ExpectedCounts);
        assertEventReportCount(
                attributionDestinations3, EventSurfaceType.WEB, destination3ExpectedCounts);
        assertEventReportCount(
                attributionDestinations4, EventSurfaceType.WEB, destination4ExpectedCounts);
        assertEventReportCount(
                attributionDestinations5, EventSurfaceType.APP, destination5ExpectedCounts);
    }

    @Test
    public void testGetSourceEventReports() {
        List<Source> sourceList =
                Arrays.asList(
                        SourceFixture.getValidSourceBuilder()
                                .setId("1")
                                .setEventId(new UnsignedLong(3L))
                                .setEnrollmentId("1")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("2")
                                .setEventId(new UnsignedLong(4L))
                                .setEnrollmentId("1")
                                .build(),
                        // Should always be ignored
                        SourceFixture.getValidSourceBuilder()
                                .setId("3")
                                .setEventId(new UnsignedLong(4L))
                                .setEnrollmentId("2")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("15")
                                .setEventId(new UnsignedLong(15L))
                                .setEnrollmentId("2")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("16")
                                .setEventId(new UnsignedLong(16L))
                                .setEnrollmentId("2")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("20")
                                .setEventId(new UnsignedLong(20L))
                                .setEnrollmentId("2")
                                .build());

        List<Trigger> triggers =
                Arrays.asList(
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("101")
                                .setEnrollmentId("2")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("102")
                                .setEnrollmentId("2")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("201")
                                .setEnrollmentId("2")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("202")
                                .setEnrollmentId("2")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("1001")
                                .setEnrollmentId("2")
                                .build());

        // Should match with source 1
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(
                new EventReport.Builder()
                        .setId("1")
                        .setSourceEventId(new UnsignedLong(3L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(0).getAppDestination())
                        .setSourceType(sourceList.get(0).getSourceType())
                        .setSourceId("1")
                        .setTriggerId("101")
                        .build());
        reportList1.add(
                new EventReport.Builder()
                        .setId("7")
                        .setSourceEventId(new UnsignedLong(3L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(0).getAppDestination())
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceType(sourceList.get(0).getSourceType())
                        .setSourceId("1")
                        .setTriggerId("102")
                        .build());

        // Should match with source 2
        List<EventReport> reportList2 = new ArrayList<>();
        reportList2.add(
                new EventReport.Builder()
                        .setId("3")
                        .setSourceEventId(new UnsignedLong(4L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(1).getAppDestination())
                        .setSourceType(sourceList.get(1).getSourceType())
                        .setSourceId("2")
                        .setTriggerId("201")
                        .build());
        reportList2.add(
                new EventReport.Builder()
                        .setId("8")
                        .setSourceEventId(new UnsignedLong(4L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(1).getAppDestination())
                        .setSourceType(sourceList.get(1).getSourceType())
                        .setSourceId("2")
                        .setTriggerId("202")
                        .build());

        List<EventReport> reportList3 = new ArrayList<>();
        // Should not match with any source
        reportList3.add(
                new EventReport.Builder()
                        .setId("2")
                        .setSourceEventId(new UnsignedLong(5L))
                        .setEnrollmentId("1")
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceId("15")
                        .setTriggerId("1001")
                        .build());
        reportList3.add(
                new EventReport.Builder()
                        .setId("4")
                        .setSourceEventId(new UnsignedLong(6L))
                        .setEnrollmentId("1")
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceId("16")
                        .setTriggerId("1001")
                        .build());
        reportList3.add(
                new EventReport.Builder()
                        .setId("5")
                        .setSourceEventId(new UnsignedLong(1L))
                        .setEnrollmentId("1")
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceId("15")
                        .setTriggerId("1001")
                        .build());
        reportList3.add(
                new EventReport.Builder()
                        .setId("6")
                        .setSourceEventId(new UnsignedLong(2L))
                        .setEnrollmentId("1")
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceId("20")
                        .setTriggerId("1001")
                        .build());

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        sourceList.forEach(source -> AbstractDbIntegrationTest.insertToDb(source, db));
        triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

        Stream.of(reportList1, reportList2, reportList3)
                .flatMap(Collection::stream)
                .forEach(
                        (eventReport -> {
                            DatastoreManagerFactory.getDatastoreManager(sContext)
                                    .runInTransaction((dao) -> dao.insertEventReport(eventReport));
                        }));

        assertEquals(
                reportList1,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getSourceEventReports(sourceList.get(0)))
                        .orElseThrow());

        assertEquals(
                reportList2,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getSourceEventReports(sourceList.get(1)))
                        .orElseThrow());
    }

    @Test
    public void getSourceEventReports_sourcesWithSameEventId_haveSeparateEventReportsMatch() {
        List<Source> sourceList =
                Arrays.asList(
                        SourceFixture.getValidSourceBuilder()
                                .setId("1")
                                .setEventId(new UnsignedLong(1L))
                                .setEnrollmentId("1")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("2")
                                .setEventId(new UnsignedLong(1L))
                                .setEnrollmentId("1")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("3")
                                .setEventId(new UnsignedLong(2L))
                                .setEnrollmentId("2")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setId("4")
                                .setEventId(new UnsignedLong(2L))
                                .setEnrollmentId("2")
                                .build());

        List<Trigger> triggers =
                Arrays.asList(
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("101")
                                .setEnrollmentId("2")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setId("102")
                                .setEnrollmentId("2")
                                .build());

        // Should match with source 1
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(
                new EventReport.Builder()
                        .setId("1")
                        .setSourceEventId(new UnsignedLong(1L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(0).getAppDestination())
                        .setSourceType(sourceList.get(0).getSourceType())
                        .setSourceId("1")
                        .setTriggerId("101")
                        .build());
        reportList1.add(
                new EventReport.Builder()
                        .setId("2")
                        .setSourceEventId(new UnsignedLong(1L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(0).getAppDestination())
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceType(sourceList.get(0).getSourceType())
                        .setSourceId("1")
                        .setTriggerId("102")
                        .build());

        // Should match with source 2
        List<EventReport> reportList2 = new ArrayList<>();
        reportList2.add(
                new EventReport.Builder()
                        .setId("3")
                        .setSourceEventId(new UnsignedLong(2L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(1).getAppDestination())
                        .setSourceType(sourceList.get(1).getSourceType())
                        .setSourceId("2")
                        .setTriggerId("101")
                        .build());
        reportList2.add(
                new EventReport.Builder()
                        .setId("4")
                        .setSourceEventId(new UnsignedLong(2L))
                        .setEnrollmentId("1")
                        .setAttributionDestination(sourceList.get(1).getAppDestination())
                        .setSourceType(sourceList.get(1).getSourceType())
                        .setSourceId("2")
                        .setTriggerId("102")
                        .build());

        // Match with source3
        List<EventReport> reportList3 = new ArrayList<>();
        reportList3.add(
                new EventReport.Builder()
                        .setId("5")
                        .setSourceEventId(new UnsignedLong(2L))
                        .setEnrollmentId("2")
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceId("3")
                        .setTriggerId("101")
                        .build());
        reportList3.add(
                new EventReport.Builder()
                        .setId("6")
                        .setSourceEventId(new UnsignedLong(2L))
                        .setEnrollmentId("2")
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionDestination(APP_DESTINATION)
                        .setSourceId("3")
                        .setTriggerId("102")
                        .build());

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        sourceList.forEach(source -> AbstractDbIntegrationTest.insertToDb(source, db));
        triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));

        Stream.of(reportList1, reportList2, reportList3)
                .flatMap(Collection::stream)
                .forEach(
                        (eventReport -> {
                            DatastoreManagerFactory.getDatastoreManager(sContext)
                                    .runInTransaction((dao) -> dao.insertEventReport(eventReport));
                        }));

        assertEquals(
                reportList1,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getSourceEventReports(sourceList.get(0)))
                        .orElseThrow());

        assertEquals(
                reportList2,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getSourceEventReports(sourceList.get(1)))
                        .orElseThrow());

        assertEquals(
                reportList3,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao ->
                                        measurementDao.getSourceEventReports(sourceList.get(2)))
                        .orElseThrow());
    }

    @Test
    public void testUpdateSourceStatus() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);

        List<Source> sourceList = new ArrayList<>();
        sourceList.add(SourceFixture.getValidSourceBuilder().setId("1").build());
        sourceList.add(SourceFixture.getValidSourceBuilder().setId("2").build());
        sourceList.add(SourceFixture.getValidSourceBuilder().setId("3").build());
        sourceList.forEach(
                source -> {
                    ContentValues values = new ContentValues();
                    values.put(SourceContract.ID, source.getId());
                    values.put(SourceContract.STATUS, 1);
                    db.insert(SourceContract.TABLE, null, values);
                });

        // Multiple Elements
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.updateSourceStatus(
                                                List.of("1", "2", "3"), Source.Status.IGNORED)));

        // Single Element
        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.updateSourceStatus(
                                                List.of("1", "2"), Source.Status.IGNORED)));
    }

    @Test
    public void testGetMatchingActiveSources() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        String enrollmentId = "enrollment-id";
        Uri appDestination = Uri.parse("android-app://com.example.abc");
        Uri webDestination = Uri.parse("https://example.com");
        Uri webDestinationWithSubdomain = Uri.parse("https://xyz.example.com");
        Source sApp1 =
                SourceFixture.getValidSourceBuilder()
                        .setId("1")
                        .setEventTime(10)
                        .setExpiryTime(20)
                        .setAppDestination(appDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();
        Source sApp2 =
                SourceFixture.getValidSourceBuilder()
                        .setId("2")
                        .setEventTime(10)
                        .setExpiryTime(50)
                        .setAppDestination(appDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();
        Source sApp3 =
                SourceFixture.getValidSourceBuilder()
                        .setId("3")
                        .setEventTime(20)
                        .setExpiryTime(50)
                        .setAppDestination(appDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();
        Source sApp4 =
                SourceFixture.getValidSourceBuilder()
                        .setId("4")
                        .setEventTime(30)
                        .setExpiryTime(50)
                        .setAppDestination(appDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();
        Source sWeb5 =
                SourceFixture.getValidSourceBuilder()
                        .setId("5")
                        .setEventTime(10)
                        .setExpiryTime(20)
                        .setWebDestination(webDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();
        Source sWeb6 =
                SourceFixture.getValidSourceBuilder()
                        .setId("6")
                        .setEventTime(10)
                        .setExpiryTime(50)
                        .setWebDestination(webDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();
        Source sAppWeb7 =
                SourceFixture.getValidSourceBuilder()
                        .setId("7")
                        .setEventTime(10)
                        .setExpiryTime(20)
                        .setAppDestination(appDestination)
                        .setWebDestination(webDestination)
                        .setEnrollmentId(enrollmentId)
                        .build();

        List<Source> sources = Arrays.asList(sApp1, sApp2, sApp3, sApp4, sWeb5, sWeb6, sAppWeb7);
        sources.forEach(source -> insertInDb(db, source));

        Function<Trigger, List<Source>> runFunc = trigger -> {
            List<Source> result = DatastoreManagerFactory.getDatastoreManager(sContext)
                    .runInTransactionWithResult(
                            measurementDao -> measurementDao.getMatchingActiveSources(trigger)
                    ).orElseThrow();
            result.sort(Comparator.comparing(Source::getId));
            return result;
        };

        // Trigger Time > sApp1's eventTime and < sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time < sApp3's eventTime
        // Trigger Time < sApp4's eventTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
        // Expected: Match with sApp1, sApp2, sAppWeb7
        Trigger trigger1MatchSource1And2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(12)
                        .setEnrollmentId(enrollmentId)
                        .setAttributionDestination(appDestination)
                        .setDestinationType(EventSurfaceType.APP)
                        .build();
        List<Source> result1 = runFunc.apply(trigger1MatchSource1And2);
        assertEquals(3, result1.size());
        assertEquals(sApp1.getId(), result1.get(0).getId());
        assertEquals(sApp2.getId(), result1.get(1).getId());
        assertEquals(sAppWeb7.getId(), result1.get(2).getId());

        // Trigger Time > sApp1's eventTime and = sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time = sApp3's eventTime
        // Trigger Time < sApp4's eventTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's eventTime and = sAppWeb7's expiryTime
        // Expected: Match with sApp2, sApp3
        Trigger trigger2MatchSource127 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(20)
                        .setEnrollmentId(enrollmentId)
                        .setAttributionDestination(appDestination)
                        .setDestinationType(EventSurfaceType.APP)
                        .build();

        List<Source> result2 = runFunc.apply(trigger2MatchSource127);
        assertEquals(2, result2.size());
        assertEquals(sApp2.getId(), result2.get(0).getId());
        assertEquals(sApp3.getId(), result2.get(1).getId());

        // Trigger Time > sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
        // Trigger Time < sApp4's eventTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's expiryTime
        // Expected: Match with sApp2, sApp3
        Trigger trigger3MatchSource237 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(21)
                        .setEnrollmentId(enrollmentId)
                        .setAttributionDestination(appDestination)
                        .setDestinationType(EventSurfaceType.APP)
                        .build();

        List<Source> result3 = runFunc.apply(trigger3MatchSource237);
        assertEquals(2, result3.size());
        assertEquals(sApp2.getId(), result3.get(0).getId());
        assertEquals(sApp3.getId(), result3.get(1).getId());

        // Trigger Time > sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
        // Trigger Time > sApp4's eventTime and < sApp4's expiryTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's expiryTime
        // Expected: Match with sApp2, sApp3 and sApp4
        Trigger trigger4MatchSource1And2And3 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(31)
                        .setEnrollmentId(enrollmentId)
                        .setAttributionDestination(appDestination)
                        .setDestinationType(EventSurfaceType.APP)
                        .build();

        List<Source> result4 = runFunc.apply(trigger4MatchSource1And2And3);
        assertEquals(3, result4.size());
        assertEquals(sApp2.getId(), result4.get(0).getId());
        assertEquals(sApp3.getId(), result4.get(1).getId());
        assertEquals(sApp4.getId(), result4.get(2).getId());

        // sApp1, sApp2, sApp3, sApp4 don't have web destination
        // Trigger Time > sWeb5's eventTime and < sApp5's expiryTime
        // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
        // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
        // Expected: Match with sApp5, sApp6, sAppWeb7
        Trigger trigger5MatchSource567 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(12)
                        .setEnrollmentId(enrollmentId)
                        .setAttributionDestination(webDestination)
                        .setDestinationType(EventSurfaceType.WEB)
                        .build();
        List<Source> result5 = runFunc.apply(trigger5MatchSource567);
        assertEquals(3, result1.size());
        assertEquals(sWeb5.getId(), result5.get(0).getId());
        assertEquals(sWeb6.getId(), result5.get(1).getId());
        assertEquals(sAppWeb7.getId(), result5.get(2).getId());

        // sApp1, sApp2, sApp3, sApp4 don't have web destination
        // Trigger Time > sWeb5's expiryTime
        // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
        // Trigger Time > sWeb7's expiryTime
        // Expected: Match with sApp6 only
        Trigger trigger6MatchSource67 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(21)
                        .setEnrollmentId(enrollmentId)
                        .setAttributionDestination(webDestinationWithSubdomain)
                        .setDestinationType(EventSurfaceType.WEB)
                        .build();

        List<Source> result6 = runFunc.apply(trigger6MatchSource67);
        assertEquals(1, result6.size());
        assertEquals(sWeb6.getId(), result6.get(0).getId());
    }

    private void insertInDb(SQLiteDatabase db, Source source) {
        ContentValues values = new ContentValues();
        values.put(SourceContract.ID, source.getId());
        values.put(SourceContract.STATUS, Source.Status.ACTIVE);
        values.put(SourceContract.EVENT_TIME, source.getEventTime());
        values.put(SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
        if (source.getAppDestination() != null) {
            values.put(SourceContract.APP_DESTINATION, source.getAppDestination().toString());
        }
        if (source.getWebDestination() != null) {
            values.put(SourceContract.WEB_DESTINATION, source.getWebDestination().toString());
        }
        values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
        values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());

        db.insert(SourceContract.TABLE, null, values);
    }

    @Test
    public void testInsertAggregateEncryptionKey() {
        String keyId = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
        String publicKey = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
        long expiry = 1653620135831L;

        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertAggregateEncryptionKey(
                        new AggregateEncryptionKey.Builder()
                                .setKeyId(keyId)
                                .setPublicKey(publicKey)
                                .setExpiry(expiry).build())
        );

        try (Cursor cursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AggregateEncryptionKey.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            assertTrue(cursor.moveToNext());
            AggregateEncryptionKey aggregateEncryptionKey =
                    SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor);
            assertNotNull(aggregateEncryptionKey);
            assertNotNull(aggregateEncryptionKey.getId());
            assertEquals(keyId, aggregateEncryptionKey.getKeyId());
            assertEquals(publicKey, aggregateEncryptionKey.getPublicKey());
            assertEquals(expiry, aggregateEncryptionKey.getExpiry());
        }
    }

    @Test
    public void testInsertAggregateReport() {
        AggregateReport validAggregateReport = AggregateReportFixture.getValidAggregateReport();
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertAggregateReport(validAggregateReport));

        try (Cursor cursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.AggregateReport.TABLE,
                                     null, null, null, null, null, null)) {
            assertTrue(cursor.moveToNext());
            AggregateReport aggregateReport =
                    SqliteObjectMapper.constructAggregateReport(cursor);
            assertNotNull(aggregateReport);
            assertNotNull(aggregateReport.getId());
            assertTrue(Objects.equals(validAggregateReport, aggregateReport));
        }
    }

    @Test
    public void testDeleteAllMeasurementDataWithEmptyList() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        Source source = SourceFixture.getValidSourceBuilder().setId("S1").build();
        ContentValues sourceValue = new ContentValues();
        sourceValue.put("_id", source.getId());
        db.insert(SourceContract.TABLE, null, sourceValue);

        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setId("T1").build();
        ContentValues triggerValue = new ContentValues();
        triggerValue.put("_id", trigger.getId());
        db.insert(TriggerContract.TABLE, null, triggerValue);

        EventReport eventReport = new EventReport.Builder().setId("E1").build();
        ContentValues eventReportValue = new ContentValues();
        eventReportValue.put("_id", eventReport.getId());
        db.insert(EventReportContract.TABLE, null, eventReportValue);

        AggregateReport aggregateReport = new AggregateReport.Builder().setId("A1").build();
        ContentValues aggregateReportValue = new ContentValues();
        aggregateReportValue.put("_id", aggregateReport.getId());
        db.insert(MeasurementTables.AggregateReport.TABLE, null, aggregateReportValue);

        ContentValues rateLimitValue = new ContentValues();
        rateLimitValue.put(AttributionContract.ID, "ARL1");
        rateLimitValue.put(AttributionContract.SOURCE_SITE, "sourceSite");
        rateLimitValue.put(AttributionContract.SOURCE_ORIGIN, "sourceOrigin");
        rateLimitValue.put(AttributionContract.DESTINATION_SITE, "destinationSite");
        rateLimitValue.put(AttributionContract.TRIGGER_TIME, 5L);
        rateLimitValue.put(AttributionContract.REGISTRANT, "registrant");
        rateLimitValue.put(AttributionContract.ENROLLMENT_ID, "enrollmentId");

        db.insert(AttributionContract.TABLE, null, rateLimitValue);

        AggregateEncryptionKey key =
                new AggregateEncryptionKey.Builder()
                        .setId("K1")
                        .setKeyId("keyId")
                        .setPublicKey("publicKey")
                        .setExpiry(1)
                        .build();
        ContentValues keyValues = new ContentValues();
        keyValues.put("_id", key.getId());

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.deleteAllMeasurementData(Collections.emptyList()));

        for (String table : ALL_MSMT_TABLES) {
            assertThat(
                            db.query(
                                            /* table */ table,
                                            /* columns */ null,
                                            /* selection */ null,
                                            /* selectionArgs */ null,
                                            /* groupBy */ null,
                                            /* having */ null,
                                            /* orderedBy */ null)
                                    .getCount())
                    .isEqualTo(0);
        }
    }

    @Test
    public void testDeleteAllMeasurementDataWithNonEmptyList() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        Source source = SourceFixture.getValidSourceBuilder().setId("S1").build();
        ContentValues sourceValue = new ContentValues();
        sourceValue.put("_id", source.getId());
        db.insert(SourceContract.TABLE, null, sourceValue);

        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setId("T1").build();
        ContentValues triggerValue = new ContentValues();
        triggerValue.put("_id", trigger.getId());
        db.insert(TriggerContract.TABLE, null, triggerValue);

        EventReport eventReport = new EventReport.Builder().setId("E1").build();
        ContentValues eventReportValue = new ContentValues();
        eventReportValue.put("_id", eventReport.getId());
        db.insert(EventReportContract.TABLE, null, eventReportValue);

        AggregateReport aggregateReport = new AggregateReport.Builder().setId("A1").build();
        ContentValues aggregateReportValue = new ContentValues();
        aggregateReportValue.put("_id", aggregateReport.getId());
        db.insert(MeasurementTables.AggregateReport.TABLE, null, aggregateReportValue);

        ContentValues rateLimitValue = new ContentValues();
        rateLimitValue.put(AttributionContract.ID, "ARL1");
        rateLimitValue.put(AttributionContract.SOURCE_SITE, "sourceSite");
        rateLimitValue.put(AttributionContract.SOURCE_ORIGIN, "sourceOrigin");
        rateLimitValue.put(AttributionContract.DESTINATION_SITE, "destinationSite");
        rateLimitValue.put(AttributionContract.TRIGGER_TIME, 5L);
        rateLimitValue.put(AttributionContract.REGISTRANT, "registrant");
        rateLimitValue.put(AttributionContract.ENROLLMENT_ID, "enrollmentId");
        db.insert(AttributionContract.TABLE, null, rateLimitValue);

        AggregateEncryptionKey key =
                new AggregateEncryptionKey.Builder()
                        .setId("K1")
                        .setKeyId("keyId")
                        .setPublicKey("publicKey")
                        .setExpiry(1)
                        .build();
        ContentValues keyValues = new ContentValues();
        keyValues.put("_id", key.getId());

        List<String> excludedTables = List.of(SourceContract.TABLE);

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.deleteAllMeasurementData(excludedTables));

        for (String table : ALL_MSMT_TABLES) {
            if (!excludedTables.contains(table)) {
                assertThat(
                                db.query(
                                                /* table */ table,
                                                /* columns */ null,
                                                /* selection */ null,
                                                /* selectionArgs */ null,
                                                /* groupBy */ null,
                                                /* having */ null,
                                                /* orderedBy */ null)
                                        .getCount())
                        .isEqualTo(0);
            } else {
                assertThat(
                                db.query(
                                                /* table */ table,
                                                /* columns */ null,
                                                /* selection */ null,
                                                /* selectionArgs */ null,
                                                /* groupBy */ null,
                                                /* having */ null,
                                                /* orderedBy */ null)
                                        .getCount())
                        .isNotEqualTo(0);
            }
        }
    }

    /** Test that the variable ALL_MSMT_TABLES actually has all the measurement related tables. */
    @Test
    public void testAllMsmtTables() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Cursor cursor =
                db.query(
                        "sqlite_master",
                        /* columns */ null,
                        /* selection */ "type = ? AND name like ?",
                        /* selectionArgs*/ new String[] {"table", MSMT_TABLE_PREFIX + "%"},
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);

        List<String> tableNames = new ArrayList<>();
        while (cursor.moveToNext()) {
            String tableName = cursor.getString(cursor.getColumnIndex("name"));
            tableNames.add(tableName);
        }
        assertThat(tableNames.size()).isEqualTo(ALL_MSMT_TABLES.length);
        for (String tableName : tableNames) {
            assertThat(ALL_MSMT_TABLES).asList().contains(tableName);
        }
    }

    @Test
    public void insertAttributionRateLimit() {
        // Setup
        Source source = SourceFixture.getValidSource();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(source.getEventTime() + TimeUnit.HOURS.toMillis(1))
                        .build();
        Attribution attribution =
                new Attribution.Builder()
                        .setEnrollmentId(source.getEnrollmentId())
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(trigger.getTriggerTime())
                        .build();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);

        // Execution
        dm.runInTransaction(
                (dao) -> {
                    dao.insertAttribution(attribution);
                });

        // Assertion
        AtomicLong attributionsCount = new AtomicLong();
        dm.runInTransaction(
                (dao) -> {
                    attributionsCount.set(dao.getAttributionsPerRateLimitWindow(source, trigger));
                });

        assertEquals(1L, attributionsCount.get());
    }

    @Test
    public void testGetAttributionsPerRateLimitWindow_atTimeWindow() {
        // Setup
        Source source = SourceFixture.getValidSource();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(source.getEventTime() + TimeUnit.HOURS.toMillis(1))
                        .build();
        Attribution attribution =
                new Attribution.Builder()
                        .setEnrollmentId(source.getEnrollmentId())
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(trigger.getTriggerTime()
                                - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS + 1)
                        .build();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);

        // Execution
        dm.runInTransaction(
                (dao) -> {
                    dao.insertAttribution(attribution);
                });

        // Assertion
        AtomicLong attributionsCount = new AtomicLong();
        dm.runInTransaction(
                (dao) -> {
                    attributionsCount.set(dao.getAttributionsPerRateLimitWindow(source, trigger));
                });

        assertEquals(1L, attributionsCount.get());
    }

    @Test
    public void testGetAttributionsPerRateLimitWindow_beyondTimeWindow() {
        // Setup
        Source source = SourceFixture.getValidSource();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(source.getEventTime() + TimeUnit.HOURS.toMillis(1))
                        .build();
        Attribution attribution =
                new Attribution.Builder()
                        .setEnrollmentId(source.getEnrollmentId())
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(trigger.getTriggerTime()
                                - PrivacyParams.RATE_LIMIT_WINDOW_MILLISECONDS)
                        .build();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);

        // Execution
        dm.runInTransaction(
                (dao) -> {
                    dao.insertAttribution(attribution);
                });

        // Assertion
        AtomicLong attributionsCount = new AtomicLong();
        dm.runInTransaction(
                (dao) -> {
                    attributionsCount.set(dao.getAttributionsPerRateLimitWindow(source, trigger));
                });

        assertEquals(0L, attributionsCount.get());
    }

    @Test
    public void testTransactionRollbackForRuntimeException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DatastoreManagerFactory.getDatastoreManager(sContext)
                                .runInTransaction(
                                        (dao) -> {
                                            dao.insertSource(SourceFixture.getValidSource());
                                            // build() call throws IllegalArgumentException
                                            Trigger trigger = new Trigger.Builder().build();
                                            dao.insertTrigger(trigger);
                                        }));
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        // There should be no insertions
        assertEquals(
                0,
                db.query(MeasurementTables.SourceContract.TABLE, null, null, null, null, null, null)
                        .getCount());
        assertEquals(
                0,
                db.query(
                                MeasurementTables.TriggerContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)
                        .getCount());
    }

    @Test
    public void testDeleteAppRecordsNotPresentForSources() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        List<Source> sourceList = new ArrayList<>();
        // Source registrant is still installed, record is not deleted.
        sourceList.add(
                new Source.Builder()
                        .setId("1")
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestination(Uri.parse("android-app://installed-app-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-registrant"))
                        .setPublisher(Uri.parse("android-app://installed-registrant"))
                        .setStatus(Source.Status.ACTIVE)
                        .build());
        // Source registrant is not installed, record is deleted.
        sourceList.add(
                new Source.Builder()
                        .setId("2")
                        .setEventId(new UnsignedLong(2L))
                        .setAppDestination(Uri.parse("android-app://installed-app-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://not-installed-registrant"))
                        .setPublisher(Uri.parse("android-app://not-installed-registrant"))
                        .setStatus(Source.Status.ACTIVE)
                        .build());
        // Source registrant is installed and status is active on not installed destination, record
        // is not deleted.
        sourceList.add(
                new Source.Builder()
                        .setId("3")
                        .setEventId(new UnsignedLong(3L))
                        .setAppDestination(Uri.parse("android-app://not-installed-app-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-registrant"))
                        .setPublisher(Uri.parse("android-app://installed-registrant"))
                        .setStatus(Source.Status.ACTIVE)
                        .build());

        // Source registrant is installed and status is ignored on not installed destination, record
        // is deleted.
        sourceList.add(
                new Source.Builder()
                        .setId("4")
                        .setEventId(new UnsignedLong(4L))
                        .setAppDestination(Uri.parse("android-app://not-installed-app-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-registrant"))
                        .setPublisher(Uri.parse("android-app://installed-registrant"))
                        .setStatus(Source.Status.IGNORED)
                        .build());

        // Source registrant is installed and status is ignored on installed destination, record is
        // not deleted.
        sourceList.add(
                new Source.Builder()
                        .setId("5")
                        .setEventId(new UnsignedLong(5L))
                        .setAppDestination(Uri.parse("android-app://installed-app-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-registrant"))
                        .setPublisher(Uri.parse("android-app://installed-registrant"))
                        .setStatus(Source.Status.IGNORED)
                        .build());

        sourceList.forEach(
                source -> {
                    ContentValues values = new ContentValues();
                    values.put(SourceContract.ID, source.getId());
                    values.put(SourceContract.EVENT_ID, source.getEventId().toString());
                    values.put(
                            SourceContract.APP_DESTINATION, source.getAppDestination().toString());
                    values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
                    values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());
                    values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
                    values.put(SourceContract.STATUS, source.getStatus());
                    db.insert(SourceContract.TABLE, /* nullColumnHack */ null, values);
                });

        long count = DatabaseUtils.queryNumEntries(db, SourceContract.TABLE, /* selection */ null);
        assertEquals(5, count);

        List<Uri> installedUriList = new ArrayList<>();
        installedUriList.add(Uri.parse("android-app://installed-registrant"));
        installedUriList.add(Uri.parse("android-app://installed-app-destination"));

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count = DatabaseUtils.queryNumEntries(db, SourceContract.TABLE, /* selection */ null);
        assertEquals(3, count);

        Cursor cursor =
                db.query(
                        SourceContract.TABLE,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);
        while (cursor.moveToNext()) {
            Source source = SqliteObjectMapper.constructSourceFromCursor(cursor);
            assertThat(Arrays.asList("1", "3", "5")).contains(source.getId());
        }
    }

    @Test
    public void testDeleteAppRecordsNotPresentForTriggers() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Trigger> triggerList = new ArrayList<>();
        // Trigger registrant is still installed, record will not be deleted.
        triggerList.add(
                new Trigger.Builder()
                        .setId("1")
                        .setAttributionDestination(
                                Uri.parse("android-app://attribution-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-registrant"))
                        .build());

        // Trigger registrant is not installed, record will be deleted.
        triggerList.add(
                new Trigger.Builder()
                        .setId("2")
                        .setAttributionDestination(
                                Uri.parse("android-app://attribution-destination"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://not-installed-registrant"))
                        .build());

        triggerList.forEach(
                trigger -> {
                    ContentValues values = new ContentValues();
                    values.put(TriggerContract.ID, trigger.getId());
                    values.put(
                            TriggerContract.ATTRIBUTION_DESTINATION,
                            trigger.getAttributionDestination().toString());
                    values.put(TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
                    values.put(TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
                    db.insert(TriggerContract.TABLE, /* nullColumnHack */ null, values);
                });

        long count = DatabaseUtils.queryNumEntries(db, TriggerContract.TABLE, /* selection */ null);
        assertEquals(2, count);

        List<Uri> installedUriList = new ArrayList<>();
        installedUriList.add(Uri.parse("android-app://installed-registrant"));

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count = DatabaseUtils.queryNumEntries(db, TriggerContract.TABLE, /* selection */ null);
        assertEquals(1, count);

        Cursor cursor =
                db.query(
                        TriggerContract.TABLE,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);
        while (cursor.moveToNext()) {
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(cursor);
            assertEquals("1", trigger.getId());
        }
    }

    @Test
    public void testDeleteAppRecordsNotPresentForEventReports() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<EventReport> eventReportList = new ArrayList<>();
        // Event report attribution destination is still installed, record will not be deleted.
        eventReportList.add(
                new EventReport.Builder()
                        .setId("1")
                        .setAttributionDestination(
                                Uri.parse("android-app://installed-attribution-destination"))
                        .build());
        // Event report attribution destination is not installed, record will be deleted.
        eventReportList.add(
                new EventReport.Builder()
                        .setId("2")
                        .setAttributionDestination(
                                Uri.parse("android-app://not-installed-attribution-destination"))
                        .build());
        eventReportList.forEach(
                eventReport -> {
                    ContentValues values = new ContentValues();
                    values.put(EventReportContract.ID, eventReport.getId());
                    values.put(
                            EventReportContract.ATTRIBUTION_DESTINATION,
                            eventReport.getAttributionDestination().toString());
                    db.insert(EventReportContract.TABLE, /* nullColumnHack */ null, values);
                });

        long count =
                DatabaseUtils.queryNumEntries(db, EventReportContract.TABLE, /* selection */ null);
        assertEquals(2, count);

        List<Uri> installedUriList = new ArrayList<>();
        installedUriList.add(Uri.parse("android-app://installed-attribution-destination"));

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count = DatabaseUtils.queryNumEntries(db, EventReportContract.TABLE, /* selection */ null);
        assertEquals(1, count);

        Cursor cursor =
                db.query(
                        EventReportContract.TABLE,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);
        while (cursor.moveToNext()) {
            EventReport eventReport = SqliteObjectMapper.constructEventReportFromCursor(cursor);
            assertEquals("1", eventReport.getId());
        }
    }

    @Test
    public void testDeleteAppRecordsNotPresentForAggregateReports() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<AggregateReport> aggregateReportList = new ArrayList<>();
        // Aggregate report attribution destination and publisher is still installed, record will
        // not be deleted.
        aggregateReportList.add(
                new AggregateReport.Builder()
                        .setId("1")
                        .setAttributionDestination(
                                Uri.parse("android-app://installed-attribution-destination"))
                        .setPublisher(Uri.parse("android-app://installed-publisher"))
                        .build());
        // Aggregate report attribution destination is not installed, record will be deleted.
        aggregateReportList.add(
                new AggregateReport.Builder()
                        .setId("2")
                        .setAttributionDestination(
                                Uri.parse("android-app://not-installed-attribution-destination"))
                        .setPublisher(Uri.parse("android-app://installed-publisher"))
                        .build());
        // Aggregate report publisher is not installed, record will be deleted.
        aggregateReportList.add(
                new AggregateReport.Builder()
                        .setId("3")
                        .setAttributionDestination(
                                Uri.parse("android-app://installed-attribution-destination"))
                        .setPublisher(Uri.parse("android-app://not-installed-publisher"))
                        .build());
        aggregateReportList.forEach(
                aggregateReport -> {
                    ContentValues values = new ContentValues();
                    values.put(MeasurementTables.AggregateReport.ID, aggregateReport.getId());
                    values.put(
                            MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                            aggregateReport.getAttributionDestination().toString());
                    values.put(
                            MeasurementTables.AggregateReport.PUBLISHER,
                            aggregateReport.getPublisher().toString());
                    db.insert(
                            MeasurementTables.AggregateReport.TABLE, /* nullColumnHack */
                            null,
                            values);
                });

        long count =
                DatabaseUtils.queryNumEntries(
                        db, MeasurementTables.AggregateReport.TABLE, /* selection */ null);
        assertEquals(3, count);

        List<Uri> installedUriList = new ArrayList<>();
        installedUriList.add(Uri.parse("android-app://installed-attribution-destination"));
        installedUriList.add(Uri.parse("android-app://installed-publisher"));

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count =
                DatabaseUtils.queryNumEntries(
                        db, MeasurementTables.AggregateReport.TABLE, /* selection */ null);
        assertEquals(1, count);

        Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);
        while (cursor.moveToNext()) {
            AggregateReport aggregateReport = SqliteObjectMapper.constructAggregateReport(cursor);
            assertEquals("1", aggregateReport.getId());
        }
    }

    @Test
    public void testDeleteAppRecordsNotPresentForAttributions() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Attribution> attributionList = new ArrayList<>();
        // Attribution has source site and destination site still installed, record will not be
        // deleted.
        attributionList.add(
                new Attribution.Builder()
                        .setId("1")
                        .setSourceSite("android-app://installed-source-site")
                        .setSourceOrigin("android-app://installed-source-site")
                        .setDestinationSite("android-app://installed-destination-site")
                        .setDestinationOrigin("android-app://installed-destination-site")
                        .setRegistrant("android-app://installed-source-site")
                        .setEnrollmentId("enrollment-id")
                        .build());
        // Attribution has source site not installed, record will be deleted.
        attributionList.add(
                new Attribution.Builder()
                        .setId("2")
                        .setSourceSite("android-app://not-installed-source-site")
                        .setSourceOrigin("android-app://not-installed-source-site")
                        .setDestinationSite("android-app://installed-destination-site")
                        .setDestinationOrigin("android-app://installed-destination-site")
                        .setRegistrant("android-app://installed-source-site")
                        .setEnrollmentId("enrollment-id")
                        .build());
        // Attribution has destination site not installed, record will be deleted.
        attributionList.add(
                new Attribution.Builder()
                        .setId("3")
                        .setSourceSite("android-app://installed-source-site")
                        .setSourceOrigin("android-app://installed-source-site")
                        .setDestinationSite("android-app://not-installed-destination-site")
                        .setDestinationOrigin("android-app://not-installed-destination-site")
                        .setRegistrant("android-app://installed-source-site")
                        .setEnrollmentId("enrollment-id")
                        .build());
        attributionList.forEach(
                attribution -> {
                    ContentValues values = new ContentValues();
                    values.put(AttributionContract.ID, attribution.getId());
                    values.put(AttributionContract.SOURCE_SITE, attribution.getSourceSite());
                    values.put(AttributionContract.SOURCE_ORIGIN, attribution.getSourceOrigin());
                    values.put(
                            AttributionContract.DESTINATION_SITE, attribution.getDestinationSite());
                    values.put(
                            AttributionContract.DESTINATION_ORIGIN,
                            attribution.getDestinationOrigin());
                    values.put(AttributionContract.REGISTRANT, attribution.getRegistrant());
                    values.put(AttributionContract.ENROLLMENT_ID, attribution.getEnrollmentId());
                    db.insert(AttributionContract.TABLE, /* nullColumnHack */ null, values);
                });

        long count =
                DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE, /* selection */ null);
        assertEquals(3, count);

        List<Uri> installedUriList = new ArrayList<>();
        installedUriList.add(Uri.parse("android-app://installed-source-site"));
        installedUriList.add(Uri.parse("android-app://installed-destination-site"));

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count = DatabaseUtils.queryNumEntries(db, AttributionContract.TABLE, /* selection */ null);
        assertEquals(1, count);

        Cursor cursor =
                db.query(
                        AttributionContract.TABLE,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);
        while (cursor.moveToNext()) {
            Attribution attribution = constructAttributionFromCursor(cursor);
            assertEquals("1", attribution.getId());
        }
    }

    @Test
    public void testDeleteAppRecordsNotPresentForEventReportsFromSources() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        List<Source> sourceList = new ArrayList<>();
        sourceList.add(
                new Source.Builder() // deleted
                        .setId("1")
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestination(Uri.parse("android-app://app-destination-1"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://uninstalled-app"))
                        .setPublisher(Uri.parse("android-app://uninstalled-app"))
                        .build());
        sourceList.add(
                new Source.Builder()
                        .setId("2")
                        .setEventId(new UnsignedLong(2L))
                        .setAppDestination(Uri.parse("android-app://app-destination-2"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-app"))
                        .setPublisher(Uri.parse("android-app://installed-app"))
                        .build());
        sourceList.forEach(source -> AbstractDbIntegrationTest.insertToDb(source, db));

        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("1")
                        .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                        .setRegistrant(Uri.parse("android-app://installed-app"))
                        .build();
        AbstractDbIntegrationTest.insertToDb(trigger, db);

        List<EventReport> reportList = new ArrayList<>();
        reportList.add(
                new EventReport.Builder()
                        .setId("1") // deleted
                        .setSourceEventId(new UnsignedLong(1L))
                        .setAttributionDestination(Uri.parse("android-app://app-destination-1"))
                        .setEnrollmentId("enrollment-id")
                        .setTriggerData(new UnsignedLong(5L))
                        .setSourceId(sourceList.get(0).getId())
                        .setTriggerId(trigger.getId())
                        .setSourceType(sourceList.get(0).getSourceType())
                        .build());
        reportList.add(
                new EventReport.Builder()
                        .setId("2")
                        .setSourceEventId(new UnsignedLong(2L))
                        .setAttributionDestination(Uri.parse("android-app://app-destination-2"))
                        .setEnrollmentId("enrollment-id")
                        .setTriggerData(new UnsignedLong(5L))
                        .setSourceId(sourceList.get(1).getId())
                        .setTriggerId(trigger.getId())
                        .setSourceType(sourceList.get(1).getSourceType())
                        .build());
        reportList.forEach(report -> AbstractDbIntegrationTest.insertToDb(report, db));

        long count =
                DatabaseUtils.queryNumEntries(db, EventReportContract.TABLE, /* selection */ null);
        assertEquals(2, count);

        List<Uri> installedUriList = new ArrayList<>();
        installedUriList.add(Uri.parse("android-app://installed-app"));
        installedUriList.add(Uri.parse("android-app://app-destination-1"));
        installedUriList.add(Uri.parse("android-app://app-destination-2"));

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count = DatabaseUtils.queryNumEntries(db, EventReportContract.TABLE, /* selection */ null);
        assertEquals(1, count);

        Cursor cursor =
                db.query(
                        EventReportContract.TABLE,
                        /* columns */ null,
                        /* selection */ null,
                        /* selectionArgs */ null,
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);
        while (cursor.moveToNext()) {
            EventReport eventReport = SqliteObjectMapper.constructEventReportFromCursor(cursor);
            assertEquals("2", eventReport.getId());
        }
    }

    @Test
    public void testDeleteAppRecordsNotPresentForLargeAppList() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Source> sourceList = new ArrayList<>();

        int limit = 5000;
        sourceList.add(
                new Source.Builder()
                        .setId("1")
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestination(Uri.parse("android-app://app-destination-1"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-app" + limit))
                        .setPublisher(Uri.parse("android-app://installed-app" + limit))
                        .build());
        sourceList.add(
                new Source.Builder()
                        .setId("2")
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestination(Uri.parse("android-app://app-destination-1"))
                        .setEnrollmentId("enrollment-id")
                        .setRegistrant(Uri.parse("android-app://installed-app" + (limit + 1)))
                        .setPublisher(Uri.parse("android-app://installed-app" + (limit + 1)))
                        .build());
        sourceList.forEach(
                source -> {
                    ContentValues values = new ContentValues();
                    values.put(SourceContract.ID, source.getId());
                    values.put(SourceContract.EVENT_ID, source.getEventId().toString());
                    values.put(
                            SourceContract.APP_DESTINATION, source.getAppDestination().toString());
                    values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
                    values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());
                    values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
                    db.insert(SourceContract.TABLE, /* nullColumnHack */ null, values);
                });

        long count = DatabaseUtils.queryNumEntries(db, SourceContract.TABLE, /* selection */ null);
        assertEquals(2, count);

        List<Uri> installedUriList = new ArrayList<>();
        for (int i = 0; i <= limit; i++) {
            installedUriList.add(Uri.parse("android-app://installed-app" + i));
        }

        assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.deleteAppRecordsNotPresent(
                                                installedUriList)));

        count = DatabaseUtils.queryNumEntries(db, SourceContract.TABLE, /* selection */ null);
        assertEquals(1, count);
    }

    private static List<Source> getSourcesWithDifferentDestinations(
            int numSources,
            boolean hasAppDestination,
            boolean hasWebDestination,
            long eventTime,
            Uri publisher,
            String enrollmentId,
            @Source.Status int sourceStatus) {
        long expiryTime = eventTime + TimeUnit.SECONDS.toMillis(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        return getSourcesWithDifferentDestinations(
                numSources,
                hasAppDestination,
                hasWebDestination,
                eventTime,
                expiryTime,
                publisher,
                enrollmentId,
                sourceStatus);
    }

    private static List<Source> getSourcesWithDifferentDestinations(
            int numSources,
            boolean hasAppDestination,
            boolean hasWebDestination,
            long eventTime,
            long expiryTime,
            Uri publisher,
            String enrollmentId,
            @Source.Status int sourceStatus) {
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < numSources; i++) {
            Source.Builder sourceBuilder =
                    new Source.Builder()
                            .setEventId(new UnsignedLong(0L))
                            .setEventTime(eventTime)
                            .setExpiryTime(expiryTime)
                            .setPublisher(publisher)
                            .setEnrollmentId(enrollmentId)
                            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                            .setStatus(sourceStatus);
            if (hasAppDestination) {
                sourceBuilder.setAppDestination(
                        Uri.parse("android-app://app-destination-" + String.valueOf(i)));
            }
            if (hasWebDestination) {
                sourceBuilder.setWebDestination(
                        Uri.parse("https://web-destination-" + String.valueOf(i) + ".com"));
            }
            sources.add(sourceBuilder.build());
        }
        return sources;
    }

    private static List<Source> getSourcesWithDifferentEnrollments(
            int numSources,
            Uri appDestination,
            Uri webDestination,
            long eventTime,
            Uri publisher,
            @Source.Status int sourceStatus) {
        long expiryTime = eventTime + TimeUnit.SECONDS.toMillis(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        return getSourcesWithDifferentEnrollments(
            numSources,
            appDestination,
            webDestination,
            eventTime,
            expiryTime,
            publisher,
            sourceStatus);
    }

    private static List<Source> getSourcesWithDifferentEnrollments(
            int numSources,
            Uri appDestination,
            Uri webDestination,
            long eventTime,
            long expiryTime,
            Uri publisher,
            @Source.Status int sourceStatus) {
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < numSources; i++) {
            Source.Builder sourceBuilder =
                    new Source.Builder()
                            .setEventId(new UnsignedLong(0L))
                            .setEventTime(eventTime)
                            .setExpiryTime(expiryTime)
                            .setPublisher(publisher)
                            .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                            .setStatus(sourceStatus)
                            .setAppDestination(appDestination)
                            .setWebDestination(webDestination)
                            .setEnrollmentId("enrollment-id-" + i);
            sources.add(sourceBuilder.build());
        }
        return sources;
    }

    private static List<Attribution> getAttributionsWithDifferentEnrollments(
            int numAttributions,
            Uri destinationSite,
            long triggerTime,
            Uri sourceSite,
            String registrant) {
        List<Attribution> attributions = new ArrayList<>();
        for (int i = 0; i < numAttributions; i++) {
            Attribution.Builder attributionBuilder =
                    new Attribution.Builder()
                            .setTriggerTime(triggerTime)
                            .setSourceSite(sourceSite.toString())
                            .setSourceOrigin(sourceSite.toString())
                            .setDestinationSite(destinationSite.toString())
                            .setDestinationOrigin(destinationSite.toString())
                            .setEnrollmentId("enrollment-id-" + i)
                            .setRegistrant(registrant);
            attributions.add(attributionBuilder.build());
        }
        return attributions;
    }

    private static void insertAttribution(Attribution attribution) {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(AttributionContract.ID, UUID.randomUUID().toString());
        values.put(AttributionContract.SOURCE_SITE, attribution.getSourceSite());
        values.put(AttributionContract.DESTINATION_SITE, attribution.getDestinationSite());
        values.put(AttributionContract.ENROLLMENT_ID, attribution.getEnrollmentId());
        values.put(AttributionContract.TRIGGER_TIME, attribution.getTriggerTime());
        values.put(AttributionContract.SOURCE_ID, attribution.getSourceId());
        values.put(AttributionContract.TRIGGER_ID, attribution.getTriggerId());
        long row = db.insert("msmt_attribution", null, values);
        assertNotEquals("Attribution insertion failed", -1, row);
    }

    private static Attribution createAttributionWithSourceAndTriggerIds(
            String attributionId, String sourceId, String triggerId) {
        return new Attribution.Builder()
                .setId(attributionId)
                .setTriggerTime(0L)
                .setSourceSite("android-app://source.app")
                .setSourceOrigin("android-app://source.app")
                .setDestinationSite("android-app://destination.app")
                .setDestinationOrigin("android-app://destination.app")
                .setEnrollmentId("enrollment-id-")
                .setRegistrant("android-app://registrant.app")
                .setSourceId(sourceId)
                .setTriggerId(triggerId)
                .build();
    }

    private static Attribution createAttributionWithSourceAndTriggerIds(
            String sourceId, String triggerId) {
        return new Attribution.Builder()
                .setTriggerTime(0L)
                .setSourceSite("android-app://source.app")
                .setSourceOrigin("android-app://source.app")
                .setDestinationSite("android-app://destination.app")
                .setDestinationOrigin("android-app://destination.app")
                .setEnrollmentId("enrollment-id-")
                .setRegistrant("android-app://registrant.app")
                .setSourceId(sourceId)
                .setTriggerId(triggerId)
                .build();
    }

    // This is needed because MeasurementDao::insertSource inserts a default value for status.
    private static void insertSource(Source source) {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SourceContract.ID, UUID.randomUUID().toString());
        values.put(SourceContract.EVENT_ID, source.getEventId().getValue());
        values.put(SourceContract.PUBLISHER, source.getPublisher().toString());
        values.put(SourceContract.PUBLISHER_TYPE, source.getPublisherType());
        values.put(
                SourceContract.APP_DESTINATION, getNullableUriString(source.getAppDestination()));
        values.put(
                SourceContract.WEB_DESTINATION, getNullableUriString(source.getWebDestination()));
        values.put(SourceContract.ENROLLMENT_ID, source.getEnrollmentId());
        values.put(SourceContract.EVENT_TIME, source.getEventTime());
        values.put(SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(SourceContract.PRIORITY, source.getPriority());
        values.put(SourceContract.STATUS, source.getStatus());
        values.put(SourceContract.SOURCE_TYPE, source.getSourceType().name());
        values.put(SourceContract.REGISTRANT, source.getRegistrant().toString());
        values.put(SourceContract.INSTALL_ATTRIBUTION_WINDOW, source.getInstallAttributionWindow());
        values.put(SourceContract.INSTALL_COOLDOWN_WINDOW, source.getInstallCooldownWindow());
        values.put(SourceContract.ATTRIBUTION_MODE, source.getAttributionMode());
        values.put(SourceContract.AGGREGATE_SOURCE, source.getAggregateSource());
        values.put(SourceContract.FILTER_DATA, source.getFilterData());
        values.put(SourceContract.AGGREGATE_CONTRIBUTIONS, 0);
        long row = db.insert("msmt_source", null, values);
        assertNotEquals("Source insertion failed", -1, row);
    }

    private static String getNullableUriString(Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }

    /** Test that the AsyncRegistration is inserted correctly. */
    @Test
    public void testInsertAsyncRegistration() {
        AsyncRegistration validAsyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistration();
        String validAsyncRegistrationId = validAsyncRegistration.getId();

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.insertAsyncRegistration(validAsyncRegistration));

        try (Cursor cursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                MeasurementTables.AsyncRegistrationContract.ID + " = ? ",
                                new String[] {validAsyncRegistrationId},
                                null,
                                null,
                                null)) {

            assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertNotNull(asyncRegistration);
            assertNotNull(asyncRegistration.getId());
            assertEquals(asyncRegistration.getId(), validAsyncRegistration.getId());
            assertNotNull(asyncRegistration.getEnrollmentId());
            assertEquals(
                    asyncRegistration.getEnrollmentId(), validAsyncRegistration.getEnrollmentId());
            assertNotNull(asyncRegistration.getRegistrationUri());
            assertNotNull(asyncRegistration.getTopOrigin());
            assertEquals(asyncRegistration.getTopOrigin(), validAsyncRegistration.getTopOrigin());
            assertNotNull(asyncRegistration.getRegistrant());
            assertEquals(asyncRegistration.getRegistrant(), validAsyncRegistration.getRegistrant());
            assertNotNull(asyncRegistration.getSourceType());
            assertEquals(asyncRegistration.getSourceType(), validAsyncRegistration.getSourceType());
            assertNotNull(asyncRegistration.getDebugKeyAllowed());
            assertEquals(
                    asyncRegistration.getDebugKeyAllowed(),
                    validAsyncRegistration.getDebugKeyAllowed());
            assertNotNull(asyncRegistration.getRetryCount());
            assertEquals(asyncRegistration.getRetryCount(), validAsyncRegistration.getRetryCount());
            assertNotNull(asyncRegistration.getRequestTime());
            assertEquals(
                    asyncRegistration.getRequestTime(), validAsyncRegistration.getRequestTime());
            assertNotNull(asyncRegistration.getOsDestination());
            assertEquals(
                    asyncRegistration.getOsDestination(),
                    validAsyncRegistration.getOsDestination());
            assertNotNull(asyncRegistration.getLastProcessingTime());
            assertEquals(
                    asyncRegistration.getLastProcessingTime(),
                    validAsyncRegistration.getLastProcessingTime());
            assertEquals(
                    asyncRegistration.getRedirectType(), validAsyncRegistration.getRedirectType());
            assertEquals(
                    asyncRegistration.getRedirectCount(),
                    validAsyncRegistration.getRedirectCount());
            assertNotNull(asyncRegistration.getRegistrationUri());
            assertEquals(
                    asyncRegistration.getRegistrationUri(),
                    validAsyncRegistration.getRegistrationUri());
            assertNotNull(asyncRegistration.getDebugKeyAllowed());
            assertEquals(
                    asyncRegistration.getDebugKeyAllowed(),
                    validAsyncRegistration.getDebugKeyAllowed());
        }
    }

    /** Test that records in AsyncRegistration queue are fetched properly. */
    @Test
    public void testFetchNextQueuedAsyncRegistration_validRetryLimit() {
        AsyncRegistration asyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
        String asyncRegistrationId = asyncRegistration.getId();

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.insertAsyncRegistration(asyncRegistration));
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            AsyncRegistration fetchedAsyncRegistration =
                                    dao.fetchNextQueuedAsyncRegistration(
                                            (short) 1, new ArrayList<>());
                            assertNotNull(fetchedAsyncRegistration);
                            assertEquals(fetchedAsyncRegistration.getId(), asyncRegistrationId);
                            fetchedAsyncRegistration.incrementRetryCount();
                            dao.updateRetryCount(fetchedAsyncRegistration);
                        });

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            AsyncRegistration fetchedAsyncRegistration =
                                    dao.fetchNextQueuedAsyncRegistration(
                                            (short) 1, new ArrayList<>());
                            assertNull(fetchedAsyncRegistration);
                        });
    }

    /** Test that records in AsyncRegistration queue are fetched properly. */
    @Test
    public void testFetchNextQueuedAsyncRegistration_excludeByEnrollmentId() {
        AsyncRegistration firstAsyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistration();
        AsyncRegistration secondAsyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistration();
        String firstAsyncRegistrationEnrollmentId = firstAsyncRegistration.getEnrollmentId();
        String secondAsyncRegistrationEnrollmentId = secondAsyncRegistration.getEnrollmentId();

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.insertAsyncRegistration(firstAsyncRegistration));
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.insertAsyncRegistration(secondAsyncRegistration));
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            ArrayList<String> excludedEnrollmentIds = new ArrayList<>();
                            excludedEnrollmentIds.add(firstAsyncRegistrationEnrollmentId);
                            AsyncRegistration fetchedAsyncRegistration =
                                    dao.fetchNextQueuedAsyncRegistration(
                                            (short) 1, excludedEnrollmentIds);
                            assertNotNull(fetchedAsyncRegistration);
                            assertEquals(
                                    fetchedAsyncRegistration.getEnrollmentId(),
                                    secondAsyncRegistrationEnrollmentId);
                        });
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            ArrayList<String> excludedEnrollmentIds = new ArrayList<>();
                            excludedEnrollmentIds.add(firstAsyncRegistrationEnrollmentId);
                            excludedEnrollmentIds.add(secondAsyncRegistrationEnrollmentId);
                            AsyncRegistration fetchedAsyncRegistration =
                                    dao.fetchNextQueuedAsyncRegistration(
                                            (short) 1, excludedEnrollmentIds);
                            assertNull(fetchedAsyncRegistration);
                        });
    }

    /** Test that AsyncRegistration is deleted correctly. */
    @Test
    public void testDeleteAsyncRegistration() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        AsyncRegistration asyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
        String asyncRegistrationID = asyncRegistration.getId();

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.insertAsyncRegistration(asyncRegistration));
        try (Cursor cursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                MeasurementTables.AsyncRegistrationContract.ID + " = ? ",
                                new String[] {asyncRegistration.getId().toString()},
                                null,
                                null,
                                null)) {
            assertTrue(cursor.moveToNext());
            AsyncRegistration updateAsyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertNotNull(updateAsyncRegistration);
        }
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.deleteAsyncRegistration(asyncRegistration.getId()));

        db.query(
                /* table */ MeasurementTables.AsyncRegistrationContract.TABLE,
                /* columns */ null,
                /* selection */ MeasurementTables.AsyncRegistrationContract.ID + " = ? ",
                /* selectionArgs */ new String[] {asyncRegistrationID.toString()},
                /* groupBy */ null,
                /* having */ null,
                /* orderedBy */ null);

        assertThat(
                        db.query(
                                        /* table */ MeasurementTables.AsyncRegistrationContract
                                                .TABLE,
                                        /* columns */ null,
                                        /* selection */ MeasurementTables.AsyncRegistrationContract
                                                        .ID
                                                + " = ? ",
                                        /* selectionArgs */ new String[] {
                                            asyncRegistrationID.toString()
                                        },
                                        /* groupBy */ null,
                                        /* having */ null,
                                        /* orderedBy */ null)
                                .getCount())
                .isEqualTo(0);
    }

    /** Test that retry count in AsyncRegistration is updated correctly. */
    @Test
    public void testUpdateAsyncRegistrationRetryCount() {
        AsyncRegistration asyncRegistration = AsyncRegistrationFixture.getValidAsyncRegistration();
        String asyncRegistrationId = asyncRegistration.getId();
        long originalRetryCount = asyncRegistration.getRetryCount();

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.insertAsyncRegistration(asyncRegistration));
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            asyncRegistration.incrementRetryCount();
                            dao.updateRetryCount(asyncRegistration);
                        });

        try (Cursor cursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                MeasurementTables.AsyncRegistrationContract.ID + " = ? ",
                                new String[] {asyncRegistrationId},
                                null,
                                null,
                                null)) {
            assertTrue(cursor.moveToNext());
            AsyncRegistration updateAsyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            assertNotNull(updateAsyncRegistration);
            assertTrue(updateAsyncRegistration.getRetryCount() == originalRetryCount + 1);
        }
    }

    @Test
    public void getSource_fetchesMatchingSourceFromDb() {
        // Setup - insert 2 sources with different IDs
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        String sourceId1 = "source1";
        Source source1 = SourceFixture.getValidSourceBuilder().setId(sourceId1).build();
        insertInDb(db, source1);
        String sourceId2 = "source2";
        Source source2 = SourceFixture.getValidSourceBuilder().setId(sourceId2).build();
        insertInDb(db, source2);

        // Execution
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            assertEquals(source1, dao.getSource(sourceId1));
                            assertEquals(source2, dao.getSource(sourceId2));
                        });
    }

    @Test
    public void fetchMatchingAggregateReports_returnsMatchingReports() {
        // setup - create reports for 3*3 combinations of source and trigger
        List<Source> sources =
                Arrays.asList(
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(1L))
                                .setId("source1")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(2L))
                                .setId("source2")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(3L))
                                .setId("source3")
                                .build());
        List<Trigger> triggers =
                Arrays.asList(
                        TriggerFixture.getValidTriggerBuilder().setId("trigger1").build(),
                        TriggerFixture.getValidTriggerBuilder().setId("trigger2").build(),
                        TriggerFixture.getValidTriggerBuilder().setId("trigger3").build());
        List<AggregateReport> reports =
                ImmutableList.of(
                        createAggregateReportForSourceAndTrigger(sources.get(0), triggers.get(0)),
                        createAggregateReportForSourceAndTrigger(sources.get(0), triggers.get(1)),
                        createAggregateReportForSourceAndTrigger(sources.get(0), triggers.get(2)),
                        createAggregateReportForSourceAndTrigger(sources.get(1), triggers.get(0)),
                        createAggregateReportForSourceAndTrigger(sources.get(1), triggers.get(1)),
                        createAggregateReportForSourceAndTrigger(sources.get(1), triggers.get(2)),
                        createAggregateReportForSourceAndTrigger(sources.get(2), triggers.get(0)),
                        createAggregateReportForSourceAndTrigger(sources.get(2), triggers.get(1)),
                        createAggregateReportForSourceAndTrigger(sources.get(2), triggers.get(2)));

        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        sources.forEach(source -> AbstractDbIntegrationTest.insertToDb(source, db));
        triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));
        reports.forEach(
                report ->
                        DatastoreManagerFactory.getDatastoreManager(sContext)
                                .runInTransaction((dao) -> dao.insertAggregateReport(report)));

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            // Execution
                            List<AggregateReport> aggregateReports =
                                    dao.fetchMatchingAggregateReports(
                                            Arrays.asList(
                                                    sources.get(1).getId(), "nonMatchingSource"),
                                            Arrays.asList(
                                                    triggers.get(2).getId(), "nonMatchingTrigger"));
                            assertEquals(5, aggregateReports.size());

                            aggregateReports =
                                    dao.fetchMatchingAggregateReports(
                                            Arrays.asList(
                                                    sources.get(0).getId(), sources.get(1).getId()),
                                            Collections.emptyList());
                            assertEquals(6, aggregateReports.size());

                            aggregateReports =
                                    dao.fetchMatchingAggregateReports(
                                            Collections.emptyList(),
                                            Arrays.asList(
                                                    triggers.get(0).getId(),
                                                    triggers.get(2).getId()));
                            assertEquals(6, aggregateReports.size());

                            aggregateReports =
                                    dao.fetchMatchingAggregateReports(
                                            Arrays.asList(
                                                    sources.get(0).getId(),
                                                    sources.get(1).getId(),
                                                    sources.get(2).getId()),
                                            Arrays.asList(
                                                    triggers.get(0).getId(),
                                                    triggers.get(1).getId(),
                                                    triggers.get(2).getId()));
                            assertEquals(9, aggregateReports.size());
                        });
    }

    @Test
    public void fetchMatchingEventReports_returnsMatchingReports() throws JSONException {
        // setup - create reports for 3*3 combinations of source and trigger
        List<Source> sources =
                Arrays.asList(
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(1L))
                                .setId("source1")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(2L))
                                .setId("source2")
                                .build(),
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(3L))
                                .setId("source3")
                                .build());
        List<Trigger> triggers =
                Arrays.asList(
                        TriggerFixture.getValidTriggerBuilder()
                                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                                .setId("trigger1")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                                .setId("trigger2")
                                .build(),
                        TriggerFixture.getValidTriggerBuilder()
                                .setEventTriggers(TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS)
                                .setId("trigger3")
                                .build());
        List<EventReport> reports =
                ImmutableList.of(
                        createEventReportForSourceAndTrigger(sources.get(0), triggers.get(0)),
                        createEventReportForSourceAndTrigger(sources.get(0), triggers.get(1)),
                        createEventReportForSourceAndTrigger(sources.get(0), triggers.get(2)),
                        createEventReportForSourceAndTrigger(sources.get(1), triggers.get(0)),
                        createEventReportForSourceAndTrigger(sources.get(1), triggers.get(1)),
                        createEventReportForSourceAndTrigger(sources.get(1), triggers.get(2)),
                        createEventReportForSourceAndTrigger(sources.get(2), triggers.get(0)),
                        createEventReportForSourceAndTrigger(sources.get(2), triggers.get(1)),
                        createEventReportForSourceAndTrigger(sources.get(2), triggers.get(2)));

        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        sources.forEach(source -> AbstractDbIntegrationTest.insertToDb(source, db));
        triggers.forEach(trigger -> AbstractDbIntegrationTest.insertToDb(trigger, db));
        reports.forEach(
                report ->
                        DatastoreManagerFactory.getDatastoreManager(sContext)
                                .runInTransaction((dao) -> dao.insertEventReport(report)));

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            // Execution
                            List<EventReport> eventReports =
                                    dao.fetchMatchingEventReports(
                                            Arrays.asList(
                                                    sources.get(1).getId(), "nonMatchingSource"),
                                            Arrays.asList(
                                                    triggers.get(2).getId(), "nonMatchingTrigger"));
                            assertEquals(5, eventReports.size());

                            eventReports =
                                    dao.fetchMatchingEventReports(
                                            Arrays.asList(
                                                    sources.get(0).getId(), sources.get(1).getId()),
                                            Collections.emptyList());
                            assertEquals(6, eventReports.size());

                            eventReports =
                                    dao.fetchMatchingEventReports(
                                            Collections.emptyList(),
                                            Arrays.asList(
                                                    triggers.get(0).getId(),
                                                    triggers.get(2).getId()));
                            assertEquals(6, eventReports.size());

                            eventReports =
                                    dao.fetchMatchingEventReports(
                                            Arrays.asList(
                                                    sources.get(0).getId(),
                                                    sources.get(1).getId(),
                                                    sources.get(2).getId()),
                                            Arrays.asList(
                                                    triggers.get(0).getId(),
                                                    triggers.get(1).getId(),
                                                    triggers.get(2).getId()));
                            assertEquals(9, eventReports.size());
                        });
    }

    @Test
    public void fetchMatchingSources_bringsMatchingSources() {
        // Setup
        Source source1 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(Uri.parse("https://subdomain1.site1.com"))
                        .setEventTime(5000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("source1")
                        .build();
        Source source2 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(2L))
                        .setPublisher(Uri.parse("https://subdomain1.site1.com"))
                        .setEventTime(10000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("source2")
                        .build();
        Source source3 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(3L))
                        .setPublisher(Uri.parse("https://subdomain2.site1.com"))
                        .setEventTime(15000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("source3")
                        .build();
        Source source4 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(4L))
                        .setPublisher(Uri.parse("https://subdomain2.site2.com"))
                        .setEventTime(15000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("source4")
                        .build();
        Source source5 =
                SourceFixture.getValidSourceBuilder()
                        .setEventId(new UnsignedLong(5L))
                        .setPublisher(Uri.parse("https://subdomain2.site1.com"))
                        .setEventTime(20000)
                        .setRegistrant(Uri.parse("android-app://com.registrant2"))
                        .setId("source5")
                        .build();
        List<Source> sources = List.of(source1, source2, source3, source4, source5);

        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        sources.forEach(source -> insertInDb(db, source));

        // Execution
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        dao -> {
                            // --- DELETE behaviour ---
                            // 1,2,3 & 4 are match registrant1
                            List<String> actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(),
                                            DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(4, actualSources.size());

                            // 1 & 2 match registrant1 and "https://subdomain1.site1.com" publisher
                            // origin
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(2, actualSources.size());

                            // Only 2 matches registrant1 and "https://subdomain1.site1.com"
                            // publisher origin within
                            // the range
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(8000),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(1, actualSources.size());

                            // 1,2 & 3 matches registrant1 and "https://site1.com" publisher origin
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(Uri.parse("https://site1.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(3, actualSources.size());

                            // 3 matches origin and 4 matches domain URI
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(10000),
                                            Instant.ofEpochMilli(20000),
                                            List.of(Uri.parse("https://subdomain2.site1.com")),
                                            List.of(Uri.parse("https://site2.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(2, actualSources.size());

                            // --- PRESERVE (anti-match exception registrant) behaviour ---
                            // all registrant1 registrant based sources are matched to returns 0 as
                            // anti-match
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(),
                                            DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(0, actualSources.size());

                            // 3 & 4 match registrant1 and don't match
                            // "https://subdomain1.site1.com" publisher origin
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(2, actualSources.size());

                            // 3 & 4 match registrant1, in range and don't match
                            // "https://subdomain1.site1.com"
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(8000),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(2, actualSources.size());

                            // Only 4 matches registrant1, in range and don't match
                            // "https://site1.com"
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(Uri.parse("https://site1.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(1, actualSources.size());

                            // only 2 is registrant1 based, in range and does not match either
                            // site2.com or subdomain2.site1.com
                            actualSources =
                                    dao.fetchMatchingSources(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(10000),
                                            Instant.ofEpochMilli(20000),
                                            List.of(Uri.parse("https://subdomain2.site1.com")),
                                            List.of(Uri.parse("https://site2.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(1, actualSources.size());
                        });
    }

    @Test
    public void fetchMatchingTriggers_bringsMatchingTriggers() {
        // Setup
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://subdomain1.site1.com"))
                        .setTriggerTime(5000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("trigger1")
                        .build();
        Trigger trigger2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://subdomain1.site1.com"))
                        .setTriggerTime(10000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("trigger2")
                        .build();
        Trigger trigger3 =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://subdomain2.site1.com"))
                        .setTriggerTime(15000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("trigger3")
                        .build();
        Trigger trigger4 =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://subdomain2.site2.com"))
                        .setTriggerTime(15000)
                        .setRegistrant(Uri.parse("android-app://com.registrant1"))
                        .setId("trigger4")
                        .build();
        Trigger trigger5 =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://subdomain2.site1.com"))
                        .setTriggerTime(20000)
                        .setRegistrant(Uri.parse("android-app://com.registrant2"))
                        .setId("trigger5")
                        .build();
        List<Trigger> triggers = List.of(trigger1, trigger2, trigger3, trigger4, trigger5);

        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        triggers.forEach(
                trigger -> {
                    ContentValues values = new ContentValues();
                    values.put(TriggerContract.ID, trigger.getId());
                    values.put(
                            TriggerContract.ATTRIBUTION_DESTINATION,
                            trigger.getAttributionDestination().toString());
                    values.put(TriggerContract.TRIGGER_TIME, trigger.getTriggerTime());
                    values.put(TriggerContract.ENROLLMENT_ID, trigger.getEnrollmentId());
                    values.put(TriggerContract.REGISTRANT, trigger.getRegistrant().toString());
                    values.put(TriggerContract.STATUS, trigger.getStatus());
                    db.insert(TriggerContract.TABLE, /* nullColumnHack */ null, values);
                });

        // Execution
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        dao -> {
                            // --- DELETE behaviour ---
                            // 1,2,3 & 4 are match registrant1
                            List<String> actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(),
                                            DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(4, actualSources.size());

                            // 1 & 2 match registrant1 and "https://subdomain1.site1.com" publisher
                            // origin
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(2, actualSources.size());

                            // Only 2 matches registrant1 and "https://subdomain1.site1.com"
                            // publisher origin within
                            // the range
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(8000),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(1, actualSources.size());

                            // 1,2 & 3 matches registrant1 and "https://site1.com" publisher origin
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(Uri.parse("https://site1.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(3, actualSources.size());

                            // 3 matches origin and 4 matches domain URI
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(10000),
                                            Instant.ofEpochMilli(20000),
                                            List.of(Uri.parse("https://subdomain2.site1.com")),
                                            List.of(Uri.parse("https://site2.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_DELETE);
                            assertEquals(2, actualSources.size());

                            // --- PRESERVE (anti-match exception registrant) behaviour ---
                            // all registrant1 registrant based sources are matched to returns 0 as
                            // anti-match
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(),
                                            DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(0, actualSources.size());

                            // 3 & 4 match registrant1 and don't match
                            // "https://subdomain1.site1.com" publisher origin
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(2, actualSources.size());

                            // 3 & 4 match registrant1, in range and don't match
                            // "https://subdomain1.site1.com"
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                                    Instant.ofEpochMilli(8000),
                                            Instant.ofEpochMilli(50000),
                                                    List.of(
                                                            Uri.parse(
                                                                    "https://subdomain1.site1.com")),
                                            List.of(), DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(2, actualSources.size());

                            // Only 4 matches registrant1, in range and don't match
                            // "https://site1.com"
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(0),
                                            Instant.ofEpochMilli(50000),
                                            List.of(),
                                            List.of(Uri.parse("https://site1.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(1, actualSources.size());

                            // only 2 is registrant1 based, in range and does not match either
                            // site2.com or subdomain2.site1.com
                            actualSources =
                                    dao.fetchMatchingTriggers(
                                            Uri.parse("android-app://com.registrant1"),
                                            Instant.ofEpochMilli(10000),
                                            Instant.ofEpochMilli(20000),
                                            List.of(Uri.parse("https://subdomain2.site1.com")),
                                            List.of(Uri.parse("https://site2.com")),
                                            DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
                            assertEquals(1, actualSources.size());
                        });
    }

    private AggregateReport createAggregateReportForSourceAndTrigger(
            Source source, Trigger trigger) {
        return createAggregateReportForSourceAndTrigger(
                UUID.randomUUID().toString(), source, trigger);
    }

    private EventReport createEventReportForSourceAndTrigger(Source source, Trigger trigger)
            throws JSONException {
        return createEventReportForSourceAndTrigger(UUID.randomUUID().toString(), source, trigger);
    }

    private AggregateReport createAggregateReportForSourceAndTrigger(
            String reportId, Source source, Trigger trigger) {
        return AggregateReportFixture.getValidAggregateReportBuilder()
                .setId(reportId)
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .build();
    }

    private EventReport createEventReportForSourceAndTrigger(
            String reportId, Source source, Trigger trigger) throws JSONException {
        return new EventReport.Builder()
                .setId(reportId)
                .populateFromSourceAndTrigger(source, trigger, trigger.parseEventTriggers().get(0))
                .setSourceEventId(source.getEventId())
                .setSourceId(source.getId())
                .setTriggerId(trigger.getId())
                .build();
    }

    private void setupSourceAndTriggerData() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Source> sourcesList = new ArrayList<>();
        sourcesList.add(
                SourceFixture.getValidSourceBuilder()
                        .setId("S1")
                        .setRegistrant(APP_TWO_SOURCES)
                        .setPublisher(APP_TWO_PUBLISHER)
                        .setPublisherType(EventSurfaceType.APP)
                        .build());
        sourcesList.add(
                SourceFixture.getValidSourceBuilder()
                        .setId("S2")
                        .setRegistrant(APP_TWO_SOURCES)
                        .setPublisher(APP_TWO_PUBLISHER)
                        .setPublisherType(EventSurfaceType.APP)
                        .build());
        sourcesList.add(
                SourceFixture.getValidSourceBuilder()
                        .setId("S3")
                        .setRegistrant(APP_ONE_SOURCE)
                        .setPublisher(APP_ONE_PUBLISHER)
                        .setPublisherType(EventSurfaceType.APP)
                        .build());
        for (Source source : sourcesList) {
            ContentValues values = new ContentValues();
            values.put("_id", source.getId());
            values.put("registrant", source.getRegistrant().toString());
            values.put("publisher", source.getPublisher().toString());
            values.put("publisher_type", source.getPublisherType());

            long row = db.insert("msmt_source", null, values);
            assertNotEquals("Source insertion failed", -1, row);
        }
        List<Trigger> triggersList = new ArrayList<>();
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T1")
                        .setRegistrant(APP_TWO_DESTINATION)
                        .build());
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T2")
                        .setRegistrant(APP_TWO_DESTINATION)
                        .build());
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T3")
                        .setRegistrant(APP_ONE_DESTINATION)
                        .build());

        // Add web triggers.
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T4")
                        .setRegistrant(APP_BROWSER)
                        .setAttributionDestination(WEB_ONE_DESTINATION)
                        .build());
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T5")
                        .setRegistrant(APP_BROWSER)
                        .setAttributionDestination(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN)
                        .build());
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T7")
                        .setRegistrant(APP_BROWSER)
                        .setAttributionDestination(WEB_ONE_DESTINATION_DIFFERENT_SUBDOMAIN_2)
                        .build());
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T8")
                        .setRegistrant(APP_BROWSER)
                        .setAttributionDestination(WEB_TWO_DESTINATION)
                        .build());
        triggersList.add(
                TriggerFixture.getValidTriggerBuilder()
                        .setId("T9")
                        .setRegistrant(APP_BROWSER)
                        .setAttributionDestination(WEB_TWO_DESTINATION_WITH_PATH)
                        .build());

        for (Trigger trigger : triggersList) {
            ContentValues values = new ContentValues();
            values.put("_id", trigger.getId());
            values.put("registrant", trigger.getRegistrant().toString());
            values.put("attribution_destination", trigger.getAttributionDestination().toString());
            long row = db.insert("msmt_trigger", null, values);
            Assert.assertNotEquals("Trigger insertion failed", -1, row);
        }
    }

    private Trigger createWebTrigger(Uri attributionDestination) {
        return TriggerFixture.getValidTriggerBuilder()
                .setId("ID" + mValueId++)
                .setAttributionDestination(attributionDestination)
                .setRegistrant(APP_BROWSER)
                .build();
    }

    private Trigger createAppTrigger(Uri registrant, Uri destination) {
        return TriggerFixture.getValidTriggerBuilder()
                .setId("ID" + mValueId++)
                .setAttributionDestination(destination)
                .setRegistrant(registrant)
                .build();
    }

    private void addTriggersToDatabase(List<Trigger> triggersList) {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        for (Trigger trigger : triggersList) {
            ContentValues values = new ContentValues();
            values.put("_id", trigger.getId());
            values.put("registrant", trigger.getRegistrant().toString());
            values.put("attribution_destination", trigger.getAttributionDestination().toString());
            long row = db.insert("msmt_trigger", null, values);
            assertNotEquals("Trigger insertion failed", -1, row);
        }
    }

    private void setupSourceDataForPublisherTypeWeb() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Source> sourcesList = new ArrayList<>();
        sourcesList.add(
                SourceFixture.getValidSourceBuilder()
                        .setId("W1")
                        .setPublisher(WEB_PUBLISHER_ONE)
                        .setPublisherType(EventSurfaceType.WEB)
                        .build());
        sourcesList.add(
                SourceFixture.getValidSourceBuilder()
                        .setId("W2")
                        .setPublisher(WEB_PUBLISHER_TWO)
                        .setPublisherType(EventSurfaceType.WEB)
                        .build());
        sourcesList.add(
                SourceFixture.getValidSourceBuilder()
                        .setId("S3")
                        .setPublisher(WEB_PUBLISHER_THREE)
                        .setPublisherType(EventSurfaceType.WEB)
                        .build());
        for (Source source : sourcesList) {
            ContentValues values = new ContentValues();
            values.put("_id", source.getId());
            values.put("publisher", source.getPublisher().toString());
            values.put("publisher_type", source.getPublisherType());

            long row = db.insert("msmt_source", null, values);
            assertNotEquals("Source insertion failed", -1, row);
        }
    }

    private Source createSourceForIATest(
            String id,
            long currentTime,
            long priority,
            int eventTimePastDays,
            boolean expiredIAWindow,
            String enrollmentId) {
        return new Source.Builder()
                .setId(id)
                .setPublisher(Uri.parse("android-app://com.example.sample"))
                .setRegistrant(Uri.parse("android-app://com.example.sample"))
                .setEnrollmentId(enrollmentId)
                .setExpiryTime(currentTime + TimeUnit.DAYS.toMillis(30))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(expiredIAWindow ? 0 : 30))
                .setAppDestination(INSTALLED_PACKAGE)
                .setEventTime(
                        currentTime
                                - TimeUnit.DAYS.toMillis(
                                        eventTimePastDays == -1 ? 10 : eventTimePastDays))
                .setPriority(priority == -1 ? 100 : priority)
                .build();
    }

    private AggregateReport generateMockAggregateReport(String attributionDestination, int id) {
        return new AggregateReport.Builder()
                .setId(String.valueOf(id))
                .setAttributionDestination(Uri.parse(attributionDestination))
                .build();
    }

    private EventReport generateMockEventReport(String attributionDestination, int id) {
        return new EventReport.Builder()
                .setId(String.valueOf(id))
                .setAttributionDestination(Uri.parse(attributionDestination))
                .build();
    }

    private void assertAggregateReportCount(
            List<String> attributionDestinations,
            int destinationType,
            List<Integer> expectedCounts) {
        IntStream.range(0, attributionDestinations.size())
                .forEach(i -> Assert.assertEquals(expectedCounts.get(i),
                        DatastoreManagerFactory.getDatastoreManager(sContext)
                                .runInTransactionWithResult(measurementDao ->
                                        measurementDao.getNumAggregateReportsPerDestination(
                                                Uri.parse(attributionDestinations.get(i)),
                                                destinationType))
                                .orElseThrow()));
    }

    private void assertEventReportCount(
            List<String> attributionDestinations,
            int destinationType,
            List<Integer> expectedCounts) {
        IntStream.range(0, attributionDestinations.size())
                .forEach(i -> Assert.assertEquals(expectedCounts.get(i),
                        DatastoreManagerFactory.getDatastoreManager(sContext)
                                .runInTransactionWithResult(measurementDao ->
                                        measurementDao.getNumEventReportsPerDestination(
                                                Uri.parse(attributionDestinations.get(i)),
                                                destinationType))
                                .orElseThrow()));
    }

    private List<String> createAppDestinationVariants(int destinationNum) {
        return Arrays.asList(
                "android-app://subdomain.destination-" + destinationNum + ".app/abcd",
                "android-app://subdomain.destination-" + destinationNum + ".app",
                "android-app://destination-" + destinationNum + ".app/abcd",
                "android-app://destination-" + destinationNum + ".app",
                "android-app://destination-" + destinationNum + ".ap");
    }

    private List<String> createWebDestinationVariants(int destinationNum) {
        return Arrays.asList(
                "https://subdomain.destination-" + destinationNum + ".com/abcd",
                "https://subdomain.destination-" + destinationNum + ".com",
                "https://destination-" + destinationNum + ".com/abcd",
                "https://destination-" + destinationNum + ".com",
                "https://destination-" + destinationNum + ".co");
    }

    private boolean getInstallAttributionStatus(String sourceDbId, SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        SourceContract.TABLE,
                        new String[] {SourceContract.IS_INSTALL_ATTRIBUTED},
                        SourceContract.ID + " = ? ",
                        new String[] {sourceDbId},
                        null,
                        null,
                        null,
                        null);
        assertTrue(cursor.moveToFirst());
        return cursor.getInt(0) == 1;
    }

    private void removeSources(List<String> dbIds, SQLiteDatabase db) {
        db.delete(
                SourceContract.TABLE,
                SourceContract.ID + " IN ( ? )",
                new String[] {String.join(",", dbIds)});
    }

    /** Create {@link Attribution} object from SQLite datastore. */
    private static Attribution constructAttributionFromCursor(Cursor cursor) {
        Attribution.Builder builder = new Attribution.Builder();
        int index = cursor.getColumnIndex(MeasurementTables.AttributionContract.ID);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setId(cursor.getString(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.SOURCE_SITE);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setSourceSite(cursor.getString(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.SOURCE_ORIGIN);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setSourceOrigin(cursor.getString(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.DESTINATION_SITE);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setDestinationSite(cursor.getString(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.DESTINATION_ORIGIN);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setDestinationOrigin(cursor.getString(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.ENROLLMENT_ID);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setEnrollmentId(cursor.getString(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.TRIGGER_TIME);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setTriggerTime(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(MeasurementTables.AttributionContract.REGISTRANT);
        if (index > -1 && !cursor.isNull(index)) {
            builder.setRegistrant(cursor.getString(index));
        }
        return builder.build();
    }
}
