import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createApp } from '../src/app.js';
import { ProviderError } from '../src/errors.js';
import { normalizeAvailability, normalizeBetween, normalizeFare, normalizeSchedule, normalizeStations } from '../src/provider/normalize.js';
import { DEFAULT_BASE_URL, RailCoreClient } from '../src/provider/railCoreClient.js';

async function withServer(app, run) {
  const server = createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const address = server.address();
    await run(`http://127.0.0.1:${address.port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function fakeClient(get = async () => ({})) { return { get }; }

test('replays the reported SL fare and waitlist contract using fixtures only', async () => {
  const app = createApp({ capabilities: { fare: true, availability: true }, client: fakeClient(async (path) => ({
    train_number: '15004', from_station_code: 'GKP', to_station_code: 'PRYJ', quota: 'GN', journey_date: '2026-11-15',
    ...(path === 'fares/estimate' ? { fares: [{ class_code: 'SL', fare: 245, currency: 'INR' }] }
      : { classes: [{ class_code: 'SL', availability_text: 'GNWL107/WL62', status: 'WAITLIST' }] }),
  })) });
  await withServer(app, async (base) => {
    const query = 'source=GKP&destination=PRYJ&journeyDate=2026-11-15&classCode=SL&quotaCode=GN';
    const fare = (await (await fetch(`${base}/api/trains/15004/fare?${query}`)).json()).data;
    assert.equal(fare.fareKnown, true); assert.equal(fare.totalFare, 245); assert.equal(fare.providerSource, 'RailCore fare estimate');
    const availability = (await (await fetch(`${base}/api/trains/15004/availability?${query}`)).json()).data;
    assert.equal(availability.availabilityDate, '2026-11-15'); assert.equal(availability.availabilityStatus, 'GNWL107/WL62');
    assert.equal(availability.statusType, 'WAITLIST'); assert.equal(availability.providerSource, 'RailCore availability');
  });
});

test('optional capabilities are disabled by default without contacting the provider', async () => {
  let calls = 0;
  await withServer(createApp({ client: fakeClient(async () => { calls++; }), capabilities: {} }), async (base) => {
    for (const endpoint of ['fare', 'availability']) {
      const response = await fetch(`${base}/api/trains/12345/${endpoint}?source=AAA&destination=BBB&journeyDate=2026-11-13&classCode=3A`);
      assert.equal(response.status, 503);
      assert.equal((await response.json()).error.code, 'CAPABILITY_UNAVAILABLE');
    }
  });
  assert.equal(calls, 0);
});

test('offered classes come exclusively from provider lists and survive route and schedule normalization', () => {
  const train = { train_number: '12345', train_name: 'Fixture', departure_time: '08:00', arrival_time: '12:00', duration_minutes: 240 };
  const between = (entry) => normalizeBetween({ from_station_code: 'AAA', to_station_code: 'BBB', trains: [entry] }).trains[0];
  assert.equal(between(train).classes, null);
  assert.deepEqual(between({ ...train, classes: ['SL', '3A', '2A', 'CC', 'SL', null, 2, 'invalid'] }).classes, ['SL', '3A', '2A', 'CC']);
  const schedule = normalizeSchedule({ train_number: '12345', train_name: 'Fixture', stops: [], classes: ['CC', 'EC'] });
  assert.deepEqual(schedule.train.classes, ['CC', 'EC']);
  assert.equal(schedule.train.classSource, 'RailCore schedule');
  assert.deepEqual(between({ ...train, classes: [] }).classes, []);
});

test('fare fixtures preserve provenance and reject negative, non-INR and missing prices', () => {
  const requested = { number: '12345', source: 'AAA', destination: 'BBB', classCode: '3A', quotaCode: 'GN' };
  for (const fare of [{ fare: -1, currency: 'INR' }, { fare: 10, currency: 'USD' }, { fare: 10 }, {}]) {
    assert.equal(normalizeFare({ fares: [{ class_code: '3A', ...fare }] }, requested).fareKnown, false);
  }
  const result = normalizeFare({ fares: [{ class_code: '3A', fare: 875.50, currency: 'INR' }] }, requested);
  assert.equal(result.totalFare, 875.50);
  assert.equal(result.providerSource, 'RailCore fare estimate');
  assert.ok(Number.isFinite(Date.parse(result.retrievedAt)));
  assert.throws(() => normalizeFare({ train_number: '99999' }, requested), (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
  assert.throws(() => normalizeAvailability({ from_station_code: 'WRONG' }, requested), (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
});

test('enabled availability rejects another date and never permits intermediary caching', async () => {
  let date = '2026-11-14';
  const app = createApp({ client: fakeClient(async () => ({ journey_date: date, classes: [{ class_code: '3A', status: 'RAC', last_updated_at: '2026-11-12T10:00:00Z' }] })),
    capabilities: { availability: true } });
  await withServer(app, async (base) => {
    const url = `${base}/api/trains/12345/availability?source=AAA&destination=BBB&journeyDate=2026-11-13&classCode=3A`;
    assert.equal((await fetch(url)).status, 502);
    date = '2026-11-13';
    const response = await fetch(url);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    const result = (await response.json()).data;
    assert.equal(result.inventoryUpdatedAt, '2026-11-12T10:00:00Z');
    assert.equal(result.providerSource, 'RailCore availability');
  });
});

test('health returns a safe process status without provider configuration', async () => {
  await withServer(createApp({ client: fakeClient(), rateLimitMax: 100 }), async (base) => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.status, 200);
    assert.deepEqual((await response.json()).data, { status: 'ok' });
  });
});

test('valid station request normalizes provider result and preserves empty results', async () => {
  let observed;
  const app = createApp({ client: fakeClient(async (path, query) => {
    observed = { path, query };
    return { results: [{ station_code: 'NDLS', station_name: 'New Delhi', display_name: 'New Delhi', city: 'Delhi' }] };
  }), rateLimitMax: 100 });
  await withServer(app, async (base) => {
    const response = await fetch(`${base}/api/stations/search?q=Delhi`);
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.deepEqual(observed, { path: 'stations/search', query: { q: 'Delhi', limit: 10 } });
    assert.deepEqual(body.data.stations, [{ code: 'NDLS', name: 'New Delhi', city: 'Delhi' }]);
  });
  assert.deepEqual(normalizeStations({ results: [] }), []);
  assert.throws(() => normalizeStations(undefined), (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
});

test('station search returns the documented success envelope for an empty provider result', async () => {
  await withServer(createApp({ client: fakeClient(async () => []), rateLimitMax: 100 }), async (base) => {
    const response = await fetch(`${base}/api/stations/search?q=NoMatches`);
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.success, true);
    assert.deepEqual(body.data.stations, []);
    assert.equal(typeof body.meta.requestId, 'string');
  });
});

test('validates malformed input before provider call', async () => {
  let calls = 0;
  await withServer(createApp({ client: fakeClient(async () => { calls += 1; }), rateLimitMax: 100 }), async (base) => {
    const response = await fetch(`${base}/api/trains/between/`);
    assert.equal(response.status, 400);
    const invalidDate = await fetch(`${base}/api/trains/between?from=NDLS&to=HWH&date=2026-02-30`);
    assert.equal(invalidDate.status, 400);
    assert.equal((await invalidDate.json()).error.code, 'INVALID_INPUT');
  });
  assert.equal(calls, 0);
});

test('route search sends RailCore query parameters and normalizes its response', async () => {
  let observed;
  const app = createApp({ client: fakeClient(async (path, query) => {
    observed = { path, query };
    return { from_station_code: 'NDLS', to_station_code: 'HWH', journey_date: '2026-10-25',
      trains: [{ train_number: '12345', train_name: 'Test Express', departure_time: '20:00',
        arrival_time: '08:00', duration_minutes: 720, running_days: ['MON'] }] };
  }), rateLimitMax: 100 });
  await withServer(app, async (base) => {
    const response = await fetch(`${base}/api/trains/between?from=NDLS&to=HWH&date=2026-10-25`);
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.deepEqual(observed, { path: 'routes/trains', query: { from: 'NDLS', to: 'HWH', date: '2026-10-25' } });
    assert.equal(body.data.trains[0].number, '12345');
    assert.equal(body.data.trains[0].to.day, 1);
  });
});

test('authorized schedule, fare, and availability routes use RailCore endpoint contracts', async () => {
  const observed = [];
  const app = createApp({ client: fakeClient(async (path, query) => {
    observed.push({ path, query });
    if (path === 'trains/12345/schedule') {
      return { train_number: '12345', train_name: 'Test Express', source_station_code: 'NDLS',
        destination_station_code: 'HWH', running_days: ['MON'],
        stops: [{ sequence: 1, station_code: 'NDLS', station_name: 'New Delhi', departure_time: '20:00', day: 1, is_stop: true }] };
    }
    if (path === 'fares/estimate') {
      return { train_number: '12345', from_station_code: 'NDLS', to_station_code: 'HWH',
        quota: 'GN', fares: [{ class_code: '3A', fare: 875, currency: 'INR' }] };
    }
    return { train_number: '12345', from_station_code: 'NDLS', to_station_code: 'HWH',
      journey_date: '2026-10-25', quota: 'GN',
      classes: [{ class_code: '3A', status: 'AVAILABLE', availability_text: 'AVAILABLE 4' }] };
  }), capabilities: { fare: true, availability: true }, rateLimitMax: 100 });

  await withServer(app, async (base) => {
    const schedule = await fetch(`${base}/api/trains/12345/schedule`);
    assert.equal(schedule.status, 200);
    const fare = await fetch(`${base}/api/trains/12345/fare?source=NDLS&destination=HWH&journeyDate=2026-10-25&classCode=3A&quotaCode=GN`);
    assert.equal(fare.status, 200);
    assert.equal((await fare.json()).data.totalFare, 875);
    const availability = await fetch(`${base}/api/trains/12345/availability?source=NDLS&destination=HWH&journeyDate=2026-10-25&classCode=3A&quotaCode=GN`);
    assert.equal(availability.status, 200);
    assert.equal((await availability.json()).data.statusType, 'AVAILABLE');
  });

  assert.deepEqual(observed, [
    { path: 'trains/12345/schedule', query: undefined },
    { path: 'fares/estimate', query: { train_number: '12345', from: 'NDLS', to: 'HWH', class: '3A', quota: 'GN' } },
    { path: 'availability/seats', query: { train_number: '12345', from: 'NDLS', to: 'HWH', date: '2026-10-25', class: '3A', quota: 'GN' } },
  ]);
});

test('RailCore route results normalize into the app contract with derived day offsets', () => {
  const normalized = normalizeBetween({ from_station_code: 'NDLS', to_station_code: 'HWH', trains: [
    { train_number: '12345', train_name: 'Test', train_type: 'Express', running_days: ['MON'],
      departure_time: '20:00', arrival_time: '08:00', duration_minutes: 720, distance_km: 100 },
  ] }, { from: 'NDLS', to: 'HWH' });
  assert.equal(normalized.trains[0].from.day, 0);
  assert.equal(normalized.trains[0].to.day, 1);
  assert.equal(normalized.trains[0].to.arrival, '08:00');
  assert.equal(normalized.trains[0].from.station.name, null);
  assert.equal(normalized.trains[0].totalHaltsBetween, null);
});

test('schedule and fare normalization preserve provider details and unknown values', () => {
  const schedule = normalizeSchedule({ train_number: '12345', train_name: 'Example train', source_station_code: 'NDLS',
    destination_station_code: 'HWH', running_days: ['MON'], total_duration_minutes: 720, distance_km: 100,
    stops: [{ sequence: 2, station_code: 'HWH', station_name: 'Howrah', arrival_time: '08:00', day: 2, is_stop: true }] });
  assert.equal(schedule.stops[0].sequence, 2);
  assert.equal(schedule.stops[0].arrivalDay, 1);
  assert.equal(schedule.train.category, null);
  const fare = normalizeFare({ train_number: '12345', from_station_code: 'NDLS', to_station_code: 'HWH',
    fares: [{ class_code: '3A', fare: 875, currency: 'INR' }] },
  { number: '12345', source: 'NDLS', destination: 'HWH', classCode: '3A', quotaCode: 'GN' });
  assert.equal(fare.fareKnown, true);
  assert.equal(fare.totalFare, 875);
  assert.equal(fare.breakdown.baseFare, null);
  assert.equal(fare.breakdown.reservationCharge, null);
  assert.deepEqual(fare.source, { code: 'NDLS', name: null });
  assert.equal(normalizeFare({}, { number: '12345', source: 'NDLS', destination: 'HWH', classCode: '3A', quotaCode: 'GN' }).fareKnown, false);
});

test('malformed provider train and schedule payloads are not reported as empty successful results', () => {
  assert.throws(() => normalizeBetween({ from_station_code: 'NDLS', to_station_code: 'HWH' }, { from: 'NDLS', to: 'HWH' }),
    (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
  assert.throws(() => normalizeSchedule({ train_number: '12345', train_name: 'Example' }),
    (error) => error.code === 'INVALID_PROVIDER_RESPONSE');
});

test('availability selects requested date only and preserves RAC, waitlist, and unknown values', () => {
  const requested = { number: '12345', source: 'NDLS', destination: 'HWH', journeyDateValue: '2026-10-21', classCode: '3A', quotaCode: 'GN' };
  const payload = { train_number: '12345', journey_date: '2026-10-21', classes: [
    { class_code: '2A', status: 'AVAILABLE', availability_text: 'AVAILABLE 4' },
    { class_code: '3A', status: 'RAC', availability_text: 'RAC 12' },
  ] };
  assert.equal(normalizeAvailability(payload, requested).availabilityStatus, 'RAC 12');
  assert.equal(normalizeAvailability(payload, requested).statusType, 'RAC');
  assert.equal(normalizeAvailability({ classes: [{ class_code: '3A', status: 'AVAILABLE', availability_text: 'AVAILABLE-0042' }] }, requested).statusType, 'AVAILABLE');
  assert.equal(normalizeAvailability({ classes: [{ class_code: '3A', status: 'WAITLIST', availability_text: 'GNWL24/WL11' }] }, requested).statusType, 'WAITLIST');
  const missing = normalizeAvailability(payload, { ...requested, classCode: 'SL' });
  assert.equal(missing.availabilityStatus, null);
  assert.equal(missing.statusType, 'UNKNOWN');
});

test('provider request uses RailCore HTTPS URL and API-key header without exposing key', async () => {
  const key = 'private-test-key';
  let request;
  const client = new RailCoreClient({ apiKey: key, fetchImpl: async (url, options) => {
    request = { url: new URL(String(url)), options };
    return { ok: true, json: async () => ({ success: true, data: [] }) };
  } });
  assert.equal(DEFAULT_BASE_URL, 'https://ir.railcore.tech/v1');
  await client.get('stations/search', { q: 'Delhi', limit: 10 });
  assert.equal(request.url.protocol, 'https:');
  assert.equal(request.url.pathname, '/v1/stations/search');
  assert.equal(request.url.searchParams.get('q'), 'Delhi');
  assert.equal(request.options.headers['X-RailCore-Key'], key);
  assert.equal(request.options.headers.Authorization, undefined);
});

test('provider retries HTTP 429 with the next key and keeps using the successful key', async () => {
  const usedKeys = [];
  const client = new RailCoreClient({ apiKeys: ['first-key', 'second-key', 'third-key'], fetchImpl: async (_url, options) => {
    usedKeys.push(options.headers['X-RailCore-Key']);
    return options.headers['X-RailCore-Key'] === 'first-key'
      ? { ok: false, status: 429 }
      : { ok: true, json: async () => ({ success: true, data: [] }) };
  } });

  await client.get('stations/search');
  await client.get('stations/search');

  assert.deepEqual(usedKeys, ['first-key', 'second-key', 'second-key']);
});

test('provider returns rate-limit error after all configured keys return HTTP 429', async () => {
  const usedKeys = [];
  const client = new RailCoreClient({ apiKeys: ['first-key', 'second-key', 'third-key'], fetchImpl: async (_url, options) => {
    usedKeys.push(options.headers['X-RailCore-Key']);
    return { ok: false, status: 429 };
  } });

  await assert.rejects(client.get('stations/search'), (error) => error.code === 'PROVIDER_RATE_LIMITED');
  assert.deepEqual(usedKeys, ['first-key', 'second-key', 'third-key']);
});

test('provider does not switch keys for errors other than HTTP 429', async () => {
  const usedKeys = [];
  const client = new RailCoreClient({ apiKeys: ['first-key', 'second-key', 'third-key'], fetchImpl: async (_url, options) => {
    usedKeys.push(options.headers['X-RailCore-Key']);
    return { ok: false, status: 403 };
  } });

  await assert.rejects(client.get('stations/search'), (error) => error.code === 'PROVIDER_ACCESS_DENIED');
  assert.deepEqual(usedKeys, ['first-key']);
});

test('missing key and timeout have distinct safe errors', async () => {
  const missing = new RailCoreClient({ apiKey: '' });
  await assert.rejects(missing.get('x'), (error) => error.code === 'PROVIDER_NOT_CONFIGURED');
  const timed = new RailCoreClient({ apiKey: 'secret', fetchImpl: async () => { const error = new Error('timed out'); error.name = 'TimeoutError'; throw error; } });
  await assert.rejects(timed.get('x'), (error) => error.code === 'PROVIDER_TIMEOUT');
});

test('provider status mapping distinguishes auth, not found, throttling and outage; secrets are not returned', async () => {
  for (const [status, code, expectedStatus] of [
    [401, 'PROVIDER_AUTH_ERROR', 502], [403, 'PROVIDER_ACCESS_DENIED', 502],
    [402, 'PROVIDER_CREDITS_EXHAUSTED', 402], [404, 'PROVIDER_NOT_FOUND', 404],
    [429, 'PROVIDER_RATE_LIMITED', 503], [503, 'PROVIDER_UNAVAILABLE', 502],
    [504, 'PROVIDER_TIMEOUT', 504],
  ]) {
    const secret = 'never-return-this-key';
    const client = new RailCoreClient({ apiKey: secret, fetchImpl: async () => ({ ok: false, status, json: async () => ({ message: secret }) }) });
    await withServer(createApp({ client, rateLimitMax: 100 }), async (base) => {
      const response = await fetch(`${base}/api/stations/search?q=test`);
      const body = await response.text();
      assert.equal(response.status, expectedStatus);
      assert.equal(JSON.parse(body).error.code, code);
      assert.equal(body.includes(secret), false);
    });
  }
});

test('malformed provider JSON and missing API key are sanitized into error envelopes', async () => {
  const malformed = new RailCoreClient({ apiKey: 'secret', fetchImpl: async () => ({ ok: true, json: async () => { throw new Error('secret parse details'); } }) });
  const noKey = new RailCoreClient({ apiKey: '' });
  for (const [client, code] of [[malformed, 'INVALID_PROVIDER_RESPONSE'], [noKey, 'PROVIDER_NOT_CONFIGURED']]) {
    await withServer(createApp({ client, rateLimitMax: 100 }), async (base) => {
      const response = await fetch(`${base}/api/stations/search?q=test`);
      const body = await response.text();
      assert.equal(response.status, code === 'PROVIDER_NOT_CONFIGURED' ? 503 : 502);
      assert.equal(JSON.parse(body).error.code, code);
      assert.equal(body.includes('secret'), false);
      assert.equal(body.includes('stack'), false);
    });
  }
});

test('rate limit has the same JSON error envelope', async () => {
  await withServer(createApp({ client: fakeClient(), rateLimitMax: 1 }), async (base) => {
    await fetch(`${base}/api/stations/search?q=test`);
    const response = await fetch(`${base}/api/stations/search?q=test`);
    assert.equal(response.status, 429);
    assert.equal((await response.json()).error.code, 'RATE_LIMITED');
  });
});

test('unknown endpoint is a not-found error', async () => {
  await withServer(createApp({ client: fakeClient() }), async (base) => {
    const response = await fetch(`${base}/no-such-route`);
    assert.equal(response.status, 404);
    assert.equal((await response.json()).error.code, 'NOT_FOUND');
  });
});

test('provider error objects do not embed upstream body or credentials', async () => {
  const client = new RailCoreClient({ apiKey: 'secret', fetchImpl: async () => ({ ok: false, status: 401, text: async () => 'secret' }) });
  await assert.rejects(client.get('x'), (error) => error instanceof ProviderError && !error.message.includes('secret'));
});
