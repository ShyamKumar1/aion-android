# Contributing

AION is a private project. As such there is no public contribution process.
This file is a stub for when the project is open-sourced.

Until then, all work is managed by Mr. Stark, coordinated through the Hermes
agent, and tracked in `.hermes/plans/aion-master.md`.

## Workflow (internal)

1. All changes go through `develop` branch.
2. PRs are self-reviewed by Mr. Stark or the Hermes agent.
3. Every PR must build (`./gradlew assembleDebug`).
4. Every PR must pass unit tests (`./gradlew testDebugUnitTest`).
5. Commit messages follow conventional commits format.
6. `main` always compiles and passes tests.
