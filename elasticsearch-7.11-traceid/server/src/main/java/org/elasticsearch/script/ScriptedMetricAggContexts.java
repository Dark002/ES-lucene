/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.script;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Scorable;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.search.lookup.LeafSearchLookup;
import org.elasticsearch.search.lookup.SearchLookup;
import org.elasticsearch.search.lookup.SourceLookup;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ScriptedMetricAggContexts {

    public abstract static class InitScript {
        private final Map<String, Object> params;
        private final Map<String, Object> state;

        public InitScript(Map<String, Object> params, Map<String, Object> state) {
            this.params = params;
            this.state = state;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public Object getState() {
            return state;
        }

        public abstract void execute();

        public interface Factory extends ScriptFactory {
            InitScript newInstance(Map<String, Object> params, Map<String, Object> state);
        }

        public static String[] PARAMETERS = {};
        public static ScriptContext<Factory> CONTEXT = new ScriptContext<>("aggs_init", Factory.class);
    }

    public abstract static class MapScript {

        private static final DeprecationLogger deprecationLogger = DeprecationLogger.getLogger(DynamicMap.class);
        private static final Map<String, Function<Object, Object>> PARAMS_FUNCTIONS = org.elasticsearch.common.collect.Map.of(
                "doc", value -> {
                    deprecationLogger.deprecate("map-script_doc",
                            "Accessing variable [doc] via [params.doc] from within an scripted metric agg map script "
                                    + "is deprecated in favor of directly accessing [doc].");
                    return value;
                },
                "_doc", value -> {
                    deprecationLogger.deprecate("map-script__doc",
                            "Accessing variable [doc] via [params._doc] from within an scripted metric agg map script "
                                    + "is deprecated in favor of directly accessing [doc].");
                    return value;
                }, "_agg", value -> {
                    deprecationLogger.deprecate("map-script__agg",
                            "Accessing variable [_agg] via [params._agg] from within a scripted metric agg map script "
                                    + "is deprecated in favor of using [state].");
                    return value;
                },
                "_source", value -> ((SourceLookup)value).loadSourceIfNeeded()
        );

        private final Map<String, Object> params;
        private final Map<String, Object> state;
        private final LeafSearchLookup leafLookup;
        private Scorable scorer;

        public MapScript(Map<String, Object> params, Map<String, Object> state, SearchLookup lookup, LeafReaderContext leafContext) {
            this.state = state;
            this.leafLookup = leafContext == null ? null : lookup.getLeafSearchLookup(leafContext);
            if (leafLookup != null) {
                params = new HashMap<>(params); // copy params so we aren't modifying input
                params.putAll(leafLookup.asMap()); // add lookup vars
                params = new DynamicMap(params, PARAMS_FUNCTIONS); // wrap with deprecations
            }
            this.params = params;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public Map<String, Object> getState() {
            return state;
        }

        // Return the doc as a map (instead of LeafDocLookup) in order to abide by type whitelisting rules for
        // Painless scripts.
        public Map<String, ScriptDocValues<?>> getDoc() {
            return leafLookup == null ? null : leafLookup.doc();
        }

        public void setDocument(int docId) {
            if (leafLookup != null) {
                leafLookup.setDocument(docId);
            }
        }

        public void setScorer(Scorable scorer) {
            this.scorer = scorer;
        }

        // get_score() is named this way so that it's picked up by Painless as '_score'
        public double get_score() {
            if (scorer == null) {
                return 0.0;
            }

            try {
                return scorer.score();
            } catch (IOException e) {
                throw new ElasticsearchException("Couldn't look up score", e);
            }
        }

        public abstract void execute();

        public interface LeafFactory {
            MapScript newInstance(LeafReaderContext ctx);
        }

        public interface Factory extends ScriptFactory {
            LeafFactory newFactory(Map<String, Object> params, Map<String, Object> state, SearchLookup lookup);
        }

        public static String[] PARAMETERS = new String[] {};
        public static ScriptContext<Factory> CONTEXT = new ScriptContext<>("aggs_map", Factory.class);
    }

    public abstract static class CombineScript {
        private final Map<String, Object> params;
        private final Map<String, Object> state;

        public CombineScript(Map<String, Object> params, Map<String, Object> state) {
            this.params = params;
            this.state = state;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public Map<String, Object> getState() {
            return state;
        }

        public abstract Object execute();

        public interface Factory extends ScriptFactory {
            CombineScript newInstance(Map<String, Object> params, Map<String, Object> state);
        }

        public static String[] PARAMETERS = {};
        public static ScriptContext<Factory> CONTEXT = new ScriptContext<>("aggs_combine", Factory.class);
    }

    public abstract static class ReduceScript {
        private final Map<String, Object> params;
        private final List<Object> states;

        public ReduceScript(Map<String, Object> params, List<Object> states) {
            this.params = params;
            this.states = states;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public List<Object> getStates() {
            return states;
        }

        public abstract Object execute();

        public interface Factory extends ScriptFactory {
            ReduceScript newInstance(Map<String, Object> params, List<Object> states);
        }

        public static String[] PARAMETERS = {};
        public static ScriptContext<Factory> CONTEXT = new ScriptContext<>("aggs_reduce", Factory.class);
    }
}