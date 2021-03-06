/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.create;

import java.util.Collections;
import java.util.Set;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetadataCreateIndexService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.indices.SystemIndices;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 * Create index action.
 */
public class TransportCreateIndexAction extends TransportMasterNodeAction<CreateIndexRequest, CreateIndexResponse> {

    private final MetadataCreateIndexService createIndexService;
    private final SystemIndices systemIndices;

    @Inject
    public TransportCreateIndexAction(TransportService transportService, ClusterService clusterService,
                                      ThreadPool threadPool, MetadataCreateIndexService createIndexService,
                                      ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                      SystemIndices systemIndices) {
        super(CreateIndexAction.NAME, transportService, clusterService, threadPool, actionFilters, CreateIndexRequest::new,
            indexNameExpressionResolver, CreateIndexResponse::new, ThreadPool.Names.SAME);
        this.createIndexService = createIndexService;
        this.systemIndices = systemIndices;
    }

    @Override
    protected ClusterBlockException checkBlock(CreateIndexRequest request, ClusterState state) {
        return state.blocks().indexBlockedException(ClusterBlockLevel.METADATA_WRITE, request.index());
    }

    @Override
    protected void masterOperation(final CreateIndexRequest request, final ClusterState state,
                                   final ActionListener<CreateIndexResponse> listener) {
        String cause = request.cause();
        if (cause.isEmpty()) {
            cause = "api";
        }

        final String indexName = indexNameExpressionResolver.resolveDateMathExpression(request.index());

        final SystemIndexDescriptor descriptor = systemIndices.findMatchingDescriptor(indexName);
        final CreateIndexClusterStateUpdateRequest updateRequest = descriptor != null && descriptor.isAutomaticallyManaged()
            ? buildSystemIndexUpdateRequest(request, cause, descriptor)
            : buildUpdateRequest(request, cause, indexName);

        createIndexService.createIndex(updateRequest, listener.map(response ->
            new CreateIndexResponse(response.isAcknowledged(), response.isShardsAcknowledged(), indexName)));
    }

    private CreateIndexClusterStateUpdateRequest buildUpdateRequest(CreateIndexRequest request, String cause, String indexName) {
        return new CreateIndexClusterStateUpdateRequest(cause, indexName, request.index()).ackTimeout(request.timeout())
            .masterNodeTimeout(request.masterNodeTimeout())
            .settings(request.settings())
            .mappings(request.mappings())
            .aliases(request.aliases())
            .waitForActiveShards(request.waitForActiveShards());
    }

    private CreateIndexClusterStateUpdateRequest buildSystemIndexUpdateRequest(
        CreateIndexRequest request,
        String cause,
        SystemIndexDescriptor descriptor
    ) {
        Settings settings = descriptor.getSettings();
        if (settings == null) {
            settings = Settings.EMPTY;
        }

        final Set<Alias> aliases;
        if (descriptor.getAliasName() == null) {
            aliases = Collections.emptySet();
        } else {
            aliases = Collections.singleton(new Alias(descriptor.getAliasName()));
        }

        final CreateIndexClusterStateUpdateRequest updateRequest = new CreateIndexClusterStateUpdateRequest(
            cause,
            descriptor.getPrimaryIndex(),
            request.index()
        );

        return updateRequest.ackTimeout(request.timeout())
            .masterNodeTimeout(request.masterNodeTimeout())
            .aliases(aliases)
            .waitForActiveShards(ActiveShardCount.ALL)
            .mappings(Collections.singletonMap(MapperService.SINGLE_MAPPING_NAME, descriptor.getMappings()))
            .settings(settings);
    }
}
