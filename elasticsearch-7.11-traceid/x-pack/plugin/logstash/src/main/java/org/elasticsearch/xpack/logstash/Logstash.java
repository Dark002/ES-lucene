/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.logstash;

import org.apache.logging.log4j.LogManager;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.IndexTemplateMetadata;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SystemIndexPlugin;
import org.elasticsearch.xpack.core.XPackPlugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.xpack.core.template.TemplateUtils;
import org.elasticsearch.xpack.logstash.action.DeletePipelineAction;
import org.elasticsearch.xpack.logstash.action.GetPipelineAction;
import org.elasticsearch.xpack.logstash.action.PutPipelineAction;
import org.elasticsearch.xpack.logstash.action.TransportDeletePipelineAction;
import org.elasticsearch.xpack.logstash.action.TransportGetPipelineAction;
import org.elasticsearch.xpack.logstash.action.TransportPutPipelineAction;
import org.elasticsearch.xpack.logstash.rest.RestDeletePipelineAction;
import org.elasticsearch.xpack.logstash.rest.RestGetPipelineAction;
import org.elasticsearch.xpack.logstash.rest.RestPutPipelineAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * This class activates/deactivates the logstash modules depending if we're running a node client or transport client
 */
public class Logstash extends Plugin implements SystemIndexPlugin {

    public static final String LOGSTASH_CONCRETE_INDEX_NAME = ".logstash";
    private static final String LOGSTASH_TEMPLATE_FILE_NAME = "logstash-management";
    private static final String LOGSTASH_INDEX_TEMPLATE_NAME = ".logstash-management";
    private static final String OLD_LOGSTASH_INDEX_NAME = "logstash-index-template";
    private static final String TEMPLATE_VERSION_VARIABLE = "logstash.template.version";

    public Logstash() {}

    public Collection<Module> createGuiceModules() {
        List<Module> modules = new ArrayList<>();
        modules.add(b -> { XPackPlugin.bindFeatureSet(b, LogstashFeatureSet.class); });
        return modules;
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return org.elasticsearch.common.collect.List.of(
            new ActionHandler<>(PutPipelineAction.INSTANCE, TransportPutPipelineAction.class),
            new ActionHandler<>(GetPipelineAction.INSTANCE, TransportGetPipelineAction.class),
            new ActionHandler<>(DeletePipelineAction.INSTANCE, TransportDeletePipelineAction.class)
        );
    }

    @Override
    public List<RestHandler> getRestHandlers(
        Settings settings,
        RestController restController,
        ClusterSettings clusterSettings,
        IndexScopedSettings indexScopedSettings,
        SettingsFilter settingsFilter,
        IndexNameExpressionResolver indexNameExpressionResolver,
        Supplier<DiscoveryNodes> nodesInCluster
    ) {
        return org.elasticsearch.common.collect.List.of(
            new RestPutPipelineAction(),
            new RestGetPipelineAction(),
            new RestDeletePipelineAction()
        );
    }

    public UnaryOperator<Map<String, IndexTemplateMetadata>> getIndexTemplateMetadataUpgrader() {
        return templates -> {
            templates.keySet().removeIf(OLD_LOGSTASH_INDEX_NAME::equals);
            TemplateUtils.loadTemplateIntoMap(
                "/" + LOGSTASH_TEMPLATE_FILE_NAME + ".json",
                templates,
                LOGSTASH_INDEX_TEMPLATE_NAME,
                Version.CURRENT.toString(),
                TEMPLATE_VERSION_VARIABLE,
                LogManager.getLogger(Logstash.class)
            );
            // internal representation of typeless templates requires the default "_doc" type, which is also required for internal templates
            assert templates.get(LOGSTASH_INDEX_TEMPLATE_NAME).mappings().get(MapperService.SINGLE_MAPPING_NAME) != null;
            return templates;
        };
    }

    @Override
    public Collection<SystemIndexDescriptor> getSystemIndexDescriptors(Settings settings) {
        return Collections.singletonList(
            new SystemIndexDescriptor(LOGSTASH_CONCRETE_INDEX_NAME, "Contains data for Logstash Central Management")
        );
    }
}
