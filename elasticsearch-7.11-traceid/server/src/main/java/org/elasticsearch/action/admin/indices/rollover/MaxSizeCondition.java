/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.action.admin.indices.rollover;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

/**
 * A size-based condition for an index size.
 * Evaluates to <code>true</code> if the index size is at least {@link #value}.
 */
public class MaxSizeCondition extends Condition<ByteSizeValue> {
    public static final String NAME = "max_size";

    public MaxSizeCondition(ByteSizeValue value) {
        super(NAME);
        this.value = value;
    }

    public MaxSizeCondition(StreamInput in) throws IOException {
        super(NAME);
        this.value = new ByteSizeValue(in.readVLong(), ByteSizeUnit.BYTES);
    }

    @Override
    public Result evaluate(Stats stats) {
        return new Result(this, stats.indexSize.getBytes() >= value.getBytes());
    }

    @Override
    boolean includedInVersion(Version version) {
        return version.onOrAfter(Version.V_6_1_0);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        // While we technically could serialize this with value.writeTo(...), that would
        // require doing the song and dance around backwards compatibility for this value. Since
        // in this case the deserialized version is not displayed to a user, it's okay to simply use
        // bytes.
        out.writeVLong(value.getBytes());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.field(NAME, value.getStringRep());
    }

    public static MaxSizeCondition fromXContent(XContentParser parser) throws IOException {
        if (parser.nextToken() == XContentParser.Token.VALUE_STRING) {
            return new MaxSizeCondition(ByteSizeValue.parseBytesSizeValue(parser.text(), NAME));
        } else {
            throw new IllegalArgumentException("invalid token: " + parser.currentToken());
        }
    }
}
