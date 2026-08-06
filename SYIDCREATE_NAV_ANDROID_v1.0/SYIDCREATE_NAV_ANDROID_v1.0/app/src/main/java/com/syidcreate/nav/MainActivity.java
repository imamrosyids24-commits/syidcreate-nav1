package com.syidcreate.nav;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements BleClient.Listener {
    private static final int REQUEST_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BT = 1002;

    private TextView bleStatus;
    private TextView notificationStatus;
    private TextView navStatus;
    private TextView logView;

    private final SimpleDateFormat logTime = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
        refreshPermissionStatus();
        SyidCreateApp.ble(this).addListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
        if (navStatus != null) navStatus.setText(BleForegroundService.latestNavigation());
        if (hasBluetoothPermissions()) BleForegroundService.start(this);
    }

    @Override
    protected void onDestroy() {
        SyidCreateApp.ble(this).removeListener(this);
        super.onDestroy();
    }

    private View createContent() {
        int padding = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(7, 16, 24));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);

        TextView title = text("SYIDCREATE NAV", 28, Color.rgb(32, 233, 255), true);
        root.addView(title);
        TextView subtitle = text("Auto-connect BLE + Google Maps notification bridge", 14, Color.rgb(145, 167, 184), false);
        subtitle.setPadding(0, 2, 0, dp(16));
        root.addView(subtitle);

        bleStatus = statusCard(root, "BLUETOOTH", "Memeriksa...");
        notificationStatus = statusCard(root, "AKSES GOOGLE MAPS", "Memeriksa...");
        navStatus = statusCard(root, "DATA NAVIGASI TERAKHIR", "Belum ada data");

        Button permissions = button("1. AKTIFKAN IZIN BLUETOOTH", v -> requestBluetoothPermissions());
        root.addView(permissions);

        Button notification = button("2. BUKA AKSES NOTIFIKASI", v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
        root.addView(notification);

        Button connect = button("3. HUBUNGKAN SEKARANG", v -> {
            requestEnableBluetoothIfNeeded();
            BleForegroundService.start(this);
            SyidCreateApp.ble(this).startAutoConnect();
        });
        root.addView(connect);

        Button test = button("KIRIM TES BELOK KANAN", v -> {
            NavParser.NavInstruction instruction = new NavParser.NavInstruction(
                    "RIGHT", 250, "Jl. Sudirman", "14:45"
            );
            SyidCreateApp.ble(this).sendNavigation(instruction);
            navStatus.setText(instruction.toString());
            appendLog("Mengirim data tes navigasi");
        });
        root.addView(test);

        Button clear = button("HAPUS NAVIGASI DI DISPLAY", v -> SyidCreateApp.ble(this).clearNavigation());
        root.addView(clear);

        Button battery = button("IZINKAN BERJALAN DI LATAR BELAKANG", v -> requestBatteryExemption());
        root.addView(battery);

        Button stop = button("HENTIKAN AUTO-CONNECT", v -> BleForegroundService.stop(this));
        root.addView(stop);

        TextView hint = text(
                "Urutan pemakaian pertama:\n" +
                        "1. Berikan izin Bluetooth.\n" +
                        "2. Aktifkan akses notifikasi.\n" +
                        "3. Nyalakan T-Display-S3.\n" +
                        "4. Buka Google Maps dan mulai navigasi.\n\n" +
                        "Setelah pengaturan pertama, aplikasi akan mencoba menyambung kembali secara otomatis.",
                14,
                Color.rgb(244, 250, 255),
                false
        );
        hint.setPadding(dp(12), dp(16), dp(12), dp(16));
        root.addView(hint);

        TextView logTitle = text("LOG KONEKSI", 15, Color.rgb(86, 240, 138), true);
        root.addView(logTitle);
        logView = text("Aplikasi siap.\n", 12, Color.rgb(145, 167, 184), false);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(8), 0, dp(30));
        root.addView(logView);

        return scrollView;
    }

    private TextView statusCard(LinearLayout root, String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.rgb(16, 35, 49));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        TextView labelView = text(label, 12, Color.rgb(145, 167, 184), true);
        TextView valueView = text(value, 17, Color.rgb(244, 250, 255), true);
        valueView.setPadding(0, dp(4), 0, 0);
        card.addView(labelView);
        card.addView(valueView);
        root.addView(card);
        return valueView;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.rgb(7, 16, 24));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.rgb(32, 233, 255));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
            requestPermissions(permissions, REQUEST_PERMISSIONS);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasBluetoothPermissions() {
        return SyidCreateApp.ble(this).hasPermissions();
    }

    private void requestEnableBluetoothIfNeeded() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && !adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
        }
    }

    private void requestBatteryExemption() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        } else {
            new AlertDialog.Builder(this)
                    .setMessage("Aplikasi sudah diizinkan berjalan di latar belakang.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void refreshPermissionStatus() {
        if (bleStatus != null) {
            bleStatus.setText(hasBluetoothPermissions() ? SyidCreateApp.ble(this).getState() : "IZIN BLUETOOTH BELUM AKTIF");
            bleStatus.setTextColor(hasBluetoothPermissions() ? Color.rgb(86, 240, 138) : Color.rgb(255, 216, 77));
        }
        if (notificationStatus != null) {
            boolean enabled = isNotificationListenerEnabled();
            notificationStatus.setText(enabled ? "AKTIF, GOOGLE MAPS DAPAT DIBACA" : "BELUM AKTIF");
            notificationStatus.setTextColor(enabled ? Color.rgb(86, 240, 138) : Color.rgb(255, 216, 77));
        }
    }

    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        String packageName = getPackageName();
        for (String componentName : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(componentName);
            if (component != null && packageName.equals(component.getPackageName())) return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            refreshPermissionStatus();
            if (hasBluetoothPermissions()) {
                requestEnableBluetoothIfNeeded();
                BleForegroundService.start(this);
            }
        }
    }

    @Override
    public void onBleState(String state, boolean connected) {
        runOnUiThread(() -> {
            bleStatus.setText(state);
            bleStatus.setTextColor(connected ? Color.rgb(86, 240, 138) : Color.rgb(255, 216, 77));
            appendLog(state);
        });
    }

    @Override
    public void onBleLog(String line) {
        runOnUiThread(() -> appendLog(line));
    }

    private void appendLog(String line) {
        if (logView == null) return;
        String entry = logTime.format(new Date()) + "  " + line + "\n";
        logView.append(entry);
        if (logView.length() > 7000) {
            logView.setText(logView.getText().subSequence(logView.length() - 5000, logView.length()));
        }
    }
}
