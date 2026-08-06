# SYIDCREATE NAV Android

Aplikasi pendamping untuk T-Display-S3 firmware SYIDCREATE.

## Fitur

- mencari perangkat BLE bernama `SYIDCREATE NAV`
- auto-connect dan auto-reconnect
- Nordic UART Service:
  - Service `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
  - RX `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
  - TX `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
- membaca notifikasi navigasi Google Maps
- mengirim perintah `MODE|NAV` dan `NAV|...` ke ESP32
- sinkronisasi tanggal dan jam setelah BLE terhubung
- koneksi foreground dan mulai ulang setelah boot
- tombol tes navigasi

## Build melalui Android Studio

1. Buka folder proyek ini di Android Studio.
2. Tunggu Gradle Sync selesai.
3. Pilih **Build > Build APK(s)**.
4. APK debug berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build melalui GitHub Actions

1. Upload seluruh folder ini ke repository GitHub.
2. Buka tab **Actions**.
3. Jalankan workflow **Build SYIDCREATE NAV APK**.
4. Download artifact `SYIDCREATE-NAV-debug-apk`.

## Pengaturan pertama di ponsel

1. Berikan izin Bluetooth dan notifikasi.
2. Buka tombol **Akses Notifikasi**, lalu aktifkan `SYIDCREATE Google Maps Reader`.
3. Izinkan aplikasi berjalan di latar belakang.
4. Nyalakan T-Display-S3.
5. Tekan **Hubungkan Sekarang**.
6. Buka Google Maps dan mulai navigasi.

## Catatan

Format notifikasi Google Maps dapat berbeda menurut bahasa dan versi aplikasi. Parser sudah mengenali instruksi Bahasa Indonesia dan Bahasa Inggris yang umum. Bila suatu petunjuk tidak dikenali, arah akan memakai `STRAIGHT` sebagai fallback.
