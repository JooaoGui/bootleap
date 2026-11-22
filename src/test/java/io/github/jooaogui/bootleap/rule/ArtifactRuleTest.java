package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class ArtifactRuleTest {

    @TempDir
    Path root;

    @Test
    void flagsRemovedStartersInMaven() throws IOException {
        file("pom.xml",
                "<project>",
                "  <dependencies>",
                "    <dependency>",
                "      <groupId>org.springframework.boot</groupId>",
                "      <artifactId>spring-boot-starter-aop</artifactId>",
                "    </dependency>",
                "  </dependencies>",
                "</project>");

        assertThat(check()).singleElement().satisfies(f -> {
            assertThat(f.ruleId()).isEqualTo("artifact");
            assertThat(f.location()).isEqualTo("pom.xml:5");
            assertThat(f.fix()).isEqualTo("Use org.springframework.boot:spring-boot-starter-aspectj instead.");
        });
    }

    @Test
    void flagsRemovedStartersInGradle() throws IOException {
        file("app/build.gradle",
                "dependencies {",
                "    implementation 'org.springframework.boot:spring-boot-starter-web'",
                "    implementation 'org.springframework.boot:spring-boot-starter-undertow'",
                "}");

        assertThat(check()).satisfiesExactly(
                f -> {
                    assertThat(f.location()).isEqualTo("app/build.gradle:2");
                    assertThat(f.problem()).startsWith("spring-boot-starter-web is deprecated in Spring Boot 4. It still works");
                    assertThat(f.fix()).isEqualTo("Use org.springframework.boot:spring-boot-starter-webmvc instead.");
                },
                f -> {
                    assertThat(f.location()).isEqualTo("app/build.gradle:3");
                    assertThat(f.problem()).startsWith("Spring Boot 4 dropped Undertow");
                });
    }

    @Test
    void acceptsStartersThatStillExist() throws IOException {
        file("build.gradle.kts",
                "dependencies {",
                "    implementation(\"org.springframework.boot:spring-boot-starter-aspectj\")",
                "}");

        assertThat(check()).isEmpty();
    }

    private List<Finding> check() {
        return new ArtifactRule().check(new ProjectScanner().scan(root));
    }

    private void file(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
