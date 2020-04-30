package org.agp8x.android.esp32temperature;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.agp8x.android.esp32temperature.Constants.ENVIONMENTAL_SENSING_UUID;

public class MyService extends Service {
    private boolean isScanning;
    private static final long SCAN_PERIOD = 10_000;
    private Handler handler;
    private BluetoothLeScanner scanner;
    private List<BluetoothDevice> devices;
    private Map<BluetoothGatt, MyCallback> gatts;
    private ScanCallback leScanCallback = new MyScanCallback();
    private static final String TAG = "ESP32_S";
    private int connectionState = STATE_DISCONNECTED;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final List<UUID> characteristicWhitelist;
    private final IBinder binder = new LocalBinder();
    private final List<ServiceListener> listeners;
    private final Map<Integer, List<Measurement>> data; // TODO: use map for latest value, store history with timestamps
    private final Map<Integer, Measurement> latestData;

    public MyService() {
        handler = new Handler();
        devices = new ArrayList<>();
        gatts = new HashMap<>();
        listeners = new ArrayList<>();
        data = new ConcurrentHashMap<>();
        characteristicWhitelist = Constants.projectSensors();
        latestData = new HashMap<>();
    }

    private void notifyListeners() {
        for (ServiceListener listener : listeners) {
            if (listener != null) {
                try {
                    listener.handleUpdate();
                } catch (Exception e) {
                    Log.e(TAG, "notifyListeners: exceptional", e);
                }
            }
        }
    }

    private void broadcastUpdate(String action, BluetoothGatt gatt) {
        Log.d(TAG, "broadcastUpdate_: " + action);
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(String action, BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        Log.d(TAG, "broadcastUpdate: " + action + "@ gatt (" + gatt + ") ||| " + characteristic);
        Intent intent = new Intent(action);
        //TODO: add data
        Log.d(TAG, "broadcastUpdate raw: " + Arrays.toString(characteristic.getValue()));
        UUID uuid = characteristic.getUuid();
        Log.d(TAG, "broadcastUpdate uuid: " + uuid);
        if (!characteristicWhitelist.contains(uuid)) {
            Log.wtf(TAG, "broadcastUpdate: FAILED for "+uuid.toString());
            return;
        }
        int instanceId = characteristic.getInstanceId();
        Log.d(TAG, "broadcastUpdate instance: " + instanceId);
        int index = characteristicWhitelist.indexOf(uuid);
        int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        Measurement m = new Measurement(uuid, instanceId, index, value);
        latestData.put(index, m);
        Log.d(TAG, "broadcastUpdate sint16: " + value);
        if (!data.containsKey(index)) {
            data.put(index, new ArrayList<Measurement>());
        }
        data.get(index).add(m);
        notifyListeners();
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        for (BluetoothGatt gatt : gatts.keySet()) {
            gatt.close();
        }
        stopSelf();
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {
        MyService getService() {
            return MyService.this;
        }
    }

    public void scanLeDevice(final boolean enable, BluetoothAdapter bluetoothAdapter) {
        if (scanner == null) {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        if (enable) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isScanning = false;
                    scanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);
            isScanning = true;
            ScanSettings settings = new ScanSettings.Builder().build(); //TODO: change settings?
            ArrayList<ScanFilter> filters = new ArrayList<>();
           // filters.add(new ScanFilter.Builder().setDeviceName("mpy-temp").setServiceUuid(new ParcelUuid(ENVIONMENTAL_SENSING_UUID)).build());
            scanner.startScan(filters, settings, leScanCallback);
        } else {
            isScanning = false;
            scanner.stopScan(leScanCallback);
        }
    }

    public void connect() {
        for (BluetoothDevice device : devices) {
            MyCallback cb = new MyCallback();
            BluetoothGatt gatt = device.connectGatt(this, true, cb);
            gatts.put(gatt, cb);
            Log.d(TAG, "connect: " + gatt + cb);
            //gatt.discoverServices();

        }
    }

    public Map<Integer, List<Measurement>> getData() {
        return data;
    }

    public Map<Integer, Measurement> getLatestData() {
        return latestData;
    }

    public void clearData() {
        data.clear();
    }

    public void addListener(ServiceListener sl) {
        listeners.add(sl);
    }

    public void removeListener(ServiceListener sl) {
        listeners.remove(sl);
    }


    public interface ServiceListener {
        public void handleUpdate();
    }

    private class MyCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                connectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction, gatt);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction, gatt);
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt);
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(TAG, "onServicesDiscovered: " + characteristic);
                        Log.d(TAG, "onServicesDiscovered: " + gatt.readCharacteristic(characteristic));
                        gatt.setCharacteristicNotification(characteristic, true);
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "onCharacteristicRead: " + characteristic);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged: " + characteristic);
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt);
        }
    }

    private class MyScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onBatchScanResults(" + callbackType + "," + result + "): " + info(result));
            if (!devices.contains(result.getDevice())) {
                devices.add(result.getDevice());
                connect();
            }
        }

        private String info(ScanResult result) {
            StringBuilder sb = new StringBuilder();
            sb.append(result.getDevice().getName()).append(": ").append(result.getDevice().getAddress());
            sb.append(" (");
            if (result.getDevice().getUuids() != null) {
                for (ParcelUuid uuid : result.getDevice().getUuids()) {
                    sb.append(uuid.toString()).append("; ");
                }
                sb.append(")");
            }
            return sb.toString();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults(" + results.size() + ")");
            for (ScanResult result : results) {
                onScanResult(-1, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "scan failed (" + errorCode + ")");
        }
    }
}
