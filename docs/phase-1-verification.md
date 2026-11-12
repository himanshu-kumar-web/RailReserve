# Phase 1 build recovery and device verification

Investigation date: 12 November 2026. Evaluation: 16 November 2026.

## Findings

- The initial working tree already modified `README.md`, `app/build.gradle.kts`, and `docs/live-mode-setup.md` to use the deployed HTTPS backend by default. These changes were preserved.
- The wrapper had no uncommitted diff at inspection time. Its tracked distribution is the existing local Gradle 9.5.0 ZIP with a checksum. The wrapper, AGP 9.3.3, package name, SDK levels, and Java 11 source/target compatibility were preserved.
- `gradle/gradle-daemon-jvm.properties` requests a Java 17 daemon and contains Foojay download URLs. This daemon requirement is separate from Java source compatibility. Java 21 is the shell default, but a complete Temurin 17.0.20.1 installation is already cached locally. Selecting that JDK explicitly and disabling auto-download recovered the Android build; no toolchain upgrade was needed.
- Before this investigation's LAN build, generated debug `BuildConfig.BACKEND_BASE_URL` was `https://railreserve-x8fq.onrender.com`. It was not the emulator URL. Generated sources do not prove what is installed on a disconnected phone.
- `railreserveDebugBackendUrl` is correctly consumed by the debug BuildConfig field. The build below sets it to this computer's observed Wi-Fi address, `http://10.143.112.110:3000`. Recheck `ipconfig` after changing networks.
- Initially no USB device or local AVD was available. The user subsequently connected their Android 16 phone; the device checks below were then executed.
- The LAN health endpoint refused connections at investigation time. A LAN APK requires a running backend; changing the APK URL alone does not provide that service.
- A single deployed `/health` request returned HTTP 200 / `success:true` after 27,705 ms. One deployed `/api/stations/search?q=Prayagraj` request returned HTTP 200 / `success:true` / 10 stations after 3,854 ms. Only status, timing, station count, and safe error codes were logged. No provider smoke sweep was run.
- Health does not call the provider. A healthy process therefore does not prove station search works. The station envelope is `{success:true,data:{stations:[{code,name,city}]},meta:{requestId}}`; Android unwraps `data` and then parses `stations`.
- The app's read timeout is 25 seconds and connection timeout is 8 seconds. The observed deployed health latency exceeded the read timeout, consistent with a possible startup delay. This is evidence to investigate, not a confirmed physical-phone diagnosis.
- Station loading runs through one executor. Previously queued obsolete queries still made network calls even though their UI results were discarded. A generation check now skips obsolete work before starting it. The generation and destroyed flags are volatile for worker visibility. An already-running request still finishes or times out; this change does not claim to cancel active sockets.
- Existing completion handling ignores obsolete results, clears loading text for current success/empty/error responses, and offers retry for failures. New debug-only `RailReserveBackend` logs show the configured scheme/host/port, endpoint without query, HTTP status, safe error code/type, and elapsed time. They exclude response bodies, user queries, credentials, and exception messages.

The earlier physical-phone failure did not reproduce in either the original installed APK or the updated diagnostic APK. Before updating, the installed APK was pulled and its DEX strings showed the deployed HTTPS backend, not `10.0.2.2`. The original APK passed an actual From/To autocomplete, selection, and validation test. Cold-start latency remains a plausible contributor, not a confirmed root cause. If the issue returns, capture the diagnostic logs at that time rather than assuming the installed URL is wrong.

## USB phone follow-up

- The connected phone runs Android 16 (API 36). Its original RailReserve installation was debuggable and contained the deployed backend URL. The original APK was retained under ignored build output for comparison.
- Xiaomi's security policy rejects ADB `input tap` and `input text` with `INJECT_EVENTS` permission errors. Those input attempts did not execute and are not counted as tests. Tests instead invoked RailReserve's own controls on its UI thread through Android instrumentation. No phone security settings were changed. This verifies control logic and popup state, not a manual finger/keyboard interaction.
- The original installed APK passed nine existing Android instrumentation tests and one opt-in station UI test. Thus an obsolete emulator URL was ruled out on this phone, and the reported loading failure was not reproducible during the session.
- The final diagnostic APK was built for `https://railreserve-x8fq.onrender.com` and installed with `adb install -r -t`. No uninstall, app-storage clearing, or user booking mutation was performed.
- Eleven tests passed on the updated APK: the nine existing tests, a real station UI test, and an isolated SQLite regression test. Two more journey UI tests then passed separately, for **13 distinct device tests passed, zero failed/skipped** across the two final runs.
- From `Prayagraj` and To `Delhi` suggestions appeared in the focused field; popup visibility, station labels/codes, selection, missing-source rejection, and identical-station rejection were asserted. Both station requests returned HTTP 200 in 1,070 ms and 631 ms. Identical-route validation reused the station cache without another provider request.
- The date picker asserted today's minimum date and a maximum of today + 14 days, selected tomorrow, and verified the changed date label.
- A read-only PRYJ-to-NDLS search returned HTTP 200 in 4,682 ms. Opening the first returned train's schedule (`12309`) returned HTTP 200 in 734 ms and displayed stops. The test asserted that fare and availability initially remained "not checked". No fare or seat-availability request was made.
- The real SQLite DAO was exercised against a uniquely named database in the app cache, never `railreserve.db`: reserve, PNR lookup, duplicate-seat rejection/rollback, cancellation, repeated-cancellation rejection, seat release, rebooking with a new PNR, and booking/history persistence after closing/reopening the helper all passed. The temporary database was deleted after the test. Reopening a helper is not a full phone-reboot or UI persistence test.

