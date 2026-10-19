package com.orange.igr.easydebug.starter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdsTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN = "00f067aa0ba902b7";
    private static final String HEADER = "00-" + TRACE + "-" + SPAN + "-01";

    @Test
    void acceptsVersion00WithUpperCaseHex() {
        assertThat(TraceIds.w3c("00-" + TRACE.toUpperCase() + "-" + SPAN.toUpperCase() + "-0A"))
                .isEqualTo(TRACE);
        assertThat(TraceIds.w3c("00-" + TRACE + "-" + SPAN + "-01 ")).isEqualTo(TRACE);
        assertThat(TraceIds.from(Map.of("TraceParent", "  " + HEADER + "  "), Map.of()))
                .isEqualTo(TRACE);
    }

    @Test
    void rejectsMalformedAndUnsupportedHeaders() {
        assertThat(TraceIds.w3c(null)).isNull();
        assertThat(TraceIds.w3c("")).isNull();
        assertThat(TraceIds.w3c("   ")).isNull();
        assertThat(TraceIds.w3c("not-a-traceparent")).isNull();
        assertThat(TraceIds.w3c("00-" + TRACE + "-" + SPAN)).isNull();
        assertThat(TraceIds.w3c(HEADER + "-extra")).isNull();
        assertThat(TraceIds.w3c("00-" + TRACE + " -" + SPAN + "-01")).isNull();
        assertThat(TraceIds.w3c("00-" + "4bf92f3577b34da6a3ce929d0e0e473" + "-" + SPAN + "-01")).isNull();
        assertThat(TraceIds.w3c("00-" + TRACE + "-00f067aa0ba902b-" + "01")).isNull();
        assertThat(TraceIds.w3c("00-" + TRACE + "-" + SPAN + "-1")).isNull();
        assertThat(TraceIds.w3c("00-" + TRACE + "-" + SPAN + "-zz")).isNull();
        assertThat(TraceIds.w3c("00-gggggggggggggggggggggggggggggggg-" + SPAN + "-01")).isNull();
        assertThat(TraceIds.w3c("ff-" + TRACE + "-" + SPAN + "-01")).isNull();
        assertThat(TraceIds.w3c("01-" + TRACE + "-" + SPAN + "-01")).isNull();
        assertThat(TraceIds.w3c("0-" + TRACE + "-" + SPAN + "-01")).isNull();
    }

    @Test
    void rejectsAllZeroTraceAndSpanIds() {
        String zeros32 = "00000000000000000000000000000000";
        String zeros16 = "0000000000000000";
        assertThat(TraceIds.w3c("00-" + zeros32 + "-" + SPAN + "-01")).isNull();
        assertThat(TraceIds.w3c("00-" + TRACE + "-" + zeros16 + "-00")).isNull();
    }

    @Test
    void invalidHeaderDoesNotFallBackToMdc() {
        assertThat(TraceIds.from(
                Map.of("traceparent", "01-" + TRACE + "-" + SPAN + "-01"),
                Map.of("traceId", TRACE))).isNull();
        assertThat(TraceIds.from(
                Map.of("traceparent", ""),
                Map.of("traceId", TRACE))).isNull();
    }

    @Test
    void mdcIsUsedOnlyWhenTheHeaderIsAbsent() {
        assertThat(TraceIds.from(Map.of(), Map.of("traceId", TRACE.toUpperCase()))).isEqualTo(TRACE);
        assertThat(TraceIds.from(Map.of(), Map.of("TraceId", TRACE))).isEqualTo(TRACE);
        assertThat(TraceIds.from(Map.of(), Map.of("traceId", "00f067aa0ba902b7"))).isNull();
        assertThat(TraceIds.from(Map.of(), Map.of("traceId", "00000000000000000000000000000000"))).isNull();
        assertThat(TraceIds.from(Map.of(), Map.of("traceId", TRACE + " "))).isNull();
        assertThat(TraceIds.from(Map.of(), Map.of())).isNull();
    }
}
