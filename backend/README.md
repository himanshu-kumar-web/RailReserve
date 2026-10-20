# RailReserve backend

Small Node.js adapter that keeps the RailRadar credential on the server and exposes a stable API for the Android app. This service reads train data only; it does **not** book IRCTC tickets, reserve seats, or change a booking.

## Requirements and local setup

- Node.js 24 LTS (the backend uses native `fetch`, `AbortSignal.timeout`, and `--env-file-if-exists`).
- npm (included with Node.js).

From this directory:

```sh
copy .env.example .env
```

Edit `.env` locally and set `RAILRADAR_API_KEY` from your provider account. Never add a real key to source control. Then run:

```sh
npm install
npm test
npm run lint
npm start
```

The server listens on `0.0.0.0:3000` by default. `GET /health` is local and does not call RailRadar. It returns only process health. The server refuses to start if `RAILRADAR_API_KEY` is empty.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `RAILRADAR_API_KEY` | required | Private provider key; sent as `Authorization: Bearer …` and never returned or logged. |
| `RAILRADAR_BASE_URL` | `https://api.railradar.in/v1` | HTTPS RailRadar API base URL. Credentials, query strings, and fragments are rejected. |
| `PORT` | `3000` | HTTP listener port. |
| `REQUEST_TIMEOUT_MS` | `8000` | Provider request timeout (accepted range 500–30000 ms). |
| `RATE_LIMIT_WINDOW_MS` | `900000` | Public API rate-limit window. |
| `RATE_LIMIT_MAX` | `60` | Requests per client IP per window on `/api`. |

The backend does not log request headers or provider response bodies. Its errors use stable codes and sanitized messages; provider error details and stack traces are not forwarded.

## API contract

All successful responses use `{ "success": true, "data": ..., "meta": { "requestId": "..." } }`. Errors use `{ "success": false, "error": { "code": "...", "message": "..." }, "meta": { "requestId": "..." } }`. Missing provider fields are represented as `null`; no train, fare, or seat values are fabricated.

### Health

```http
GET /health
```

Example: `{ "success": true, "data": { "status": "ok" }, "meta": { "requestId": "…" } }`.

### Station search

```http
GET /api/stations/search?q=Delhi&limit=10
```

`q` is required; optional `limit` is one of 5, 10, 20, or 50. `data.stations` contains actual station `code`, `name`, and `city` fields. No matches produces an empty array.

### Trains between stations

```http
GET /api/trains/between?from=NDLS&to=HWH&date=2026-10-25
```

Station codes and a real `YYYY-MM-DD` date are required. Response `data.trains` contains provider train identifiers, names, run days, source/departure and destination/arrival information, duration, distance, and halt count. Provider day offsets are retained, including overnight arrival. Missing source values are `null`.

### Train schedule

```http
GET /api/trains/12345/schedule
```

`data.train` contains provider train details and `data.stops` retains route sequence, station code/name, times, day offsets, distance, halt, and platform where supplied.

### Fare

```http
GET /api/trains/12345/fare?source=NDLS&destination=HWH&journeyDate=2026-10-25&classCode=3A&quotaCode=GN
```

Quota defaults to `GN`. Supported classes and quotas follow the provider documentation. `totalFare` is the provider value or `null`; `fareKnown` is false if the provider omitted it. Breakdown fields are preserved from the provider schema and use `null` when missing.

### Availability

```http
GET /api/trains/12345/availability?source=NDLS&destination=HWH&journeyDate=2026-10-25&classCode=3A&quotaCode=GN
```

The backend selects only the exact requested date from the provider's `avlDayList`. `availabilityStatus` preserves the provider string (including RAC and waitlist text), and `statusType` classifies recognizable `AVAILABLE`, `RAC`, and waitlist forms. Unknown provider values remain `UNKNOWN`; an absent requested date yields a null status and `UNKNOWN`. No seat count is inferred.

## Opt-in provider smoke test

The smoke script calls the **RailReserve backend** (local by default), never RailRadar directly, and never sends or prints `RAILRADAR_API_KEY`. The backend makes five provider requests: station search, train search, schedule, fare, and availability. Run it only after confirming your provider access/quota and starting the backend with its key configured.

Set these non-secret inputs in `.env` (the example file has safe sample values):

