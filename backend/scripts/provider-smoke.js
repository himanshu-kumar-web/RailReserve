import { pathToFileURL } from 'node:url';
import { enumValue, journeyDate, QUOTA_CODES, stationCode, trainNumber, CLASS_CODES } from '../src/validation.js';

const DAY_MS = 24 * 60 * 60 * 1000;

export async function runSmoke({ env = process.env, fetchImpl = globalThis.fetch, log = console.log, now = new Date() } = {}) {
  const results = [];
  let base;
  let baseError;
  try { base = backendBase(env.SMOKE_BASE_URL); } catch (error) { baseError = error; }
  const health = await check('GET /health', async () => {
    if (baseError) throw baseError;
    const response = await requestJson(fetchImpl, `${base}/health`);
    requireSuccessEnvelope(response, 'health');
    if (response.data?.status !== 'ok') throw new SmokeFailure('INVALID_BACKEND_RESPONSE', 'health status was missing');
  });
  results.push(health);

  const checks = [
    ['Station search', async () => {
      const q = requiredEnv(env, 'SMOKE_STATION_QUERY');
      const url = new URL(`${base}/api/stations/search`);
      url.searchParams.set('q', q);
      const response = await requestJson(fetchImpl, url);
      requireSuccessEnvelope(response, 'station search');
      if (!Array.isArray(response.data?.stations) || response.data.stations.some((station) =>
        !station || typeof station.code !== 'string' || !station.code || typeof station.name !== 'string' || !station.name)) {
        throw new SmokeFailure('INVALID_BACKEND_RESPONSE', 'station list or required station fields were missing');
      }
      return `${response.data.stations.length} matching station(s)`;
    }],
    ['Trains between stations', async () => {
      const from = stationCode(requiredEnv(env, 'SMOKE_FROM'), 'SMOKE_FROM');
      const to = stationCode(requiredEnv(env, 'SMOKE_TO'), 'SMOKE_TO');
      if (from === to) throw new SmokeFailure('INVALID_SMOKE_INPUT', 'SMOKE_FROM and SMOKE_TO must differ');
      const selectedTrain = trainNumber(requiredEnv(env, 'SMOKE_TRAIN_NUMBER'));
      const date = smokeJourneyDate(env.SMOKE_JOURNEY_DATE, now);
      const url = new URL(`${base}/api/trains/between`);
      url.search = new URLSearchParams({ from, to, date }).toString();
      const response = await requestJson(fetchImpl, url);
      requireSuccessEnvelope(response, 'train search');
      if (!namedStation(response.data?.from) || !namedStation(response.data?.to)
        || !Number.isFinite(response.data?.count) || !Array.isArray(response.data?.trains)) {
        throw new SmokeFailure('INVALID_BACKEND_RESPONSE', 'train search omitted required route or train-list fields');
      }
      if (response.data.trains.some((train) => !train || typeof train.number !== 'string' || !train.number
        || typeof train.name !== 'string' || !train.name || typeof train.from?.departure !== 'string'
        || typeof train.to?.arrival !== 'string')) {
        throw new SmokeFailure('INVALID_BACKEND_RESPONSE', 'train search returned a train missing required fields');
      }
      if (!response.data.trains.some((train) => train.number === selectedTrain)) {
        throw new SmokeFailure('SELECTED_TRAIN_NOT_ON_ROUTE', 'SMOKE_TRAIN_NUMBER was not returned for the selected route and date');
      }
      return `${response.data.trains.length} train(s) for ${date}`;
    }],
    ['Train schedule', async () => {
      const number = trainNumber(requiredEnv(env, 'SMOKE_TRAIN_NUMBER'));
      const response = await requestJson(fetchImpl, `${base}/api/trains/${encodeURIComponent(number)}/schedule`);
      requireSuccessEnvelope(response, 'train schedule');
      if (response.data?.train?.number !== number || typeof response.data.train.name !== 'string'
        || !Array.isArray(response.data.stops) || response.data.stops.length === 0
        || response.data.stops.some((stop) => !Number.isFinite(stop?.sequence) || !namedStation(stop?.station))) {
        throw new SmokeFailure('INVALID_BACKEND_RESPONSE', 'schedule train identity or route stops were missing');
      }
      return `${response.data.stops.length} route stop(s)`;
    }],
    ['Fare', async () => {
      const input = fareAvailabilityInput(env, now);
      const response = await requestJson(fetchImpl, fareUrl(base, input, 'fare'));
      requireSuccessEnvelope(response, 'fare');
      const fare = response.data;
      if (fare?.trainNumber !== input.number || fare?.source?.code !== input.source
        || fare?.destination?.code !== input.destination || fare?.classCode !== input.classCode
        || fare?.quotaCode !== input.quotaCode || fare?.requestedJourneyDate !== input.date
        || fare?.fareKnown !== true || !Number.isFinite(fare.totalFare) || !fare.breakdown || typeof fare.breakdown !== 'object') {
        throw new SmokeFailure('FARE_UNAVAILABLE', 'fare response did not contain a known fare for the requested train, route, date, class, and quota');
      }
      return `fare response validated for ${input.date} (${input.classCode}/${input.quotaCode})`;
    }],
    ['Availability', async () => {
      const input = fareAvailabilityInput(env, now);
      const response = await requestJson(fetchImpl, fareUrl(base, input, 'availability'));
      requireSuccessEnvelope(response, 'availability');
      const availability = response.data;
      if (availability?.trainNumber !== input.number || availability?.source?.code !== input.source
        || availability?.destination?.code !== input.destination || availability?.classCode !== input.classCode
        || availability?.quotaCode !== input.quotaCode || availability?.requestedJourneyDate !== input.date
        || availability?.availabilityDate !== input.date || typeof availability?.availabilityStatus !== 'string'
        || !availability.availabilityStatus.trim() || typeof availability?.statusType !== 'string') {
        throw new SmokeFailure('AVAILABILITY_UNCONFIRMED', 'provider did not return a status for the exact requested route and date');
      }
      return `exact date validated; provider status ${JSON.stringify(availability.availabilityStatus)}`;
    }],
  ];

  for (const [label, run] of checks) {
    if (health.status !== 'PASS') {
      results.push({ label, status: 'SKIPPED', detail: 'local backend health check failed' });
      continue;
    }
    results.push(await check(label, run));
  }

  for (const result of results) log(`${result.status.padEnd(7)} ${result.label}${result.detail ? ` — ${result.detail}` : ''}`);
  const failed = results.some((result) => result.status === 'FAIL');
  const skipped = results.some((result) => result.status === 'SKIPPED');
  log(failed ? 'Smoke test finished with failures.' : skipped ? 'Smoke test incomplete; skipped checks are not passes.' : 'All provider smoke checks passed.');
  return { exitCode: failed ? 1 : skipped ? 2 : 0, results };
}

