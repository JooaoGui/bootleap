package io.github.jooaogui.bootleap.rule;

import java.nio.file.Path;

/**
 * Something in the project that is likely to break on Spring Boot 4.
 *
 * @param ruleId  id of the rule that produced it
 * @param file    file where it was found, relative to the project root
 * @param line    1-based line number, or 0 when it applies to the whole file
 * @param problem what is wrong, in one sentence
 * @param fix     what to change
 */
public record Finding(String ruleId, Path file, int line, String problem, String fix) {

    public String location() {
        String path = file.toString().replace('\\', '/');
        return line > 0 ? path + ":" + line : path;
    }
}
