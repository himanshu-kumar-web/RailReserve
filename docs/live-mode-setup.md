# RailReserve live train data setup

Live Train Search is read-only and separate from Demo Booking. It calls the RailReserve backend for station search, route/date train search, and a schedule only after a passenger opens one train. It does not call fare or availability endpoints, use SQLite trains in live mode, or offer live booking.

## Configure a backend URL

The app reads one `BuildConfig.BACKEND_BASE_URL` value per build variant. No provider credential belongs in the Android project.

- Debug defaults to `http://10.0.2.2:3000`, the Android emulator's route to the development computer.
- Release defaults to an HTTPS placeholder and refuses a non-HTTPS `railreserveBackendUrl` Gradle property. Set that property to the deployed HTTPS backend.
- Debug builds alone include a cleartext network policy for local development.

To use a physical Android device, put the phone and computer on the same trusted network, allow inbound port 3000 through the computer firewall, and build with the computer's LAN address:

```powershell
.\gradlew.bat :app:assembleDebug -PrailreserveDebugBackendUrl=http://192.168.1.20:3000
```

Replace `192.168.1.20` with the development computer's current LAN address. For a deployed backend, use HTTPS:

```powershell
.\gradlew.bat :app:assembleDebug -PrailreserveDebugBackendUrl=https://your-service.example.com
.\gradlew.bat :app:assembleRelease -PrailreserveBackendUrl=https://your-service.example.com
```

The backend uses RailCore's API at `https://ir.railcore.tech/v1`. Set `RAILCORE_API_KEY` in the backend's private `.env`/host configuration. The key needs `stations:read`, `journeys:read`, and `schedule:read` scopes for the app's live search. Fare and availability endpoints require `fares:read` and `availability:read` if enabled. Station search, route search, and schedules consume RailCore credits; see its current [API documentation](https://railcore.tech/docs). Never put the provider key in Gradle or the Android app.

## Manual live-mode check

1. Configure the backend's private `RAILCORE_API_KEY` and start it from `backend/` with `npm start`.
2. Build/install the debug app with the emulator URL, a reachable computer LAN URL, or the deployed HTTPS URL above.
3. The app opens on Live Home. Search a station name/code, wait for suggestions, and select the displayed name/code for both route ends.
4. Choose a journey date and press **Search live trains**. Confirm the screen identifies live scheduled data and shows only returned fields, including day offsets when supplied.
5. Open a train and confirm its ordered scheduled stops, null endpoint times, and day offsets. On the results screen, select a class and explicitly check seat availability; each request uses General quota and may consume 5 RailCore credits.
6. Stop the backend or disconnect the device. Confirm live search/schedule shows an error and never substitutes demo trains.
7. Return to the home screen's **Demo Booking** search. Exercise a demo booking, confirmation, PNR lookup, cancellation, and My Bookings to verify the local workflow remains available.

Live results are scheduled timetable data, not a live-running or on-time guarantee. An emulator cannot reach the host through `localhost`; use `10.0.2.2`. A physical device needs the computer's LAN IP. A deployed service must use HTTPS.
