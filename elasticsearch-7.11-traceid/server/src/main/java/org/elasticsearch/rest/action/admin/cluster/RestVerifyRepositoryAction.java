/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.rest.action.admin.cluster;

import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;

import java.io.IOException;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.elasticsearch.client.Requests.verifyRepositoryRequest;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestVerifyRepositoryAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return singletonList(new Route(POST, "/_snapshot/{repository}/_verify"));
    }

    @Override
    public String getName() {
        return "verify_repository_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        VerifyRepositoryRequest verifyRepositoryRequest = verifyRepositoryRequest(request.param("repository"));
        verifyRepositoryRequest.masterNodeTimeout(request.paramAsTime("master_timeout", verifyRepositoryRequest.masterNodeTimeout()));
        verifyRepositoryRequest.timeout(request.paramAsTime("timeout", verifyRepositoryRequest.timeout()));
        return channel -> client.admin().cluster().verifyRepository(verifyRepositoryRequest, new RestToXContentListener<>(channel));
    }
}
