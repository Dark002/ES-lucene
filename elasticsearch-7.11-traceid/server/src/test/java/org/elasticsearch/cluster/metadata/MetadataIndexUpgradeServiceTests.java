/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.cluster.metadata;

import org.elasticsearch.Version;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.indices.SystemIndexDescriptor;
import org.elasticsearch.indices.SystemIndices;
import org.elasticsearch.indices.mapper.MapperRegistry;
import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;

import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;

public class MetadataIndexUpgradeServiceTests extends ESTestCase {

    public void testArchiveBrokenIndexSettings() {
        MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        IndexMetadata src = newIndexMeta("foo", Settings.EMPTY);
        IndexMetadata indexMetadata = service.archiveBrokenIndexSettings(src);
        assertSame(indexMetadata, src);

        src = newIndexMeta("foo", Settings.builder().put("index.refresh_interval", "-200").build());
        indexMetadata = service.archiveBrokenIndexSettings(src);
        assertNotSame(indexMetadata, src);
        assertEquals("-200", indexMetadata.getSettings().get("archived.index.refresh_interval"));

        src = newIndexMeta("foo", Settings.builder().put("index.codec", "best_compression1").build());
        indexMetadata = service.archiveBrokenIndexSettings(src);
        assertNotSame(indexMetadata, src);
        assertEquals("best_compression1", indexMetadata.getSettings().get("archived.index.codec"));

        src = newIndexMeta("foo", Settings.builder().put("index.refresh.interval", "-1").build());
        indexMetadata = service.archiveBrokenIndexSettings(src);
        assertNotSame(indexMetadata, src);
        assertEquals("-1", indexMetadata.getSettings().get("archived.index.refresh.interval"));

        src = newIndexMeta("foo", indexMetadata.getSettings()); // double archive?
        indexMetadata = service.archiveBrokenIndexSettings(src);
        assertSame(indexMetadata, src);
    }

    public void testAlreadyUpgradedIndexArchivesBrokenIndexSettings() {
        final MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        final IndexMetadata initial = newIndexMeta(
            "foo",
            Settings.builder().put(IndexMetadata.SETTING_VERSION_UPGRADED, Version.CURRENT).put("index.refresh_interval", "-200").build());
        assertTrue(service.isUpgraded(initial));
        final IndexMetadata after = service.upgradeIndexMetadata(initial, Version.CURRENT.minimumIndexCompatibilityVersion());
        // the index does not need to be upgraded, but checking that it does should archive any broken settings
        assertThat(after.getSettings().get("archived.index.refresh_interval"), equalTo("-200"));
        assertNull(after.getSettings().get("index.refresh_interval"));
    }

    public void testUpgrade() {
        MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        IndexMetadata src = newIndexMeta("foo", Settings.builder().put("index.refresh_interval", "-200").build());
        assertFalse(service.isUpgraded(src));
        src = service.upgradeIndexMetadata(src, Version.CURRENT.minimumIndexCompatibilityVersion());
        assertTrue(service.isUpgraded(src));
        assertEquals("-200", src.getSettings().get("archived.index.refresh_interval"));
        assertNull(src.getSettings().get("index.refresh_interval"));
        assertSame(src, service.upgradeIndexMetadata(src, Version.CURRENT.minimumIndexCompatibilityVersion())); // no double upgrade
    }

    public void testUpgradeCustomSimilarity() {
        MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        IndexMetadata src = newIndexMeta("foo",
            Settings.builder()
                .put("index.similarity.my_similarity.type", "DFR")
                .put("index.similarity.my_similarity.after_effect", "l")
                .build());
        assertFalse(service.isUpgraded(src));
        src = service.upgradeIndexMetadata(src, Version.CURRENT.minimumIndexCompatibilityVersion());
        assertTrue(service.isUpgraded(src));
    }