## Reproduce the LAN build

From the project root in PowerShell:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon '-PrailreserveDebugBackendUrl=http://10.143.112.110:3000' '-Dorg.gradle.java.installations.auto-download=false' "-Dorg.gradle.java.installations.paths=$env:JAVA_HOME"
```

Use the existing normal user Gradle cache; an isolated project `.gradle` user home lacked the Foojay plugin dependency during the sandbox attempt. If the cached JDK path is unavailable on another machine, install JDK 17 and substitute its directory. Do not change the Gradle wrapper or source compatibility to solve a missing JDK.

Current phone/deployed-backend APK: `app/build/outputs/apk/debug/app-debug.apk`. To reproduce this final APK, substitute `-PrailreserveDebugBackendUrl=https://railreserve-x8fq.onrender.com` in the command above. The LAN APK was also successfully built and retained at `app/build/phase1-device/app-debug-lan.apk`; a listening LAN backend is still required to use it.
Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

The LAN APK's generated BuildConfig and packaged DEX strings were checked: it contains `http://10.143.112.110:3000`; no DEX contained `http://10.0.2.2:3000`. LAN APK SHA-256: `803743291F921C7ACBF2EF8BADABBEE4E1D5D5FB552A235A9A24695A5757017C`. Final HTTPS phone APK SHA-256: `87D995B789A173EAD8FAEBE50D26CADB0C33AACEF673C6C02EDD05C783A4F331`; its effective backend was confirmed by generated BuildConfig and phone logs.

## Executed checks

| Command/check | Actual result |
| --- | --- |
| `npm.cmd test` in `backend` | 22 passed, 0 failed, 0 skipped; mocked provider requests |
| `npm.cmd run lint` in `backend` | Passed syntax checks |
| `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug` with the JDK/URL flags above | BUILD SUCCESSFUL in 1m 27s |
| Android JVM test XML reports | 17 tests, 0 failures/errors/skips; includes 6 new provider-free JSON contract tests |
| Android lint report | 0 errors, 198 warnings; mostly localization/layout/style warnings, plus debug cleartext configuration and dependency suggestions. No version upgrades made |
| Final HTTPS `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest` | BUILD SUCCESSFUL in 19s; 17 JVM tests passed |
| Existing device instrumentation before update | 9 passed |
| Original APK opt-in station UI test | 1 passed; earlier failure not reproduced |
| Updated APK instrumentation run | 11 passed, including station UI and isolated real SQLite |
| Updated APK date/train/schedule run | 2 passed; nonempty schedule verified |
| Final instrumentation compilation after adding journey checks | BUILD SUCCESSFUL in 16s |
| Manual touch/keyboard, full app-restart persistence, and UI empty/error/delay scenarios | NOT executed; remaining checklist below |
| `git diff --check` | Passed |

The initial bare `npm test` command was blocked by PowerShell's script execution policy; `npm.cmd test` then actually ran and passed. The initial isolated-cache Android attempt failed resolving the Foojay plugin; the normal-cache JDK 17 command subsequently succeeded. These failed attempts are not counted as passing tests.

## Files and Git preservation

Changes made in this phase:

- `app/src/main/java/com/railreserve/live/BackendApiClient.java`: safe debug-only connection diagnostics.
- `app/src/main/java/com/railreserve/ui/LiveSearchActivity.java`: skip obsolete queued station queries and make worker-read lifecycle/generation flags visible across threads.
- `app/build.gradle.kts`: add only the JVM JSON test dependency; preserve both pre-existing backend-default edits.
- `app/src/test/java/com/railreserve/live/LiveDataContractTest.java`: six provider-free contract tests.
- `app/src/androidTest/java/com/railreserve/live/LiveStationDeviceTest.java`: opt-in actual station UI, popup, selection, and route validation checks.
- `app/src/androidTest/java/com/railreserve/live/LiveJourneyDeviceTest.java`: date-picker checks and opt-in real train/schedule checks.
- `app/src/androidTest/java/com/railreserve/dao/SQLiteBookingRegressionTest.java`: isolated SQLite booking/cancellation/PNR/reopen regression.
- `docs/phase-1-verification.md`: findings, actual results, reproducible build and device steps.

