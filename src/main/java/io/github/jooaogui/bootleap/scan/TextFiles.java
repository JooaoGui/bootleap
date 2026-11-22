package io.github.jooaogui.bootleap.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads project files without caring too much about their encoding. */
public final class TextFiles {

    private TextFiles() {
    }

    /**
     * Returns the lines of a file. Bytes that are not valid UTF-8 (a Latin-1 comment, for example)
     * are replaced instead of failing, because only the ASCII parts matter to the rules.
     */
    public static List<String> lines(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The 1-based line of a character offset inside a text. */
    public static int lineAt(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
