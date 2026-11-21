package io.github.jooaogui.bootleap.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @TempDir
    Path root;

    @Test
    void findsBuildFilesWrapperAndSources() throws IOException {
        touch("build.gradle.kts");
        touch("gradle/wrapper/gradle-wrapper.properties");
        touch("src/main/java/demo/App.java");
        touch("src/test/kotlin/demo/AppTest.kt");

        ScannedProject project = new ProjectScanner().scan(root);

        assertThat(project.buildFiles()).extracting(project::relativize).extracting(Path::toString)
                .containsExactlyInAnyOrder("build.gradle.kts", path("gradle/wrapper/gradle-wrapper.properties"));
        assertThat(project.sources()).hasSize(2);
        assertThat(project.gradleWrapperProperties()).isPresent();
    }

    @Test
    void skipsBuildOutputAndVcsDirectories() throws IOException {
        touch("pom.xml");
        touch("target/generated-sources/Gen.java");
        touch("build/tmp/Other.java");
        touch(".git/hooks/Hook.java");
        touch("node_modules/x/pom.xml");

        ScannedProject project = new ProjectScanner().scan(root);

        assertThat(project.buildFiles()).hasSize(1);
        assertThat(project.sources()).isEmpty();
    }

    private void touch(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
    }

    private static String path(String unix) {
        return unix.replace('/', java.io.File.separatorChar);
    }
}
