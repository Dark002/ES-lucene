/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.index.mapper.MapperService.MergeReason;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

/**
 * Wrapper around everything that defines a mapping, without references to
 * utility classes like MapperService, ...
 */
public final class Mapping implements ToXContentFragment {

    final RootObjectMapper root;
    final MetadataFieldMapper[] metadataMappers;
    final Map<Class<? extends MetadataFieldMapper>, MetadataFieldMapper> metadataMappersMap;
    final Map<String, MetadataFieldMapper> metadataMappersByName;
    final Map<String, Object> meta;

    public Mapping(RootObjectMapper rootObjectMapper, MetadataFieldMapper[] metadataMappers, Map<String, Object> meta) {
        this.metadataMappers = metadataMappers;
        Map<Class<? extends MetadataFieldMapper>, MetadataFieldMapper> metadataMappersMap = new HashMap<>();
        Map<String, MetadataFieldMapper> metadataMappersByName = new HashMap<>();
        for (MetadataFieldMapper metadataMapper : metadataMappers) {
            metadataMappersMap.put(metadataMapper.getClass(), metadataMapper);
            metadataMappersByName.put(metadataMapper.name(), metadataMapper);
        }
        this.root = rootObjectMapper;
        // keep root mappers sorted for consistent serialization
        Arrays.sort(metadataMappers, new Comparator<Mapper>() {
            @Override
            public int compare(Mapper o1, Mapper o2) {
                return o1.name().compareTo(o2.name());
            }
        });
        this.metadataMappersMap = unmodifiableMap(metadataMappersMap);
        this.metadataMappersByName = unmodifiableMap(metadataMappersByName);
        this.meta = meta;
    }

    /** Return the root object mapper. */
    public RootObjectMapper root() {
        return root;
    }

    public void validate(MappingLookup mappers) {
        for (MetadataFieldMapper metadataFieldMapper : metadataMappers) {
            metadataFieldMapper.validate(mappers);
        }
        root.validate(mappers);
    }

    /**
     * Generate a mapping update for the given root object mapper.
     */
    public Mapping mappingUpdate(RootObjectMapper rootObjectMapper) {
        return new Mapping(rootObjectMapper, metadataMappers, meta);
    }

    /** Get the root mapper with the given class. */
    @SuppressWarnings("unchecked")
    public <T extends MetadataFieldMapper> T metadataMapper(Class<T> clazz) {
        return (T) metadataMappersMap.get(clazz);
    }

    /**
     * Merges a new mapping into the existing one.
     *
     * @param mergeWith the new mapping to merge into this one.
     * @param reason the reason this merge was initiated.
     * @return the resulting merged mapping.
     */
    public Mapping merge(Mapping mergeWith, MergeReason reason) {
        RootObjectMapper mergedRoot = root.merge(mergeWith.root, reason);

        // When merging metadata fields as part of applying an index template, new field definitions
        // completely overwrite existing ones instead of being merged. This behavior matches how we
        // merge leaf fields in the 'properties' section of the mapping.
        Map<Class<? extends MetadataFieldMapper>, MetadataFieldMapper> mergedMetadataMappers = new HashMap<>(metadataMappersMap);
        for (MetadataFieldMapper metaMergeWith : mergeWith.metadataMappers) {
            MetadataFieldMapper mergeInto = mergedMetadataMappers.get(metaMergeWith.getClass());
            MetadataFieldMapper merged;
            if (mergeInto == null || reason == MergeReason.INDEX_TEMPLATE) {
                merged = metaMergeWith;
            } else {
                merged = (MetadataFieldMapper) mergeInto.merge(metaMergeWith);
            }
            mergedMetadataMappers.put(merged.getClass(), merged);
        }

        // If we are merging the _meta object as part of applying an index template, then the new object
        // is deep-merged into the existing one to allow individual keys to be added or overwritten. For
        // standard mapping updates, the new _meta object completely replaces the old one.
        Map<String, Object> mergedMeta;
        if (mergeWith.meta == null) {
            mergedMeta = meta;
        } else if (meta == null || reason != MergeReason.INDEX_TEMPLATE) {
            mergedMeta = mergeWith.meta;
        } else {
            mergedMeta = new HashMap<>(mergeWith.meta);
            XContentHelper.mergeDefaults(mergedMeta, meta);
        }

        return new Mapping(mergedRoot, mergedMetadataMappers.values().toArray(new MetadataFieldMapper[0]), mergedMeta);
    }

    public MetadataFieldMapper getMetadataMapper(String mapperName) {
        return metadataMappersByName.get(mapperName);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        root.toXContent(builder, params, new ToXContent() {
            @Override
            public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                if (meta != null) {
                    builder.field("_meta", meta);
                }
                for (Mapper mapper : metadataMappers) {
                    mapper.toXContent(builder, params);
                }
                return builder;
            }
        });
        return builder;
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
            toXContent(builder, new ToXContent.MapParams(emptyMap()));
            return Strings.toString(builder.endObject());
        } catch (IOException bogus) {
            throw new UncheckedIOException(bogus);
        }
    }
}
