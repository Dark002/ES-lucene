/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.client.indices;

import org.apache.lucene.util.CollectionUtil;
import org.elasticsearch.client.GetAliasesResponseTests;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContent.Params;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.RandomCreateIndexGenerator;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.test.AbstractXContentTestCase.xContentTester;

public class GetIndexResponseTests extends ESTestCase {

    // Because the client-side class does not have a toXContent method, we test xContent serialization by creating
    // a random client object, converting it to a server object then serializing it to xContent, and finally
    // parsing it back as a client object. We check equality between the original client object, and the parsed one.
    public void testFromXContent() throws IOException {
        xContentTester(
            this::createParser,
            GetIndexResponseTests::createTestInstance,
            GetIndexResponseTests::toXContent,
            GetIndexResponse::fromXContent)
            .supportsUnknownFields(false)
            .assertToXContentEquivalence(false)
            .assertEqualsConsumer(GetIndexResponseTests::assertEqualInstances)
            .test();
    }

    private static void assertEqualInstances(GetIndexResponse expected, GetIndexResponse actual) {
        assertArrayEquals(expected.getIndices(), actual.getIndices());
        assertEquals(expected.getMappings(), actual.getMappings());
        assertEquals(expected.getSettings(), actual.getSettings());
        assertEquals(expected.getDefaultSettings(), actual.getDefaultSettings());
        assertEquals(expected.getAliases(), actual.getAliases());
    }

    private static GetIndexResponse createTestInstance() {
        String[] indices = generateRandomStringArray(5, 5, false, false);
        Map<String, MappingMetadata> mappings = new HashMap<>();
        Map<String, List<AliasMetadata>> aliases = new HashMap<>();
        Map<String, Settings> settings = new HashMap<>();
        Map<String, Settings> defaultSettings = new HashMap<>();
        Map<String, String> dataStreams = new HashMap<>();
        IndexScopedSettings indexScopedSettings = IndexScopedSettings.DEFAULT_SCOPED_SETTINGS;
        boolean includeDefaults = randomBoolean();
        for (String index: indices) {
            mappings.put(index, createMappingsForIndex());

            List<AliasMetadata> aliasMetadataList = new ArrayList<>();
            int aliasesNum = randomIntBetween(0, 3);
            for (int i=0; i<aliasesNum; i++) {
                aliasMetadataList.add(GetAliasesResponseTests.createAliasMetadata());
            }
            CollectionUtil.timSort(aliasMetadataList, Comparator.comparing(AliasMetadata::alias));
            aliases.put(index, Collections.unmodifiableList(aliasMetadataList));

            Settings.Builder builder = Settings.builder();
            builder.put(RandomCreateIndexGenerator.randomIndexSettings());
            settings.put(index, builder.build());

            if (includeDefaults) {
                defaultSettings.put(index, indexScopedSettings.diff(settings.get(index), Settings.EMPTY));
            }

            if (randomBoolean()) {
                dataStreams.put(index, randomAlphaOfLength(5).toLowerCase(Locale.ROOT));
            }
        }
        return new GetIndexResponse(indices, mappings, aliases, settings, defaultSettings, dataStreams);
    }

    private static MappingMetadata createMappingsForIndex() {
        int typeCount = rarely() ? 0 : 1;
        MappingMetadata mmd;
        try {
            mmd = new MappingMetadata(MapperService.SINGLE_MAPPING_NAME, Collections.emptyMap());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < typeCount; i++) {
            if (rarely() == false) { // rarely have no fields
                Map<String, Object> mappings = new HashMap<>();
                mappings.put("field-" + i, randomFieldMapping());
                if (randomBoolean()) {
                    mappings.put("field2-" + i, randomFieldMapping());
                }

                try {
                    String typeName = MapperService.SINGLE_MAPPING_NAME;
                    mmd = new MappingMetadata(typeName, mappings);
                } catch (IOException e) {
                    fail("shouldn't have failed " + e);
                }
            }
        }
        return mmd;
    }

    // Not meant to be exhaustive
    private static Map<String, Object> randomFieldMapping() {
        Map<String, Object> mappings = new HashMap<>();
        if (randomBoolean()) {
            mappings.put("type", randomBoolean() ? "text" : "keyword");
            mappings.put("index", "analyzed");
            mappings.put("analyzer", "english");
        } else if (randomBoolean()) {
            mappings.put("type", randomFrom("integer", "float", "long", "double"));
            mappings.put("index", Objects.toString(randomBoolean()));
        } else if (randomBoolean()) {
            mappings.put("type", "object");
            mappings.put("dynamic", "strict");
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> props1 = new HashMap<>();
            props1.put("type", randomFrom("text", "keyword"));
            props1.put("analyzer", "keyword");
            properties.put("subtext", props1);
            Map<String, Object> props2 = new HashMap<>();
            props2.put("type", "object");
            Map<String, Object> prop2properties = new HashMap<>();
            Map<String, Object> props3 = new HashMap<>();
            props3.put("type", "integer");
            props3.put("index", "false");
            prop2properties.put("subsubfield", props3);
            props2.put("properties", prop2properties);
            mappings.put("properties", properties);
        } else {
            mappings.put("type", "keyword");
        }
        return mappings;
    }

    private static void toXContent(GetIndexResponse response, XContentBuilder builder) throws IOException {
        // first we need to repackage from GetIndexResponse to org.elasticsearch.action.admin.indices.get.GetIndexResponse
        ImmutableOpenMap.Builder<String, ImmutableOpenMap<String, MappingMetadata>> allMappings = ImmutableOpenMap.builder();
        ImmutableOpenMap.Builder<String, List<AliasMetadata>> aliases = ImmutableOpenMap.builder();
        ImmutableOpenMap.Builder<String, Settings> settings = ImmutableOpenMap.builder();
        ImmutableOpenMap.Builder<String, Settings> defaultSettings = ImmutableOpenMap.builder();

        Map<String, MappingMetadata> indexMappings = response.getMappings();
        for (String index : response.getIndices()) {
            MappingMetadata mmd = indexMappings.get(index);
            ImmutableOpenMap.Builder<String, MappingMetadata> typedMappings = ImmutableOpenMap.builder();
            if (mmd != null) {
                typedMappings.put(MapperService.SINGLE_MAPPING_NAME, mmd);
            }
            allMappings.put(index, typedMappings.build());
            aliases.put(index, response.getAliases().get(index));
            settings.put(index, response.getSettings().get(index));
            defaultSettings.put(index, response.getDefaultSettings().get(index));
        }

        org.elasticsearch.action.admin.indices.get.GetIndexResponse serverResponse
            = new org.elasticsearch.action.admin.indices.get.GetIndexResponse(
                response.getIndices(),
                allMappings.build(),
                aliases.build(),
                settings.build(),
                defaultSettings.build(),
                ImmutableOpenMap.<String, String>builder().build()
            );

        // then we can call its toXContent method, forcing no output of types
        Params params = new ToXContent.MapParams(Collections.singletonMap(BaseRestHandler.INCLUDE_TYPE_NAME_PARAMETER, "false"));
        serverResponse.toXContent(builder, params);
    }
}
