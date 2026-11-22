# bootleap - find what breaks before you upgrade to Spring Boot 4

[![CI](https://github.com/JooaoGui/bootleap/actions/workflows/ci.yml/badge.svg)](https://github.com/JooaoGui/bootleap/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Java 17+](https://img.shields.io/badge/java-17%2B-orange.svg)

bootleap reads a Spring Boot 3 project (build files and sources) and lists everything that is likely to break on Spring Boot 4, all at once, with the file, the line and what to change. It does not run your build and does not touch your code.

```
$ java -jar bootleap.jar my-service
The build is on Spring Boot 3.5.16 in build.gradle:2, checking against Spring Boot 4.2. Use --to to pick another 4.x version.

7 thing(s) to fix before Spring Boot 4:

[gradle-version] gradle/wrapper/gradle-wrapper.properties:3
  Gradle 8.10 is too old. The Spring Boot 4 plugin needs Gradle 8.14 or later, so the build fails before compiling anything.
  Fix: Run ./gradlew wrapper --gradle-version latest (anything from 8.14 up works) and commit the wrapper files.

[artifact] build.gradle:6
  spring-boot-starter-aop was renamed to spring-boot-starter-aspectj in Spring Boot 4, so the old name no longer resolves.
  Fix: Use org.springframework.boot:spring-boot-starter-aspectj instead.

[migration-starter] build.gradle:7
  flyway-core is used without spring-boot-starter-flyway. In Spring Boot 4 the Flyway auto-configuration lives in its own module, so the build still passes but the migrations stop running at startup.
  Fix: Add org.springframework.boot:spring-boot-starter-flyway (it brings flyway-core with it).

[testcontainers-2] build.gradle:8
  Testcontainers 2 (managed by Spring Boot 4) renamed postgresql to testcontainers-postgresql, and the old name does not exist in 2.x.
  Fix: Use org.testcontainers:testcontainers-postgresql instead.

[jackson-3] src/main/java/com/example/orders/OrderEvents.java:3
  Uses Jackson 2 (com.fasterxml.jackson.databind.ObjectMapper). Spring Boot 4 uses Jackson 3, where these packages start with tools.jackson, ...
  Fix: Change the imports to tools.jackson.* (com.fasterxml.jackson.annotation stays as it is) ...

[mock-bean] src/test/java/com/example/orders/OrderControllerTest.java:4
  @MockBean was removed in Spring Boot 4, so this test no longer compiles.
  Fix: Use @MockitoBean from org.springframework.test.context.bean.override.mockito (part of Spring Framework). ...

[moved-class] src/test/java/com/example/orders/OrderControllerTest.java:3
  WebMvcTest moved to org.springframework.boot.webmvc.test.autoconfigure in Spring Boot 4.2, so this import no longer compiles.
  Fix: Import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest instead. It lives in the spring-boot-webmvc-test module now, ...
```

## Table of contents

- [Why](#why)
- [How to use](#how-to-use)
- [Checks](#checks)
- [Where the data comes from](#where-the-data-comes-from)
- [Building from source](#building-from-source)
- [Roadmap](#roadmap)
- [Author](#author)
- [License](#license)

## Why

Open source support for Spring Boot 3.5 ended in June 2026. When you bump the version, the upgrade usually fails in layers: CI stops on the Gradle version, you fix it, then it stops on Jackson 3, then on test classes that moved, and so on. Each layer is another CI run, and the first error hides the rest.

I looked at 84 open Dependabot PRs that bump Spring Boot to 4.x. 73 had failing CI, and in the 72 logs I could read, most failures came from a short list of causes:

| Cause | PRs |
|---|---|
| Gradle older than 8.14 | 23 |
| Jackson 3 (`com.fasterxml.jackson` → `tools.jackson`) | 14 |
| Test classes that moved (`@WebMvcTest`, `TestRestTemplate`, `@MockBean`) | 11 |
| Auto-configuration classes that moved to new modules | 10 |
| Testcontainers 2 artifact renames | 5 |
| Removed or renamed starters (`spring-boot-starter-aop`, ...) | 4 |

All of them can be found by reading the build files and the imports, which is what bootleap does.

## How to use

```
java -jar bootleap.jar [path] [--to VERSION] [--report-only]
```

| Option | What it does |
|---|---|
| `path` | Project directory. Default: the current directory. Multi-module builds are scanned as a whole. |
| `--to VERSION` | Spring Boot version you are moving to: `4.0`, `4.1` or `4.2`. Some classes moved again during 4.x, so the advice depends on it. Without it, bootleap reads the version from your build (Maven parent or BOM, Gradle plugin, `gradle.properties` or version catalog): on a Dependabot branch that is already the new 4.x version. If the build is still on 3.x, it checks against the newest one. |
| `--report-only` | Always exit with 0, even when something is found. |

Exit codes: `0` nothing found, `1` something found, `2` bad input. So it can fail a CI step on its own.

There is no release to download yet. Until then, [build it from source](#building-from-source).

## Checks

| ID | What it finds | Breaks the build? |
|---|---|---|
| `gradle-version` | Gradle wrappers older than 8.14 | Yes, before compiling anything |
| `artifact` | Starters removed in 4.0 (`aop`, `undertow`, `pulsar-reactive`), starters deprecated in favor of new names (`web` → `webmvc`, `web-services`, the `oauth2` ones) and `hibernate-jpamodelgen`, now `hibernate-processor` | Yes for the removed ones |
| `migration-starter` | `flyway-core` or `liquibase-core` without their starter | No. The migrations just stop running at startup |
| `testcontainers-2` | The 61 Testcontainers modules renamed to `testcontainers-*`, the removed `dynalite` module, and JUnit 4 `@Rule`/`@ClassRule` containers | Yes |
| `jackson-3` | Jackson 2 imports (except annotations) and Spring Boot Jackson classes renamed in 4.0, like `Jackson2ObjectMapperBuilderCustomizer` | Yes |
| `mock-bean` | `@MockBean`, `@SpyBean` and the rest of `org.springframework.boot.test.mock.mockito` | Yes |
| `moved-class` | Imports of the ~770 Spring Boot classes that changed package, with the new import and the module that now holds the class | Yes |

## Where the data comes from

Nothing is guessed. Each rule comes from the official [migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) or from the published artifacts themselves:

- **Moved classes:** [`tools/MovedClasses.java`](tools/MovedClasses.java) reads the module list from the Spring Boot BOM, downloads the jars from Maven Central and compares the public classes of 3.5.16 with each 4.x release. A class counts as moved when its old name is gone and exactly one class with the same simple name exists in the new version.
- **Starters and artifacts:** the Spring Boot BOMs of 3.5.16 and 4.0.10, and the "deprecated in favor of" note in each 4.0.10 starter pom.
- **Testcontainers:** the Testcontainers BOMs of 1.21.4 and 2.0.5, the versions that Spring Boot 3.5 and 4 manage.

To rebuild a moved classes table:

```
java tools/MovedClasses.java 3.5.16 4.2.0 > src/main/resources/io/github/jooaogui/bootleap/rule/moved-classes-4.2.tsv
```

## Building from source

You need Java 17 or later. The Maven wrapper is included.

```
./mvnw verify
java -jar target/bootleap.jar path/to/your/project
```

Found a false positive, or something that broke on your upgrade and bootleap missed? Please [open an issue](https://github.com/JooaoGui/bootleap/issues) with the error message.

## Roadmap

- [ ] Downloadable release
- [ ] GitHub Action that comments on the Dependabot PR that bumps Spring Boot to 4.x
- [ ] Markdown and JSON output
- [x] Read the target version from the build file, so `--to` is not needed

## Author

Made by **João Guilherme Andrade**, Java developer.

- LinkedIn: [joao-guilherme-andrade-santos](https://www.linkedin.com/in/joao-guilherme-andrade-santos)
- GitHub: [JooaoGui](https://github.com/JooaoGui)

## License

[Apache License 2.0](LICENSE)
