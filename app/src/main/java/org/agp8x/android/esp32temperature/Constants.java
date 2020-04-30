package org.agp8x.android.esp32temperature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public final class Constants {

    private static List<UUID> sensors;

    private Constants() {
    }

    public final static String TEMPERATURE = "00002a6e-0000-1000-8000-00805f9b34fb";
    public final static UUID TEMPERATURE_UUID = UUID.fromString(TEMPERATURE);
    public final static String SCIENTIFIC_TEMPERATURE = "00002a3c-0000-1000-8000-00805f9b34fb";
    public final static UUID SCIENTIFIC_TEMPERATURE_UUID = UUID.fromString(SCIENTIFIC_TEMPERATURE);
    public final static String TEMPERATURE_CELSIUS = "00002a1f-0000-1000-8000-00805f9b34fb";
    public final static UUID TEMPERATURE_CELSIUS_UUID = UUID.fromString(TEMPERATURE_CELSIUS);
    public final static String INTERMEDIATE_TEMPERATURE = "00002a1e-0000-1000-8000-00805f9b34fb";
    public final static UUID INTERMEDITA_TEMPERATURE_UUID = UUID.fromString(INTERMEDIATE_TEMPERATURE);
    public final static String TEMPERATURE_MEASUREMENT = "00002a1c-0000-1000-8000-00805f9b34fb";
    public final static UUID TEMPERATURE_MEASUREMENT_UUID = UUID.fromString(TEMPERATURE_MEASUREMENT);
    public final static String ENVIRONMENTAL_SENSING = "0000181A-0000-1000-8000-00805f9b34fb";
    public final static UUID ENVIONMENTAL_SENSING_UUID = UUID.fromString(ENVIRONMENTAL_SENSING);
    
    public static List<UUID> projectSensors(){
        if (sensors == null) {
            sensors = new ArrayList<>(5);
            sensors.add(TEMPERATURE_UUID);
            sensors.add(SCIENTIFIC_TEMPERATURE_UUID);
            sensors.add(TEMPERATURE_CELSIUS_UUID);
            sensors.add(INTERMEDITA_TEMPERATURE_UUID);
            sensors.add(TEMPERATURE_MEASUREMENT_UUID);
        }
        return new ArrayList<>(sensors);
    } 
}
