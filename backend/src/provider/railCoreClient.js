import { ProviderError, mapProviderStatus } from '../errors.js';

export const DEFAULT_BASE_URL = 'https://ir.railcore.tech/v1';

export class RailCoreClient {
  constructor({ apiKey, apiKeys, baseUrl = process.env.RAILCORE_BASE_URL || DEFAULT_BASE_URL,
    timeoutMs = Number(process.env.REQUEST_TIMEOUT_MS || 20000), fetchImpl = globalThis.fetch } = {}) {
    this.apiKeys = normalizeApiKeys(apiKeys ?? (apiKey === undefined
      ? [process.env.RAILCORE_API_KEY, process.env.RAILCORE_API_KEY_2, process.env.RAILCORE_API_KEY_3]
      : [apiKey]));
    this.activeApiKeyIndex = 0;
    this.baseUrl = validateBaseUrl(baseUrl);
    this.timeoutMs = Number.isInteger(timeoutMs) && timeoutMs >= 500 && timeoutMs <= 30000 ? timeoutMs : 20000;
    this.fetchImpl = fetchImpl;
  }

  async get(path, query = {}) {
    if (this.apiKeys.length === 0) {
      throw new ProviderError(503, 'PROVIDER_NOT_CONFIGURED', 'Train data is not configured on this server.');
    }
    const url = new URL(`${this.baseUrl}/${path.replace(/^\/+/, '')}`);
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
    }
    let response;
    let rateLimited = false;
    for (let offset = 0; offset < this.apiKeys.length; offset++) {
      const keyIndex = (this.activeApiKeyIndex + offset) % this.apiKeys.length;
      try {
        response = await this.fetchImpl(url, {
          method: 'GET',
          headers: { Accept: 'application/json', 'X-RailCore-Key': this.apiKeys[keyIndex] },
          signal: AbortSignal.timeout(this.timeoutMs),
        });
      } catch (error) {
        if (error?.name === 'TimeoutError' || error?.name === 'AbortError') {
          throw new ProviderError(504, 'PROVIDER_TIMEOUT', 'The train data provider did not respond in time.');
        }
        const causeCode = typeof error?.cause?.code === 'string' && /^[A-Z0-9_]+$/.test(error.cause.code)
          ? error.cause.code
          : 'UNKNOWN';
        process.stderr.write(`RailCore network request failed (${error?.name || 'Error'}; ${causeCode}).\n`);
        throw new ProviderError(502, 'PROVIDER_UNAVAILABLE', 'The train data provider could not be reached.');
      }

      if (response.status === 429) {
        rateLimited = true;
        continue;
      }
      if (!response.ok) throw mapProviderStatus(response.status);
      this.activeApiKeyIndex = keyIndex;
      break;
    }
    if (rateLimited && (!response || response.status === 429)) throw mapProviderStatus(429);

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

function normalizeApiKeys(values) {
  if (!Array.isArray(values)) throw new Error('RailCore API keys must be provided as an array.');
  return values.filter((value) => typeof value === 'string' && value.trim()).map((value) => value.trim());
}

function validateBaseUrl(value) {
  let parsed;
  try { parsed = new URL(value); } catch {
    throw new Error('RAILCORE_BASE_URL must be a valid HTTPS URL.');
  }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('RAILCORE_BASE_URL must be HTTPS and must not contain credentials, query, or fragment.');
  }
  return parsed.toString().replace(/\/$/, '');
}
