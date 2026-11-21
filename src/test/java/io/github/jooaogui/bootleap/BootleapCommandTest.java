package io.github.jooaogui.bootleap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class BootleapCommandTest {

    @TempDir
    Path dir;

    @Test
    void returnsZeroWhenNothingIsFound() {
        assertThat(run(dir.toString())).isZero();
    }

    @Test
    void returnsOneWhenSomethingIsFound() throws IOException {
        oldGradleWrapper();

        assertThat(run(dir.toString())).isEqualTo(1);
    }

    @Test
    void canReportWithoutFailing() throws IOException {
        oldGradleWrapper();

        assertThat(run(dir.toString(), "--report-only")).isZero();
    }

    @Test
    void returnsTwoForMissingDirectory() {
        assertThat(run(dir.resolve("missing").toString())).isEqualTo(2);
    }

    private static int run(String... args) {
        return new CommandLine(new BootleapCommand()).execute(args);
    }

    private void oldGradleWrapper() throws IOException {
        Path file = dir.resolve("gradle/wrapper/gradle-wrapper.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip\n");
    }
}
