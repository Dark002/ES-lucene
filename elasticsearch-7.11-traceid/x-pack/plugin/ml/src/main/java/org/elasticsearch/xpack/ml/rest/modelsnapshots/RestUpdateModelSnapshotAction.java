/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.ml.rest.modelsnapshots;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;
import org.elasticsearch.xpack.core.ml.action.UpdateModelSnapshotAction;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.ml.job.process.autodetect.state.ModelSnapshotField;
import org.elasticsearch.xpack.ml.MachineLearning;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestUpdateModelSnapshotAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return Collections.emptyList();
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        // TODO: remove deprecated endpoint in 8.0.0
        return Collections.singletonList(
            new ReplacedRoute(POST, MachineLearning.BASE_PATH + "anomaly_detectors/{"
                + Job.ID.getPreferredName() + "}/model_snapshots/{" + ModelSnapshotField.SNAPSHOT_ID +"}/_update",
                POST, MachineLearning.PRE_V7_BASE_PATH + "anomaly_detectors/{"
                + Job.ID.getPreferredName() + "}/model_snapshots/{" + ModelSnapshotField.SNAPSHOT_ID +"}/_update")
        );
    }

    @Override
    public String getName() {
        return "ml_update_model_snapshot_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest restRequest, NodeClient client) throws IOException {
        XContentParser parser = restRequest.contentParser();
        UpdateModelSnapshotAction.Request updateModelSnapshot = UpdateModelSnapshotAction.Request.parseRequest(
                restRequest.param(Job.ID.getPreferredName()),
                restRequest.param(ModelSnapshotField.SNAPSHOT_ID.getPreferredName()),
                parser);

        return channel ->
                client.execute(UpdateModelSnapshotAction.INSTANCE, updateModelSnapshot, new RestStatusToXContentListener<>(channel));
    }
}
