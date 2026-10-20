import { ProviderError, mapProviderStatus } from '../errors.js';

export const DEFAULT_BASE_URL = 'https://api.railradar.in/v1';

export class RailRadarClient {
  constructor({ apiKey = process.env.RAILRADAR_API_KEY, baseUrl = process.env.RAILRADAR_BASE_URL || DEFAULT_BASE_URL,
    timeoutMs = Number(process.env.REQUEST_TIMEOUT_MS || 8000), fetchImpl = globalThis.fetch } = {}) {
    this.apiKey = apiKey;
    this.baseUrl = validateBaseUrl(baseUrl);
    this.timeoutMs = Number.isInteger(timeoutMs) && timeoutMs >= 500 && timeoutMs <= 30000 ? timeoutMs : 8000;
    this.fetchImpl = fetchImpl;
  }

  async get(path, query = {}) {
    if (typeof this.apiKey !== 'string' || !this.apiKey.trim()) {
      throw new ProviderError(503, 'PROVIDER_NOT_CONFIGURED', 'Train data is not configured on this server.');
    }
    const url = new URL(`${this.baseUrl}/${path.replace(/^\/+/, '')}`);
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
    }
    let response;
    try {
      response = await this.fetchImpl(url, {
        method: 'GET',
        headers: { Accept: 'application/json', Authorization: `Bearer ${this.apiKey}` },
        signal: AbortSignal.timeout(this.timeoutMs),
      });
    } catch (error) {
      if (error?.name === 'TimeoutError' || error?.name === 'AbortError') {
        throw new ProviderError(504, 'PROVIDER_TIMEOUT', 'The train data provider did not respond in time.');
      }
      throw new ProviderError(502, 'PROVIDER_UNAVAILABLE', 'The train data provider could not be reached.');
    }
    if (!response.ok) throw mapProviderStatus(response.status);

    let payload;
    try {
      payload = await response.json();
    } catch {
      throw new ProviderError(502, 'INVALID_PROVIDER_RESPONSE', 'The train data provider returned invalid JSON.');
    }
    if (!payload || typeof payload !== 'object' || payload.success !== true || !Object.hasOwn(payload, 'data')) {
      throw new ProviderError(502, 'INVALID_PROVIDER_RESPONSE', 'The train data provider returned an unexpected response.');
    }
    return payload.data;
  }
}

function validateBaseUrl(value) {
  let parsed;
  try { parsed = new URL(value); } catch {
    throw new Error('RAILRADAR_BASE_URL must be a valid HTTPS URL.');
  }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('RAILRADAR_BASE_URL must be HTTPS and must not contain credentials, query, or fragment.');
  }
  return parsed.toString().replace(/\/$/, '');
}
