Small projects used by the Action test workflow (`.github/workflows/action-test.yml`).

- `upgrading/` asks for Spring Boot 4 and has known problems, so the action must report them.
- `not-upgrading/` is still on Spring Boot 3.5, so the action must stay quiet.
