/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.eql.expression.function.scalar.string;

import org.elasticsearch.xpack.eql.session.EqlConfiguration;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Expressions;
import org.elasticsearch.xpack.ql.expression.FieldAttribute;
import org.elasticsearch.xpack.ql.expression.function.scalar.string.CaseSensitiveScalarFunction;
import org.elasticsearch.xpack.ql.expression.gen.pipeline.Pipe;
import org.elasticsearch.xpack.ql.expression.gen.script.ScriptTemplate;
import org.elasticsearch.xpack.ql.expression.gen.script.Scripts;
import org.elasticsearch.xpack.ql.session.Configuration;
import org.elasticsearch.xpack.ql.tree.NodeInfo;
import org.elasticsearch.xpack.ql.tree.Source;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;
import static org.elasticsearch.xpack.eql.expression.function.scalar.string.StringContainsFunctionProcessor.doProcess;
import static org.elasticsearch.xpack.ql.expression.TypeResolutions.isStringAndExact;
import static org.elasticsearch.xpack.ql.expression.gen.script.ParamsBuilder.paramsBuilder;

/**
 * EQL specific stringContains function.
 * stringContains(a, b)
 * Returns true if b is a substring of a
 */
public class StringContains extends CaseSensitiveScalarFunction {

    private final Expression string, substring;

    public StringContains(Source source, Expression string, Expression substring, Configuration configuration) {
        super(source, Arrays.asList(string, substring), configuration);
        this.string = string;
        this.substring = substring;
    }

    @Override
    protected TypeResolution resolveType() {
        if (!childrenResolved()) {
            return new TypeResolution("Unresolved children");
        }

        TypeResolution resolution = isStringAndExact(string, sourceText(), Expressions.ParamOrdinal.FIRST);
        if (resolution.unresolved()) {
            return resolution;
        }

        return isStringAndExact(substring, sourceText(), Expressions.ParamOrdinal.SECOND);
    }

    public Expression string() {
        return string;
    }

    public Expression substring() {
        return substring;
    }

    @Override
    protected Pipe makePipe() {
        return new StringContainsFunctionPipe(source(), this,
                Expressions.pipe(string), Expressions.pipe(substring), isCaseSensitive());
    }

    @Override
    public boolean foldable() {
        return string.foldable() && substring.foldable();
    }

    @Override
    public Object fold() {
        return doProcess(string.fold(), substring.fold(), isCaseSensitive());
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, StringContains::new, string, substring, eqlConfiguration());
    }

    @Override
    public ScriptTemplate asScript() {
        return asScriptFrom(asScript(string), asScript(substring));
    }

    protected ScriptTemplate asScriptFrom(ScriptTemplate stringScript, ScriptTemplate substringScript) {
        return new ScriptTemplate(format(Locale.ROOT, formatTemplate("{eql}.%s(%s,%s,%s)"),
                "stringContains",
                stringScript.template(),
                substringScript.template(),
                "{}"),
                paramsBuilder()
                    .script(stringScript.params())
                    .script(substringScript.params())
                    .variable(isCaseSensitive())
                    .build(), dataType());
    }

    @Override
    public ScriptTemplate scriptWithField(FieldAttribute field) {
        return new ScriptTemplate(processScript(Scripts.DOC_VALUE),
                paramsBuilder().variable(field.exactAttribute().name()).build(),
                dataType());
    }

    @Override
    public DataType dataType() {
        return DataTypes.BOOLEAN;
    }

    @Override
    public Expression replaceChildren(List<Expression> newChildren) {
        if (newChildren.size() != 2) {
            throw new IllegalArgumentException("expected [2] children but received [" + newChildren.size() + "]");
        }

        return new StringContains(source(), newChildren.get(0), newChildren.get(1), eqlConfiguration());
    }

    public EqlConfiguration eqlConfiguration() {
        return (EqlConfiguration) configuration();
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }
}
