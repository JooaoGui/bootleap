package io.github.jooaogui.bootleap.report;

import java.io.PrintStream;
import java.util.List;

import io.github.jooaogui.bootleap.rule.Finding;

/**
 * JSON for scripts and other tools. Written by hand to keep the jar free of a JSON library.
 *
 * <pre>
 * {
 *   "context": "Checking against Spring Boot 4.0 (found 4.0.10 in pom.xml:5).",
 *   "count": 1,
 *   "findings": [
 *     {"rule": "mock-bean", "file": "src/test/java/A.java", "line": 4, "problem": "...", "fix": "..."}
 *   ]
 * }
 * </pre>
 *
 * A line of 0 means the finding is about the whole file.
 */
public final class JsonReport implements Report {

    @Override
    public void print(String context, List<Finding> findings, PrintStream out) {
        out.println("{");
        out.println("  \"context\": " + string(context) + ",");
        out.println("  \"count\": " + findings.size() + ",");
        if (findings.isEmpty()) {
            out.println("  \"findings\": []");
        } else {
            out.println("  \"findings\": [");
            for (int i = 0; i < findings.size(); i++) {
                Finding f = findings.get(i);
                out.println("    {"
                        + "\"rule\": " + string(f.ruleId())
                        + ", \"file\": " + string(f.file().toString().replace('\\', '/'))
                        + ", \"line\": " + f.line()
                        + ", \"problem\": " + string(f.problem())
                        + ", \"fix\": " + string(f.fix())
                        + "}" + (i < findings.size() - 1 ? "," : ""));
            }
            out.println("  ]");
        }
        out.println("}");
    }

    /** A JSON string literal, with quotes, backslashes and control characters escaped. */
    static String string(String text) {
        StringBuilder json = new StringBuilder(text.length() + 2).append('"');
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
