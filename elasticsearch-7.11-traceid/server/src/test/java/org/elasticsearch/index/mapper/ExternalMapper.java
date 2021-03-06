/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.elasticsearch.Version;
import org.elasticsearch.common.collect.Iterators;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.builders.PointBuilder;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This mapper add a new sub fields
 * .bin Binary type
 * .bool Boolean type
 * .point GeoPoint type
 * .shape GeoShape type
 */
public class ExternalMapper extends FieldMapper {

    public static class Names {
        public static final String FIELD_BIN = "bin";
        public static final String FIELD_BOOL = "bool";
        public static final String FIELD_POINT = "point";
        public static final String FIELD_SHAPE = "shape";
    }

    private static final IndexAnalyzers INDEX_ANALYZERS = new IndexAnalyzers(
        Collections.singletonMap("default", new NamedAnalyzer("default", AnalyzerScope.INDEX, new StandardAnalyzer())),
        Collections.emptyMap(),
        Collections.emptyMap()
    );

    public static class Builder extends FieldMapper.Builder {

        private final BinaryFieldMapper.Builder binBuilder = new BinaryFieldMapper.Builder(Names.FIELD_BIN);
        private final BooleanFieldMapper.Builder boolBuilder = new BooleanFieldMapper.Builder(Names.FIELD_BOOL);
        private final GeoPointFieldMapper.Builder latLonPointBuilder = new GeoPointFieldMapper.Builder(Names.FIELD_POINT, false);
        private final GeoShapeFieldMapper.Builder shapeBuilder = new GeoShapeFieldMapper.Builder(Names.FIELD_SHAPE, false, true);
        private final LegacyGeoShapeFieldMapper.Builder legacyShapeBuilder
            = new LegacyGeoShapeFieldMapper.Builder(Names.FIELD_SHAPE, Version.CURRENT, false, true);
        private final Mapper.Builder stringBuilder;
        private final String generatedValue;
        private final String mapperName;
        private final Version indexCreatedVersion;

        public Builder(String name, String generatedValue, String mapperName, Version indexCreatedVersion) {
            super(name);
            this.stringBuilder = new TextFieldMapper.Builder(name, INDEX_ANALYZERS).store(false);
            this.generatedValue = generatedValue;
            this.mapperName = mapperName;
            this.indexCreatedVersion = indexCreatedVersion;
        }

        @Override
        protected List<Parameter<?>> getParameters() {
            return Collections.emptyList();
        }

        @Override
        public ExternalMapper build(ContentPath contentPath) {
            contentPath.add(name);
            BinaryFieldMapper binMapper = binBuilder.build(contentPath);
            BooleanFieldMapper boolMapper = boolBuilder.build(contentPath);
            GeoPointFieldMapper pointMapper = (GeoPointFieldMapper) latLonPointBuilder.build(contentPath);
            AbstractShapeGeometryFieldMapper<?, ?> shapeMapper = (indexCreatedVersion.before(Version.V_6_6_0))
                ? legacyShapeBuilder.build(contentPath)
                : shapeBuilder.build(contentPath);
            FieldMapper stringMapper = (FieldMapper) stringBuilder.build(contentPath);
            contentPath.remove();

            return new ExternalMapper(name, buildFullName(contentPath), generatedValue, mapperName, binMapper, boolMapper,
                pointMapper, shapeMapper, stringMapper, indexCreatedVersion,
                multiFieldsBuilder.build(this, contentPath), copyTo.build());
        }
    }

    public static TypeParser parser(String mapperName, String generatedValue) {
        return new TypeParser((n, c) -> new Builder(n, generatedValue, mapperName, c.indexVersionCreated()));
    }

    static class ExternalFieldType extends TermBasedFieldType {

        private ExternalFieldType(String name, boolean indexed, boolean stored, boolean hasDocValues) {
            super(name, indexed, stored, hasDocValues, TextSearchInfo.SIMPLE_MATCH_ONLY, Collections.emptyMap());
        }

        @Override
        public String typeName() {
            return "faketype";
        }

        @Override
        public ValueFetcher valueFetcher(QueryShardContext context, String format) {
            return SourceValueFetcher.identity(name(), context, format);
        }
    }

    private final String generatedValue;
    private final String mapperName;

    private final BinaryFieldMapper binMapper;
    private final BooleanFieldMapper boolMapper;
    private final GeoPointFieldMapper pointMapper;
    private final AbstractShapeGeometryFieldMapper<?, ?> shapeMapper;
    private final FieldMapper stringMapper;

    private final Version indexCreatedVersion;

    public ExternalMapper(String simpleName, String contextName,
                          String generatedValue, String mapperName,
                          BinaryFieldMapper binMapper, BooleanFieldMapper boolMapper, GeoPointFieldMapper pointMapper,
                          AbstractShapeGeometryFieldMapper<?, ?> shapeMapper, FieldMapper stringMapper,
                          Version indexCreatedVersion,
                          MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, new ExternalFieldType(contextName, true, true, false), multiFields, copyTo);
        this.generatedValue = generatedValue;
        this.mapperName = mapperName;
        this.binMapper = binMapper;
        this.boolMapper = boolMapper;
        this.pointMapper = pointMapper;
        this.shapeMapper = shapeMapper;
        this.stringMapper = stringMapper;
        this.indexCreatedVersion = indexCreatedVersion;
    }

    @Override
    public void parse(ParseContext context) throws IOException {
        byte[] bytes = "Hello world".getBytes(Charset.defaultCharset());
        binMapper.parse(context.createExternalValueContext(bytes));

        boolMapper.parse(context.createExternalValueContext(true));

        // Let's add a Dummy Point
        double lat = 42.0;
        double lng = 51.0;
        ArrayList<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(lat, lng));
        pointMapper.parse(context.createExternalValueContext(points));

        // Let's add a Dummy Shape
        if (shapeMapper instanceof GeoShapeFieldMapper) {
            shapeMapper.parse(context.createExternalValueContext(new Point(-100, 45)));
        } else {
            PointBuilder pb = new PointBuilder(-100, 45);
            shapeMapper.parse(context.createExternalValueContext(pb.buildS4J()));
        }

        context = context.createExternalValueContext(generatedValue);

        // Let's add a Original String
        stringMapper.parse(context);

        multiFields.parse(this, context);
    }

    @Override
    protected void parseCreateField(ParseContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Mapper> iterator() {
        return Iterators.concat(super.iterator(), Arrays.asList(binMapper, boolMapper, pointMapper, shapeMapper, stringMapper).iterator());
    }

    @Override
    public FieldMapper.Builder getMergeBuilder() {
        return new Builder(simpleName(), generatedValue, mapperName, indexCreatedVersion);
    }

    @Override
    protected String contentType() {
        return mapperName;
    }
}
