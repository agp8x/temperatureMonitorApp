package org.agp8x.android.esp32temperature;

import java.time.LocalDateTime;
import java.util.UUID;

public class Measurement {
    private final int value;
    private final UUID uuid;
    private final int instanceId;
    private final int index;
    private final LocalDateTime timestamp;

    public Measurement(UUID uuid, int instanceId, int index, int value) {
        this.uuid = uuid;
        this.instanceId = instanceId;
        this.index = index;
        this.value = value;
        this.timestamp = LocalDateTime.now();
    }

    public double getValue() {
        return value / 100.0;
    }

    public int getIndex() {
        return index;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
