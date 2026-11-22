package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class TestcontainersRuleTest {

    @TempDir
    Path root;

    @Test
    void flagsModulesRenamedInTestcontainers2() throws IOException {
        file("build.gradle",
                "dependencies {",
                "    testImplementation 'org.testcontainers:junit-jupiter'",
                "    testImplementation 'org.testcontainers:postgresql'",
                "}");

        assertThat(check()).satisfiesExactly(
                f -> {
                    assertThat(f.ruleId()).isEqualTo("testcontainers-2");
                    assertThat(f.location()).isEqualTo("build.gradle:2");
                    assertThat(f.fix()).isEqualTo("Use org.testcontainers:testcontainers-junit-jupiter instead.");
                },
                f -> assertThat(f.fix()).isEqualTo("Use org.testcontainers:testcontainers-postgresql instead."));
    }

    @Test
    void flagsTheRemovedDynaliteModule() throws IOException {
        file("pom.xml",
                "<project><dependencies><dependency>",
                "  <groupId>org.testcontainers</groupId>",
                "  <artifactId>dynalite</artifactId>",
                "</dependency></dependencies></project>");

        assertThat(check()).singleElement().extracting(Finding::problem)
                .isEqualTo("Testcontainers 2 removed the dynalite module.");
    }

    @Test
    void acceptsTestcontainers2Names() throws IOException {
        file("build.gradle.kts",
                "dependencies {",
                "    testImplementation(\"org.testcontainers:testcontainers\")",
                "    testImplementation(\"org.testcontainers:testcontainers-postgresql\")",
                "    testImplementation(\"org.springframework.boot:spring-boot-testcontainers\")",
                "}");

        assertThat(check()).isEmpty();
    }

    @Test
    void flagsJUnit4RulesThatHoldContainers() throws IOException {
        file("src/test/java/demo/RepositoryTest.java",
                "package demo;",
                "import org.junit.ClassRule;",
                "import org.junit.Test;",
                "import org.testcontainers.containers.PostgreSQLContainer;");

        assertThat(check()).singleElement().satisfies(f -> {
            assertThat(f.location()).isEqualTo("src/test/java/demo/RepositoryTest.java:2");
            assertThat(f.problem()).startsWith("Testcontainers 2 dropped JUnit 4 support");
        });
    }

    @Test
    void ignoresJUnit4RulesInTestsWithoutContainers() throws IOException {
        file("src/test/java/demo/OldTest.java",
                "package demo;",
                "import org.junit.Rule;",
                "import org.junit.rules.TemporaryFolder;");

        assertThat(check()).isEmpty();
    }

    private List<Finding> check() {
        return new TestcontainersRule().check(new ProjectScanner().scan(root));
    }

    private void file(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
