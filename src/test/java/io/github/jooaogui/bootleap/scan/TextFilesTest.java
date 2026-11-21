package io.github.jooaogui.bootleap.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextFilesTest {

    @TempDir
    Path dir;

    @Test
    void readsLatin1FilesWithoutFailing() throws IOException {
        Path file = dir.resolve("Pedido.java");
        Files.write(file, "// Configuração do pedido\r\nimport com.example.Foo;\n".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(TextFiles.lines(file))
                .hasSize(2)
                .last().isEqualTo("import com.example.Foo;");
    }
}
