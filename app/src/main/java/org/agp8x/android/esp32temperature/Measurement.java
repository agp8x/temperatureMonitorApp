package org.agp8x.android.esp32temperature;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.agp8x.android.esp32temperature.Constants.sensors;

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
        this.timestamp = now();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    public Measurement(BluetoothGattCharacteristic characteristic) {
        uuid = characteristic.getUuid();
        instanceId = characteristic.getInstanceId();
        index = sensors.indexOf(uuid);
        value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        timestamp = now();
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

    public String toCSV() {
        StringBuilder sb = new StringBuilder();
        sb.append(uuid).
                append(",").
                append(instanceId).
                append(",").
                append(index).
                append(",").
                append(value).
                append(",").
                append(timestamp);
        return sb.toString();
    }
}
