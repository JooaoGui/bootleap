package io.github.jooaogui.bootleap.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jooaogui.bootleap.rule.Finding;

class TextReportTest {

    @Test
    void saysWhenNothingWasFound() {
        assertThat(print(List.of())).isEqualTo("No known Spring Boot 4 problems found.\n");
    }

    @Test
    void printsEachFindingWithItsFix() {
        String report = print(List.of(
                new Finding("demo", Path.of("A.java"), 3, "Problem A.", "Fix A.")));

        assertThat(report).isEqualTo(String.join("\n",
                "1 thing(s) to fix before Spring Boot 4:",
                "",
                "[demo] A.java:3",
                "  Problem A.",
                "  Fix: Fix A.",
                "",
                ""));
    }

    @Test
    void doesNotRepeatTheSameFix() {
        String report = print(List.of(
                new Finding("demo", Path.of("A.java"), 3, "Problem A.", "Shared fix."),
                new Finding("demo", Path.of("B.java"), 5, "Problem B.", "Shared fix."),
                new Finding("other", Path.of("C.java"), 1, "Problem C.", "Other fix.")));

        assertThat(report)
                .containsOnlyOnce("Shared fix.")
                .contains("[demo] B.java:5\n  Problem B.\n  Fix: same as above.")
                .contains("Fix: Other fix.");
    }

    private static String print(List<Finding> findings) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new TextReport().print(findings, out);
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
