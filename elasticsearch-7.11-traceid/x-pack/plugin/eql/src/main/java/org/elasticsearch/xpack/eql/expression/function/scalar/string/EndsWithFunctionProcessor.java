/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.eql.expression.function.scalar.string;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.eql.EqlIllegalArgumentException;
import org.elasticsearch.xpack.ql.expression.gen.processor.Processor;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public class EndsWithFunctionProcessor implements Processor {

    public static final String NAME = "senw";

    private final Processor input;
    private final Processor pattern;
    private final boolean isCaseSensitive;

    public EndsWithFunctionProcessor(Processor input, Processor pattern, boolean isCaseSensitive) {
        this.input = input;
        this.pattern = pattern;
        this.isCaseSensitive = isCaseSensitive;
    }

    public EndsWithFunctionProcessor(StreamInput in) throws IOException {
        input = in.readNamedWriteable(Processor.class);
        pattern = in.readNamedWriteable(Processor.class);
        isCaseSensitive = in.readBoolean();
    }

    @Override
    public final void writeTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(input);
        out.writeNamedWriteable(pattern);
        out.writeBoolean(isCaseSensitive);
    }

    @Override
    public Object process(Object o) {
        return doProcess(input.process(o), pattern.process(o), isCaseSensitive());
    }

    public static Object doProcess(Object input, Object pattern, boolean isCaseSensitive) {
        if (input == null) {
            return null;
        }
        if (input instanceof String == false && input instanceof Character == false) {
            throw new EqlIllegalArgumentException("A string/char is required; received [{}]", input);
        }
        if (pattern == null) {
            return null;
        }
        if (pattern instanceof String == false && pattern instanceof Character == false) {
            throw new EqlIllegalArgumentException("A string/char is required; received [{}]", pattern);
        }

        if (isCaseSensitive) {
            return input.toString().endsWith(pattern.toString());
        } else {
            return input.toString().toLowerCase(Locale.ROOT).endsWith(pattern.toString().toLowerCase(Locale.ROOT));
        }
    }

    protected Processor input() {
        return input;
    }

    protected Processor pattern() {
        return pattern;
    }

    protected boolean isCaseSensitive() {
        return isCaseSensitive;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EndsWithFunctionProcessor other = (EndsWithFunctionProcessor) obj;
        return Objects.equals(input(), other.input())
                && Objects.equals(pattern(), other.pattern())
                && Objects.equals(isCaseSensitive(), other.isCaseSensitive());
    }

    @Override
    public int hashCode() {
        return Objects.hash(input(), pattern(), isCaseSensitive());
    }


    @Override
    public String getWriteableName() {
        return NAME;
    }
}
