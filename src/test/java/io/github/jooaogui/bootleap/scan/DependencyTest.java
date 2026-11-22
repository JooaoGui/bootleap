package io.github.jooaogui.bootleap.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyTest {

    @TempDir
    Path dir;

    @Test
    void readsMavenDependenciesAndAnnotationProcessors() throws IOException {
        Path pom = write("pom.xml",
                "<project>",
                "  <parent>",
                "    <groupId>org.springframework.boot</groupId>",
                "    <artifactId>spring-boot-starter-parent</artifactId>",
                "  </parent>",
                "  <dependencies>",
                "    <dependency>",
                "      <groupId>org.springframework.boot</groupId>",
                "      <artifactId>spring-boot-starter-web</artifactId>",
                "      <exclusions>",
                "        <exclusion>",
                "          <groupId>org.springframework.boot</groupId>",
                "          <artifactId>spring-boot-starter-tomcat</artifactId>",
                "        </exclusion>",
                "      </exclusions>",
                "    </dependency>",
                "  </dependencies>",
                "  <annotationProcessorPaths>",
                "    <path>",
                "      <groupId>org.hibernate.orm</groupId>",
                "      <artifactId>hibernate-jpamodelgen</artifactId>",
                "    </path>",
                "  </annotationProcessorPaths>",
                "</project>");

        assertThat(Dependency.read(pom))
                .extracting(Dependency::line, Dependency::coordinates)
                .containsExactly(
                        tuple(9, "org.springframework.boot:spring-boot-starter-web"),
                        tuple(21, "org.hibernate.orm:hibernate-jpamodelgen"));
    }

    @Test
    void readsGradleBuildFiles() throws IOException {
        Path build = write("build.gradle.kts",
                "plugins { id(\"org.springframework.boot\") version \"3.5.16\" }",
                "dependencies {",
                "    implementation(\"org.springframework.boot:spring-boot-starter-aop\")",
                "    // implementation(\"org.example:commented-out\")",
                "    runtimeOnly('org.flywaydb:flyway-core:10.20.1')",
                "}");

        assertThat(Dependency.read(build))
                .extracting(Dependency::line, Dependency::coordinates)
                .containsExactly(
                        tuple(3, "org.springframework.boot:spring-boot-starter-aop"),
                        tuple(5, "org.flywaydb:flyway-core"));
    }

    @Test
    void readsVersionCatalogs() throws IOException {
        Path catalog = write("libs.versions.toml",
                "[libraries]",
                "boot-web = { module = \"org.springframework.boot:spring-boot-starter-web\" }",
                "liquibase = { group = \"org.liquibase\", name = \"liquibase-core\", version = \"4.31.1\" }");

        assertThat(Dependency.read(catalog))
                .extracting(Dependency::line, Dependency::coordinates)
                .containsExactly(
                        tuple(2, "org.springframework.boot:spring-boot-starter-web"),
                        tuple(3, "org.liquibase:liquibase-core"));
    }

    @Test
    void ignoresOtherFiles() throws IOException {
        Path wrapper = write("gradle-wrapper.properties",
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip");

        assertThat(Dependency.read(wrapper)).isEmpty();
    }

    private Path write(String name, String... lines) throws IOException {
        return Files.writeString(dir.resolve(name), String.join("\n", lines));
    }
}
