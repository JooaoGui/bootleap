package io.github.jooaogui.bootleap.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The files found in a project, as seen by the rules.
 */
public record ScannedProject(Path root, List<Path> buildFiles, List<Path> sources) {

    public Optional<Path> gradleWrapperProperties() {
        return buildFiles.stream()
                .filter(p -> p.getFileName().toString().equals("gradle-wrapper.properties"))
                .findFirst();
    }

    public Path relativize(Path file) {
        return root.relativize(file);
    }
}
