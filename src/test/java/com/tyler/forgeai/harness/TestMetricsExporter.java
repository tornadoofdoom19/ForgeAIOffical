package com.tyler.forgeai.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Simple JSON exporter for harness results. Writes a compact array of scenario
 * results to `build/test-results/forgeai-harness-results.json`.
 */
public class TestMetricsExporter {
    public static Path defaultOut() {
        return Path.of("build", "test-results", "forgeai-harness-results.json");
    }

    public static void export(List<PvPTestResult> results) throws IOException {
        Path out = defaultOut();
        Files.createDirectories(out.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"generatedAt\": \"").append(Instant.now().toString()).append('\"');
        sb.append(',');
        sb.append("\"results\": [");
        boolean first = true;
        for (PvPTestResult r : results) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"scenario\": \"").append(escape(r.getScenarioName())).append('\"').append(',');
            sb.append("\"expected\": \"").append(escape(r.getExpectedModule())).append('\"').append(',');
            sb.append("\"selected\": \"").append(escape(r.getModuleSelected())).append('\"').append(',');
            sb.append("\"success\": ").append(r.isSuccess()).append(',');
            sb.append("\"elapsedMs\": ").append(String.format("%.3f", r.getElapsedMillis())).append(',');
            sb.append("\"reason\": \"").append(escape(r.getReason() == null ? "" : r.getReason())).append('\"');
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');

        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
