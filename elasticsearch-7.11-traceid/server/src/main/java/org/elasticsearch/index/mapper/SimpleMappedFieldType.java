/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.time.DateMathParser;
import org.elasticsearch.index.query.QueryShardContext;

import java.time.ZoneId;
import java.util.Map;

/**
 * {@link MappedFieldType} base impl for field types that are neither dates nor ranges.
 */
public abstract class SimpleMappedFieldType extends MappedFieldType {

    protected SimpleMappedFieldType(String name, boolean isSearchable, boolean isStored, boolean hasDocValues,
                                    TextSearchInfo textSearchInfo, Map<String, String> meta) {
        super(name, isSearchable, isStored, hasDocValues, textSearchInfo, meta);
    }

    @Override
    public final Query rangeQuery(Object lowerTerm, Object upperTerm, boolean includeLower, boolean includeUpper,
                                  ShapeRelation relation, ZoneId timeZone, DateMathParser parser, QueryShardContext context) {
        if (relation == ShapeRelation.DISJOINT) {
            throw new IllegalArgumentException("Field [" + name() + "] of type [" + typeName() +
                    "] does not support DISJOINT ranges");
        }
        // We do not fail on non-null time zones and date parsers
        // The reasoning is that on query parsers, you might want to set a time zone or format for date fields
        // but then the API has no way to know which fields are dates and which fields are not dates
        return rangeQuery(lowerTerm, upperTerm, includeLower, includeUpper, context);
    }

    /**
     * Same as {@link #rangeQuery(Object, Object, boolean, boolean, ShapeRelation, ZoneId, DateMathParser, QueryShardContext)}
     * but without the trouble of relations or date-specific options.
     */
    protected Query rangeQuery(Object lowerTerm, Object upperTerm, boolean includeLower, boolean includeUpper,
            QueryShardContext context) {
        throw new IllegalArgumentException("Field [" + name() + "] of type [" + typeName() + "] does not support range queries");
    }

}
