package io.github.jooaogui.bootleap.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jooaogui.bootleap.rule.Finding;

class JsonReportTest {

    private static final String CONTEXT = "Checking against Spring Boot 4.0 (found 4.0.10 in pom.xml:5).";

    @Test
    void printsAnEmptyListWhenNothingWasFound() {
        assertThat(print(List.of())).isEqualTo(String.join("\n",
                "{",
                "  \"context\": \"Checking against Spring Boot 4.0 (found 4.0.10 in pom.xml:5).\",",
                "  \"count\": 0,",
                "  \"findings\": []",
                "}",
                ""));
    }

    @Test
    void printsOneObjectPerFinding() {
        String report = print(List.of(
                new Finding("artifact", Path.of("build.gradle"), 6, "aop was renamed.", "Use aspectj."),
                new Finding("mock-bean", Path.of("src", "test", "A.java"), 4, "@MockBean was removed.", "Use @MockitoBean.")));

        assertThat(report).isEqualTo(String.join("\n",
                "{",
                "  \"context\": \"Checking against Spring Boot 4.0 (found 4.0.10 in pom.xml:5).\",",
                "  \"count\": 2,",
                "  \"findings\": [",
                "    {\"rule\": \"artifact\", \"file\": \"build.gradle\", \"line\": 6, "
                        + "\"problem\": \"aop was renamed.\", \"fix\": \"Use aspectj.\"},",
                "    {\"rule\": \"mock-bean\", \"file\": \"src/test/A.java\", \"line\": 4, "
                        + "\"problem\": \"@MockBean was removed.\", \"fix\": \"Use @MockitoBean.\"}",
                "  ]",
                "}",
                ""));
    }

    @Test
    void escapesStrings() {
        assertThat(JsonReport.string("say \"hi\"\\n\tC:\\dir\n" + (char) 1))
                .isEqualTo("\"say \\\"hi\\\"\\\\n\\tC:\\\\dir\\n\\u0001\"");
    }

    private static String print(List<Finding> findings) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new JsonReport().print(CONTEXT, findings, out);
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
