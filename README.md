# bootleap

Find out what will break before you upgrade to Spring Boot 4.

> Early development. Nothing to download yet.

## Why

Open source support for Spring Boot 3.5 ended in June 2026, so a lot of projects are moving to Boot 4 right now. The upgrade usually fails in layers: you bump the version, CI fails on the Gradle version, you fix that, then it fails on Jackson 3 imports, then on test annotations that moved, and so on. Each round is another CI run.

I looked at 84 open Dependabot PRs that bump Spring Boot from 2.x/3.x to 4.x. 73 of them had failing CI. In the 72 logs I could read, most failures came from a short list of known causes:

| Cause | PRs |
|---|---|
| Gradle older than 8.14 | 23 |
| Jackson 3 (`com.fasterxml.jackson` → `tools.jackson`) | 14 |
| Test classes that moved (`@WebMvcTest`, `TestRestTemplate`, `@MockBean`) | 11 |
| Auto-configuration classes that moved to new modules | 10 |
| Testcontainers 2 artifact renames | 5 |
| Removed or renamed starters (`spring-boot-starter-aop`, ...) | 4 |

Almost all of these can be found by reading the build file and the imports, without running the build.

## What bootleap will do

Read your project (build file and sources) and list everything that is likely to break on Boot 4, all at once, with what to change for each item. It does not run your build and does not change your code.

Planned:

- a command line tool (`java -jar bootleap.jar path/to/project`)
- a GitHub Action that comments on the Dependabot PR that bumps Spring Boot to 4.x

## Status

Work in progress. Rules are being built from the official Spring Boot 4.0 migration guide and from comparing the class names inside the 3.5.x and 4.x jars.

What it already checks:

| Rule | What it finds |
|---|---|
| `gradle-version` | Gradle wrappers older than 8.14, which stop the build before anything compiles |
| `jackson-3` | Jackson 2 imports (`com.fasterxml.jackson.*`, except annotations) and Spring Boot Jackson classes renamed in 4.0, like `Jackson2ObjectMapperBuilderCustomizer` |
| `mock-bean` | `@MockBean`, `@SpyBean` and the rest of `org.springframework.boot.test.mock.mockito`, removed in 4.0 in favor of Spring Framework's `@MockitoBean` and `@MockitoSpyBean` |
| `moved-class` | Imports of the ~770 Spring Boot classes that changed package in 4.x, like `@WebMvcTest`, `TestRestTemplate` and `@EntityScan`, with the new import and the module that now holds the class |

Some classes moved more than once during 4.x (for example `spring-boot-amqp` became `spring-boot-rabbitmq`), so pass the version you are moving to with `--to 4.0`, `--to 4.1` or `--to 4.2` (the default).

### Where the moved classes table comes from

It is not written by hand. [`tools/MovedClasses.java`](tools/MovedClasses.java) reads the list of Spring Boot modules from the BOM of each version, downloads the jars from Maven Central and compares the public classes of 3.5.16 with those of each 4.x release. A class counts as moved when its old name is gone and exactly one class with the same simple name exists in the new version. To rebuild a table:

```
java tools/MovedClasses.java 3.5.16 4.2.0 > src/main/resources/io/github/jooaogui/bootleap/rule/moved-classes-4.2.tsv
```

## License

Apache License 2.0
