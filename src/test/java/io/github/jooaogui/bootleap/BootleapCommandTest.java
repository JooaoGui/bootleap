package io.github.jooaogui.bootleap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.BootVersion;
import io.github.jooaogui.bootleap.scan.ScannedProject;
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

    @Test
    void acceptsTheVersionToMoveTo() {
        assertThat(run(dir.toString(), "--to", "4.0")).isZero();
        assertThat(run(dir.toString(), "--to", "4.1.3")).isZero();
    }

    @Test
    void acceptsTheOutputFormats() throws IOException {
        oldGradleWrapper();

        assertThat(run(dir.toString(), "--format", "markdown")).isEqualTo(1);
        assertThat(run(dir.toString(), "--format", "text")).isEqualTo(1);
        assertThat(run(dir.toString(), "--format", "html")).isEqualTo(2);
    }

    @Test
    void skipsProjectsNotYetOnSpringBoot4WhenAsked() throws IOException {
        oldGradleWrapper();
        Files.writeString(dir.resolve("build.gradle"), "plugins { id 'org.springframework.boot' version '3.5.16' }");

        assertThat(run(dir.toString(), "--only-if-upgrading")).isZero();
        assertThat(run(dir.toString())).isEqualTo(1);
    }

    @Test
    void checksProjectsThatAskForSpringBoot4() throws IOException {
        oldGradleWrapper();
        Files.writeString(dir.resolve("build.gradle"), "plugins { id 'org.springframework.boot' version '4.0.10' }");

        assertThat(run(dir.toString(), "--only-if-upgrading")).isEqualTo(1);
    }

    @Test
    void returnsTwoForAnUnknownVersion() {
        assertThat(run(dir.toString(), "--to", "3.5")).isEqualTo(2);
        assertThat(run(dir.toString(), "--to", "latest")).isEqualTo(2);
    }

    @Test
    void usesThe4xVersionOfTheBuild() {
        var choice = BootleapCommand.choose(project(), found("4.0.10"));

        assertThat(choice.version()).isEqualTo("4.0");
        assertThat(choice.message()).isEqualTo("Checking against Spring Boot 4.0 (found 4.0.10 in pom.xml:5).");
    }

    @Test
    void usesTheNewestVersionWhenTheBuildIsStillOn3x() {
        var choice = BootleapCommand.choose(project(), found("3.5.16"));

        assertThat(choice.version()).isEqualTo("4.2");
        assertThat(choice.message()).startsWith("The build is on Spring Boot 3.5.16 in pom.xml:5, checking against Spring Boot 4.2.");
    }

    @Test
    void usesTheNewestVersionForAVersionItDoesNotKnow() {
        assertThat(BootleapCommand.choose(project(), found("4.9.0")).version()).isEqualTo("4.2");
    }

    @Test
    void usesTheNewestVersionWhenNothingIsFound() {
        var choice = BootleapCommand.choose(project(), Optional.empty());

        assertThat(choice.version()).isEqualTo("4.2");
        assertThat(choice.message()).startsWith("No Spring Boot version found");
    }

    private ScannedProject project() {
        return new ScannedProject(dir, List.of(), List.of());
    }

    private Optional<BootVersion> found(String version) {
        return Optional.of(new BootVersion(version, dir.resolve("pom.xml"), 5));
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
