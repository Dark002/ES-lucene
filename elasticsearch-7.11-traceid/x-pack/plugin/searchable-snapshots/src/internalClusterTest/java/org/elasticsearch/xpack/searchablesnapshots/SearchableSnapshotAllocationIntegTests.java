/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.searchablesnapshots;

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.routing.allocation.decider.EnableAllocationDecider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xpack.searchablesnapshots.cache.CacheService;

import java.util.Collections;
import java.util.Set;

import static org.elasticsearch.index.IndexSettings.INDEX_SOFT_DELETES_SETTING;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.TEST, numDataNodes = 0)
public class SearchableSnapshotAllocationIntegTests extends BaseSearchableSnapshotsIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
            .put(super.nodeSettings(nodeOrdinal))
            // ensure the cache is definitely used
            .put(CacheService.SNAPSHOT_CACHE_SIZE_SETTING.getKey(), new ByteSizeValue(1L, ByteSizeUnit.GB))
            .build();
    }

    public void testAllocatesToBestAvailableNodeOnRestart() throws Exception {
        internalCluster().startMasterOnlyNode();
        final String firstDataNode = internalCluster().startDataOnlyNode();
        final String index = "test-idx";
        createIndexWithContent(index, indexSettingsNoReplicas(1).put(INDEX_SOFT_DELETES_SETTING.getKey(), true).build());
        final String repoName = "test-repo";
        createRepository(repoName, "fs");
        final String snapshotName = "test-snapshot";
        createSnapshot(repoName, snapshotName, Collections.singletonList(index));
        assertAcked(client().admin().indices().prepareDelete(index));
        final String restoredIndex = mountSnapshot(repoName, snapshotName, index, Settings.EMPTY);
        ensureGreen(restoredIndex);
        internalCluster().startDataOnlyNodes(randomIntBetween(1, 4));

        setAllocation(EnableAllocationDecider.Allocation.NONE);

        final CacheService cacheService = internalCluster().getInstance(CacheService.class, firstDataNode);
        cacheService.synchronizeCache();
        internalCluster().restartNode(firstDataNode);
        ensureStableCluster(internalCluster().numDataAndMasterNodes());

        setAllocation(EnableAllocationDecider.Allocation.ALL);
        ensureGreen(restoredIndex);

        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        assertEquals(
            state.nodes().resolveNode(firstDataNode).getId(),
            state.routingTable().index(restoredIndex).shard(0).primaryShard().currentNodeId()
        );
    }

    public void testAllocatesReplicaToBestAvailableNodeOnRestart() throws Exception {
        internalCluster().startMasterOnlyNode();
        final String firstDataNode = internalCluster().startDataOnlyNode();
        final String secondDataNode = internalCluster().startDataOnlyNode();
        final String index = "test-idx";
        createIndexWithContent(index, indexSettingsNoReplicas(1).put(INDEX_SOFT_DELETES_SETTING.getKey(), true).build());
        final String repoName = "test-repo";
        createRepository(repoName, "fs");
        final String snapshotName = "test-snapshot";
        createSnapshot(repoName, snapshotName, Collections.singletonList(index));
        assertAcked(client().admin().indices().prepareDelete(index));
        final String restoredIndex = mountSnapshot(
            repoName,
            snapshotName,
            index,
            Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1).build()
        );
        ensureGreen(restoredIndex);
        internalCluster().startDataOnlyNodes(randomIntBetween(1, 4));

        setAllocation(EnableAllocationDecider.Allocation.NONE);

        internalCluster().getInstance(CacheService.class, firstDataNode).synchronizeCache();
        internalCluster().getInstance(CacheService.class, secondDataNode).synchronizeCache();
        internalCluster().restartNode(firstDataNode);
        internalCluster().restartNode(secondDataNode);
        ensureStableCluster(internalCluster().numDataAndMasterNodes());

        setAllocation(EnableAllocationDecider.Allocation.ALL);
        ensureGreen(restoredIndex);

        final ClusterState state = client().admin().cluster().prepareState().get().getState();
        final Set<String> nodesWithCache = Sets.newHashSet(
            state.nodes().resolveNode(firstDataNode).getId(),
            state.nodes().resolveNode(secondDataNode).getId()
        );
        assertThat(state.routingTable().index(restoredIndex).shard(0).primaryShard().currentNodeId(), is(in(nodesWithCache)));
        assertThat(state.routingTable().index(restoredIndex).shard(0).replicaShards().get(0).currentNodeId(), is(in(nodesWithCache)));
    }

    private void setAllocation(EnableAllocationDecider.Allocation allocation) {
        logger.info("--> setting allocation to [{}]", allocation);
        assertAcked(
            client().admin()
                .cluster()
                .prepareUpdateSettings()
                .setTransientSettings(
                    Settings.builder()
                        .put(EnableAllocationDecider.CLUSTER_ROUTING_ALLOCATION_ENABLE_SETTING.getKey(), allocation.name())
                        .build()
                )
                .get()
        );
    }
}
