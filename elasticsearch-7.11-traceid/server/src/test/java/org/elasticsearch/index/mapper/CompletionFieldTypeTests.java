/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.elasticsearch.index.analysis.AnalyzerScope;
import org.elasticsearch.index.analysis.NamedAnalyzer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompletionFieldTypeTests extends FieldTypeTestCase {

    public void testFetchSourceValue() throws IOException {
        NamedAnalyzer defaultAnalyzer = new NamedAnalyzer("standard", AnalyzerScope.INDEX, new StandardAnalyzer());

        MappedFieldType fieldType = new CompletionFieldMapper.CompletionFieldType("name", defaultAnalyzer, Collections.emptyMap());

        assertEquals(Collections.singletonList("value"), fetchSourceValue(fieldType, "value"));

        List<String> list = Arrays.asList("first", "second");
        assertEquals(list, fetchSourceValue(fieldType, list));

        Map<String, Object> object = new HashMap<>();
        object.put("input", Arrays.asList("first", "second"));
        object.put("weight", "2.718");
        assertEquals(Collections.singletonList(object), fetchSourceValue(fieldType, object));
    }
}
