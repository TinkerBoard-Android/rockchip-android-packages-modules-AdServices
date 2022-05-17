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

package com.android.adservices.service.measurement.attribution;

import android.annotation.Nullable;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.adservices.service.measurement.Trigger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class AttributionJobHandler {

    private final DatastoreManager mDatastoreManager;

    AttributionJobHandler(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    /**
     * Finds the {@link AdtechUrl} when given a postback url.
     *
     * @param postbackUrl the postback url of the request AdtechUrl
     * @return the requested AdtechUrl; Null in case of SQL failure
     */
    @Nullable
    synchronized AdtechUrl findAdtechUrl(String postbackUrl) {
        if (postbackUrl == null) {
            return null;
        }
        return mDatastoreManager
                .runInTransactionWithResult((dao) -> dao.getAdtechEnrollmentData(postbackUrl))
                .orElse(null);
    }

    /**
     * Queries and returns all the postback urls with the same adtech id as the given postback url.
     *
     * @param postbackUrl the postback url of the request AdtechUrl
     * @return all the postback urls with the same adtech id; Null in case of SQL failure
     */
    public List<String> getAllAdtechUrls(String postbackUrl) {
        if (postbackUrl == null) {
            return new ArrayList<>();
        }
        return mDatastoreManager
                .runInTransactionWithResult((dao) -> dao.getAllAdtechUrls(postbackUrl))
                .orElse(null);
    }

    /**
     * Perform attribution by finding relevant {@link Source} and generates {@link EventReport}.
     *
     * @return false if there are datastore failures or pending {@link Trigger} left, true otherwise
     */
    synchronized boolean performPendingAttributions() {
        Optional<List<String>> pendingTriggersOpt = mDatastoreManager
                .runInTransactionWithResult(IMeasurementDao::getPendingTriggerIds);
        if (!pendingTriggersOpt.isPresent()) {
            // Failure during trigger retrieval
            // Reschedule for retry
            return false;
        }
        List<String> pendingTriggers = pendingTriggersOpt.get();

        for (int i = 0; i < pendingTriggers.size()
                && i < SystemHealthParams.MAX_ATTRIBUTIONS_PER_INVOCATION; i++) {
            boolean success = performAttribution(pendingTriggers.get(i));
            if (!success) {
                // Failure during trigger attribution
                // Reschedule for retry
                return false;
            }
        }

        // Reschedule if there are unprocessed pending triggers.
        return SystemHealthParams.MAX_ATTRIBUTIONS_PER_INVOCATION >= pendingTriggers.size();
    }

    /**
     * Perform attribution for {@code triggerId}.
     *
     * @param triggerId datastore id of the {@link Trigger}
     * @return success
     */
    private boolean performAttribution(String triggerId) {
        return mDatastoreManager.runInTransaction(measurementDao -> {
            Trigger trigger = measurementDao.getTrigger(triggerId);
            if (trigger.getStatus() != Trigger.Status.PENDING) {
                return;
            }
            Optional<Source> sourceOpt = getMatchingSource(trigger, measurementDao);
            if (sourceOpt.isEmpty()) {
                ignoreTrigger(trigger, measurementDao);
                return;
            }
            Source source = sourceOpt.get();

            if (!hasAttributionQuota(source, trigger, measurementDao)) {
                ignoreTrigger(trigger, measurementDao);
                return;
            }

            boolean eventReportGenerated =
                    maybeGenerateEventReport(source, trigger, measurementDao);

            if (eventReportGenerated) {
                attributeTriggerAndIncrementRateLimit(trigger, source, measurementDao);
            } else {
                ignoreTrigger(trigger, measurementDao);
            }
        });
    }

    private Optional<Source> getMatchingSource(Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        List<Source> matchingSources = measurementDao.getMatchingActiveSources(trigger);

        if (matchingSources.isEmpty()) {
            return Optional.empty();
        }

        // Sort based on isInstallAttributed, Priority and Event Time.
        matchingSources.sort(
                Comparator.comparing(
                        (Source source) ->
                            // Is a valid install-attributed source.
                            source.isInstallAttributed()
                                    && isWithinInstallCooldownWindow(source,
                                    trigger),
                            Comparator.reverseOrder())
                        .thenComparing(Source::getPriority, Comparator.reverseOrder())
                        .thenComparing(Source::getEventTime, Comparator.reverseOrder()));

        Source selectedSource = matchingSources.get(0);
        matchingSources.remove(0);
        if (!matchingSources.isEmpty()) {
            matchingSources.forEach((s) -> s.setStatus(Source.Status.IGNORED));
            measurementDao.updateSourceStatus(matchingSources, Source.Status.IGNORED);
        }
        return Optional.of(selectedSource);
    }

    private boolean maybeGenerateEventReport(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {
        // Do not generate event reports for source which have attributionMode != Truthfully.
        // TODO: Handle attribution rate limit consideration for non-truthful cases.
        if (source.getAttributionMode() != Source.AttributionMode.TRUTHFULLY) {
            return false;
        }
        // Check if deduplication key clashes with existing reports.
        if (trigger.getDedupKey() != null
                && source.getDedupKeys().contains(trigger.getDedupKey())) {
            return false;
        }

        EventReport newEventReport = new EventReport.Builder()
                .populateFromSourceAndTrigger(source, trigger).build();

        if (!provisionEventReportQuota(source, newEventReport, measurementDao)) {
            return false;
        }

        finalizeEventReportCreation(source, trigger, newEventReport, measurementDao);
        return true;
    }

    private boolean provisionEventReportQuota(Source source,
            EventReport newEventReport, IMeasurementDao measurementDao) throws DatastoreException {
        List<EventReport> sourceEventReports =
                measurementDao.getSourceEventReports(source);

        if (isWithinReportLimit(source, sourceEventReports.size())) {
            return true;
        }

        List<EventReport> relevantEventReports = sourceEventReports.stream()
                .filter((r) -> r.getStatus() == EventReport.Status.PENDING)
                .filter((r) -> r.getReportTime() == newEventReport.getReportTime())
                .sorted(Comparator.comparingLong(EventReport::getTriggerPriority)
                        .thenComparing(EventReport::getTriggerTime, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        if (relevantEventReports.isEmpty()) {
            return false;
        }

        EventReport lowestPriorityEventReport = relevantEventReports.get(0);
        if (lowestPriorityEventReport.getTriggerPriority()
                >= newEventReport.getTriggerPriority()) {
            return false;
        }

        if (lowestPriorityEventReport.getTriggerDedupKey() != null) {
            source.getDedupKeys().remove(lowestPriorityEventReport.getTriggerDedupKey());
        }
        measurementDao.deleteEventReport(lowestPriorityEventReport);
        return true;
    }

    private void finalizeEventReportCreation(
            Source source, Trigger trigger, EventReport eventReport, IMeasurementDao measurementDao)
            throws DatastoreException {
        if (trigger.getDedupKey() != null) {
            source.getDedupKeys().add(trigger.getDedupKey());
        }
        measurementDao.updateSourceDedupKeys(source);

        measurementDao.insertEventReport(eventReport);
    }

    private void attributeTriggerAndIncrementRateLimit(Trigger trigger, Source source,
            IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        measurementDao.updateTriggerStatus(trigger);
        measurementDao.insertAttributionRateLimit(source, trigger);
    }

    private void ignoreTrigger(Trigger trigger, IMeasurementDao measurementDao)
            throws DatastoreException {
        trigger.setStatus(Trigger.Status.IGNORED);
        measurementDao.updateTriggerStatus(trigger);
    }

    private boolean hasAttributionQuota(Source source, Trigger trigger,
            IMeasurementDao measurementDao) throws DatastoreException {
        long attributionCount =
                measurementDao.getAttributionsPerRateLimitWindow(source, trigger);
        return attributionCount < PrivacyParams.MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW;
    }

    private boolean isWithinReportLimit(Source source, int existingReportCount) {
        return source.getMaxReportCount() > existingReportCount;
    }

    private boolean isWithinInstallCooldownWindow(Source source, Trigger trigger) {
        return trigger.getTriggerTime()
                < (source.getEventTime() + source.getInstallCooldownWindow());
    }
}
