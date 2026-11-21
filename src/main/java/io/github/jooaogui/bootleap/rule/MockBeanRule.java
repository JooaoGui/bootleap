package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.jooaogui.bootleap.scan.Import;
import io.github.jooaogui.bootleap.scan.ScannedProject;

/**
 * Spring Boot 4 removed {@code @MockBean} and {@code @SpyBean}. Spring Framework has its own
 * {@code @MockitoBean} and {@code @MockitoSpyBean} since 6.2.
 */
public final class MockBeanRule implements Rule {

    static final String ID = "mock-bean";

    private static final String REMOVED_PACKAGE = "org.springframework.boot.test.mock.mockito";
    private static final String NEW_PACKAGE = "org.springframework.test.context.bean.override.mockito";

    private static final Map<String, String> REPLACEMENTS = Map.of(
            "MockBean", "MockitoBean",
            "SpyBean", "MockitoSpyBean");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(ScannedProject project) {
        List<Finding> findings = new ArrayList<>();
        for (Path source : project.sources()) {
            for (Import i : Import.read(source)) {
                if (!i.isIn(REMOVED_PACKAGE)) {
                    continue;
                }
                String oldName = i.name().substring(REMOVED_PACKAGE.length() + 1);
                String newName = REPLACEMENTS.get(oldName);
                if (newName != null) {
                    findings.add(new Finding(
                            ID,
                            project.relativize(source),
                            i.line(),
                            "@" + oldName + " was removed in Spring Boot 4, so this test no longer compiles.",
                            "Use @" + newName + " from " + NEW_PACKAGE + " (part of Spring Framework). It only works on "
                                    + "fields of test classes, so mocks declared in a @Configuration class have to move into the test."));
                }
            }
        }
        return findings;
    }
}
