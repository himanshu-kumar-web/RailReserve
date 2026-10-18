# RailReserve

RailReserve is a native Android Java train search and reservation demonstration for a B.Tech Semester 3 Object-Oriented Methodology project. It uses XML views and a local SQLite database. Schedules, fares, bookings, and PNRs are fictional; the app is not connected to IRCTC, live railway schedules, or live seat inventory. Phase 2 supports one passenger and one seat per local demo booking, cancellation, and seat release. It does not issue real tickets or process payments.

## Run

Open this existing project in Android Studio, allow Gradle sync, select an emulator or connected Android device, and run the `app` configuration. The app targets Android 37, supports Android 8.0/API 26 and newer, seeds a rolling 15-day schedule on a background executor, and stores reservations locally in SQLite.

Build from PowerShell with ` .\gradlew.bat :app:assembleDebug ` and run unit tests with ` .\gradlew.bat :app:testDebugUnitTest `.

See [Phase 1 contracts](docs/architecture/PHASE1_CONTRACTS.md) for package responsibilities, API signatures, and Intent extras.
See [Phase 2 contracts](docs/architecture/PHASE2_CONTRACTS.md) for booking contracts, migration behavior, and Intent extras.
