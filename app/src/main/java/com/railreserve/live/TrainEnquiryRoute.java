package com.railreserve.live;

/** The exact scheduled stops used for fare, availability, and local demo booking enquiries. */
public final class TrainEnquiryRoute {
    private final LiveTrainStop boardingStop;
    private final LiveTrainStop alightingStop;
    private final boolean searchedSource;
    private final boolean searchedDestination;

    TrainEnquiryRoute(LiveTrainStop boardingStop, LiveTrainStop alightingStop,
                      boolean searchedSource, boolean searchedDestination) {
        this.boardingStop = boardingStop;
        this.alightingStop = alightingStop;
        this.searchedSource = searchedSource;
        this.searchedDestination = searchedDestination;
    }

    public LiveTrainStop getBoardingStop() { return boardingStop; }
    public LiveTrainStop getAlightingStop() { return alightingStop; }
    public boolean usesSearchedSource() { return searchedSource; }
    public boolean usesSearchedDestination() { return searchedDestination; }
}
