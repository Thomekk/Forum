# Forum — Android project

This directory is a standalone Gradle project for the Java Android app. Open this directory in Android Studio, or build a debug APK from a terminal.

## Requirements

- JDK 17
- Android SDK Platform 35 (the app targets API 34 and supports Android 5.0 / API 21 and newer)
- Internet access for Gradle to resolve the Android Gradle Plugin and SDK dependencies on first build

The Gradle 8.9 wrapper is included. `local.properties` is created by Android Studio (or can be set up by the user) and is intentionally not included.

## Build

Linux or macOS:

```sh
./gradlew assembleDebug
```

Windows:

```bat
gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

- `app/src/main/java/org/anonymous/forum/` — UI, local Q&A state, translations/topics, and the optional YggChat service.
- `app/src/main/java/net/yggchat/yggchat/` and `app/src/main/java/go/` — JNI compatibility bindings for the supplied YggChat 0.2.0 library.
- `app/src/main/jniLibs/arm64-v8a/libgojni.so` — bundled native YggChat core.
- `app/src/main/AndroidManifest.xml` — app and service declarations.

The minimum OS is Android 5.0 / API 21. The native core is available only for `arm64-v8a`, so this APK is intended for 64-bit ARM devices; 32-bit ARM and x86 devices need a separately built native core and are not supported by this package. The original YggChat APK is not needed to compile this project; the required native library and Java bindings are already included. Read `THIRD_PARTY_NOTICES.md` before redistributing builds containing the native library.