Final working tree: the three initially modified files remain modified, alongside the two production Java files above; the new JVM test, three instrumentation test files, and this report are untracked. `README.md` and `docs/live-mode-setup.md` were not edited during this phase. The wrapper remains unchanged with no Git diff. No commit was made, and generated APKs/build outputs remain ignored.

## Physical phone installation and checks

Start the existing configured backend separately. This investigation did not read or modify `backend/.env`, and did not start a service that loads that file. On the phone's network, verify both `http://10.143.112.110:3000/health` and `http://10.143.112.110:3000/api/stations/search?q=Prayagraj`. A failed LAN connection needs a running backend/network correction first. If Chrome was using a deployed HTTPS URL, build with that same URL instead of the LAN URL.

Enable USB debugging, authorize the computer, then run:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.railreserve/.ui.LiveSearchActivity
& $adb logcat -s RailReserveBackend:D '*:S'
```

Use `install -r` to retain demo SQLite data. Do not uninstall or clear app storage for diagnosis. Confirm the startup log names the expected backend host. A fresh install of this APK removes ambiguity about older emulator builds.

The automated subset completed above does not replace the remaining manual checks:

1. From: type `Prayagraj`, select the returned station and confirm its code. To: type `Delhi`, select a returned destination. Suggestions must appear for both inputs.
2. Type several edits while a request is slow. Only the latest query may update its field; repeat after switching fields and after leaving/reopening the screen.
3. Submit with no selection, a manually typed station label, and identical station selections. Confirm validation; then select distinct suggestions.
4. Choose today and a date within the next 14 days. Confirm past dates and dates beyond the allowed range cannot be selected, and the chosen date reaches results.
5. Search trains, open a schedule, and verify returned stop order and absent endpoint times. No demo train may replace a live response.
6. With a mock/test backend, exercise empty stations, empty trains, empty schedule, malformed JSON, 403, 429, 503, and a delayed response. Loading must resolve into results, an empty message, or a retryable error. Restore connectivity and retry.
7. Confirm no live ticket booking action exists. Demo Booking must remain separate. Existing fare/availability buttons explicitly request provider data; before a request they say "not checked", and failures must not invent data. This pre-existing functionality differs from a requirement that these features always be unavailable; it was preserved rather than silently removed.
8. In Demo Booking, search/sort/filter, reserve a seat, record its demo PNR, find it, cancel it, verify the seat is available again, and verify history/cancellation after restarting the app.

To run provider-free Android instrumentation, use the same JDK and URL arguments with `:app:connectedDebugAndroidTest`. The two provider-request tests require explicit opt-in and are otherwise skipped, never counted as passed. The final tests were actually executed with these commands, after installing the matching APKs:

```powershell
& $adb shell am instrument -w -r -e runLiveStationChecks true com.railreserve.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -r -e class com.railreserve.live.LiveJourneyDeviceTest -e runLiveTrainChecks true com.railreserve.test/androidx.test.runner.AndroidJUnitRunner
```

The first command was run before `LiveJourneyDeviceTest` was added (11 tests). The second ran just the two journey checks after they were added. To run all 13 together in future, pass both `-e runLiveStationChecks true` and `-e runLiveTrainChecks true` without the `class` restriction. Those flags allow real provider requests; omit them for provider-free regression runs. Raw output is retained in ignored `app/build/phase1-device/instrumentation-after.txt` and `journey-after.txt`.

## Regression scope and remaining risks

The booking DAO and schema were inspected without changes: booking/cancellation use transactions, conditional seat updates, foreign keys, a unique active-booking-per-seat index, and a versioned non-destructive schema upgrade. Existing JVM tests cover PNR validation, booking input validation, sorting, filtering, and history ordering. The new on-device SQLite test verifies DAO transactions and reopening persistence; a full manual booking UI/app-restart check remains outstanding.

Priority order:

1. Medium: the earlier intermittent loading failure was not reproduced; deployed startup latency can exceed Android's read timeout. Capture logs if it recurs before altering timeout policy.
2. Medium: deterministic UI empty/error/delayed-response scenarios and manual touch/keyboard behavior remain unverified. Backend and parser failure cases are covered by mocked tests.
3. Medium: full booking UI and app-restart persistence remain to be checked; SQLite DAO transactions/reopening now pass on device.
4. Configuration: the LAN service was not listening during initial checks. The installed final APK uses the working deployed HTTPS backend instead.
5. Requirement discrepancy: live fare/availability requests already exist. No fabricated values were added, but "always unavailable" is not the application's current behavior.

No provider credentials were read, printed, changed, or packaged. No generated outputs were committed.
