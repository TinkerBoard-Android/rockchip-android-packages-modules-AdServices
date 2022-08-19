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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mobiledatadownload.ClientConfigProto.ClientFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Model Manager Test {@link ModelManager}. */
public class ModelManagerTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private ImmutableList<Integer> mProductionLabels;
    private ImmutableMap<String, ImmutableMap<String, String>> mProductionClassifierAssetsMetadata;
    private ImmutableMap<String, ImmutableMap<String, String>> mTestClassifierAssetsMetadata;
    private ModelManager mTestModelManager;
    private ModelManager mProductionModelManager;
    private static final String TEST_LABELS_FILE_PATH = "classifier/labels_test_topics.txt";
    private static final String TEST_APPS_FILE_PATH = "classifier/precomputed_test_app_list.csv";
    private static final String TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_test_assets_metadata.json";
    private static final String PRODUCTION_LABELS_FILE_PATH = "classifier/labels_topics.txt";
    private static final String PRODUCTION_APPS_FILE_PATH = "classifier/precomputed_app_list.csv";
    private static final String PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH =
            "classifier/classifier_assets_metadata.json";
    private static final String MODEL_FILE_PATH = "classifier/model.tflite";

    @Mock SynchronousFileStorage mMockFileStorage;
    @Mock Map<String, ClientFile> mMockDownloadedFiles;
    private MockitoSession mMockitoSession = null;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .strictness(Strictness.WARN)
                        .startMocking();
    }

    @After
    public void tearDown() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testGetInstance() {
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        ModelManager firstInstance = ModelManager.getInstance(sContext);
        ModelManager secondInstance = ModelManager.getInstance(sContext);

        assertThat(firstInstance).isNotNull();
        assertThat(secondInstance).isNotNull();
        assertThat(firstInstance).isEqualTo(secondInstance);
    }

    @Test
    public void testRetrieveModel_bundled() throws IOException {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        ByteBuffer byteBuffer = mProductionModelManager.retrieveModel();
        // Check byteBuffer capacity greater than 0 when retrieveModel() finds bundled TFLite model
        // and loads file as a ByteBuffer.
        assertThat(byteBuffer.capacity()).isGreaterThan(0);
    }

    @Test
    public void testRetrieveLabels_bundled_successfulRead() {
        // Test the labels list in production assets
        // Check size of list.
        // The labels_topics.txt contains 446 topics.
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionLabels = mProductionModelManager.retrieveLabels();
        assertThat(mProductionLabels.size()).isEqualTo(446);

        // Check some labels.
        assertThat(mProductionLabels).containsAtLeast(10010, 10200, 10270, 10432);
    }

    @Test
    public void testRetrieveLabels_downloaded_successfulRead() throws IOException {
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(sContext.getAssets().open(PRODUCTION_LABELS_FILE_PATH));

        // Test the labels list in MDD downloaded label.
        // Check size of list.
        // The labels_topics.txt contains 446 topics.
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);
        mProductionLabels = mProductionModelManager.retrieveLabels();
        assertThat(mProductionLabels.size()).isEqualTo(446);

        // Check some labels.
        assertThat(mProductionLabels).containsAtLeast(10010, 10200, 10270, 10432);
    }

    @Test
    public void testRetrieveLabels_bundled_emptyListReturnedOnException() {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionLabels = mProductionModelManager.retrieveLabels();
        ImmutableList<Integer> labels = mProductionModelManager.retrieveLabels();
        // Check empty list returned.
        assertThat(labels).isEmpty();
    }

    @Test
    public void testRetrieveLabels_downloaded_emptyListReturnedOnException() throws IOException {
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any())).thenReturn(FileInputStream.nullInputStream());
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        "WrongFilePath",
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionLabels = mProductionModelManager.retrieveLabels();
        ImmutableList<Integer> labels = mProductionModelManager.retrieveLabels();
        // Check empty list returned.
        assertThat(labels).isEmpty();
    }

    @Test
    public void testLoadedAppTopics_bundled() {
        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        Map<String, List<Integer>> appTopic = mTestModelManager.retrieveAppClassificationTopics();
        // Check size of map
        // The app topics file contains 11 sample apps + 2 test valid topics' apps
        // + 1 end2end test app.
        assertThat(appTopic.size()).isEqualTo(14);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp" in
        // assets/precomputed_test_app_list.csv are 10222, 10223, 10116, 10243, 10254
        String sampleAppPrefix = "com.example.adservices.samples.topics.sampleapp";
        List<Integer> sampleAppTopics = Arrays.asList(10222, 10223, 10116, 10243, 10254);
        assertThat(appTopic.get(sampleAppPrefix)).isEqualTo(sampleAppTopics);

        // The topicIds of "com.example.adservices.samples.topics.sampleapp4" in
        // assets/precomputed_test_app_list.csv are 10253, 10146, 10227, 10390, 10413
        List<Integer> sampleApp4Topics = Arrays.asList(10253, 10146, 10227, 10390, 10413);
        assertThat(appTopic.get(sampleAppPrefix + "4")).isEqualTo(sampleApp4Topics);

        // Check if all sample apps have 5 unique topics
        for (int appIndex = 1; appIndex <= 10; appIndex++) {
            assertThat(new HashSet<>(appTopic.get(sampleAppPrefix + appIndex)).size()).isEqualTo(5);
        }

        // Verify that the topics from the file are valid:
        // the valid topic is one of the topic in the labels file.
        // The invalid topics will not be loaded in the app topics map.
        String validTestAppPrefix = "com.example.adservices.valid.topics.testapp";

        // The valid topicIds of "com.example.adservices.valid.topics.testapp1" in
        // assets/precomputed_test_app_list.csv are 10147, 10253, 10254
        List<Integer> validTestApp1Topics = Arrays.asList(10147, 10253, 10254);
        assertThat(appTopic.get(validTestAppPrefix + "1")).isEqualTo(validTestApp1Topics);

        // The valid topicIds of "com.example.adservices.valid.topics.testapp2" in
        // assets/precomputed_test_app_list.csv are 143, 15
        List<Integer> validTestApp2Topics = Arrays.asList(10253, 10254);
        assertThat(appTopic.get(validTestAppPrefix + "2")).isEqualTo(validTestApp2Topics);
    }

    @Test
    public void testGetProductionClassifierAssetsMetadata_bundled_correctFormat() {
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionClassifierAssetsMetadata =
                mProductionModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(mProductionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mProductionClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly("taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "2".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("2");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "2"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("2");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-07-29"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-07-29");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "e5d118889e7e57f1e5ed166354f3dfa81963ee7e917f98c8a687d541b9bbe489"
        assertThat(mProductionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("e5d118889e7e57f1e5ed166354f3dfa81963ee7e917f98c8a687d541b9bbe489");
    }

    @Test
    public void testGetProductionClassifierAssetsMetadata_downloaded_correctFormat()
            throws IOException {
        // Mock a MDD FileGroup and FileStorage
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        sContext.getAssets().open(PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH));
        mProductionModelManager =
                new ModelManager(
                        sContext,
                        PRODUCTION_LABELS_FILE_PATH,
                        PRODUCTION_APPS_FILE_PATH,
                        PRODUCTION_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mProductionClassifierAssetsMetadata =
                mProductionModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 4 assets and 1 property in classifier_assets_metadata.json.
        assertThat(mProductionClassifierAssetsMetadata).hasSize(5);

        // The property of metadata in production metadata should contain 4 attributions:
        // "taxonomy_type", "taxonomy_version", "updated_date".
        // The key name of property is "version_info"
        assertThat(mProductionClassifierAssetsMetadata.get("version_info")).hasSize(3);
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").keySet())
                .containsExactly("taxonomy_type", "taxonomy_version", "updated_date");

        // The property "version_info" should have attribution "taxonomy_version"
        // and its value should be "2".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_version"))
                .isEqualTo("2");

        // The property "version_info" should have attribution "taxonomy_type"
        // and its value should be "chrome_and_mobile_taxonomy".
        assertThat(mProductionClassifierAssetsMetadata.get("version_info").get("taxonomy_type"))
                .isEqualTo("chrome_and_mobile_taxonomy");

        // The metadata of 1 asset in production metadata should contain 4 attributions:
        // "asset_version", "path", "checksum", "updated_date".
        // Check if "labels_topics" asset has the correct format.
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics")).hasSize(4);
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").keySet())
                .containsExactly("asset_version", "path", "checksum", "updated_date");

        // The asset "labels_topics" should have attribution "asset_version" and its value should be
        // "2"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("asset_version"))
                .isEqualTo("2");

        // The asset "labels_topics" should have attribution "path" and its value should be
        // "assets/classifier/labels_topics.txt"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("path"))
                .isEqualTo("assets/classifier/labels_topics.txt");

        // The asset "labels_topics" should have attribution "updated_date" and its value should be
        // "2022-07-29"
        assertThat(mProductionClassifierAssetsMetadata.get("labels_topics").get("updated_date"))
                .isEqualTo("2022-07-29");

        // There should contain 5 metadata attributions in asset "topic_id_to_name"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name")).hasSize(4);

        // The asset "topic_id_to_name" should have attribution "path" and its value should be
        // "assets/classifier/topic_id_to_name.csv"
        assertThat(mProductionClassifierAssetsMetadata.get("topic_id_to_name").get("path"))
                .isEqualTo("assets/classifier/topic_id_to_name.csv");

        // The asset "precomputed_app_list" should have attribution "checksum" and
        // its value should be "e5d118889e7e57f1e5ed166354f3dfa81963ee7e917f98c8a687d541b9bbe489"
        assertThat(mProductionClassifierAssetsMetadata.get("precomputed_app_list").get("checksum"))
                .isEqualTo("e5d118889e7e57f1e5ed166354f3dfa81963ee7e917f98c8a687d541b9bbe489");
    }

    @Test
    public void testGetTestClassifierAssetsMetadata_wrongFormat() {
        mTestModelManager =
                new ModelManager(
                        sContext,
                        TEST_LABELS_FILE_PATH,
                        TEST_APPS_FILE_PATH,
                        TEST_CLASSIFIER_ASSETS_METADATA_FILE_PATH,
                        MODEL_FILE_PATH,
                        mMockFileStorage,
                        mMockDownloadedFiles);

        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        // There should contain 1 metadata attributions in asset "test_asset1",
        // because it doesn't have "checksum" and "updated_date"
        mTestClassifierAssetsMetadata = mTestModelManager.retrieveClassifierAssetsMetadata();
        assertThat(mTestClassifierAssetsMetadata.get("test_asset1")).hasSize(1);

        // The asset "test_asset1" should have attribution "path" and its value should be
        // "assets/classifier/test1"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset1").get("path"))
                .isEqualTo("assets/classifier/test1");

        // There should contain 4 metadata attributions in asset "test_asset2",
        // because "redundant_field1" and "redundant_field2" are not correct attributions.
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2")).hasSize(4);

        // The asset "test_asset2" should have attribution "path" and its value should be
        // "assets/classifier/test2"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2").get("path"))
                .isEqualTo("assets/classifier/test2");

        // The asset "test_asset2" shouldn't have redundant attribution "redundant_field1"
        assertThat(mTestClassifierAssetsMetadata.get("test_asset2"))
                .doesNotContainKey("redundant_field1");
    }
}
