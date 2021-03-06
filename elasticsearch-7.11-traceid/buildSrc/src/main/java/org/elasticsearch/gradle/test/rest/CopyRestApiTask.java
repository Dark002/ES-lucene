/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.gradle.test.rest;

import org.elasticsearch.gradle.VersionProperties;
import org.elasticsearch.gradle.info.BuildParams;
import org.elasticsearch.gradle.util.GradleUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.elasticsearch.gradle.util.GradleUtils.getProjectPathFromTask;

/**
 * Copies the files needed for the Rest YAML specs to the current projects test resources output directory.
 * This is intended to be be used from {@link RestResourcesPlugin} since the plugin wires up the needed
 * configurations and custom extensions.
 * @see RestResourcesPlugin
 */
public class CopyRestApiTask extends DefaultTask {
    private static final String REST_API_PREFIX = "rest-api-spec/api";
    final ListProperty<String> includeCore = getProject().getObjects().listProperty(String.class);
    final ListProperty<String> includeXpack = getProject().getObjects().listProperty(String.class);
    String sourceSetName;
    boolean skipHasRestTestCheck;
    FileCollection coreConfig;
    FileCollection xpackConfig;
    FileCollection additionalConfig;
    Function<FileCollection, FileTree> coreConfigToFileTree = FileCollection::getAsFileTree;
    Function<FileCollection, FileTree> xpackConfigToFileTree = FileCollection::getAsFileTree;
    Function<FileCollection, FileTree> additionalConfigToFileTree = FileCollection::getAsFileTree;

    private final PatternFilterable corePatternSet;
    private final PatternFilterable xpackPatternSet;
    private final ProjectLayout projectLayout;

    @Inject
    public CopyRestApiTask(ProjectLayout projectLayout) {
        corePatternSet = getPatternSetFactory().create();
        xpackPatternSet = getPatternSetFactory().create();
        this.projectLayout = projectLayout;
    }

    @Inject
    protected Factory<PatternSet> getPatternSetFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileSystemOperations getFileSystemOperations() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ArchiveOperations getArchiveOperations() {
        throw new UnsupportedOperationException();
    }

    @Input
    public ListProperty<String> getIncludeCore() {
        return includeCore;
    }

    @Input
    public ListProperty<String> getIncludeXpack() {
        return includeXpack;
    }

    @Input
    String getSourceSetName() {
        return sourceSetName;
    }

    @Input
    public boolean isSkipHasRestTestCheck() {
        return skipHasRestTestCheck;
    }

    @SkipWhenEmpty
    @InputFiles
    public FileTree getInputDir() {
        FileTree coreFileTree = null;
        FileTree xpackFileTree = null;
        if (includeXpack.get().isEmpty() == false) {
            xpackPatternSet.setIncludes(includeXpack.get().stream().map(prefix -> prefix + "*/**").collect(Collectors.toList()));
            xpackFileTree = xpackConfigToFileTree.apply(xpackConfig).matching(xpackPatternSet);
        }
        boolean projectHasYamlRestTests = skipHasRestTestCheck || projectHasYamlRestTests();
        if (includeCore.get().isEmpty() == false || projectHasYamlRestTests) {
            if (BuildParams.isInternal()) {
                corePatternSet.setIncludes(includeCore.get().stream().map(prefix -> prefix + "*/**").collect(Collectors.toList()));
                coreFileTree = coreConfigToFileTree.apply(coreConfig).matching(corePatternSet); // directory on disk
            } else {
                coreFileTree = coreConfig.getAsFileTree(); // jar file
            }
        }

        FileCollection fileCollection = additionalConfig == null
            ? projectLayout.files(coreFileTree, xpackFileTree)
            : projectLayout.files(coreFileTree, xpackFileTree, additionalConfigToFileTree.apply(additionalConfig));

        // if project has rest tests or the includes are explicitly configured execute the task, else NO-SOURCE due to the null input
        return projectHasYamlRestTests || includeCore.get().isEmpty() == false || includeXpack.get().isEmpty() == false
            ? fileCollection.getAsFileTree()
            : null;
    }

    @OutputDirectory
    public File getOutputDir() {
        return new File(
            getSourceSet().orElseThrow(() -> new IllegalArgumentException("could not find source set [" + sourceSetName + "]"))
                .getOutput()
                .getResourcesDir(),
            REST_API_PREFIX
        );
    }

