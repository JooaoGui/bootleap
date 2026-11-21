package io.github.jooaogui.bootleap.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportTest {

    @TempDir
    Path dir;

    @Test
    void readsJavaImports() throws IOException {
        Path file = write("Demo.java",
                "package demo;",
                "",
                "import java.util.List;",
                "import static org.assertj.core.api.Assertions.assertThat;",
                "import com.example.*;",
                "",
                "class Demo {}");

        assertThat(Import.read(file)).containsExactly(
                new Import(3, "java.util.List", false),
                new Import(4, "org.assertj.core.api.Assertions.assertThat", true),
                new Import(5, "com.example.*", false));
    }

    @Test
    void readsKotlinImports() throws IOException {
        Path file = write("Demo.kt",
                "package demo",
                "import com.example.Foo",
                "import com.example.Bar as Baz");

        assertThat(Import.read(file)).extracting(Import::name)
                .containsExactly("com.example.Foo", "com.example.Bar");
    }

    @Test
    void knowsItsPackage() {
        Import i = new Import(1, "com.fasterxml.jackson.databind.ObjectMapper", false);

        assertThat(i.isIn("com.fasterxml.jackson")).isTrue();
        assertThat(i.isIn("com.fasterxml.jackson.annotation")).isFalse();
        assertThat(i.isIn("com.fasterxml.jack")).isFalse();
    }

    private Path write(String name, String... lines) throws IOException {
        return Files.writeString(dir.resolve(name), String.join("\n", lines));
    }
}
