import { ApiError } from './errors.js';

function scalar(value, name) {
  if (typeof value !== 'string') {
    throw new ApiError(400, 'INVALID_INPUT', `${name} is required.`);
  }
  return value.trim();
}

export function requiredText(value, name, min = 1, max = 80) {
  const result = scalar(value, name);
  if (result.length < min || result.length > max) {
    throw new ApiError(400, 'INVALID_INPUT', `${name} must be between ${min} and ${max} characters.`);
  }
  return result;
}

export function stationCode(value, name) {
  const result = scalar(value, name).toUpperCase();
  if (!/^[A-Z0-9]{2,6}$/.test(result)) {
    throw new ApiError(400, 'INVALID_INPUT', `${name} must be a valid station code.`);
  }
  return result;
}

export function trainNumber(value) {
  const result = scalar(value, 'Train number');
  if (!/^\d{5}$/.test(result)) {
    throw new ApiError(400, 'INVALID_INPUT', 'Train number must contain five digits.');
  }
  return result;
}

export function journeyDate(value, name = 'Journey date') {
  const result = scalar(value, name);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(result)) {
    throw new ApiError(400, 'INVALID_INPUT', `${name} must use YYYY-MM-DD format.`);
  }
  const parsed = new Date(`${result}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== result) {
    throw new ApiError(400, 'INVALID_INPUT', `${name} must be a real calendar date.`);
  }
  return result;
}

export function enumValue(value, name, allowed) {
  const result = scalar(value, name).toUpperCase();
  if (!allowed.includes(result)) {
    throw new ApiError(400, 'INVALID_INPUT', `${name} is not supported.`);
  }
  return result;
}

export function queryString(value) {
  return requiredText(value, 'Search query', 1, 64);
}

export function stationLimit(value) {
  if (value === undefined) return 10;
  const result = scalar(value, 'Limit');
  if (!['5', '10', '20', '50'].includes(result)) {
    throw new ApiError(400, 'INVALID_INPUT', 'Limit must be 5, 10, 20, or 50.');
  }
  return Number(result);
}

export const CLASS_CODES = ['1A', '2A', '3A', '3E', 'CC', 'EC', 'EA', 'FC', 'SL', '2S', 'VS', 'CH', 'SH', 'VC', 'EV'];
export const QUOTA_CODES = ['GN', 'TQ', 'PT', 'LD', 'DF', 'FT', 'SS', 'YU', 'DP', 'HP', 'PH'];
