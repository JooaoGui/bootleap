package io.github.jooaogui.bootleap.scan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Walks a project directory and collects the files the rules care about:
 * build files, the Gradle wrapper properties and Java/Kotlin sources.
 */
public final class ProjectScanner {

    private static final Set<String> SKIPPED_DIRS = Set.of(
            ".git", ".gradle", ".idea", ".mvn", "target", "build", "out", "node_modules");

    private static final Set<String> BUILD_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
            "libs.versions.toml", "gradle.properties", "gradle-wrapper.properties");

    public ScannedProject scan(Path root) {
        List<Path> buildFiles = new ArrayList<>();
        List<Path> sources = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(root) && SKIPPED_DIRS.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (BUILD_FILES.contains(name)) {
                        buildFiles.add(file);
                    } else if (name.endsWith(".java") || name.endsWith(".kt")) {
                        sources.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + root, e);
        }
        buildFiles.sort(null);
        sources.sort(null);
        return new ScannedProject(root, List.copyOf(buildFiles), List.copyOf(sources));
    }
}
