package io.github.jooaogui.bootleap.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * The files found in a project, as seen by the rules.
 */
public record ScannedProject(Path root, List<Path> buildFiles, List<Path> sources) {

    public Path relativize(Path file) {
        return root.relativize(file);
    }
}
