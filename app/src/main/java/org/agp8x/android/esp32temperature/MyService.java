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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MyService extends Service {
    private boolean isScanning;
    private static final long SCAN_PERIOD = 10_000;
    private Handler handler;
    private BluetoothLeScanner scanner;
    private List<BluetoothDevice> devices;
    private Map<BluetoothGatt, MyCallback> gatts;
    private ScanCallback leScanCallback = new ScanCallback() {
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
    };


    private final IBinder binder = new LocalBinder();
    private final List<ServiceListener> listeners;
    private final Map<Integer,List<Integer>> data;

    public MyService() {
        handler = new Handler();
        devices = new ArrayList<>();
        gatts = new HashMap<>();
        listeners = new ArrayList<>();
        data = new ConcurrentHashMap<>();
    }

    private void notifyListeners(){
        for (ServiceListener listener : listeners) {
            if (listener != null) {
                try {
                    listener.handleUpdate();
                }catch (Exception e){
                    Log.e(TAG, "notifyListeners: exceptional", e);
                }
            }
        }
    }

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

    public final static UUID TEPERATURE_UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb");


    private void broadcastUpdate(String action, BluetoothGatt gatt, int index) {
        Log.d(TAG, "broadcastUpdate_: " + action);
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(String action, BluetoothGattCharacteristic characteristic, BluetoothGatt gatt, int index) {
        Log.d(TAG, "broadcastUpdate: " + action + "@ gatt (" + gatt + ")[" + index + " ||| " + characteristic);
        Intent intent = new Intent(action);
        //TODO: add data
        Log.d(TAG, "broadcastUpdate raw: " + Arrays.toString(characteristic.getValue()));
        Log.d(TAG, "broadcastUpdate uuid: " + characteristic.getUuid());
        if (!TEPERATURE_UUID.equals(characteristic.getUuid())){
            return;
        }
        Log.d(TAG, "broadcastUpdate instance: " + characteristic.getInstanceId());
        index = characteristic.getInstanceId();
        int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        Log.d(TAG, "broadcastUpdate sint16: " + value);
        if (!data.containsKey(index)){
            data.put(index, new ArrayList<Integer>());
        }
        data.get(index).add(value);
        try {
            double value2 = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0);
            Log.d(TAG, "broadcastUpdate float: " + value2);
        } catch (Exception e) {
            // Log.e(TAG, "broadcastUpdate: ", e);
        }
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
            ArrayList<ScanFilter> filters = new ArrayList<>(); //TODO: restrict?
            scanner.startScan(filters, settings, leScanCallback);
        } else {
            isScanning = false;
            scanner.stopScan(leScanCallback);
        }
    }

    public void connect() {
        for (BluetoothDevice device : devices) {
            int index = devices.indexOf(device);
            MyCallback cb = new MyCallback(index);
            BluetoothGatt gatt = device.connectGatt(this, true, cb);
            gatts.put(gatt, cb);
            Log.d(TAG, "connect: " + gatt + cb);
            //gatt.discoverServices();

        }
    }

    public Map<Integer, List<Integer>> getData() {
        return data;
    }
    public void clearData(){
        data.clear();
    }
    public void addListener(ServiceListener sl){
        listeners.add(sl);
    }
    public void removeListener(ServiceListener sl){
        listeners.remove(sl);
    }


    public class MyCallback extends BluetoothGattCallback {

        private final int index;

        public MyCallback(int index) {
            super();
            this.index = index;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                connectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction, gatt, index);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                connectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction, gatt, index);
            }
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt, index);
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
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt, index);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged: " + characteristic);
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt, index);
        }
    }

    public interface ServiceListener {
        public void handleUpdate();
    }
}
