# RailReserve

RailReserve is a native Android train ticket reservation application for a B.Tech Semester 3 Object-Oriented Methodology course, developed by a team of three. Features should be demo-ready by **19 October 2026**; the submission deadline is **20 October 2026**.

## Current foundation

Phase 0 preserves the generated Android application. Login, train search, booking, and database implementation are deferred to later phases.

- Existing project directory: `C:\Users\Ritesh PASWAN\AndroidStudioProjects\MyApplication`
- Gradle project name: `My Application`; application module: `app`
- Namespace and application ID: `com.railreserve`
- Launcher Activity: `com.railreserve.MainActivity`
- Layout: `app/src/main/res/layout/activity_main.xml`
- Minimum SDK: 26; compile and target SDK: 37
- Java source/target compatibility: 11
- Android Gradle Plugin: 9.3.3; Gradle wrapper: 9.5.0

## Stack

Use Java for application logic, Android XML Views for UI, and Android SQLite for local persistence. Gradle builds the app; Android Studio handles SDK management, debugging, emulator/device runs, and APK generation. VS Code with Codex edits the same existing project directory. GitHub is the planned collaboration platform. Gradle's existing Kotlin DSL build scripts are build configuration, not application source code.

## Planned architecture

Packages under `app/src/main/java/com/railreserve/`:

| Package | Planned responsibility |
| --- | --- |
| `model` | Encapsulated domain entities and meaningful domain abstractions |
| `database` | SQLite schema, connection helper, and migrations |
| `dao` | Data access interfaces and SQLite implementations |
| `service` | Business rules and use cases using DAO interfaces |
| `ui` | Activities and UI coordination using XML layouts |
| `adapter` | Bind model data to Android list views |
| `util` | Shared validation and narrowly scoped helpers |

The intended dependency flow is UI/adapters → services → DAOs → SQLite, with model objects shared where appropriate. Encapsulation, inheritance, polymorphism, abstraction, and interfaces should arise from meaningful responsibilities. The generated `MainActivity` stays in its original package during Phase 0.

Documentation directories: `docs/architecture/` for design decisions, `docs/uml/` for diagrams, and `docs/presentation/` for demo and presentation material. Empty directories contain `.gitkeep` files.

## Development workflow

1. Open this same project directory in Android Studio and VS Code; do not create a replacement project.
2. Install the SDK components requested by the existing Gradle configuration. Keep each machine's SDK path in ignored `local.properties` and use a compatible Gradle JDK (JDK 21 is available on the inspected machine).
3. Read `AGENTS.md`, make focused changes on feature branches, and coordinate responsibilities across the three team members through GitHub pull requests.
4. Build from PowerShell with `./gradlew.bat :app:assembleDebug`. Run local unit tests with `./gradlew.bat :app:testDebugUnitTest` when relevant. Use Android Studio for emulator/device checks and debugging.
5. Review changes before committing. Exclude build outputs, local IDE settings, credentials, and signing keys. Keep architecture and UML documentation aligned with implemented behavior.

## Setup observations

- At Phase 0 inspection, this directory was not a Git repository. Initialize Git and connect the intended GitHub remote when ready to set up team collaboration.
- The preserved wrapper configuration uses `file:///C:/Users/Ritesh%20PASWAN/Documents/gradle-9.5.0-bin.zip` with a configured SHA-256 checksum. Other machines need that archive or an agreed portable distribution URL for the same version. Do not change Gradle versions merely to resolve this local-path issue.
- Application features and SQLite persistence are not implemented in Phase 0.
- Phase 0 verification: `./gradlew.bat :app:assembleDebug` succeeded with JDK 21 and the installed Android SDK. The sandbox initially blocked writes to `C:\.gradle`; the build succeeded when run with the required filesystem access.
