/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.Version;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.OriginSettingClient;
import org.elasticsearch.cluster.routing.OperationRouting;
import org.elasticsearch.cluster.routing.allocation.decider.AwarenessAllocationDecider;
import org.elasticsearch.cluster.service.ClusterApplierService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.cluster.service.MasterService;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.xpack.core.ClientHelper;
import org.elasticsearch.xpack.core.ml.action.PutJobAction;
import org.elasticsearch.xpack.core.ml.action.UpgradeJobModelSnapshotAction;
import org.elasticsearch.xpack.core.ml.inference.MlInferenceNamedXContentProvider;
import org.elasticsearch.xpack.core.ml.job.config.AnalysisConfig;
import org.elasticsearch.xpack.core.ml.job.config.DataDescription;
import org.elasticsearch.xpack.core.ml.job.config.DetectionRule;
import org.elasticsearch.xpack.core.ml.job.config.Detector;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.ml.job.persistence.AnomalyDetectorsIndex;
import org.elasticsearch.xpack.core.ml.job.process.autodetect.state.ModelSnapshot;
import org.elasticsearch.xpack.ml.MlSingleNodeTestCase;
import org.elasticsearch.xpack.ml.inference.ingest.InferenceProcessor;
import org.elasticsearch.xpack.ml.inference.modelsize.MlModelSizeNamedXContentProvider;
import org.elasticsearch.xpack.ml.job.persistence.JobResultsPersister;
import org.elasticsearch.xpack.ml.notifications.AnomalyDetectionAuditor;
import org.elasticsearch.xpack.ml.utils.persistence.ResultsPersisterService;
import org.junit.Before;

public class JobModelSnapshotUpgraderIT extends MlSingleNodeTestCase {

    private JobResultsPersister jobResultsPersister;

    @Before
    public void createComponents() throws Exception {
        ThreadPool tp = mockThreadPool();
        ClusterSettings clusterSettings = new ClusterSettings(Settings.EMPTY,
            new HashSet<>(Arrays.asList(InferenceProcessor.MAX_INFERENCE_PROCESSORS,
                MasterService.MASTER_SERVICE_SLOW_TASK_LOGGING_THRESHOLD_SETTING,
                AwarenessAllocationDecider.CLUSTER_ROUTING_ALLOCATION_AWARENESS_ATTRIBUTE_SETTING,
                OperationRouting.USE_ADAPTIVE_REPLICA_SELECTION_SETTING,
                ResultsPersisterService.PERSIST_RESULTS_MAX_RETRIES,
                ClusterService.USER_DEFINED_METADATA,
                ClusterApplierService.CLUSTER_SERVICE_SLOW_TASK_LOGGING_THRESHOLD_SETTING)));
        ClusterService clusterService = new ClusterService(Settings.EMPTY, clusterSettings, tp);

        OriginSettingClient originSettingClient = new OriginSettingClient(client(), ClientHelper.ML_ORIGIN);
        ResultsPersisterService resultsPersisterService = new ResultsPersisterService(
            tp,
            originSettingClient,
            clusterService,
            Settings.EMPTY
        );
        AnomalyDetectionAuditor auditor = new AnomalyDetectionAuditor(client(), clusterService);
        jobResultsPersister = new JobResultsPersister(originSettingClient, resultsPersisterService, auditor);
        waitForMlTemplates();
    }

    public void testUpgradeAlreadyUpgradedSnapshot() {
        String jobId = "job-with-current-snapshot";

        createJob(jobId);
        ModelSnapshot snapshot = new ModelSnapshot.Builder(jobId).setMinVersion(Version.CURRENT).setSnapshotId("snap_1").build();
        indexModelSnapshot(snapshot);
        client().admin().indices().prepareRefresh(AnomalyDetectorsIndex.jobResultsAliasedName(jobId)).get();

        ElasticsearchStatusException ex = expectThrows(
            ElasticsearchStatusException.class,
            () -> client().execute(
                UpgradeJobModelSnapshotAction.INSTANCE,
                new UpgradeJobModelSnapshotAction.Request(jobId, "snap_1", TimeValue.timeValueMinutes(10), true)
            ).actionGet());
        assertThat(ex.status(), equalTo(RestStatus.CONFLICT));
        assertThat(
            ex.getMessage(),
            containsString(
                "Cannot upgrade job [job-with-current-snapshot] snapshot [snap_1] as it is already compatible with current version"
            )
        );
    }

    private void indexModelSnapshot(ModelSnapshot snapshot) {
        jobResultsPersister.persistModelSnapshot(snapshot, WriteRequest.RefreshPolicy.IMMEDIATE, () -> true);
    }

    private Job.Builder createJob(String jobId) {
        Job.Builder builder = new Job.Builder(jobId);
        AnalysisConfig.Builder ac = createAnalysisConfig("by_field");
        DataDescription.Builder dc = new DataDescription.Builder();
        builder.setAnalysisConfig(ac);
        builder.setDataDescription(dc);

        PutJobAction.Request request = new PutJobAction.Request(builder);
        client().execute(PutJobAction.INSTANCE, request).actionGet();
        return builder;
    }

    private AnalysisConfig.Builder createAnalysisConfig(String byFieldName) {
        Detector.Builder detector = new Detector.Builder("mean", "field");
        detector.setByFieldName(byFieldName);
        List<DetectionRule> rules = new ArrayList<>();

        detector.setRules(rules);

        return new AnalysisConfig.Builder(Collections.singletonList(detector.build()));
    }

    @Override
    public NamedXContentRegistry xContentRegistry() {
        List<NamedXContentRegistry.Entry> namedXContent = new ArrayList<>();
        namedXContent.addAll(new MlInferenceNamedXContentProvider().getNamedXContentParsers());
        namedXContent.addAll(new MlModelSizeNamedXContentProvider().getNamedXContentParsers());
        return new NamedXContentRegistry(namedXContent);
    }

}
