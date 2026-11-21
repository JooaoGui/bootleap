package io.github.jooaogui.bootleap.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One import statement of a Java or Kotlin source file.
 *
 * @param line 1-based line number
 * @param name imported name, e.g. {@code com.example.Foo}, {@code com.example.*} or a static member
 * @param isStatic whether it is a Java static import
 */
public record Import(int line, String name, boolean isStatic) {

    // Java: "import [static] a.b.C;"  Kotlin: "import a.b.C [as D]" (no semicolon).
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?(\\w+(?:\\.\\w+)*(?:\\.\\*)?)");

    /** Reads every import statement of a source file, in order. */
    public static List<Import> read(Path source) {
        List<String> lines = TextFiles.lines(source);
        List<Import> imports = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = IMPORT.matcher(lines.get(i));
            if (m.find()) {
                imports.add(new Import(i + 1, m.group(2), m.group(1) != null));
            }
        }
        return imports;
    }

    /** Whether the imported name lives in the given package or one of its sub-packages. */
    public boolean isIn(String packageName) {
        return name.startsWith(packageName + ".");
    }
}
