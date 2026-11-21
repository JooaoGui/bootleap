package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class Jackson3RuleTest {

    @TempDir
    Path root;

    @Test
    void flagsJackson2Imports() throws IOException {
        source("src/main/java/demo/Json.java",
                "package demo;",
                "",
                "import com.fasterxml.jackson.databind.ObjectMapper;",
                "import com.fasterxml.jackson.databind.SerializationFeature;",
                "import com.fasterxml.jackson.core.JsonProcessingException;");

        assertThat(check()).singleElement().satisfies(f -> {
            assertThat(f.ruleId()).isEqualTo("jackson-3");
            assertThat(f.location()).isEqualTo("src/main/java/demo/Json.java:3");
            assertThat(f.problem()).contains("com.fasterxml.jackson.databind.ObjectMapper and 2 more");
            assertThat(f.fix()).contains("tools.jackson");
        });
    }

    @Test
    void flagsKotlinAndStaticImports() throws IOException {
        source("src/main/kotlin/demo/Json.kt",
                "package demo",
                "import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper");
        source("src/test/java/demo/JsonTest.java",
                "package demo;",
                "import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;");

        assertThat(check()).extracting(Finding::location).containsExactlyInAnyOrder(
                "src/main/kotlin/demo/Json.kt:2",
                "src/test/java/demo/JsonTest.java:2");
    }

    @Test
    void acceptsAnnotationsBecauseTheyKeptTheirPackage() throws IOException {
        source("src/main/java/demo/Order.java",
                "package demo;",
                "import com.fasterxml.jackson.annotation.JsonProperty;",
                "import com.fasterxml.jackson.annotation.*;");

        assertThat(check()).isEmpty();
    }

    @Test
    void acceptsJackson3Imports() throws IOException {
        source("src/main/java/demo/Json.java",
                "package demo;",
                "import tools.jackson.databind.json.JsonMapper;");

        assertThat(check()).isEmpty();
    }

    private List<Finding> check() {
        return new Jackson3Rule().check(new ProjectScanner().scan(root));
    }

    private void source(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
