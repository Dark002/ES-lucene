/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.search;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.common.util.CollectionUtils;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.xpack.core.LocalStateCompositeXPackPlugin;
import org.elasticsearch.xpack.core.XPackClientPlugin;
import org.elasticsearch.xpack.core.XPackSettings;
import org.elasticsearch.xpack.core.search.action.ClosePointInTimeAction;
import org.elasticsearch.xpack.core.search.action.ClosePointInTimeRequest;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.search.SearchContextMissingException;
import org.elasticsearch.search.SearchService;
import org.elasticsearch.search.builder.PointInTimeBuilder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.xpack.core.search.action.OpenPointInTimeAction;
import org.elasticsearch.xpack.core.search.action.OpenPointInTimeRequest;
import org.elasticsearch.xpack.core.search.action.OpenPointInTimeResponse;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

public class PointInTimeIT extends ESIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
            .put(super.nodeSettings(nodeOrdinal))
            .put(SearchService.KEEPALIVE_INTERVAL_SETTING.getKey(), TimeValue.timeValueMillis(randomIntBetween(100, 500)))
            .build();
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singleton(LocalStateCompositeXPackPlugin.class);
    }

    @Override
    public Settings transportClientSettings() {
        return Settings.builder().put(super.transportClientSettings())
            .put(XPackSettings.SECURITY_ENABLED.getKey(), false).build();
    }

    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return CollectionUtils.appendToCopy(super.transportClientPlugins(), XPackClientPlugin.class);
    }

    public void testBasic() {
        createIndex("test");
        int numDocs = randomIntBetween(10, 50);
        for (int i = 0; i < numDocs; i++) {
            String id = Integer.toString(i);
            client().prepareIndex("test", "_doc").setId(id).setSource("value", i).get();
        }
        refresh("test");
        String pitId = openPointInTime(new String[] { "test" }, TimeValue.timeValueMinutes(2));
        SearchResponse resp1 = client().prepareSearch().setPreference(null).setPointInTime(new PointInTimeBuilder(pitId)).get();
        assertThat(resp1.pointInTimeId(), equalTo(pitId));
        assertHitCount(resp1, numDocs);
        int deletedDocs = 0;
        for (int i = 0; i < numDocs; i++) {
            if (randomBoolean()) {
                String id = Integer.toString(i);
                client().prepareDelete("test", "_doc", id).get();
                deletedDocs++;
            }
        }
        refresh("test");
        if (randomBoolean()) {
            SearchResponse resp2 = client().prepareSearch("test").setPreference(null).setQuery(new MatchAllQueryBuilder()).get();
            assertNoFailures(resp2);
            assertHitCount(resp2, numDocs - deletedDocs);
        }
        try {
            SearchResponse resp3 = client().prepareSearch()
                .setPreference(null)
                .setQuery(new MatchAllQueryBuilder())
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertNoFailures(resp3);
            assertHitCount(resp3, numDocs);
            assertThat(resp3.pointInTimeId(), equalTo(pitId));
        } finally {
            closePointInTime(pitId);
        }
    }

    public void testMultipleIndices() {
        int numIndices = randomIntBetween(1, 5);
        for (int i = 1; i <= numIndices; i++) {
            createIndex("index-" + i);
        }
        int numDocs = randomIntBetween(10, 50);
        for (int i = 0; i < numDocs; i++) {
            String id = Integer.toString(i);
            String index = "index-" + randomIntBetween(1, numIndices);
            client().prepareIndex(index, "_doc").setId(id).setSource("value", i).get();
        }
        refresh();
        String pitId = openPointInTime(new String[]{"*"}, TimeValue.timeValueMinutes(2));
        try {
            SearchResponse resp = client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs);
            assertNotNull(resp.pointInTimeId());
            assertThat(resp.pointInTimeId(), equalTo(pitId));
            int moreDocs = randomIntBetween(10, 50);
            for (int i = 0; i < moreDocs; i++) {
                String id = "more-" + i;
                String index = "index-" + randomIntBetween(1, numIndices);
                client().prepareIndex(index, "_doc").setId(id).setSource("value", i).get();
            }
            refresh();
            resp = client().prepareSearch().get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs + moreDocs);

            resp = client().prepareSearch().setPreference(null).setPointInTime(new PointInTimeBuilder(pitId)).get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs);
            assertNotNull(resp.pointInTimeId());
            assertThat(resp.pointInTimeId(), equalTo(pitId));
        } finally {
            closePointInTime(pitId);
        }
    }

    public void testRelocation() throws Exception {
        internalCluster().ensureAtLeastNumDataNodes(4);
        createIndex("test", Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, between(0, 1)).build());
        ensureGreen("test");
        int numDocs = randomIntBetween(10, 50);
        for (int i = 0; i < numDocs; i++) {
            client().prepareIndex("test", "_doc").setId(Integer.toString(i)).setSource("value", i).get();
        }
        refresh();
        String pitId = openPointInTime(new String[]{"test"}, TimeValue.timeValueMinutes(2));
        try {
            SearchResponse resp = client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs);
            assertThat(resp.pointInTimeId(), equalTo(pitId));
            final Set<String> dataNodes = StreamSupport.stream(clusterService().state().nodes().getDataNodes().spliterator(), false)
                .map(e -> e.value.getId()).collect(Collectors.toSet());
            final List<String> excludedNodes = randomSubsetOf(2, dataNodes);
            assertAcked(client().admin().indices().prepareUpdateSettings("test")
                .setSettings(Settings.builder().put("index.routing.allocation.exclude._id", String.join(",", excludedNodes)).build()));
            if (randomBoolean()) {
                int moreDocs = randomIntBetween(10, 50);
                for (int i = 0; i < moreDocs; i++) {
                    client().prepareIndex("test", "_doc").setId("more-" + i).setSource("value", i).get();
                }
                refresh();
            }
            resp = client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs);
            assertThat(resp.pointInTimeId(), equalTo(pitId));
            assertBusy(() -> {
                final Set<String> assignedNodes = clusterService().state().routingTable().allShards().stream()
                    .filter(shr -> shr.index().getName().equals("test") && shr.assignedToNode())
                    .map(ShardRouting::currentNodeId)
                    .collect(Collectors.toSet());
                assertThat(assignedNodes, everyItem(not(in(excludedNodes))));
            }, 30, TimeUnit.SECONDS);
            resp = client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs);
            assertThat(resp.pointInTimeId(), equalTo(pitId));
        } finally {
            closePointInTime(pitId);
        }
    }

    public void testPointInTimeNotFound() throws Exception {
        createIndex("index");
        int index1 = randomIntBetween(10, 50);
        for (int i = 0; i < index1; i++) {
            String id = Integer.toString(i);
            client().prepareIndex("index", "_doc").setId(id).setSource("value", i).get();
        }
        refresh();
        String pit = openPointInTime(new String[] { "index" }, TimeValue.timeValueSeconds(5));
        SearchResponse resp1 = client().prepareSearch()
            .setPreference(null)
            .setPointInTime(new PointInTimeBuilder(pit))
            .get();
        assertNoFailures(resp1);
        assertHitCount(resp1, index1);
        if (rarely()) {
            assertBusy(() -> {
                final CommonStats stats = client().admin().indices().prepareStats().setSearch(true).get().getTotal();
                assertThat(stats.search.getOpenContexts(), equalTo(0L));
            }, 60, TimeUnit.SECONDS);
        } else {
            closePointInTime(resp1.pointInTimeId());
        }
        SearchPhaseExecutionException e = expectThrows(
            SearchPhaseExecutionException.class,
            () -> client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pit))
                .get()
        );
        for (ShardSearchFailure failure : e.shardFailures()) {
            assertThat(ExceptionsHelper.unwrapCause(failure.getCause()), instanceOf(SearchContextMissingException.class));
        }
    }

    public void testIndexNotFound() {
        createIndex("index-1");
        createIndex("index-2");

        int index1 = randomIntBetween(10, 50);
        for (int i = 0; i < index1; i++) {
            String id = Integer.toString(i);
            client().prepareIndex("index-1", "_doc").setId(id).setSource("value", i).get();
        }

        int index2 = randomIntBetween(10, 50);
        for (int i = 0; i < index2; i++) {
            String id = Integer.toString(i);
            client().prepareIndex("index-2", "_doc").setId(id).setSource("value", i).get();
        }
        refresh();
        String pit = openPointInTime(new String[] { "index-*" }, TimeValue.timeValueMinutes(2));
        SearchResponse resp1 = client().prepareSearch().setPreference(null).setPointInTime(new PointInTimeBuilder(pit)).get();
        assertNoFailures(resp1);
        assertHitCount(resp1, index1 + index2);
        client().admin().indices().prepareDelete("index-1").get();
        if (randomBoolean()) {
            SearchResponse resp2 = client().prepareSearch("index-*").get();
            assertNoFailures(resp2);
            assertHitCount(resp2, index2);

        }
        expectThrows(
            IndexNotFoundException.class,
            () -> client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pit))
                .get()
        );
        closePointInTime(resp1.pointInTimeId());
    }

    public void testCanMatch() throws Exception {
        final Settings.Builder settings = Settings.builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, randomIntBetween(5, 10))
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            .put(IndexSettings.INDEX_SEARCH_IDLE_AFTER.getKey(), TimeValue.timeValueMillis(randomIntBetween(50, 100)));
        assertAcked(
            prepareCreate("test").setSettings(settings).addMapping("_doc", "created_date", "type=date,format=yyyy-MM-dd"));
        ensureGreen("test");
        String pitId = openPointInTime(new String[] { "test*" }, TimeValue.timeValueMinutes(2));
        try {
            for (String node : internalCluster().nodesInclude("test")) {
                for (IndexService indexService : internalCluster().getInstance(IndicesService.class, node)) {
                    for (IndexShard indexShard : indexService) {
                        assertBusy(() -> assertTrue(indexShard.isSearchIdle()));
                    }
                }
            }
            client().prepareIndex("test", "_doc").setId("1").setSource("created_date", "2020-01-01").get();
            SearchResponse resp = client().prepareSearch()
                .setQuery(new RangeQueryBuilder("created_date").gte("2020-01-02").lte("2020-01-03"))
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setPreference(null)
                .setPreFilterShardSize(randomIntBetween(2, 3))
                .setMaxConcurrentShardRequests(randomIntBetween(1, 2))
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertThat(resp.getHits().getHits(), arrayWithSize(0));
            for (String node : internalCluster().nodesInclude("test")) {
                for (IndexService indexService : internalCluster().getInstance(IndicesService.class, node)) {
                    for (IndexShard indexShard : indexService) {
                        // all shards are still search-idle as we did not acquire new searchers
                        assertTrue(indexShard.isSearchIdle());
                    }
                }
            }
        } finally {
            closePointInTime(pitId);
        }
    }

    public void testPartialResults() throws Exception {
        internalCluster().ensureAtLeastNumDataNodes(2);
        final List<String> dataNodes =
            StreamSupport.stream(internalCluster().clusterService().state().nodes().getDataNodes().spliterator(), false)
            .map(e -> e.value.getName())
            .collect(Collectors.toList());
        final String assignedNodeForIndex1 = randomFrom(dataNodes);

        createIndex("test-1", Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            .put("index.routing.allocation.include._name", assignedNodeForIndex1)
            .build());
        createIndex("test-2", Settings.builder().put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            .put("index.routing.allocation.exclude._name", assignedNodeForIndex1)
            .build());

        int numDocs1 = randomIntBetween(10, 50);
        for (int i = 0; i < numDocs1; i++) {
            client().prepareIndex("test-1", "_doc").setId(Integer.toString(i)).setSource("value", i).get();
        }
        int numDocs2 = randomIntBetween(10, 50);
        for (int i = 0; i < numDocs2; i++) {
            client().prepareIndex("test-2", "_doc").setId(Integer.toString(i)).setSource("value", i).get();
        }
        refresh();
        String pitId = openPointInTime(new String[]{"test-*"}, TimeValue.timeValueMinutes(2));
        try {
            SearchResponse resp = client().prepareSearch()
                .setPreference(null)
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertNoFailures(resp);
            assertHitCount(resp, numDocs1 + numDocs2);
            assertThat(resp.pointInTimeId(), equalTo(pitId));

            internalCluster().restartNode(assignedNodeForIndex1);
            resp = client().prepareSearch()
                .setPreference(null)
                .setAllowPartialSearchResults(true)
                .setPointInTime(new PointInTimeBuilder(pitId))
                .get();
            assertFailures(resp);
            assertThat(resp.pointInTimeId(), equalTo(pitId));
            assertHitCount(resp, numDocs2);
        } finally {
            closePointInTime(pitId);
        }
    }

    private String openPointInTime(String[] indices, TimeValue keepAlive) {
        OpenPointInTimeRequest request = new OpenPointInTimeRequest(
            indices,
            OpenPointInTimeRequest.DEFAULT_INDICES_OPTIONS,
            keepAlive,
            null,
            null
        );
        final OpenPointInTimeResponse response = client().execute(OpenPointInTimeAction.INSTANCE, request).actionGet();
        return response.getSearchContextId();
    }

    private void closePointInTime(String readerId) {
        client().execute(ClosePointInTimeAction.INSTANCE, new ClosePointInTimeRequest(readerId)).actionGet();
    }
}
