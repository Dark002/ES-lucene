/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.bulk;

import org.elasticsearch.Version;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.support.WriteResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.shard.ShardId;

import java.io.IOException;

public class BulkShardResponse extends ReplicationResponse implements WriteResponse {

    private static final Version COMPACT_SHARD_ID_VERSION = Version.V_7_9_0;

    private final ShardId shardId;
    private final BulkItemResponse[] responses;

    BulkShardResponse(StreamInput in) throws IOException {
        super(in);
        shardId = new ShardId(in);
        responses = in.readArray(in.getVersion().onOrAfter(COMPACT_SHARD_ID_VERSION)
                ? i -> new BulkItemResponse(shardId, i) : BulkItemResponse::new, BulkItemResponse[]::new);
    }

    // NOTE: public for testing only
    public BulkShardResponse(ShardId shardId, BulkItemResponse[] responses) {
        this.shardId = shardId;
        this.responses = responses;
    }

    public ShardId getShardId() {
        return shardId;
    }

    public BulkItemResponse[] getResponses() {
        return responses;
    }

    @Override
    public void setForcedRefresh(boolean forcedRefresh) {
        /*
         * Each DocWriteResponse already has a location for whether or not it forced a refresh so we just set that information on the
         * response.
         */
        for (BulkItemResponse response : responses) {
            DocWriteResponse r = response.getResponse();
            if (r != null) {
                r.setForcedRefresh(forcedRefresh);
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        shardId.writeTo(out);
        out.writeArray(out.getVersion().onOrAfter(COMPACT_SHARD_ID_VERSION)
                ? (o, item) -> item.writeThin(out) : (o, item) -> item.writeTo(o), responses);
    }
}
