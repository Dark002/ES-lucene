/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.search.aggregations;


import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator.PipelineTree;
import org.elasticsearch.search.aggregations.support.AggregationContext;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * A factory that knows how to create an {@link Aggregator} of a specific type.
 */
public abstract class AggregationBuilder
        implements NamedWriteable, ToXContentFragment, BaseAggregationBuilder, Rewriteable<AggregationBuilder> {

    protected final String name;
    protected AggregatorFactories.Builder factoriesBuilder = AggregatorFactories.builder();

    /**
     * Constructs a new aggregation builder.
     *
     * @param name  The aggregation name
     */
    protected AggregationBuilder(String name) {
        if (name == null) {
            throw new IllegalArgumentException("[name] must not be null: [" + name + "]");
        }
        this.name = name;
    }

    protected AggregationBuilder(AggregationBuilder clone, AggregatorFactories.Builder factoriesBuilder) {
        this.name = clone.name;
        this.factoriesBuilder = factoriesBuilder;
    }

    /** Return this aggregation's name. */
    public String getName() {
        return name;
    }

    /** Internal: build an {@link AggregatorFactory} based on the configuration of this builder. */
    protected abstract AggregatorFactory build(AggregationContext context, AggregatorFactory parent) throws IOException;

    /** Associate metadata with this {@link AggregationBuilder}. */
    @Override
    public abstract AggregationBuilder setMetadata(Map<String, Object> metadata);

    /** Return any associated metadata with this {@link AggregationBuilder}. */
    public abstract Map<String, Object> getMetadata();

    /** Add a sub aggregation to this builder. */
    public abstract AggregationBuilder subAggregation(AggregationBuilder aggregation);

    /** Add a sub aggregation to this builder. */
    public abstract AggregationBuilder subAggregation(PipelineAggregationBuilder aggregation);

    /** Return the configured set of subaggregations **/
    public Collection<AggregationBuilder> getSubAggregations() {
        return factoriesBuilder.getAggregatorFactories();
    }

    /** Return the configured set of pipeline aggregations **/
    public Collection<PipelineAggregationBuilder> getPipelineAggregations() {
        return factoriesBuilder.getPipelineAggregatorFactories();
    }

    /**
     * Internal: Registers sub-factories with this factory. The sub-factory will
     * be responsible for the creation of sub-aggregators under the aggregator
     * created by this factory. This is only for use by
     * {@link AggregatorFactories#parseAggregators(XContentParser)}.
     *
     * @param subFactories
     *            The sub-factories
     * @return this factory (fluent interface)
     */
    @Override
    public abstract AggregationBuilder subAggregations(AggregatorFactories.Builder subFactories);

    /**
     * Create a shallow copy of this builder and replacing {@link #factoriesBuilder} and <code>metadata</code>.
     * Used by {@link #rewrite(QueryRewriteContext)}.
     */
    protected abstract AggregationBuilder shallowCopy(AggregatorFactories.Builder factoriesBuilder, Map<String, Object> metadata);

    @Override
    public final AggregationBuilder rewrite(QueryRewriteContext context) throws IOException {
        AggregationBuilder rewritten = doRewrite(context);
        AggregatorFactories.Builder rewrittenSubAggs = factoriesBuilder.rewrite(context);
        if (rewritten != this) {
            return rewritten.setMetadata(getMetadata()).subAggregations(rewrittenSubAggs);
        } else if (rewrittenSubAggs != factoriesBuilder) {
            return shallowCopy(rewrittenSubAggs, getMetadata());
        } else {
            return this;
        }
    }

    /**
     * Rewrites this aggregation builder into its primitive form. By default
     * this method return the builder itself. If the builder did not change the
     * identity reference must be returned otherwise the builder will be
     * rewritten infinitely.
     */
    protected AggregationBuilder doRewrite(QueryRewriteContext queryShardContext) throws IOException {
        return this;
    }

    /**
     * Build a tree of {@link PipelineAggregator}s to modify the tree of
     * aggregation results after the final reduction.
     */
    public PipelineTree buildPipelineTree() {
        return factoriesBuilder.buildPipelineTree();
    }

    /**
     * A rough count of the number of buckets that {@link Aggregator}s built
     * by this builder will contain per parent bucket used to validate sorts
     * and pipeline aggregations. Just "zero", "one", and "many".
     * <p>
     * Unlike {@link CardinalityUpperBound} which is <strong>total</strong>
     * instead of <strong>per parent bucket</strong>.
     */
    public enum BucketCardinality {
        NONE, ONE, MANY;
    }
    /**
     * A rough count of the number of buckets that {@link Aggregator}s built
     * by this builder will contain per owning parent bucket.
     */
    public abstract BucketCardinality bucketCardinality();

    /** Common xcontent fields shared among aggregator builders */
    public static final class CommonFields extends ParseField.CommonFields {
        public static final ParseField VALUE_TYPE = new ParseField("value_type");
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }
}
