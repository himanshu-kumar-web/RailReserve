# Phase 2 contracts: bookings

Package root is `com.railreserve`. Existing Phase 1 search remains in place. The database version is now 2. The upgrade path creates `bookings` and indexes only; it does not drop or rewrite `trains`, `journeys`, or `seats`.

## Database and data access (Member 1)

- `BookingRequest(long journeyId, long seatId, TravelClass travelClass, String passengerName, int age, String gender)` contains one passenger and one requested seat.
- `Booking` is an immutable record of the PNR, train, journey, passenger, seat, class, fare snapshot, status, booking time, and cancellation time.
- `BookingDao.getAvailableSeats(long journeyId, String travelClass)` returns available seats from SQLite.
- `BookingDao.createBooking(BookingRequest request)` atomically claims an available seat and stores the booking; returns the saved booking with its PNR.
- `BookingDao.getBookings()` returns joined booking history, newest first.
- `BookingDao.cancelBooking(long bookingId)` atomically marks a confirmed booking cancelled and releases its seat.
- `SQLiteBookingDao(DatabaseHelper)` implements the DAO with bound selection arguments and short-lived cursors.
- The partial unique index `idx_one_confirmed_booking_per_seat` permits only one active booking per seat while allowing cancelled history and later rebooking.

## Business logic (Member 2)

- `BookingService.getAvailableSeats(long journeyId, String travelClass)` validates the journey and class.
- `BookingService.createBooking(BookingRequest request)` validates name (2–80 characters), age (1–120), and gender before delegating.
- `BookingService.getBookings()` retrieves persistent history.
- `BookingService.cancelBooking(long bookingId)` validates and delegates cancellation.
- `BookingServiceImpl(BookingDao)` is constructor-injected.
- `BookingServiceFactory.create(Context)` wires SQLite dependencies with application context.

PNRs are generated inside the DAO with `SecureRandom` as ten numeric digits. Existing PNRs are checked and uniqueness is enforced by SQLite's unique constraint. The fare is read from the selected seat, then copied to the booking row as a historical fare snapshot.

## UI and integration (Member 3)

- `TrainResultsActivity` passes actual `journey_id` and `train_id`, plus train/route/date/time/class display data, to `BookingActivity`.
- `BookingActivity` requires `journey_id`, `train_id`, `train_number`, `train_name`, `source`, `destination`, `date` (ISO `yyyy-MM-dd`), `departure`, `arrival`, `arrival_offset` (integer), and `travel_class` (`SL` or `3A`). It loads available seats from the service and displays confirmation only after the service returns a committed booking.
- `MyBookingsActivity` reloads the database when resumed and asks for confirmation before cancellation.
- `BookingAdapter` renders joined booking history and exposes cancellation only for `CONFIRMED` rows.

`BookingDao` and `BookingService` are interfaces; UI code depends on `BookingService`, while `SQLiteBookingDao` is the SQLite implementation. Models encapsulate their fields. Adapters extend Android `BaseAdapter` for the platform rendering contract; domain inheritance remains unnecessary. A future account domain could use an abstract account and role-specific behavior only if login/admin workflows are actually added.

All displayed PNRs and tickets are local RailReserve demonstrations. They are not Indian Railways PNRs and do not represent a real reservation.