| Variable | Required | Meaning |
| --- | --- | --- |
| `SMOKE_BASE_URL` | No | Backend origin; default `http://127.0.0.1:3000`. Local HTTP is allowed; remote targets must use HTTPS. |
| `SMOKE_STATION_QUERY` | Yes | Station name or code search term. |
| `SMOKE_FROM`, `SMOKE_TO` | Yes | Distinct station codes for the same selected itinerary. |
| `SMOKE_TRAIN_NUMBER` | Yes | Five-digit train returned for that route/date; used for schedule, fare, and availability. |
| `SMOKE_JOURNEY_DATE` | Yes | Real `YYYY-MM-DD` date, today through 13 days ahead in Asia/Kolkata time, matching the provider's documented 14-day availability forecast. |
| `SMOKE_CLASS_CODE` | Yes | Documented class code, such as `3A`. |
| `SMOKE_QUOTA_CODE` | No | Documented quota; defaults to `GN`. |

The script reports every check as `PASS`, `FAIL`, or `SKIPPED`. Missing configuration is shown as `SKIPPED` and exits 2 (incomplete); failed checks exit 1. It validates response envelopes and required fields, requires the selected train to appear in the route/date search, requires a known fare, and requires availability for the exact requested date. The original availability status is displayed as a quoted string, with no seat-count inference. Provider errors, absent backend provider configuration, invalid smoke inputs, and incomplete provider responses are reported with distinct safe error codes/reasons.

Local Windows run (terminal 1 starts backend; terminal 2 runs the opt-in check):

```powershell
cd backend
copy .env.example .env
# Edit backend/.env: set RAILRADAR_API_KEY and choose a matching, currently valid itinerary/date.
npm install
npm start
```

```powershell
cd backend
npm run smoke:provider
```

`SMOKE_JOURNEY_DATE` is intentionally a placeholder in `.env.example`; replace it with an actual date in the 14-day window. Do not put the provider key in smoke variables. To check a deployed backend after deployment, set `SMOKE_BASE_URL` to its HTTPS origin in your local, ignored `.env` and run the same command; the client still calls only that backend service.

## Errors

Input errors return HTTP 400 / `INVALID_INPUT`. A missing server key returns 503 / `PROVIDER_NOT_CONFIGURED`. Provider auth errors, provider throttling, provider 404s, timeout, outage, and invalid provider JSON have distinct codes (`PROVIDER_AUTH_ERROR`, `PROVIDER_RATE_LIMITED`, `PROVIDER_NOT_FOUND`, `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`, and `INVALID_PROVIDER_RESPONSE`). The backend's own request limiter returns 429 / `RATE_LIMITED`, distinct from provider throttling.

## Deployment checklist (Render or comparable Node.js host)

1. Create a Node.js web service and set its root directory to `backend`.
2. Use Node 24 LTS. Set build command to `npm ci` and start command to `npm start`.
3. Add `RAILRADAR_API_KEY` through the host's **private environment settings**. Add `RAILRADAR_BASE_URL` only for a provider-approved HTTPS URL. Never put secrets in source, committed files, or build commands/logs.
4. The host terminates public traffic with HTTPS; configure health-check path `/health`. The app listens on the host-provided `PORT`.
5. Deploy, check `https://<service-host>/health`, and run `npm run smoke:provider` from a machine with the non-secret smoke inputs configured and `SMOKE_BASE_URL=https://<service-host>`. Confirm the provider plan/quota before this step because it consumes five upstream calls.
6. Review smoke output; only `PASS` means verified. Resolve any `SKIPPED` setup items and `FAIL` results before Android integration. Keep the provider key only in the host's private environment settings.

The reviewed public RailRadar docs state a free sandbox quota of 100 requests per month and describe availability as a 14-day forecast, but do not identify which of these specific endpoints are gated by paid plans. Feature entitlements and current limits must be confirmed in the account dashboard/provider support before deployment. The automated tests use mocked backend responses; they do not run the provider smoke test or verify live data.

## Tests and Android integration

`npm test` uses Node's built-in test runner and mocked `fetch` implementations, so no provider account is needed. `npm run lint` runs syntax checks. Before Android integration, deploy this service over HTTPS, verify your account has access to schedule/fare/availability endpoints, and agree the mobile app's expected response/error contract and backend URL. Keep the provider key exclusively on this server.
