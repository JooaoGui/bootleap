package io.github.jooaogui.bootleap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class BootleapCommandTest {

    @Test
    void returnsZeroForExistingDirectory(@TempDir Path dir) {
        int exit = new CommandLine(new BootleapCommand()).execute(dir.toString());
        assertThat(exit).isZero();
    }

    @Test
    void returnsTwoForMissingDirectory(@TempDir Path dir) {
        int exit = new CommandLine(new BootleapCommand()).execute(dir.resolve("missing").toString());
        assertThat(exit).isEqualTo(2);
    }
}
