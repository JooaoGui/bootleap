package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class MovedClassRuleTest {

    @TempDir
    Path root;

    @Test
    void flagsClassesThatMovedToANewModule() throws IOException {
        source("src/test/java/demo/OrderControllerTest.java",
                "package demo;",
                "",
                "import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;");

        assertThat(check()).singleElement().satisfies(f -> {
            assertThat(f.ruleId()).isEqualTo("moved-class");
            assertThat(f.location()).isEqualTo("src/test/java/demo/OrderControllerTest.java:3");
            assertThat(f.problem()).isEqualTo("WebMvcTest moved to org.springframework.boot.webmvc.test.autoconfigure "
                    + "in Spring Boot 4, so this import no longer compiles.");
            assertThat(f.fix()).startsWith("Import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest instead.")
                    .contains("spring-boot-webmvc-test module");
        });
    }

    @Test
    void doesNotAskForADependencyEveryProjectHas() throws IOException {
        source("src/main/java/demo/Bootstrap.java",
                "package demo;",
                "import org.springframework.boot.BootstrapRegistry;");

        assertThat(check()).singleElement()
                .extracting(Finding::fix)
                .isEqualTo("Import org.springframework.boot.bootstrap.BootstrapRegistry instead.");
    }

    @Test
    void acceptsClassesThatStayedWhereTheyWere() throws IOException {
        source("src/main/java/demo/App.java",
                "package demo;",
                "import org.springframework.boot.SpringApplication;",
                "import org.springframework.boot.autoconfigure.SpringBootApplication;");

        assertThat(check()).isEmpty();
    }

    @Test
    void tableMatchesTheOfficialMigrationGuide() {
        var moves = MovedClassRule.load("moved-classes-4.0.tsv");

        assertThat(moves.get("org.springframework.boot.autoconfigure.domain.EntityScan").newClass())
                .isEqualTo("org.springframework.boot.persistence.autoconfigure.EntityScan");
        assertThat(moves.get("org.springframework.boot.test.web.client.TestRestTemplate").newClass())
                .isEqualTo("org.springframework.boot.resttestclient.TestRestTemplate");
        assertThat(moves.get("org.springframework.boot.test.autoconfigure.properties.PropertyMapping").newClass())
                .isEqualTo("org.springframework.boot.test.context.PropertyMapping");
    }

    private List<Finding> check() {
        return new MovedClassRule().check(new ProjectScanner().scan(root));
    }

    private void source(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