async function check(label, run) {
  try {
    const detail = await run();
    return { label, status: 'PASS', detail };
  } catch (error) {
    if (error instanceof SmokeSkipped) return { label, status: 'SKIPPED', detail: error.message };
    const code = error instanceof SmokeFailure ? error.code : 'LOCAL_BACKEND_UNREACHABLE';
    return { label, status: 'FAIL', detail: `${code}: ${safeReason(code)}` };
  }
}

async function requestJson(fetchImpl, url) {
  let response;
  try {
    response = await fetchImpl(url, { method: 'GET', headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(8000) });
  } catch {
    throw new SmokeFailure('LOCAL_BACKEND_UNREACHABLE', 'could not reach the configured backend URL');
  }
  let body;
  try { body = await response.json(); } catch {
    throw new SmokeFailure('INVALID_BACKEND_RESPONSE', 'backend returned invalid JSON');
  }
  if (!response.ok || body?.success !== true) {
    const code = typeof body?.error?.code === 'string' ? body.error.code : `HTTP_${response.status}`;
    throw new SmokeFailure(code, safeReason(code));
  }
  return body;
}

function requireSuccessEnvelope(body, endpoint) {
  if (!body || body.success !== true || !Object.hasOwn(body, 'data')) {
    throw new SmokeFailure('INVALID_BACKEND_RESPONSE', `${endpoint} response envelope was invalid`);
  }
}

function backendBase(value) {
  const input = value?.trim() || 'http://127.0.0.1:3000';
  let url;
  try { url = new URL(input); } catch { throw new SmokeFailure('INVALID_SMOKE_INPUT', 'SMOKE_BASE_URL must be an HTTP(S) URL'); }
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if ((!local && url.protocol !== 'https:') || (local && !['http:', 'https:'].includes(url.protocol))
    || url.username || url.password || url.search || url.hash) {
    throw new SmokeFailure('INVALID_SMOKE_INPUT', 'SMOKE_BASE_URL must be local HTTP(S) or remote HTTPS without credentials or query parameters');
  }
  return url.toString().replace(/\/$/, '');
}

