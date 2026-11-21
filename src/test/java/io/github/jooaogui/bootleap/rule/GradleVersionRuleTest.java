package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class GradleVersionRuleTest {

    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {"8.7", "8.10.2", "8.12.1", "8.13", "7.6.4"})
    void flagsWrappersOlderThan814(String version) throws IOException {
        wrapper("gradle", version, "bin");

        List<Finding> findings = check();

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.ruleId()).isEqualTo("gradle-version");
            assertThat(f.location()).isEqualTo("gradle/wrapper/gradle-wrapper.properties:3");
            assertThat(f.problem()).contains("is too old").contains("8.14");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.14", "8.14.3", "9.0.0", "9.9.2", "10.0-rc-1"})
    void acceptsSupportedVersions(String version) throws IOException {
        wrapper("gradle", version, "all");

        assertThat(check()).isEmpty();
    }

    @Test
    void checksEveryWrapperInTheRepository() throws IOException {
        wrapper("service-a/gradle", "8.7", "bin");
        wrapper("service-b/gradle", "9.1.0", "bin");
        wrapper("service-c/gradle", "8.12", "bin");

        assertThat(check()).extracting(Finding::location).containsExactly(
                "service-a/gradle/wrapper/gradle-wrapper.properties:3",
                "service-c/gradle/wrapper/gradle-wrapper.properties:3");
    }

    @Test
    void ignoresMavenProjects() throws IOException {
        Files.writeString(root.resolve("pom.xml"), "<project/>");

        assertThat(check()).isEmpty();
    }

    private List<Finding> check() {
        return new GradleVersionRule().check(new ProjectScanner().scan(root));
    }

    private void wrapper(String gradleDir, String version, String type) throws IOException {
        Path file = root.resolve(gradleDir).resolve("wrapper/gradle-wrapper.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n",
                "distributionBase=GRADLE_USER_HOME",
                "distributionPath=wrapper/dists",
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-" + version + "-" + type + ".zip",
                "zipStoreBase=GRADLE_USER_HOME"));
    }
}
