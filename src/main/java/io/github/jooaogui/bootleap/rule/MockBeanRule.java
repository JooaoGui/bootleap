package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.jooaogui.bootleap.scan.Import;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * Spring Boot 4 removed {@code @MockBean}, {@code @SpyBean} and the rest of
 * {@code org.springframework.boot.test.mock.mockito}. Spring Framework has its own
 * {@code @MockitoBean} and {@code @MockitoSpyBean} since 6.2.
 *
 * <p>Names checked against spring-boot-test 3.5.16 and spring-test 7.0.11.
 */
public final class MockBeanRule implements Rule {

    static final String ID = "mock-bean";

    private static final String REMOVED_PACKAGE = "org.springframework.boot.test.mock.mockito";
    private static final String NEW_PACKAGE = "org.springframework.test.context.bean.override.mockito";

    private static final Map<String, String> ANNOTATIONS = Map.of(
            "MockBean", "MockitoBean",
            "SpyBean", "MockitoSpyBean",
            "MockBeans", "MockitoBeans",
            "SpyBeans", "MockitoSpyBeans");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path source : project.sources()) {
            for (Import i : Import.read(source)) {
                if (i.isIn(REMOVED_PACKAGE)) {
                    findings.add(finding(project, source, i));
                }
            }
        }
        return findings;
    }

    private static Finding finding(ScannedProject project, Path source, Import i) {
        Path file = project.relativize(source);
        String oldName = i.name().substring(REMOVED_PACKAGE.length() + 1);
        String newName = ANNOTATIONS.get(oldName);
        if (newName != null) {
            return new Finding(ID, file, i.line(),
                    "@" + oldName + " was removed in Spring Boot 4, so this test no longer compiles.",
                    "Use @" + newName + " from " + NEW_PACKAGE + " (part of Spring Framework). It only works on "
                            + "fields of test classes, so mocks declared in a @Configuration class have to move into the test.");
        }
        if (oldName.equals("MockReset")) {
            return new Finding(ID, file, i.line(),
                    "MockReset moved out of Spring Boot in version 4, so this import no longer compiles.",
                    "Import " + NEW_PACKAGE + ".MockReset instead. It works the same way.");
        }
        return new Finding(ID, file, i.line(),
                "Imports " + i.name() + ", but Spring Boot 4 removed the whole " + REMOVED_PACKAGE + " package.",
                "Switch to the Mockito support in " + NEW_PACKAGE + " (@MockitoBean, @MockitoSpyBean, MockReset). "
                        + "Its test listeners are registered on their own, so imports of the old listeners can go.");
    }
}
