package io.github.jooaogui.bootleap.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jooaogui.bootleap.scan.ProjectScanner;

class MockBeanRuleTest {

    @TempDir
    Path root;

    @Test
    void flagsMockBeanAndSpyBean() throws IOException {
        source("src/test/java/demo/OrderControllerTest.java",
                "package demo;",
                "",
                "import org.springframework.boot.test.mock.mockito.MockBean;",
                "import org.springframework.boot.test.mock.mockito.SpyBean;");

        assertThat(check()).satisfiesExactly(
                f -> {
                    assertThat(f.ruleId()).isEqualTo("mock-bean");
                    assertThat(f.location()).isEqualTo("src/test/java/demo/OrderControllerTest.java:3");
                    assertThat(f.problem()).startsWith("@MockBean was removed");
                    assertThat(f.fix()).startsWith("Use @MockitoBean from org.springframework.test.context.bean.override.mockito");
                },
                f -> {
                    assertThat(f.location()).isEqualTo("src/test/java/demo/OrderControllerTest.java:4");
                    assertThat(f.fix()).startsWith("Use @MockitoSpyBean");
                });
    }

    @Test
    void flagsTheRestOfTheRemovedPackage() throws IOException {
        source("src/test/java/demo/AllMocksTest.java",
                "package demo;",
                "import org.springframework.boot.test.mock.mockito.MockBeans;",
                "import org.springframework.boot.test.mock.mockito.MockReset;",
                "import org.springframework.boot.test.mock.mockito.*;",
                "import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;");

        assertThat(check()).satisfiesExactly(
                f -> assertThat(f.fix()).startsWith("Use @MockitoBeans"),
                f -> assertThat(f.fix()).startsWith("Import org.springframework.test.context.bean.override.mockito.MockReset"),
                f -> assertThat(f.problem()).contains("removed the whole org.springframework.boot.test.mock.mockito package"),
                f -> assertThat(f.problem()).startsWith("Imports org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener"));
    }

    @Test
    void acceptsTheSpringFrameworkAnnotations() throws IOException {
        source("src/test/java/demo/OrderControllerTest.java",
                "package demo;",
                "import org.springframework.test.context.bean.override.mockito.MockitoBean;",
                "import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;");

        assertThat(check()).isEmpty();
    }

    private List<Finding> check() {
        return new MockBeanRule().check(new ProjectScanner().scan(root));
    }

    private void source(String path, String... lines) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }
}
