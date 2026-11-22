package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class MigrationStarterRuleTest {

    @TempDir
    Path root;

    @Test
    void flagsFlywayWithoutTheStarter() throws IOException {
        file("pom.xml",
                "<project>",
                "  <dependencies>",
                "    <dependency>",
                "      <groupId>org.springframework.boot</groupId>",
                "      <artifactId>spring-boot-starter-data-jpa</artifactId>",
                "    </dependency>",
                "    <dependency>",
                "      <groupId>org.flywaydb</groupId>",
                "      <artifactId>flyway-core</artifactId>",
                "    </dependency>",
                "  </dependencies>",
                "</project>");

        assertThat(check()).singleElement().satisfies(f -> {
            assertThat(f.ruleId()).isEqualTo("migration-starter");
            assertThat(f.location()).isEqualTo("pom.xml:9");
            assertThat(f.problem()).contains("migrations stop running at startup");
            assertThat(f.fix()).isEqualTo("Add org.springframework.boot:spring-boot-starter-flyway (it brings flyway-core with it).");
        });
    }

    @Test
    void flagsLiquibaseWithoutTheStarter() throws IOException {
        file("build.gradle",
                "dependencies {",
                "    implementation 'org.liquibase:liquibase-core'",
                "}");

        assertThat(check()).singleElement().extracting(Finding::fix).asString()
                .startsWith("Add org.springframework.boot:spring-boot-starter-liquibase");
    }

    @Test
    void acceptsTheStarterAnywhereInTheRepository() throws IOException {
        file("db/build.gradle",
                "dependencies {",
                "    implementation 'org.flywaydb:flyway-core'",
                "}");
        file("app/build.gradle",
                "dependencies {",
                "    implementation 'org.springframework.boot:spring-boot-starter-flyway'",
                "}");

        assertThat(check()).isEmpty();
    }

    private List<Finding> check() {
        return new MigrationStarterRule().check(new ProjectScanner().scan(root));
    }

    private void file(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
