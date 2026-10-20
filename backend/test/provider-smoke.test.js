import test from 'node:test';
import assert from 'node:assert/strict';
import { runSmoke } from '../scripts/provider-smoke.js';

const now = new Date('2026-10-19T10:00:00Z');
const env = {
  SMOKE_BASE_URL: 'http://127.0.0.1:3000',
  SMOKE_STATION_QUERY: 'Delhi',
  SMOKE_FROM: 'NDLS',
  SMOKE_TO: 'MMCT',
  SMOKE_TRAIN_NUMBER: '12952',
  SMOKE_JOURNEY_DATE: '2026-10-25',
  SMOKE_CLASS_CODE: '3A',
  SMOKE_QUOTA_CODE: 'GN',
};

function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function successfulBackend(fetches) {
  return async (input, options) => {
    const url = new URL(input);
    fetches.push({ url, options });
    if (url.pathname === '/health') return jsonResponse(200, { success: true, data: { status: 'ok' } });
    if (url.pathname.endsWith('/stations/search')) return jsonResponse(200, { success: true, data: { stations: [{ code: 'NDLS', name: 'New Delhi', city: 'Delhi' }] } });
    if (url.pathname.endsWith('/trains/between')) return jsonResponse(200, { success: true, data: {
      from: { code: 'NDLS', name: 'New Delhi' }, to: { code: 'MMCT', name: 'Mumbai Central' }, count: 1,
      trains: [{ number: '12952', name: 'Mumbai Rajdhani', from: { departure: '16:55' }, to: { arrival: '08:35' } }],
    } });
    if (url.pathname.endsWith('/schedule')) return jsonResponse(200, { success: true, data: {
      train: { number: '12952', name: 'Mumbai Rajdhani' }, stops: [{ sequence: 1, station: { code: 'NDLS', name: 'New Delhi' } }],
    } });
    if (url.pathname.endsWith('/fare')) return jsonResponse(200, { success: true, data: {
      trainNumber: '12952', source: { code: 'NDLS' }, destination: { code: 'MMCT' }, classCode: '3A', quotaCode: 'GN',
      requestedJourneyDate: '2026-10-25', fareKnown: true, totalFare: 2145, breakdown: { baseFare: 1750 },
    } });
    if (url.pathname.endsWith('/availability')) return jsonResponse(200, { success: true, data: {
      trainNumber: '12952', source: { code: 'NDLS' }, destination: { code: 'MMCT' }, classCode: '3A', quotaCode: 'GN',
      requestedJourneyDate: '2026-10-25', availabilityDate: '2026-10-25', availabilityStatus: 'RAC 12', statusType: 'RAC',
    } });
    throw new Error('Unexpected test URL');
  };
}

test('smoke harness checks all local endpoints, preserves status, and never adds provider authorization', async () => {
  const calls = [];
  const lines = [];
  const result = await runSmoke({ env, now, fetchImpl: successfulBackend(calls), log: (line) => lines.push(line) });
  assert.equal(result.exitCode, 0);
  assert.equal(result.results.length, 6);
  assert.ok(result.results.every((item) => item.status === 'PASS'));
  assert.ok(calls.every(({ options }) => !Object.hasOwn(options.headers, 'Authorization')));
  assert.ok(calls.some(({ url }) => url.searchParams.get('date') === env.SMOKE_JOURNEY_DATE));
  assert.ok(calls.some(({ url }) => url.searchParams.get('journeyDate') === env.SMOKE_JOURNEY_DATE));
  assert.ok(lines.some((line) => line.includes('provider status "RAC 12"')));
});

test('missing smoke configuration is SKIPPED, never PASS, and exits incomplete', async () => {
  const lines = [];
  const result = await runSmoke({ env: { SMOKE_BASE_URL: env.SMOKE_BASE_URL }, now, fetchImpl: async () => jsonResponse(200, { success: true, data: { status: 'ok' } }), log: (line) => lines.push(line) });
  assert.equal(result.results[0].status, 'PASS');
  assert.ok(result.results.slice(1).every((item) => item.status === 'SKIPPED'));
  assert.equal(result.exitCode, 2);
  assert.ok(lines.some((line) => line.startsWith('SKIPPED')));
});

test('backend provider-configuration and provider-auth errors remain distinguishable and sanitized', async () => {
  for (const [code, expected] of [
    ['PROVIDER_NOT_CONFIGURED', 'provider key is missing from backend configuration'],
    ['PROVIDER_AUTH_ERROR', 'provider rejected the backend credentials'],
  ]) {
    const secret = 'must-not-be-printed';
    const fetchImpl = async (input) => new URL(input).pathname === '/health'
      ? jsonResponse(200, { success: true, data: { status: 'ok' } })
      : jsonResponse(503, { success: false, error: { code, message: secret } });
    const lines = [];
    const result = await runSmoke({ env, now, fetchImpl, log: (line) => lines.push(line) });
    assert.equal(result.exitCode, 1);
    assert.ok(result.results.slice(1).every((item) => item.status === 'FAIL' && item.detail.includes(code)));
    assert.ok(lines.some((line) => line.includes(expected)));
    assert.equal(lines.join('\n').includes(secret), false);
  }
});

test('out-of-window dates fail smoke checks before train-data requests', async () => {
  const calls = [];
  const lines = [];
  const result = await runSmoke({ env: { ...env, SMOKE_JOURNEY_DATE: '2026-11-04' }, now,
    fetchImpl: async (input) => { calls.push(String(input)); return successfulBackend([])(input, {}); }, log: (line) => lines.push(line) });
  assert.equal(result.exitCode, 1);
  assert.ok(result.results.some((item) => item.detail?.includes('DATE_OUTSIDE_PROVIDER_WINDOW')));
  assert.equal(calls.some((url) => url.includes('/api/trains/between')), false);
  assert.ok(lines.some((line) => line.includes('DATE_OUTSIDE_PROVIDER_WINDOW')));
});

test('availability with a different date fails exact-date verification', async () => {
  const fetchImpl = async (input, options) => {
    const response = await successfulBackend([])(input, options);
    if (String(input).includes('/availability')) {
      return jsonResponse(200, { success: true, data: {
        trainNumber: '12952', source: { code: 'NDLS' }, destination: { code: 'MMCT' }, classCode: '3A', quotaCode: 'GN',
        requestedJourneyDate: '2026-10-25', availabilityDate: '2026-10-26', availabilityStatus: 'AVAILABLE-0001', statusType: 'AVAILABLE',
      } });
    }
    return response;
  };
  const result = await runSmoke({ env, now, fetchImpl, log: () => {} });
  const availability = result.results.find((item) => item.label === 'Availability');
  assert.equal(availability.status, 'FAIL');
  assert.match(availability.detail, /AVAILABILITY_UNCONFIRMED/);
});
