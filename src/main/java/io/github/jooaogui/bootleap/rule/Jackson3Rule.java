package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /** Spring Boot 3 Jackson classes and their Spring Boot 4 names (from the official migration guide). */
    private static final Map<String, String> RENAMED_BOOT_CLASSES = Map.of(
            "org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer", "JsonMapperBuilderCustomizer",
            "org.springframework.boot.jackson.JsonComponent", "JacksonComponent",
            "org.springframework.boot.jackson.JsonMixin", "JacksonMixin",
            "org.springframework.boot.jackson.JsonObjectSerializer", "ObjectValueSerializer",
            "org.springframework.boot.jackson.JsonObjectDeserializer", "ObjectValueDeserializer");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path source : project.sources()) {
            List<Import> imports = Import.read(source);
            List<Import> jackson2 = imports.stream()
                    .filter(i -> i.isIn(JACKSON_2) && !i.isIn(ANNOTATIONS))
                    .toList();
            if (!jackson2.isEmpty()) {
                findings.add(jackson2Imports(project, source, jackson2));
            }
            for (Import i : imports) {
                String newName = RENAMED_BOOT_CLASSES.get(i.name());
                if (newName != null) {
                    findings.add(renamedBootClass(project, source, i, newName));
                }
            }
        }
        return findings;
    }

    private static Finding renamedBootClass(ScannedProject project, Path source, Import i, String newName) {
        String oldName = i.name().substring(i.name().lastIndexOf('.') + 1);
        return new Finding(
                ID,
                project.relativize(source),
                i.line(),
                oldName + " was renamed to " + newName + " in Spring Boot 4, so this import no longer compiles.",
                "Use " + newName + " instead and let your IDE pick the new import.");
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
