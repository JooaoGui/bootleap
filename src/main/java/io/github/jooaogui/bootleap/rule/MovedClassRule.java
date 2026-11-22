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

import io.github.jooaogui.bootleap.scan.Import;
import io.github.jooaogui.bootleap.scan.ScannedProject;

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

    public MovedClassRule() {
        this.moves = load("moved-classes-4.0.tsv");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path source : project.sources()) {
            for (Import i : Import.read(source)) {
                Move move = moves.get(i.name());
                if (move != null) {
                    findings.add(finding(project, source, i, move));
                }
            }
        }
        return findings;
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
