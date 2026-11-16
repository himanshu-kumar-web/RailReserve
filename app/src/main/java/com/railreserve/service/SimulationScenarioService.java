package com.railreserve.service;

import com.railreserve.dao.SimulationScenarioDao;
import com.railreserve.model.SimulationRequest;
import com.railreserve.model.SimulationScenarioPolicy;
import com.railreserve.model.TrainSearchResult;
import java.time.LocalDate;

/** Prepares local inventory, never books a seat or calls a provider. */
public final class SimulationScenarioService {
    private final SimulationScenarioDao dao;
    public SimulationScenarioService(SimulationScenarioDao dao) { this.dao = dao; }

    public TrainSearchResult prepare(SimulationRequest request) {
        validateJourney(request);
        if (request.getFareSnapshot() == null) SimulationScenarioPolicy.fareRupees(request.getTravelClass());
        else {
            request.getFareSnapshot().requireContext(request.getNumber(),request.getSourceCode(),request.getDestinationCode(),request.getDate(),request.getTravelClass().getCode(),"GN");
            request.getFareSnapshot().requireFresh();
            request.getFareSnapshot().requireAvailableForBooking();
        }
        return dao.getOrCreate(request);
    }

    public static void validateJourney(SimulationRequest request) {
        if (request == null || !request.getNumber().matches("[0-9]{5}") || request.getName().isEmpty()) {
            throw new IllegalArgumentException("The selected train is missing its number or name.");
        }
        if (!request.getSourceCode().matches("[A-Z0-9]{2,6}") || !request.getDestinationCode().matches("[A-Z0-9]{2,6}")
                || request.getSourceCode().equals(request.getDestinationCode())) {
            throw new IllegalArgumentException("Choose two different stations before simulating a booking.");
        }
        LocalDate date;
        try { date = LocalDate.parse(request.getDate()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Choose a valid journey date."); }
        if (date.isBefore(LocalDate.now()) || date.isAfter(LocalDate.now().plusDays(14))) {
            throw new IllegalArgumentException("Choose a journey date within the next 14 days.");
        }
        if(request.getTravelClass()==null) throw new IllegalArgumentException("Choose a supported travel class.");
        if (!supportsTimetable(request.getDepartureDay(), request.getArrivalDay())) {
            throw new IllegalArgumentException("This timetable cannot be represented by the current demo. Use the existing Demo Booking section.");
        }
    }

    public static boolean supportsTimetable(Integer departureDay, Integer arrivalDay) {
        if (departureDay == null || arrivalDay == null || departureDay < 0 || arrivalDay < 0) return false;
        long difference = (long) arrivalDay - departureDay;
        return difference >= 0 && difference <= 1; // Preserve the existing trains CHECK constraint, never truncate days.
    }
}
