package io.github.jooaogui.bootleap.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootVersionTest {

    @TempDir
    Path root;

    @Test
    void readsTheMavenParent() throws IOException {
        file("pom.xml",
                "<project>",
                "  <parent>",
                "    <groupId>org.springframework.boot</groupId>",
                "    <artifactId>spring-boot-starter-parent</artifactId>",
                "    <version>4.0.1</version>",
                "  </parent>",
                "</project>");

        assertThat(detect()).hasValueSatisfying(v -> {
            assertThat(v.version()).isEqualTo("4.0.1");
            assertThat(v.major()).isEqualTo(4);
            assertThat(v.minor()).isZero();
            assertThat(v.line()).isEqualTo(5);
        });
    }

    @Test
    void readsAnImportedBomWhoseVersionIsAProperty() throws IOException {
        file("pom.xml",
                "<project>",
                "  <properties>",
                "    <java.version>21</java.version>",
                "    <spring-boot.version>4.1.3</spring-boot.version>",
                "  </properties>",
                "  <dependencyManagement><dependencies><dependency>",
                "    <groupId>org.springframework.boot</groupId>",
                "    <artifactId>spring-boot-dependencies</artifactId>",
                "    <version>${spring-boot.version}</version>",
                "    <type>pom</type>",
                "    <scope>import</scope>",
                "  </dependency></dependencies></dependencyManagement>",
                "</project>");

        assertThat(detect()).hasValueSatisfying(v -> {
            assertThat(v.version()).isEqualTo("4.1.3");
            assertThat(v.line()).isEqualTo(4);
        });
    }

    @Test
    void ignoresOtherParentsAndUnresolvedProperties() throws IOException {
        file("pom.xml",
                "<project>",
                "  <parent>",
                "    <groupId>com.example</groupId>",
                "    <artifactId>company-parent</artifactId>",
                "    <version>7.2.0</version>",
                "  </parent>",
                "  <dependencyManagement><dependencies><dependency>",
                "    <groupId>org.springframework.boot</groupId>",
                "    <artifactId>spring-boot-dependencies</artifactId>",
                "    <version>${defined.somewhere.else}</version>",
                "  </dependency></dependencies></dependencyManagement>",
                "</project>");

        assertThat(detect()).isEmpty();
    }

    @Test
    void picksTheNewestVersionInTheRepository() throws IOException {
        file("a/pom.xml", parent("3.5.16"));
        file("b/pom.xml", parent("4.0.10"));

        assertThat(detect()).map(BootVersion::version).hasValue("4.0.10");
    }

    private Optional<BootVersion> detect() {
        return BootVersion.detect(new ProjectScanner().scan(root));
    }

    private static String[] parent(String version) {
        return new String[] {"<project><parent>",
                "<groupId>org.springframework.boot</groupId>",
                "<artifactId>spring-boot-starter-parent</artifactId>",
                "<version>" + version + "</version>",
                "</parent></project>"};
    }

    private void file(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
