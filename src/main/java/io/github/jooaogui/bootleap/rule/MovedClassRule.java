package io.github.jooaogui.bootleap.rule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import io.github.jooaogui.bootleap.scan.Import;
import io.github.jooaogui.bootleap.scan.ScannedProject;
import io.github.jooaogui.bootleap.scan.TextFiles;

/**
 * Spring Boot 4 split its code into many small modules, and hundreds of classes changed package on the way.
 * The table of moves is generated from the real jars by {@code tools/MovedClasses.java}.
 */
public final class MovedClassRule implements Rule {

    static final String ID = "moved-class";

    /** Modules every Spring Boot project already has, so there is no dependency to add. */
    private static final Set<String> ALWAYS_PRESENT = Set.of(
            "spring-boot", "spring-boot-autoconfigure", "spring-boot-test", "spring-boot-test-autoconfigure");

    record Move(String newClass, String artifact) {
    }

    private final Map<String, Move> moves;
    private final Map<String, List<Move>> movedFromPackage;

    public MovedClassRule() {
        this.moves = load("moved-classes-4.0.tsv");
        this.movedFromPackage = new TreeMap<>();
        new TreeMap<>(moves).forEach((oldClass, move) -> movedFromPackage
                .computeIfAbsent(oldClass.substring(0, oldClass.lastIndexOf('.')), k -> new ArrayList<>())
                .add(move));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path source : project.sources()) {
            String code = null;
            for (Import i : Import.read(source)) {
                if (!i.isStatic() && i.name().endsWith(".*")) {
                    String pkg = i.name().substring(0, i.name().length() - 2);
                    List<Move> candidates = movedFromPackage.getOrDefault(pkg, List.of());
                    if (candidates.isEmpty()) {
                        continue;
                    }
                    // A wildcard only breaks the classes the file really uses, so look for their names.
                    String text = code == null ? (code = String.join("\n", TextFiles.lines(source))) : code;
                    List<Move> moved = candidates.stream()
                            .filter(m -> Pattern.compile("\\b" + simpleName(m.newClass()) + "\\b").matcher(text).find())
                            .toList();
                    if (!moved.isEmpty()) {
                        findings.add(wildcardFinding(project, source, i, pkg, moved));
                    }
                    continue;
                }
                Move move = moves.get(importedClass(i));
                if (move != null) {
                    findings.add(finding(project, source, i, move));
                }
            }
        }
        return findings;
    }

    /** The class an import refers to: {@code a.B} for {@code import a.B}, {@code import static a.B.c} and {@code import static a.B.*}. */
    private String importedClass(Import i) {
        String name = i.name().endsWith(".*") ? i.name().substring(0, i.name().length() - 2) : i.name();
        if (i.isStatic() && !moves.containsKey(name) && name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    private static Finding wildcardFinding(ScannedProject project, Path source, Import i, String pkg, List<Move> moved) {
        List<String> names = moved.stream().map(m -> simpleName(m.newClass())).toList();
        List<String> newClasses = moved.stream().map(Move::newClass).toList();
        return new Finding(
                ID,
                project.relativize(source),
                i.line(),
                String.join(", ", names) + " moved out of " + pkg + " in Spring Boot 4, "
                        + "so this wildcard import no longer finds " + (moved.size() == 1 ? "it." : "them."),
                "Import " + String.join(", ", newClasses) + ".");
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static Finding finding(ScannedProject project, Path source, Import i, Move move) {
        String simpleName = move.newClass().substring(move.newClass().lastIndexOf('.') + 1);
        String newPackage = move.newClass().substring(0, move.newClass().lastIndexOf('.'));
        String fix = "Import " + move.newClass() + " instead.";
        if (!ALWAYS_PRESENT.contains(move.artifact())) {
            fix += " It lives in the " + move.artifact() + " module now, so add that dependency "
                    + "(or the matching starter) if the build does not have it yet.";
        }
        return new Finding(
                ID,
                project.relativize(source),
                i.line(),
                simpleName + " moved to " + newPackage + " in Spring Boot 4, so this import no longer compiles.",
                fix);
    }

    static Map<String, Move> load(String table) {
        Map<String, Move> moves = new HashMap<>();
        try (InputStream in = MovedClassRule.class.getResourceAsStream(table)) {
            if (in == null) {
                throw new IllegalStateException("Missing table " + table);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t");
                moves.put(columns[0], new Move(columns[1], columns[2]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return moves;
    }
}
