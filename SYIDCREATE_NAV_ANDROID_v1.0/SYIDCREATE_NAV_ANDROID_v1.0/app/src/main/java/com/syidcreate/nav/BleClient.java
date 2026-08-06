package com.syidcreate.nav;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public final class BleClient {
    public interface Listener {
        void onBleState(String state, boolean connected);
        void onBleLog(String line);
    }

    public static final String DEVICE_NAME = "SYIDCREATE NAV";
    public static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private static final String TAG = "SYID-BLE";
    private static final long SCAN_WINDOW_MS = 15000L;
    private static final long RECONNECT_DELAY_MS = 2500L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Queue<byte[]> writeQueue = new ArrayDeque<>();

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private boolean scanning;
    private boolean manuallyStopped;
    private boolean connected;
    private boolean writeBusy;
    private int negotiatedMtu = 23;
    private String state = "BELUM AKTIF";

    public BleClient(Context context) {
        this.context = context.getApplicationContext();
        BluetoothManager manager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onBleState(state, connected);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return connected;
    }

    public String getState() {
        return state;
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public void startAutoConnect() {
        manuallyStopped = false;
        if (!hasPermissions()) {
            setState("IZIN BLUETOOTH BELUM DIBERIKAN", false);
            return;
        }
        if (adapter == null) {
            setState("BLUETOOTH TIDAK TERSEDIA", false);
            return;
        }
        if (!adapter.isEnabled()) {
            setState("BLUETOOTH PONSEL MATI", false);
            return;
        }
        if (connected || gatt != null || scanning) {
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            setState("SCANNER BLE TIDAK TERSEDIA", false);
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        try {
            scanner.startScan(null, settings, scanCallback);
            scanning = true;
            setState("MENCARI " + DEVICE_NAME, false);
            log("Pemindaian BLE dimulai");
            handler.removeCallbacks(scanTimeout);
            handler.postDelayed(scanTimeout, SCAN_WINDOW_MS);
        } catch (SecurityException e) {
            setState("IZIN BLUETOOTH DITOLAK", false);
            log("Gagal scan: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        manuallyStopped = true;
        handler.removeCallbacksAndMessages(null);
        stopScan();
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (SecurityException ignored) {
            }
        }
        gatt = null;
        rxCharacteristic = null;
        txCharacteristic = null;
        connected = false;
        synchronized (writeQueue) {
            writeQueue.clear();
            writeBusy = false;
        }
        setState("DIHENTIKAN", false);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (!scanning || scanner == null) return;
        try {
            scanner.stopScan(scanCallback);
        } catch (SecurityException ignored) {
        }
        scanning = false;
        handler.removeCallbacks(scanTimeout);
    }

    private final Runnable scanTimeout = () -> {
        stopScan();
        if (!connected && !manuallyStopped) {
            setState("PERANGKAT BELUM DITEMUKAN", false);
            handler.postDelayed(this::startAutoConnect, RECONNECT_DELAY_MS);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = null;
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermissions()) {
                    name = device.getName();
                }
            } catch (SecurityException ignored) {
            }
            if ((name == null || name.isEmpty()) && result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            if (DEVICE_NAME.equalsIgnoreCase(name)) {
                stopScan();
                connect(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            setState("SCAN GAGAL: " + errorCode, false);
            log("Kode kegagalan scan BLE: " + errorCode);
            if (!manuallyStopped) handler.postDelayed(BleClient.this::startAutoConnect, RECONNECT_DELAY_MS);
        }
    };

    @SuppressLint("MissingPermission")
    private void connect(BluetoothDevice device) {
        if (!hasPermissions()) return;
        setState("MENGHUBUNGKAN...", false);
        log("Perangkat ditemukan: " + DEVICE_NAME);
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            setState("KONEKSI DITOLAK", false);
            log("Gagal connectGatt: " + e.getMessage());
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected = true;
                setState("BLE TERHUBUNG, MEMBACA SERVICE", true);
                log("BLE tersambung");
                try {
                    boolean requested = bluetoothGatt.requestMtu(185);
                    if (!requested) discoverServices(bluetoothGatt);
                } catch (SecurityException ignored) {
                    discoverServices(bluetoothGatt);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                rxCharacteristic = null;
                txCharacteristic = null;
                synchronized (writeQueue) {
                    writeQueue.clear();
                    writeBusy = false;
                }
                try {
                    bluetoothGatt.close();
                } catch (SecurityException ignored) {
                }
                if (gatt == bluetoothGatt) gatt = null;
                setState("BLE TERPUTUS", false);
                log("BLE terputus, mencoba kembali");
                if (!manuallyStopped) handler.postDelayed(BleClient.this::startAutoConnect, RECONNECT_DELAY_MS);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Status GATT: " + status);
                try {
                    bluetoothGatt.disconnect();
                } catch (SecurityException ignored) {
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt bluetoothGatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu;
            discoverServices(bluetoothGatt);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState("SERVICE BLE GAGAL DIBACA", false);
                return;
            }
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                setState("NORDIC UART TIDAK DITEMUKAN", false);
                log("Pastikan firmware memakai UUID SYIDCREATE yang sama");
                return;
            }
            rxCharacteristic = service.getCharacteristic(RX_UUID);
            txCharacteristic = service.getCharacteristic(TX_UUID);
            if (rxCharacteristic == null) {
                setState("RX CHARACTERISTIC TIDAK DITEMUKAN", false);
                return;
            }
            enableTxNotifications(bluetoothGatt);
            setState("SYIDCREATE NAV TERHUBUNG", true);
            log("Nordic UART siap, MTU " + negotiatedMtu);
            handler.postDelayed(() -> {
                sendCurrentTime();
                sendLine("PING");
            }, 350L);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor descriptor, int status) {
            log(status == BluetoothGatt.GATT_SUCCESS ? "Balasan BLE diaktifkan" : "Notifikasi TX gagal: " + status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null) log("ESP32: " + new String(value, StandardCharsets.UTF_8).trim());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            synchronized (writeQueue) {
                writeBusy = false;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) log("Write BLE gagal: " + status);
            writeNext();
        }
    };

    @SuppressLint("MissingPermission")
    private void discoverServices(BluetoothGatt bluetoothGatt) {
        try {
            bluetoothGatt.discoverServices();
        } catch (SecurityException e) {
            setState("IZIN CONNECT HILANG", false);
        }
    }

    @SuppressLint("MissingPermission")
    private void enableTxNotifications(BluetoothGatt bluetoothGatt) {
        if (txCharacteristic == null) return;
        try {
            bluetoothGatt.setCharacteristicNotification(txCharacteristic, true);
            BluetoothGattDescriptor cccd = txCharacteristic.getDescriptor(CCCD_UUID);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(cccd);
            }
        } catch (SecurityException ignored) {
        }
    }

    public void sendCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
        Date now = new Date();
        sendLine("TIME|" + dateFormat.format(now) + "|" + timeFormat.format(now));
    }

    public void sendNavigation(NavParser.NavInstruction instruction) {
        sendLine("MODE|NAV");
        sendLine("NAV|" + instruction.turn + "|" + instruction.distanceMeters + "|"
                + sanitizeField(instruction.street, 44) + "|" + sanitizeField(instruction.eta, 18));
    }

    public void clearNavigation() {
        sendLine("CLEAR");
    }

    public void sendLine(String line) {
        if (line == null) return;
        String payload = line.endsWith("\n") ? line : line + "\n";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        int chunkSize = Math.max(20, negotiatedMtu - 3);
        synchronized (writeQueue) {
            for (int offset = 0; offset < bytes.length; offset += chunkSize) {
                int length = Math.min(chunkSize, bytes.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(bytes, offset, chunk, 0, length);
                writeQueue.add(chunk);
            }
        }
        writeNext();
    }

    @SuppressLint("MissingPermission")
    private void writeNext() {
        BluetoothGatt currentGatt = gatt;
        BluetoothGattCharacteristic currentRx = rxCharacteristic;
        if (!connected || currentGatt == null || currentRx == null || !hasPermissions()) return;

        byte[] data;
        synchronized (writeQueue) {
            if (writeBusy) return;
            data = writeQueue.poll();
            if (data == null) return;
            writeBusy = true;
        }

        int properties = currentRx.getProperties();
        currentRx.setWriteType((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        currentRx.setValue(data);
        try {
            boolean accepted = currentGatt.writeCharacteristic(currentRx);
            if (!accepted) {
                synchronized (writeQueue) {
                    writeBusy = false;
                }
                log("Perintah BLE tidak diterima oleh stack Android");
                handler.postDelayed(this::writeNext, 120L);
            } else if (currentRx.getWriteType() == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                handler.postDelayed(() -> {
                    synchronized (writeQueue) {
                        writeBusy = false;
                    }
                    writeNext();
                }, 80L);
            }
        } catch (SecurityException e) {
            synchronized (writeQueue) {
                writeBusy = false;
            }
            setState("IZIN CONNECT DITOLAK", false);
        }
    }

    private static String sanitizeField(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) return "-";
        String cleaned = value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private void setState(String newState, boolean isConnected) {
        state = newState;
        connected = isConnected;
        handler.post(() -> {
            for (Listener listener : listeners) listener.onBleState(newState, isConnected);
        });
    }

    private void log(String message) {
        Log.d(TAG, message);
        handler.post(() -> {
            for (Listener listener : listeners) listener.onBleLog(message);
        });
    }
}
