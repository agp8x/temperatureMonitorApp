package org.agp8x.android.esp32temperature;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements MyService.ServiceListener {
//https://developer.android.com/guide/topics/connectivity/bluetooth-le#java
    private static final int REQUEST_ENABLE_BT = 1234;
    private static final String TAG = "ESP32";
    private static final int REQUEST_ALLOW_LOCATION = 4321;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MyService.LocalBinder binder = (MyService.LocalBinder) iBinder;
            service = binder.getService();
            service.addListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            service = null;
        }
    };
    private MyService service;
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: "+ intent.getAction());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        checkLocationPermission();
        Intent btService = new Intent(this, MyService.class);
        startService(btService);
        Intent service = new Intent(this, MyService.class);
        bindService(service, connection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onStop() {
        unbindService(connection);
        super.onStop();
    }

    public void scanBTN(View view) {
        service.scanLeDevice(true, bluetoothAdapter);
    }

    private void checkLocationPermission() {
        boolean permissionAccessCoarseLocationApproved =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (!permissionAccessCoarseLocationApproved) {
            // App doesn't have access to the device's location at all. Make full request
            // for permission.
            ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                    },
                    REQUEST_ALLOW_LOCATION);
        }
    }

    public void connectBTN(View view) {
        service.connect();
    }

    public void refreshBTN(View view) {
        Map<Integer, List<Integer>> data = new HashMap<>(service.getData());
        LinearLayout ll = findViewById(R.id.linearLayout);
        ll.removeAllViews();
        Log.wtf(TAG, "DATA: "+ data);
        for (Map.Entry<Integer, List<Integer>> sensor : data.entrySet()) {
            //LinearLayout line = (LinearLayout) LinearLayout.inflate(this, R.layout.sensor_line, ll);
            LinearLayout line = new LinearLayout(this);
            TextView number = new TextView(this);
            number.setText(String.valueOf(sensor.getKey()));
            line.addView(number);
            TextView placeholder = new TextView(this);
            placeholder.setText("  ");
            line.addView(placeholder);
            TextView value = new TextView(this);
            Double v = sensor.getValue().get(sensor.getValue().size() - 1) / 100.0;
            Log.wtf(TAG, "refreshBTN: " + v);
            value.setText(String.valueOf(getString(R.string.degree, v)));
            line.addView(value);
            ll.addView(line);
        }
        ll.setVisibility(View.VISIBLE);
        ll.invalidate();
    }

    public void clear(View view) {
        service.clearData();
    }

    @Override
    public void handleUpdate() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshBTN(findViewById(R.id.refresh));
            }
        });
    }
}
