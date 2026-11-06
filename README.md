# ControlPhoneUnlock

LSPosed module for **遥控手机 (`com.techbao.controlphone`) 1.6.3**, built with the modern [libxposed API 102](https://github.com/libxposed/api).

## Why a runtime hook

The app's license state is computed in `q0.s.g()`, the single source of truth for every UI gate. Patching the APK directly breaks the Xiaomi wearable SDK: Mi Fitness verifies the caller's signing certificate in its own process (`Status.RESULT_SIGNATURE_VERIFY_FAILED`) and refuses to bind, so a re-signed APK can never connect to the band.

This module keeps the official APK untouched and replaces the method result at runtime:

```java
hook(q0.s.g()).intercept(chain -> new q0.u(q0.t.k, "已永久激活", null, null));
```

## Build

Every push to `main` triggers GitHub Actions:

1. Build `:app:assembleRelease` with Gradle 8.13 / AGP 8.13.2 / JDK 17.
2. Sign with the keystore stored in repository secrets.
3. Verify the APK contains `META-INF/xposed/*` and print the signer.
4. Publish a GitHub Release tagged `v1.0.<run_number>` with the APK attached.

Local build (optional):

```bash
gradle :app:assembleRelease
```

Without `SIGNING_STORE_FILE` the release build falls back to the debug key.

## Install

1. Rooted device with LSPosed supporting API 102.
2. Install the release APK, enable the module, scope: `com.techbao.controlphone`.
3. Force-stop the target app and reopen it.

## Compatibility

Only verified against 1.6.3 (`versionCode` 51). The obfuscated class names `q0.s` / `q0.t` / `q0.u` are version-specific.
