export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export class ProviderError extends Error {
  constructor(status, code, message, options = {}) {
    super(message, options);
    this.status = status;
    this.code = code;
  }
}

export function mapProviderStatus(status) {
  if (status === 401 || status === 403) {
    return new ProviderError(502, 'PROVIDER_AUTH_ERROR', 'The train data provider rejected its credentials.');
  }
  if (status === 404) {
    return new ProviderError(404, 'PROVIDER_NOT_FOUND', 'The requested train data was not found.');
  }
  if (status === 429) {
    return new ProviderError(503, 'PROVIDER_RATE_LIMITED', 'The train data provider rate limit was reached.');
  }
  if (status >= 500) {
    return new ProviderError(502, 'PROVIDER_UNAVAILABLE', 'The train data provider is temporarily unavailable.');
  }
  return new ProviderError(502, 'PROVIDER_REQUEST_FAILED', 'The train data provider could not complete the request.');
}
