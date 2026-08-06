package com.syidcreate.nav;

import android.app.Application;

public final class SyidCreateApp extends Application {
    private BleClient bleClient;

    @Override
    public void onCreate() {
        super.onCreate();
        bleClient = new BleClient(this);
    }

    public BleClient getBleClient() {
        return bleClient;
    }

    public static BleClient ble(android.content.Context context) {
        return ((SyidCreateApp) context.getApplicationContext()).getBleClient();
    }
}
