package com.pauluno.finledger.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finledger.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private long capacity = 120;
    private long refillPerSecond = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRefillPerSecond() {
        return refillPerSecond;
    }

    public void setRefillPerSecond(long refillPerSecond) {
        this.refillPerSecond = refillPerSecond;
    }
}
