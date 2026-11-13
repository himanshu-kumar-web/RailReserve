package com.railreserve.live;

import java.util.Objects;

/** The backend's response identity must agree with the request before it can be used. */
public final class EnquiryContext {
    private final String number, from, to, date, clazz, quota;
    public EnquiryContext(String number, String from, String to, String date, String clazz, String quota) {
        this.number=number; this.from=from; this.to=to; this.date=date; this.clazz=clazz; this.quota=quota;
    }
    public void requireMatch(String number,String from,String to,String date,String clazz,String quota) throws BackendException {
        if (!Objects.equals(this.number,number)||!Objects.equals(this.from,from)||!Objects.equals(this.to,to)
                ||!Objects.equals(this.date,date)||!Objects.equals(this.clazz,clazz)||!Objects.equals(this.quota,quota))
            throw new BackendException("INVALID_RESPONSE","Provider data did not match the selected train, route, date, class and quota.");
    }
}
