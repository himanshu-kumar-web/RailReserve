# RailReserve

RailReserve is a native Android Java train search demonstration for a B.Tech Semester 3 Object-Oriented Methodology project. It uses XML views and a local SQLite database. All schedules and fares are fictional; the app is not connected to IRCTC, live railway schedules, or live seat inventory. Phase 1 supports searching only; booking and payments are not implemented.

## Run

Open this existing project in Android Studio, allow Gradle sync, select an emulator or connected Android device, and run the `app` configuration. The app targets Android 37, supports Android 8.0/API 26 and newer, and seeds a rolling 15-day schedule on a background executor. Search results include locally calculated seat availability and demo fares.

Build from PowerShell with ` .\gradlew.bat :app:assembleDebug ` and run unit tests with ` .\gradlew.bat :app:testDebugUnitTest `.

See [Phase 1 contracts](docs/architecture/PHASE1_CONTRACTS.md) for package responsibilities, API signatures, and Intent extras.