    public void testIsUpgraded() {
        MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        IndexMetadata src = newIndexMeta("foo", Settings.builder().put("index.refresh_interval", "-200").build());
        assertFalse(service.isUpgraded(src));
        Version version = VersionUtils.randomVersionBetween(random(), VersionUtils.getFirstVersion(), VersionUtils.getPreviousVersion());
        src = newIndexMeta("foo", Settings.builder().put(IndexMetadata.SETTING_VERSION_UPGRADED, version).build());
        assertFalse(service.isUpgraded(src));
        src = newIndexMeta("foo", Settings.builder().put(IndexMetadata.SETTING_VERSION_UPGRADED, Version.CURRENT).build());
        assertTrue(service.isUpgraded(src));
    }

    public void testFailUpgrade() {
        MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        Version minCompat = Version.CURRENT.minimumIndexCompatibilityVersion();
        Version indexUpgraded = VersionUtils.randomVersionBetween(random(), minCompat, VersionUtils.getPreviousVersion(Version.CURRENT));
        Version indexCreated = Version.fromString((minCompat.major - 1) + "." + randomInt(5) + "." + randomInt(5));
        final IndexMetadata metadata = newIndexMeta("foo", Settings.builder()
            .put(IndexMetadata.SETTING_VERSION_UPGRADED, indexUpgraded)
            .put(IndexMetadata.SETTING_VERSION_CREATED, indexCreated)
            .build());
        String message = expectThrows(IllegalStateException.class, () -> service.upgradeIndexMetadata(metadata,
            Version.CURRENT.minimumIndexCompatibilityVersion())).getMessage();
        assertEquals(message, "The index [[foo/BOOM]] was created with version [" + indexCreated + "] " +
             "but the minimum compatible version is [" + minCompat + "]." +
            " It should be re-indexed in Elasticsearch " + minCompat.major + ".x before upgrading to " + Version.CURRENT.toString() + ".");

        indexCreated = VersionUtils.randomVersionBetween(random(), minCompat, Version.CURRENT);
        indexUpgraded = VersionUtils.randomVersionBetween(random(), indexCreated, Version.CURRENT);
        IndexMetadata goodMeta = newIndexMeta("foo", Settings.builder()
            .put(IndexMetadata.SETTING_VERSION_UPGRADED, indexUpgraded)
            .put(IndexMetadata.SETTING_VERSION_CREATED, indexCreated)
            .build());
        service.upgradeIndexMetadata(goodMeta, Version.CURRENT.minimumIndexCompatibilityVersion());
    }

    public void testMaybeMarkAsSystemIndex() {
        MetadataIndexUpgradeService service = getMetadataIndexUpgradeService();
        IndexMetadata src = newIndexMeta("foo", Settings.EMPTY);
        assertFalse(src.isSystem());
        IndexMetadata indexMetadata = service.maybeMarkAsSystemIndex(src);
        assertSame(indexMetadata, src);

        src = newIndexMeta(".system", Settings.EMPTY);
        assertFalse(src.isSystem());
        indexMetadata = service.maybeMarkAsSystemIndex(src);
        assertNotSame(indexMetadata, src);
        assertTrue(indexMetadata.isSystem());

        // test with the whole upgrade
        assertFalse(src.isSystem());
        indexMetadata = service.upgradeIndexMetadata(src, Version.CURRENT.minimumIndexCompatibilityVersion());
        assertTrue(indexMetadata.isSystem());
    }

    private MetadataIndexUpgradeService getMetadataIndexUpgradeService() {
        return new MetadataIndexUpgradeService(
            Settings.EMPTY,
            xContentRegistry(),
            new MapperRegistry(Collections.emptyMap(), Collections.emptyMap(), null,
                Collections.emptyMap(), MapperPlugin.NOOP_FIELD_FILTER),
            IndexScopedSettings.DEFAULT_SCOPED_SETTINGS,
            new SystemIndices(Collections.singletonMap("system-plugin",
                Collections.singletonList(new SystemIndexDescriptor(".system", "a system index")))),
            null
        );
    }

    public static IndexMetadata newIndexMeta(String name, Settings indexSettings) {
        Settings build = Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_CREATION_DATE, 1)
            .put(IndexMetadata.SETTING_INDEX_UUID, "BOOM")
            .put(IndexMetadata.SETTING_VERSION_UPGRADED, Version.V_6_0_0_alpha1)
            .put(indexSettings)
            .build();
        return IndexMetadata.builder(name).settings(build).build();
    }

}
