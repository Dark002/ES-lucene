/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.transform;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.transform.transforms.TransformIndexerStats;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.xpack.transform.TransformFeatureSet.PROVIDED_STATS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransformFeatureSetTests extends ESTestCase {
    private XPackLicenseState licenseState;

    @Before
    public void init() {
        licenseState = mock(XPackLicenseState.class);
    }

    public void testAvailable() {
        TransformFeatureSet featureSet = new TransformFeatureSet(
            mock(ClusterService.class),
            mock(Client.class),
            licenseState
        );
        boolean available = randomBoolean();
        when(licenseState.isAllowed(XPackLicenseState.Feature.TRANSFORM)).thenReturn(available);
        assertThat(featureSet.available(), is(available));
    }

    public void testEnabledSetting() {
        TransformFeatureSet featureSet = new TransformFeatureSet(
            mock(ClusterService.class),
            mock(Client.class),
            licenseState
        );
        assertThat(featureSet.enabled(), is(true));
    }

    public void testParseSearchAggs() {
        Aggregations emptyAggs = new Aggregations(Collections.emptyList());
        SearchResponse withEmptyAggs = mock(SearchResponse.class);
        when(withEmptyAggs.getAggregations()).thenReturn(emptyAggs);

        assertThat(TransformFeatureSet.parseSearchAggs(withEmptyAggs), equalTo(new TransformIndexerStats()));

        TransformIndexerStats expectedStats = new TransformIndexerStats(
            1,  // numPages
            2,  // numInputDocuments
            3,  // numOutputDocuments
            4,  // numInvocations
            5,  // indexTime
            6,  // searchTime
            7,  // processingTime
            8,  // indexTotal
            9,  // searchTotal
            10, // processingTotal
            11, // indexFailures
            12, // searchFailures
            13.0,  // exponential_avg_checkpoint_duration_ms
            14.0,  // exponential_avg_documents_indexed
            15.0   // exponential_avg_documents_processed
        );

        int currentStat = 1;
        List<Aggregation> aggs = new ArrayList<>(PROVIDED_STATS.length);
        for (String statName : PROVIDED_STATS) {
            aggs.add(buildAgg(statName, currentStat++));
        }
        Aggregations aggregations = new Aggregations(aggs);
        SearchResponse withAggs = mock(SearchResponse.class);
        when(withAggs.getAggregations()).thenReturn(aggregations);

        assertThat(TransformFeatureSet.parseSearchAggs(withAggs), equalTo(expectedStats));
    }

    private static Aggregation buildAgg(String name, double value) {
        NumericMetricsAggregation.SingleValue agg = mock(NumericMetricsAggregation.SingleValue.class);
        when(agg.getName()).thenReturn(name);
        when(agg.value()).thenReturn(value);
        return agg;
    }
}
