/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.searchablesnapshots.rest;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestToXContentListener;
import org.elasticsearch.xpack.searchablesnapshots.action.SearchableSnapshotsStatsAction;
import org.elasticsearch.xpack.searchablesnapshots.action.SearchableSnapshotsStatsRequest;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class RestSearchableSnapshotsStatsAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return unmodifiableList(
            asList(
                new Route(RestRequest.Method.GET, "/_searchable_snapshots/stats"),
                new Route(RestRequest.Method.GET, "/{index}/_searchable_snapshots/stats")
            )
        );
    }

    @Override
    public String getName() {
        return "searchable_snapshots_stats_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest restRequest, final NodeClient client) {
        String[] indices = Strings.splitStringByCommaToArray(restRequest.param("index"));
        return channel -> client.execute(
            SearchableSnapshotsStatsAction.INSTANCE,
            new SearchableSnapshotsStatsRequest(indices),
            new RestToXContentListener<>(channel)
        );
    }
}
