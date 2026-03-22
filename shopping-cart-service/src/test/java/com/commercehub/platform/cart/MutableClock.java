package com.commercehub.platform.cart;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

class MutableClock extends Clock {
    private Instant instant;
    private final ZoneId zoneId;

    MutableClock(Instant instant, ZoneId zoneId) {
        this.instant = instant;
        this.zoneId = zoneId;
    }

    void advanceSeconds(long seconds) {
        instant = instant.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
