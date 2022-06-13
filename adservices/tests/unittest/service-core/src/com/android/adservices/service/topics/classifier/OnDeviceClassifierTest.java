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

package com.android.adservices.service.topics.classifier;

import static com.android.adservices.service.topics.classifier.OnDeviceClassifier.MAX_LABELS_PER_APP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.topics.AppInfo;
import com.android.adservices.service.topics.PackageManagerUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/** Topic Classifier Test {@link OnDeviceClassifier}. */
public class OnDeviceClassifierTest {

    private static final String CLASSIFIER_ASSETS_METADATA_PATH =
            "classifier/classifier_assets_metadata.json";

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static Preprocessor sPreprocessor;
    private static OnDeviceClassifier sOnDeviceClassifier;

    @Mock private PackageManagerUtil mPackageManagerUtil;

    private static ImmutableMap<String, ImmutableMap<String, String>> sClassifierAssetsMetadata;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        sPreprocessor = new Preprocessor(sContext);
        sOnDeviceClassifier =
                new OnDeviceClassifier(
                        sPreprocessor, mPackageManagerUtil, sContext.getAssets(), new Random());
        sClassifierAssetsMetadata =
                CommonClassifierHelper.getAssetsMetadata(
                        sContext.getAssets(), CLASSIFIER_ASSETS_METADATA_PATH);
    }

    @Test
    public void testClassify_packageManagerError_returnsDefaultClassifications() {
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        // If fetch from PackageManagerUtil fails, we will use empty strings as descriptions.
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(ImmutableMap.of());

        ImmutableMap<String, List<Integer>> classifications =
                sOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(1);
        // Verify default classification.
        assertThat(classifications.get(appPackage1)).hasSize(MAX_LABELS_PER_APP);
        // Check all the returned labels for default empty string descriptions.
        assertThat(classifications.get(appPackage1))
                .containsExactly(20, 183, 96, 6, 13, 286, 112, 194, 242, 17);
    }

    @Test
    public void testClassify_successfulClassifications() {
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .put(
                                appPackage2,
                                new AppInfo(
                                        "appName2",
                                        "This xyz game is the best adventure game to thrill our"
                                                + " users! Play, win and share with your friends to"
                                                + " win more coins."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1, appPackage2);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        ImmutableMap<String, List<Integer>> classifications =
                sOnDeviceClassifier.classify(appPackages);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        // Two values for two input package names.
        assertThat(classifications).hasSize(2);
        // Verify size of the labels returned is MAX_LABELS_PER_APP.
        assertThat(classifications.get(appPackage1)).hasSize(MAX_LABELS_PER_APP);
        assertThat(classifications.get(appPackage2)).hasSize(MAX_LABELS_PER_APP);

        // Check if the first 10 categories contains at least the top 5.
        // Scores can differ a little on devices. Using this to reduce flakiness.
        // Expected top 10: 43, 140, 151, 189, 193, 208, 271, 262, 6, 136
        assertThat(classifications.get(appPackage1)).containsAtLeast(43, 140, 151, 189, 193);
        // Expected top 10: 93, 88, 90, 99, 101, 96, 1, 232, 91, 3
        assertThat(classifications.get(appPackage2)).containsAtLeast(93, 88, 90, 99, 101);
    }

    @Test
    public void testClassify_successfulClassificationsForUpdatedAppDescription() {
        // Check getClassification for sample descriptions.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        ImmutableMap<String, AppInfo> oldAppInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .build();
        ImmutableMap<String, AppInfo> newAppInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(
                                appPackage1,
                                new AppInfo(
                                        "appName1",
                                        "This xyz game is the best adventure game to thrill our"
                                                + " users! Play, win and share with your friends to"
                                                + " win more coins."))
                        .build();
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1);
        // Return old description first and then the new description.
        when(mPackageManagerUtil.getAppInformation(eq(appPackages)))
                .thenReturn(oldAppInfoMap)
                .thenReturn(newAppInfoMap);

        ImmutableMap<String, List<Integer>> firstClassifications =
                sOnDeviceClassifier.classify(appPackages);
        ImmutableMap<String, List<Integer>> secondClassifications =
                sOnDeviceClassifier.classify(appPackages);

        // Verify two calls to packageManagerUtil.
        verify(mPackageManagerUtil, times(2)).getAppInformation(eq(appPackages));
        // Two values for two input package names.
        assertThat(secondClassifications).hasSize(1);
        // Verify size of the labels returned is MAX_LABELS_PER_APP.
        assertThat(secondClassifications.get(appPackage1)).hasSize(MAX_LABELS_PER_APP);

        // Check if the first 10 categories contains at least the top 5.
        // Scores can differ a little on devices. Using this to reduce flakiness.
        // Check different expected scores for different descriptions.
        // Expected top 10: 43, 140, 151, 189, 193, 208, 271, 262, 6, 136
        assertThat(firstClassifications.get(appPackage1)).containsAtLeast(43, 140, 151, 189, 193);
        // Expected top 10: 93, 88, 90, 99, 101, 96, 1, 232, 91, 3
        assertThat(secondClassifications.get(appPackage1)).containsAtLeast(93, 88, 90, 99, 101);
    }

    @Test
    public void testGetTopTopics_fetchTopAndRandomTopics() {
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        String appPackage3 = "com.example.adservices.samples.topics.sampleapp3";
        String commonAppDescription =
                "This xyz game is the best adventure game to thrill"
                        + " our users! Play, win and share with your"
                        + " friends to win more coins.";
        int numberOfTopTopics = 4, numberOfRandomTopics = 1;
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1, appPackage2, appPackage3);
        // Two packages have same description.
        when(mPackageManagerUtil.getAppInformation(eq(appPackages)))
                .thenReturn(
                        ImmutableMap.<String, AppInfo>builder()
                                .put(
                                        appPackage1,
                                        new AppInfo("appName1", "Sample app description."))
                                .put(appPackage2, new AppInfo("appName2", commonAppDescription))
                                .put(appPackage3, new AppInfo("appName3", commonAppDescription))
                                .build());

        ImmutableMap<String, List<Integer>> classifications =
                sOnDeviceClassifier.classify(appPackages);
        List<Integer> topTopics =
                sOnDeviceClassifier.getTopTopics(
                        classifications, numberOfTopTopics, numberOfRandomTopics);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(3);
        // Check if the returned list has numberOfTopTopics topics.
        assertThat(topTopics).hasSize(numberOfTopTopics + numberOfRandomTopics);
        // Verify the top topics are from the description that was repeated.
        ImmutableList<Integer> expectedLabelsForCommonDescription =
                ImmutableList.of(96, 1, 99, 3, 10, 251, 300, 231, 123, 56);
        assertThat(topTopics.subList(0, numberOfTopTopics))
                .containsAnyIn(expectedLabelsForCommonDescription);
    }

    @Test
    public void testGetTopTopics_verifyRandomTopics() {
        // Verify the last 4 random topics are not the same.
        String appPackage1 = "com.example.adservices.samples.topics.sampleapp1";
        String appPackage2 = "com.example.adservices.samples.topics.sampleapp2";
        ImmutableMap<String, AppInfo> appInfoMap =
                ImmutableMap.<String, AppInfo>builder()
                        .put(appPackage1, new AppInfo("appName1", "Sample app description."))
                        .put(
                                appPackage2,
                                new AppInfo(
                                        "appName2",
                                        "This xyz game is the best adventure game to thrill our"
                                                + " users! Play, win and share with your friends to"
                                                + " win more coins."))
                        .build();
        int numberOfTopTopics = 1, numberOfRandomTopics = 4;
        ImmutableSet<String> appPackages = ImmutableSet.of(appPackage1, appPackage2);
        when(mPackageManagerUtil.getAppInformation(eq(appPackages))).thenReturn(appInfoMap);

        ImmutableMap<String, List<Integer>> classifications =
                sOnDeviceClassifier.classify(appPackages);
        List<Integer> topTopics1 =
                sOnDeviceClassifier.getTopTopics(
                        classifications, numberOfTopTopics, numberOfRandomTopics);
        List<Integer> topTopics2 =
                sOnDeviceClassifier.getTopTopics(
                        classifications, numberOfTopTopics, numberOfRandomTopics);

        verify(mPackageManagerUtil).getAppInformation(eq(appPackages));
        assertThat(classifications).hasSize(2);
        // Verify random topics are not the same.
        assertThat(topTopics1.subList(numberOfTopTopics, numberOfTopTopics + numberOfRandomTopics))
                .isNotEqualTo(
                        topTopics2.subList(
                                numberOfTopTopics, numberOfTopTopics + numberOfRandomTopics));
    }

    @Test
    public void testBertModelVersion_matchesAssetsModelVersion() {
        assertThat(sOnDeviceClassifier.getBertModelVersion())
                .isEqualTo(
                        Long.parseLong(
                                sClassifierAssetsMetadata
                                        .get("tflite_model")
                                        .get("asset_version")));
    }

    @Test
    public void testBertLabelsVersion_matchesAssetsLabelsVersion() {
        assertThat(sOnDeviceClassifier.getBertLabelsVersion())
                .isEqualTo(
                        Long.parseLong(
                                sClassifierAssetsMetadata
                                        .get("labels_topics")
                                        .get("asset_version")));
    }
}
