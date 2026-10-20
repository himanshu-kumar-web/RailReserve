import { ProviderError } from '../errors.js';

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function text(value) {
  return typeof value === 'string' && value.length ? value : null;
}

function number(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function boolean(value) {
  return typeof value === 'boolean' ? value : null;
}

function station(value) {
  if (typeof value === 'string') return { code: text(value), name: null };
  const item = object(value);
  return { code: text(item.code), name: text(item.name) };
}

export function normalizeStations(data) {
  if (!Array.isArray(data)) throw invalidProviderData('Station search did not return a station list.');
  if (data.some((entry) => !entry || typeof entry !== 'object' || Array.isArray(entry)
    || typeof entry.code !== 'string' || typeof entry.name !== 'string')) {
    throw invalidProviderData('Station search returned a malformed station.');
  }
  return data.map((entry) => {
    const item = object(entry);
    return { code: text(item.code), name: text(item.name), city: text(item.city) };
  });
}

export function normalizeBetween(data) {
  const item = object(data);
  if (!isNamedStation(item.from) || !isNamedStation(item.to) || !Number.isFinite(item.count) || !Array.isArray(item.trains)) {
    throw invalidProviderData('Train search returned an incomplete result.');
  }
  if (item.trains.some((entry) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return true;
    const train = object(entry.train);
    const from = object(entry.from);
    const to = object(entry.to);
    return typeof train.number !== 'string' || typeof train.name !== 'string'
      || typeof from.departure !== 'string' || !Number.isFinite(from.day) || !Number.isFinite(from.sequence)
      || typeof to.arrival !== 'string' || !Number.isFinite(to.day) || !Number.isFinite(to.sequence);
  })) throw invalidProviderData('Train search returned a malformed train.');
  const trains = item.trains;
  return {
    from: station(item.from),
    to: station(item.to),
    count: number(item.count),
    trains: trains.map((entry) => {
      const train = object(entry);
      const from = object(train.from);
      const to = object(train.to);
      return {
        number: text(object(train.train).number),
        name: text(object(train.train).name),
        type: text(object(train.train).type),
        runDays: Array.isArray(object(train.train).runDays) ? object(train.train).runDays : null,
        from: {
          station: station(item.from), departure: text(from.departure),
          day: number(from.day), sequence: number(from.sequence),
        },
        to: {
          station: station(item.to), arrival: text(to.arrival),
          day: number(to.day), sequence: number(to.sequence),
        },
        distanceKm: number(train.distance),
        durationMinutes: number(train.duration),
        totalHaltsBetween: number(train.totalHaltsBetween),
      };
    }),
  };
}

export function normalizeSchedule(data) {
  const item = object(data);
  const train = object(item.train);
  if (typeof train.number !== 'string' || typeof train.name !== 'string' || !Array.isArray(item.route)
    || item.route.some((stop) => !stop || typeof stop !== 'object' || Array.isArray(stop)
      || !Number.isFinite(stop.sequence) || !isNamedStation(stop.station))) {
    throw invalidProviderData('Train schedule returned an incomplete route.');
  }
  const route = item.route;
  return {
    train: {
      number: text(train.number), name: text(train.name), type: text(train.type), category: text(train.category),
      source: station(train.source), destination: station(train.destination),
      runDays: Array.isArray(train.runDays) ? train.runDays : null,
      distanceKm: number(train.distance), durationMinutes: number(train.duration),
      totalHalts: number(train.totalHalts),
    },
    stops: route.map((entry) => {
      const stop = object(entry);
      return {
        sequence: number(stop.sequence), station: station(stop.station),
        arrival: text(stop.arrival), departure: text(stop.departure),
        arrivalDay: number(stop.arrivalDay), departureDay: number(stop.departureDay),
        distanceKm: number(stop.distance), isHalt: boolean(stop.isHalt), platform: text(stop.platform),
      };
    }),
  };
}

const FARE_FIELDS = ['baseFare', 'reservationCharge', 'superfastCharge', 'otherCharge', 'tatkalFare', 'goodsServiceTax', 'cateringCharge', 'dynamicFare'];

export function normalizeFare(data) {
  const item = object(data);
  const providerBreakdown = object(item.breakdown);
  const breakdown = Object.fromEntries(FARE_FIELDS.map((key) => [key, number(providerBreakdown[key])]));
  const totalFare = number(item.totalFare);
  return {
    trainNumber: text(item.trainNumber), trainName: text(item.trainName),
    source: station(item.sourceStation), destination: station(item.destinationStation),
    classCode: text(item.classCode), quotaCode: text(item.quotaCode),
    fareKnown: totalFare !== null, totalFare, breakdown,
  };
}

function classifyAvailability(raw) {
  if (!raw) return 'UNKNOWN';
  const status = raw.trim().toUpperCase();
  if (/^AVAILABLE(?:\b|-)/.test(status)) return 'AVAILABLE';
  if (/^RAC(?:\b|\s|\d)/.test(status)) return 'RAC';
  if (/(?:WL|WAIT\s*LIST)/.test(status)) return 'WAITLIST';
  return 'UNKNOWN';
}

export function normalizeAvailability(data, requestedDate) {
  const item = object(data);
  const days = Array.isArray(item.avlDayList) ? item.avlDayList : [];
  const day = days.find((entry) => object(entry).availablityDate === requestedDate);
  const rawStatus = day ? text(object(day).availablityStatus) : null;
  return {
    trainNumber: text(item.trainNumber), trainName: text(item.trainName),
    source: station(item.sourceStation), destination: station(item.destinationStation),
    classCode: text(item.classCode), quotaCode: text(item.quotaCode),
    requestedJourneyDate: requestedDate,
    availabilityDate: day ? text(object(day).availablityDate) : null,
    availabilityStatus: rawStatus,
    statusType: classifyAvailability(rawStatus),
  };
}

function isNamedStation(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
    && typeof value.code === 'string' && typeof value.name === 'string';
}

function invalidProviderData(message) {
  return new ProviderError(502, 'INVALID_PROVIDER_RESPONSE', message);
}
