package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.jooaogui.bootleap.scan.Import;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * Spring Boot 4 uses Jackson 3, whose packages start with {@code tools.jackson} instead of
 * {@code com.fasterxml.jackson}. Only the annotations kept their old package.
 */
public final class Jackson3Rule implements Rule {

    static final String ID = "jackson-3";

    private static final String JACKSON_2 = "com.fasterxml.jackson";
    private static final String ANNOTATIONS = "com.fasterxml.jackson.annotation";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path source : project.sources()) {
            List<Import> jackson2 = Import.read(source).stream()
                    .filter(i -> i.isIn(JACKSON_2) && !i.isIn(ANNOTATIONS))
                    .toList();
            if (!jackson2.isEmpty()) {
                findings.add(jackson2Imports(project, source, jackson2));
            }
        }
        return findings;
    }

    private static Finding jackson2Imports(ScannedProject project, Path source, List<Import> imports) {
        String names = imports.get(0).name() + (imports.size() > 1 ? " and " + (imports.size() - 1) + " more" : "");
        return new Finding(
                ID,
                project.relativize(source),
                imports.get(0).line(),
                "Uses Jackson 2 (" + names + "). Spring Boot 4 uses Jackson 3, where these packages start with "
                        + "tools.jackson, so this code stops compiling or works with a different mapper than the one Spring configures.",
                "Change the imports to tools.jackson.* (com.fasterxml.jackson.annotation stays as it is) and watch for renamed "
                        + "classes, like JsonProcessingException, which is now JacksonException. To move later, add "
                        + "org.springframework.boot:spring-boot-jackson2 (deprecated) to keep Jackson 2 for now.");
    }
}
