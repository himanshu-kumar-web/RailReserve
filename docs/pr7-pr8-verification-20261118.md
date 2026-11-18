**RailReserve PR #7 and PR #8 verification — 18 November 2026 (IST)**

Verified branch: `fix/pr7-waitlist-display-and-repeated-stops-20261118`, HEAD `0f671a35eba4ff5db45c58948197b740b9d09fac`.

[PR #7](https://github.com/ritesh-paswan/RailReserve/pull/7) is merged as `d58c9ae`; its comparison starts at `736f03d`. [PR #8](https://github.com/ritesh-paswan/RailReserve/pull/8) is open, unmerged, and its remote head matches the local HEAD. Its six-file change covers cancelled waitlist display and repeated-destination matching. The review also covered PR #7's SQLite v8 migration, waitlist allocation, eight classes, route verification, enquiry handling, navigation and layouts.

**Merge recommendation:** update PR #8 with these local repairs before merging. The checked working tree passes the checks below; the currently published PR does not contain the repairs. No commit, push, review comment or merge was performed.

**Build and test results**

| Check actually executed | Result |
| --- | --- |
| Backend `npm.cmd test` | 31 passed; 0 failed/skipped; mocked provider requests |
| Backend `npm.cmd run lint` | Passed all JavaScript syntax checks |
| Android `:app:testDebugUnitTest` | 45 passed; 0 failures/errors/skips |
| Final enabled fixture instrumentation suite | 93 passed; 0 failed; 5 skipped, out of 98 selected tests |
| Provider-disabled fixture instrumentation | 1 passed; covers the capability test skipped in the enabled build |
| Final normal APK instrumentation smoke test | 11 passed; 0 failed/skipped; repeats selected fixture-covered cases on the installed normal build |
| Distinct device tests across final runs | 94 passed; 0 failed; 4 still skipped; 1 additional test deliberately excluded |
| Android `:app:lintDebug` | Passed; 0 errors, 241 warnings, the same warning count as the baseline |
| Debug application and instrumentation APK builds | Passed for enabled fixtures, disabled fixtures and the final normal configuration |
| Final app and test APK installations | `adb install -r` succeeded; app storage retained |
| Installed APK checksum | Matches the final local APK exactly |
| Crash-buffer comparison | No new RailReserve crash reports |

The final normal APK uses `https://railreserve-x8fq.onrender.com`, with the existing debug fare and availability defaults enabled. The temporary localhost URL and disabled capability settings were command-line build properties; no application configuration was changed to retain them. Verification used the installed JDK 17 and the connected 2312DRA50I phone running Android API 36.

The normal build ran `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon`. Fixture builds additionally used `-PrailreserveDebugBackendUrl=http://127.0.0.1:37845` and explicit true/false fare and availability properties. Local fixture servers exist only in the test APK. No RailCore requests were made by this verification and no provider credentials were read or printed.

**Defects found and repaired**

- The Gradle wrapper referenced a missing ZIP under `C:/Users/Lenovo/Downloads`. It now uses the official HTTPS distribution for the same Gradle 9.5.0, retaining its SHA-256 check. No Gradle, plugin, SDK or package version was changed.
- The v7-to-v8 migration discarded custom booking indexes and triggers. A newer SQLite engine also rejected the table rename while an existing trigger referenced the temporarily missing bookings table. The migration now saves and restores dependent schema objects and replaces the quote guard after rebuilding the table.
- An orphaned legacy booking could disappear through the migration's inner join without failing the upgrade. The migration now checks the copied row count and performs foreign-key and integrity checks. Unsafe upgrades throw inside SQLiteOpenHelper's transaction and roll back.
- Cancelled seatless waitlists produced a fictitious seat in PDFs and in restored checkout confirmations. Both now retain their WL number and explicitly state that no seat was allocated. Confirmation rendering uses PR #8's existing shared display helper.
- A short landscape schedule screen left only 76.7dp for its list. The bounded header now reserves at least 80dp for populated lists, including their margins. The existing four-screen landscape regression passes.
- One Android parser assertion still expected an exception for missing text with `UNKNOWN` availability. PR #7 intentionally introduced an explicit unknown-status message. The test now verifies that message and still rejects a missing status description for `AVAILABLE`.

Before fixes, the six selected migration/PDF regressions had three failures: lost schema metadata, silent orphan-booking loss and cancelled PDF seat fabrication. The first full device run had two failures: the outdated parser assertion and the landscape viewport. A subsequent targeted run reproduced cancelled confirmation seat fabrication and the landscape failure. All these regressions pass after the repairs. Two newly added JVM route fixtures initially omitted the required train name; those fixture inputs were corrected without changing application parsing.

**Requested behavior verified**

| Behavior | Executed evidence |
| --- | --- |
| v7-to-v8 migration | Populated isolated v7 fixture preserves bookings, receipts, visibility, indexes, views, triggers and ID sequence; corrupt references roll back; an empty table retains its sequence. The phone's real v7 database also upgraded to v8 and was compared with its backup. |
| Confirmed reservations | SQLite DAO tests plus actual checkout screens exercise creation, PNR lookup, seat allocation and release. |
| Waitlist creation and numbering | Sequential WL numbers, duplicate checkout idempotency, seatless creation, cancellation and reopening, across all eight classes. |
| Local waitlist confirmation | Allocates an available seat of the correct class, clears the WL number, rejects repeat confirmation, and refuses confirmation when all six seats are occupied. |
| Cancelled waitlist display | Actual My Bookings and PNR lookup screens show the retained WL number and no invented seat; cancelled confirmation and PDF regressions also pass. |
| Demo payments and refunds | Success, failure, cancellation, retry, transaction rollback, stored receipts, refund exactly once and persistence after reopening. |
| All eight classes | SL, 3A, 2A, 1A, 3E, CC, EC and 2S; quote-priced confirmed checkout and waitlist/refund coverage. |
| Duplicate prevention and recovery | Concurrent DAO submission, repeated taps, stable checkout references, saved-state recovery and actual activity recreation during checkout. The preserved untracked checkout test ran its full eight-class loop. |
| Routing | Repeated destination after boarding, displayed arrival at the later stop, reversed scheduled direction, exact and nearby routes, non-halts and missing schedules. |
| PDF generation | Valid PDF output for confirmed, waiting and cancelled seatless bookings; drawing content retains the WL number and rejects a fabricated seat. |
| Navigation and reliability | Navigation policy, malformed/missing data, HTTP failure/retry, real read timeout, stale-response rejection, class enquiry isolation, small screens, landscape and large font checks. |

**Phone data preservation**

The pre-install database was version 7. The final database is version 8, `quick_check` is `ok`, and `foreign_key_check` returns no rows. Every original column of every original row in trains, journeys, seats, bookings, demo payments, simulation scenarios and fare snapshots was compared after installation and tests. No original row was changed or lost.

There are still exactly six bookings, six payment records and five fare snapshots. Fixture navigation added ten fictional scenarios and sixty demo inventory seats; those additions were left in place. No test reservation was added to the phone's real bookings table. Reservations used for payment/refund tests were in isolated UUID-named cache databases.

Private backup copies, comparison JSON, APK checksums and raw logs are in ignored `app/build/pr78-verification/`. They are not staged or included in this report. The final APK SHA-256 is `3643acc3672baf50c49f5d5695455be652fdd3e6ebfd9d009e76c3cc99cc8574`; ADB reported the same checksum for the installed package.

**Preserved pre-existing untracked files**

- `app/src/androidTest/java/com/railreserve/ui/LiveSimulationCheckoutTest.java` remains untracked and unchanged. Its SHA-256 remains `001B1433C9CEE7C2975F327CC210433508125880A283DFE711EC057D0D1FBE56`.
- The unusual root filename is `ersRitesh PASWANAndroidStudioProjectsMyApplication\uF03E `, with U+F03E followed by a trailing space. Its 2,558 bytes contain only Git warnings about LF being replaced by CRLF in Java/XML files. It appears to be accidentally redirected command output, is not required by the application/build, and was not deleted, renamed or modified. Its SHA-256 remains `ddfdb61c22dc7eae8ce99760746949ab435883ed317678fa5208df151015a490`.

**Exact files modified or added by this work**

1. `gradle/wrapper/gradle-wrapper.properties`
2. `app/src/main/java/com/railreserve/database/DatabaseHelper.java`
3. `app/src/main/java/com/railreserve/service/TicketPdfExporter.java`
4. `app/src/main/java/com/railreserve/ui/BookingActivity.java`
5. `app/src/main/java/com/railreserve/ui/BoundedHeaderScrollView.java`
6. `app/src/androidTest/java/com/railreserve/testing/VersionFiveFixture.java`
7. `app/src/androidTest/java/com/railreserve/dao/WaitlistMigrationTest.java` — new
8. `app/src/androidTest/java/com/railreserve/live/LiveDataParserTest.java`
9. `app/src/androidTest/java/com/railreserve/service/TicketPdfExporterTest.java`
10. `app/src/androidTest/java/com/railreserve/ui/WaitlistLifecycleDeviceTest.java` — new
11. `app/src/test/java/com/railreserve/live/TrainScheduleMatcherTest.java`
12. `docs/pr7-pr8-verification-20261118.md` — this new report

**Remaining limits and manual checks**

The four still-skipped tests are the two explicitly gated live-provider checks, the opt-in demo journey that creates a real-database reservation, and `ExistingBookingsUpgradeTest`, whose older pre-update fingerprint capture was unavailable. The independent before/after comparison above verifies preservation of the actual phone data. `LiveFailureDeviceTest#selectedLiveCardReusesCheckoutWithFailureCancellationAndRetry` was additionally excluded because it writes a reservation to the user's database; isolated payment and eight-class checkout tests cover that behavior without doing so.

Only the connected API 36 phone was instrumented. Lint's 241 existing warnings remain. Fixtures do not establish the deployed provider's current health or plan access.

Manual follow-up: enter passenger details with the phone's keyboard, inspect the actual touch/scroll experience, choose a destination in Android's PDF save picker, then open the saved PDF in a viewer. PDF generation itself was automated; the system picker/viewer and physical input were not. A live-provider acceptance journey remains intentionally unrun to avoid credits.

Final normal debug APK: `C:/Users/Ritesh PASWAN/AndroidStudioProjects/MyApplication/app/build/outputs/apk/debug/app-debug.apk`. The matching test APK is `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. RailReserve was left open on its normal home screen.