function requiredEnv(env, key) {
  const value = env[key]?.trim();
  if (!value) throw new SmokeSkipped(`missing ${key}`);
  return value;
}

function smokeJourneyDate(value, now) {
  if (!value?.trim()) throw new SmokeSkipped('missing SMOKE_JOURNEY_DATE');
  let date;
  try { date = journeyDate(value.trim(), 'SMOKE_JOURNEY_DATE'); } catch {
    throw new SmokeFailure('INVALID_SMOKE_INPUT', 'SMOKE_JOURNEY_DATE must be a real YYYY-MM-DD date');
  }
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(now);
  const todayMs = Date.parse(`${today}T00:00:00Z`);
  const dateMs = Date.parse(`${date}T00:00:00Z`);
  if (dateMs < todayMs || dateMs >= todayMs + 14 * DAY_MS) {
    throw new SmokeFailure('DATE_OUTSIDE_PROVIDER_WINDOW', 'journey date must be today through 13 days ahead in Asia/Kolkata time');
  }
  return date;
}

function fareAvailabilityInput(env, now) {
  const number = trainNumber(requiredEnv(env, 'SMOKE_TRAIN_NUMBER'));
  const source = stationCode(requiredEnv(env, 'SMOKE_FROM'), 'SMOKE_FROM');
  const destination = stationCode(requiredEnv(env, 'SMOKE_TO'), 'SMOKE_TO');
  if (source === destination) throw new SmokeFailure('INVALID_SMOKE_INPUT', 'SMOKE_FROM and SMOKE_TO must differ');
  const date = smokeJourneyDate(env.SMOKE_JOURNEY_DATE, now);
  const classCode = enumValue(requiredEnv(env, 'SMOKE_CLASS_CODE'), 'SMOKE_CLASS_CODE', CLASS_CODES);
  const quotaCode = enumValue(env.SMOKE_QUOTA_CODE?.trim() || 'GN', 'SMOKE_QUOTA_CODE', QUOTA_CODES);
  return { number, source, destination, date, classCode, quotaCode };
}

function fareUrl(base, input, endpoint) {
  const url = new URL(`${base}/api/trains/${encodeURIComponent(input.number)}/${endpoint}`);
  url.search = new URLSearchParams({
    source: input.source, destination: input.destination, journeyDate: input.date,
    classCode: input.classCode, quotaCode: input.quotaCode,
  }).toString();
  return url;
}

function namedStation(value) {
  return value && typeof value.code === 'string' && value.code && typeof value.name === 'string' && value.name;
}

function safeReason(code) {
  const reasons = {
    LOCAL_BACKEND_UNREACHABLE: 'could not reach the configured backend URL',
    PROVIDER_NOT_CONFIGURED: 'provider key is missing from backend configuration',
    PROVIDER_AUTH_ERROR: 'provider rejected the backend credentials',
    PROVIDER_RATE_LIMITED: 'provider rate limit was reached',
    PROVIDER_NOT_FOUND: 'provider did not find the requested train or data',
    PROVIDER_TIMEOUT: 'provider request timed out',
    PROVIDER_UNAVAILABLE: 'provider is unavailable',
    INVALID_PROVIDER_RESPONSE: 'provider response was invalid or incomplete',
    INVALID_INPUT: 'backend rejected the smoke-test input',
    INVALID_SMOKE_INPUT: 'smoke-test configuration is invalid',
    INVALID_BACKEND_RESPONSE: 'backend response did not match the documented contract',
    FARE_UNAVAILABLE: 'a fare was not returned for the selected parameters',
    AVAILABILITY_UNCONFIRMED: 'availability for the exact requested date was not returned',
    DATE_OUTSIDE_PROVIDER_WINDOW: 'date is outside the documented 14-day availability window',
    SELECTED_TRAIN_NOT_ON_ROUTE: 'selected train was not found for the configured route and date',
  };
  return reasons[code] || 'request failed';
}

class SmokeFailure extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

class SmokeSkipped extends Error {}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runSmoke().then(({ exitCode }) => { process.exitCode = exitCode; });
}
