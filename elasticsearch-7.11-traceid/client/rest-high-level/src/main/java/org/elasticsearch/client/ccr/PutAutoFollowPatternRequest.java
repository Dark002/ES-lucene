/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.client.ccr;

import org.elasticsearch.client.Validatable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class PutAutoFollowPatternRequest extends FollowConfig implements Validatable, ToXContentObject {

    static final ParseField LEADER_PATTERNS_FIELD = new ParseField("leader_index_patterns");
    static final ParseField FOLLOW_PATTERN_FIELD = new ParseField("follow_index_pattern");

    private final String name;
    private final String remoteCluster;
    private final List<String> leaderIndexPatterns;
    private String followIndexNamePattern;

    public PutAutoFollowPatternRequest(String name, String remoteCluster, List<String> leaderIndexPatterns) {
        this.name = Objects.requireNonNull(name);
        this.remoteCluster = Objects.requireNonNull(remoteCluster);
        this.leaderIndexPatterns = Objects.requireNonNull(leaderIndexPatterns);
    }

    public String getName() {
        return name;
    }

    public String getRemoteCluster() {
        return remoteCluster;
    }

    public List<String> getLeaderIndexPatterns() {
        return leaderIndexPatterns;
    }

    public String getFollowIndexNamePattern() {
        return followIndexNamePattern;
    }

    public void setFollowIndexNamePattern(String followIndexNamePattern) {
        this.followIndexNamePattern = followIndexNamePattern;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(PutFollowRequest.REMOTE_CLUSTER_FIELD.getPreferredName(), remoteCluster);
        builder.field(LEADER_PATTERNS_FIELD.getPreferredName(), leaderIndexPatterns);
        if (followIndexNamePattern != null) {
            builder.field(FOLLOW_PATTERN_FIELD.getPreferredName(), followIndexNamePattern);
        }
        toXContentFragment(builder, params);
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PutAutoFollowPatternRequest that = (PutAutoFollowPatternRequest) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(remoteCluster, that.remoteCluster) &&
            Objects.equals(leaderIndexPatterns, that.leaderIndexPatterns) &&
            Objects.equals(followIndexNamePattern, that.followIndexNamePattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            super.hashCode(),
            name,
            remoteCluster,
            leaderIndexPatterns,
            followIndexNamePattern
        );
    }
}
