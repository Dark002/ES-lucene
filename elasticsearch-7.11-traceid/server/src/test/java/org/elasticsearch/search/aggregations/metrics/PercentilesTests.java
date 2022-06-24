/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.aggregations.metrics;

import org.elasticsearch.common.xcontent.XContentParseException;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.search.aggregations.BaseAggregationTestCase;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;

public class PercentilesTests extends BaseAggregationTestCase<PercentilesAggregationBuilder> {

    @Override
    protected PercentilesAggregationBuilder createTestAggregatorBuilder() {
        PercentilesAggregationBuilder factory = new PercentilesAggregationBuilder(randomAlphaOfLengthBetween(1, 20));
        if (randomBoolean()) {
            factory.keyed(randomBoolean());
        }
        if (randomBoolean()) {
            int percentsSize = randomIntBetween(1, 20);
            double[] percents = new double[percentsSize];
            for (int i = 0; i < percentsSize; i++) {
                percents[i] = randomDouble() * 100;
            }
            factory.percentiles(percents);
        }
        if (randomBoolean()) {
            factory.numberOfSignificantValueDigits(randomIntBetween(0, 5));
        } else if (randomBoolean()) {
            factory.compression(randomIntBetween(1, 50000));
        }
        String field = randomNumericField();
        randomFieldOrScript(factory, field);
        if (randomBoolean()) {
            factory.missing("MISSING");
        }
        if (randomBoolean()) {
            factory.format("###.00");
        }
        return factory;
    }

    public void testNullOrEmptyPercentilesThrows() throws IOException {
        PercentilesAggregationBuilder builder = new PercentilesAggregationBuilder("testAgg");
        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class, () -> builder.percentiles(null));
        assertEquals("[percents] must not be null: [testAgg]", ex.getMessage());

        ex = expectThrows(IllegalArgumentException.class, () -> builder.percentiles(new double[0]));
        assertEquals("[percents] must not be empty: [testAgg]", ex.getMessage());
    }

    public void testOutOfRangePercentilesThrows() throws IOException {
        PercentilesAggregationBuilder builder = new PercentilesAggregationBuilder("testAgg");
        IllegalArgumentException ex = expectThrows(IllegalArgumentException.class, () -> builder.percentiles(-0.4));
        assertEquals("percent must be in [0,100], got [-0.4]: [testAgg]", ex.getMessage());

        ex = expectThrows(IllegalArgumentException.class, () -> builder.percentiles(104));
        assertEquals("percent must be in [0,100], got [104.0]: [testAgg]", ex.getMessage());
    }

    public void testDuplicatePercentilesDeprecated() throws IOException {
        PercentilesAggregationBuilder builder = new PercentilesAggregationBuilder("testAgg");

        // throws in 8.x, deprecated in 7.x
        builder.percentiles(5, 42, 10, 99, 42, 87);

        assertWarnings("percent [42.0] has been specified more than once, percents must be unique");

        builder.percentiles(5, 42, 42, 43, 43, 87);
        assertWarnings(
            "percent [42.0] has been specified more than once, percents must be unique",
            "percent [43.0] has been specified more than once, percents must be unique"
        );
    }

    public void testExceptionMultipleMethods() throws IOException {
        final String illegalAgg = "{\n" +
            "       \"percentiles\": {\n" +
            "           \"field\": \"load_time\",\n" +
            "           \"percents\": [99],\n" +
            "           \"tdigest\": {\n" +
            "               \"compression\": 200\n" +
            "           },\n" +
            "           \"hdr\": {\n" +
            "               \"number_of_significant_value_digits\": 3\n" +
            "           }\n" +
            "   }\n" +
            "}";
        XContentParser parser = createParser(JsonXContent.jsonXContent, illegalAgg);
        assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
        assertEquals(XContentParser.Token.FIELD_NAME, parser.nextToken());
        XContentParseException e = expectThrows(XContentParseException.class,
                () -> PercentilesAggregationBuilder.parse("myPercentiles", parser));
        assertThat(e.getMessage(), containsString("[percentiles] failed to parse field [hdr]"));
    }
}
