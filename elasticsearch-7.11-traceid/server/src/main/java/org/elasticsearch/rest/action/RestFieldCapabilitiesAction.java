/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.rest.action;

import org.elasticsearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class RestFieldCapabilitiesAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "/_field_caps"),
            new Route(POST, "/_field_caps"),
            new Route(GET, "/{index}/_field_caps"),
            new Route(POST, "/{index}/_field_caps")));
    }

    @Override
    public String getName() {
        return "field_capabilities_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request,
                                              final NodeClient client) throws IOException {
        String[] indices = Strings.splitStringByCommaToArray(request.param("index"));
        FieldCapabilitiesRequest fieldRequest = new FieldCapabilitiesRequest()
            .fields(Strings.splitStringByCommaToArray(request.param("fields")))
            .indices(indices);

        fieldRequest.indicesOptions(
            IndicesOptions.fromRequest(request, fieldRequest.indicesOptions()));
        fieldRequest.includeUnmapped(request.paramAsBoolean("include_unmapped", false));
        request.withContentOrSourceParamParserOrNull(parser -> {
            if (parser != null) {
                fieldRequest.indexFilter(RestActions.getQueryContent("index_filter", parser));
            }
        });
        return channel -> client.fieldCaps(fieldRequest, new RestToXContentListener<>(channel));
    }
}
