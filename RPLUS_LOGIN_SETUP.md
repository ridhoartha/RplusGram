# R+ Gram — Login Setup (API ID & API Hash)

APK yang dibangun dengan nilai bawaan repo memakai:

```java
APP_ID = 4
APP_HASH = "014b35b6184100b085b0d0572f9b5103"
```

Itu API ID resmi yang sudah dipublikasikan. Telegram membatasinya dan login bisa
gagal dengan error:

- `API_ID_PUBLISHED_FLOOD`
- `API_ID_INVALID`

Karena itu setiap build untuk dipakai login **wajib memakai API ID/API Hash milik Anda sendiri**.

## 1. Buat aplikasi di my.telegram.org

1. Buka <https://my.telegram.org>
2. Login dengan nomor Telegram Anda.
3. Pilih **API development tools**.
4. Isi nama aplikasi, misalnya `R+ Gram`, platform `Android`.
5. Simpan `api_id` (angka) dan `api_hash` (string).

Jangan gunakan API ID milik aplikasi lain yang sudah tersebar.

## 2. Tambahkan sebagai GitHub Actions Secrets

Di repository GitHub:

**Settings → Secrets and variables → Actions → New repository secret**

Tambahkan dua secret:

| Name | Value |
|---|---|
| `RPLUS_API_ID` | angka `api_id` Anda |
| `RPLUS_API_HASH` | string `api_hash` Anda |

Workflow `build-debug.yml` sudah membaca keduanya dan memasukkannya ke
`BuildConfig.RPLUS_API_ID` / `BuildConfig.RPLUS_API_HASH`.

> Jangan commit `api_id`/`api_hash` ke source code repository publik. Setelah
> nilainya dipublikasikan, Telegram bisa memblokirnya lagi.

## 3. Build ulang

Setelah secrets disimpan:

- Jalankan workflow **Build R+ Gram APK** (target `arm64-v8a`), atau
- push ke branch `rplusgram-dev`.

Build baru akan memakai kredensial Anda. Uninstall/clear data aplikasi beta lama
jika login masih memakai sesi lama:

```text
Settings Android → Apps → R+ Gram Beta → Storage → Clear data
```

## 4. Build lokal (opsional)

Tanpa GitHub Secrets, berikan lewat environment variable:

```bash
export RPLUS_API_ID=123456
export RPLUS_API_HASH=abcdef0123456789abcdef0123456789
./gradlew :TMessagesProj_App:assembleAfatDebug \
  -PrplusFastDebug=true -PrplusTargetAbi=arm64-v8a
```

Atau lewat properti Gradle:

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug \
  -PRPLUS_API_ID=123456 \
  -PRPLUS_API_HASH=abcdef0123456789abcdef0123456789 \
  -PrplusFastDebug=true -PrplusTargetAbi=arm64-v8a
```

Jika nilainya kosong, build tetap memakai fallback dummy dan login akan gagal.
