# Phase 1 contracts

Package root: `com.railreserve`. Data access flows from Activities through `TrainSearchService` to `TrainDao`, whose SQLite implementation uses `DatabaseHelper`. Activities never execute SQL. The factory receives an application context and wires concrete implementations.

## Database/data ownership

- `Train`: immutable train identity, route, times, and arrival day offset.
- `Journey`: immutable journey id, train id, and ISO departure date.
- `Seat`: immutable seat id, journey, class, coach/number, fare, and status.
- `TravelClass`: `SLEEPER("SL", "Sleeper")` and `AC_THREE_TIER("3A", "AC 3 Tier")`; `fromCode(String)` validates a code.
- `SeatStatus`: `AVAILABLE`, `BOOKED`.
- `TrainSearchResult`: immutable result fields with getters for train number/name, stations, times, arrival offset, date, class, fare, total seats, and available seats.
- `DatabaseHelper(Context)`: creates constrained `trains`, `journeys`, and `seats` tables, enables foreign keys, and adds search indexes. Future migrations must preserve records.
- `DemoDataSeeder.ensureSchedule(SQLiteDatabase)`: transactionally inserts eight fictional trains, today's through 14 days ahead, and missing 18-seat SL / 12-seat 3A inventory. Conflict-ignore plus unique constraints preserve existing statuses and prevent duplicates.
- `TrainDao.search(String source, String destination, String date, TravelClass travelClass)`: parameterized, case-insensitive route query with grouped seat counts and departure-time sorting.
- `TrainDao.getStations()`: distinct sorted station names.
- `SQLiteTrainDao(DatabaseHelper)`: SQLite implementation of `TrainDao`.

## Business logic ownership

- `TrainSearchService.ensureDemoData()`
- `TrainSearchService.getStationSuggestions()` returns `List<String>`.
- `TrainSearchService.search(String source, String destination, String date, TravelClass travelClass)` returns `List<TrainSearchResult>` after normalizing and validating inputs/date.
- `TrainSearchServiceImpl(TrainDao, DatabaseHelper, DemoDataSeeder)`: constructor-injected implementation.
- `TrainSearchServiceFactory.create(Context)`: creates the application-context-backed service.

## UI ownership

- `MainActivity`: station autocomplete, station swap, date picker (today to +14 days), class selector, validation, background seeding/search handoff.
- `TrainResultsActivity`: reads required extras, asynchronously loads results, and handles progress, empty, and error states.
- `TrainResultAdapter`: binds each result to `item_train_result.xml`, including overnight arrival, INR fare, and availability/sold-out state.

`TrainResultsActivity` requires Intent extras: `search_source` (String), `search_destination` (String), `search_date` (ISO `yyyy-MM-dd` String), and `search_class` (`SL` or `3A`).

## OOP notes

Encapsulation is shown by private final model fields and getters. `TrainDao` and `TrainSearchService` provide abstraction and interfaces; their SQLite/service implementations can be substituted through runtime polymorphism. `TrainResultAdapter` extends Android's `BaseAdapter` to implement the platform list-rendering contract. No artificial domain inheritance is needed for Phase 1; a later user-management phase could introduce a shared abstract account with meaningful Passenger and Admin specializations if role behavior warrants it.