    @TaskAction
    void copy() {
        // always copy the core specs if the task executes
        String projectPath = getProjectPathFromTask(getPath());
        if (BuildParams.isInternal()) {
            getLogger().debug("Rest specs for project [{}] will be copied to the test resources.", projectPath);
            getFileSystemOperations().copy(c -> {
                c.from(coreConfigToFileTree.apply(coreConfig));
                c.into(getOutputDir());
                c.include(corePatternSet.getIncludes());
            });
        } else {
            getLogger().debug(
                "Rest specs for project [{}] will be copied to the test resources from the published jar (version: [{}]).",
                projectPath,
                VersionProperties.getElasticsearch()
            );
            getFileSystemOperations().copy(c -> {
                c.from(getArchiveOperations().zipTree(coreConfig.getSingleFile())); // jar file
                // this ends up as the same dir as outputDir
                c.into(Objects.requireNonNull(getSourceSet().orElseThrow().getOutput().getResourcesDir()));
                if (includeCore.get().isEmpty()) {
                    c.include(REST_API_PREFIX + "/**");
                } else {
                    c.include(
                        includeCore.get().stream().map(prefix -> REST_API_PREFIX + "/" + prefix + "*/**").collect(Collectors.toList())
                    );
                }
            });
        }
        // only copy x-pack specs if explicitly instructed
        if (includeXpack.get().isEmpty() == false) {
            getLogger().debug("X-pack rest specs for project [{}] will be copied to the test resources.", projectPath);
            getFileSystemOperations().copy(c -> {
                c.from(xpackConfigToFileTree.apply(xpackConfig));
                c.into(getOutputDir());
                c.include(xpackPatternSet.getIncludes());
            });
        }
        // TODO: once https://github.com/elastic/elasticsearch/pull/62968 lands ensure that this uses `getFileSystemOperations()`
        // copy any additional config
        if (additionalConfig != null) {
            getFileSystemOperations().copy(c -> {
                c.from(additionalConfigToFileTree.apply(additionalConfig));
                c.into(getOutputDir());
            });
        }
    }

    /**
     * Returns true if any files with a .yml extension exist the test resources rest-api-spec/test directory (from source or output dir)
     */
    private boolean projectHasYamlRestTests() {
        File testSourceResourceDir = getTestSourceResourceDir();
        File testOutputResourceDir = getTestOutputResourceDir(); // check output for cases where tests are copied programmatically

        if (testSourceResourceDir == null && testOutputResourceDir == null) {
            return false;
        }
        try {
            if (testSourceResourceDir != null && new File(testSourceResourceDir, "rest-api-spec/test").exists()) {
                return Files.walk(testSourceResourceDir.toPath().resolve("rest-api-spec/test"))
                    .anyMatch(p -> p.getFileName().toString().endsWith("yml"));
            }
            if (testOutputResourceDir != null && new File(testOutputResourceDir, "rest-api-spec/test").exists()) {
                return Files.walk(testOutputResourceDir.toPath().resolve("rest-api-spec/test"))
                    .anyMatch(p -> p.getFileName().toString().endsWith("yml"));
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Error determining if this project [%s] has rest tests.", getProject()), e);
        }
        return false;
    }

    private File getTestSourceResourceDir() {
        Optional<SourceSet> testSourceSet = getSourceSet();
        if (testSourceSet.isPresent()) {
            SourceSet testSources = testSourceSet.get();
            Set<File> resourceDir = testSources.getResources()
                .getSrcDirs()
                .stream()
                .filter(f -> f.isDirectory() && f.getParentFile().getName().equals(getSourceSetName()) && f.getName().equals("resources"))
                .collect(Collectors.toSet());
            assert resourceDir.size() <= 1;
            if (resourceDir.size() == 0) {
                return null;
            }
            return resourceDir.iterator().next();
        } else {
            return null;
        }
    }

    private File getTestOutputResourceDir() {
        Optional<SourceSet> testSourceSet = getSourceSet();
        return testSourceSet.map(sourceSet -> sourceSet.getOutput().getResourcesDir()).orElse(null);
    }

    private Optional<SourceSet> getSourceSet() {
        Project project = getProject();
        return project.getConvention().findPlugin(JavaPluginConvention.class) == null
            ? Optional.empty()
            : Optional.ofNullable(GradleUtils.getJavaSourceSets(project).findByName(getSourceSetName()));
    }
}
