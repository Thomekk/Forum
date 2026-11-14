# Third-party native component

`app/src/main/jniLibs/arm64-v8a/libgojni.so` is the ARM64 native library extracted from the user-provided `YggChat-0.2.0-arm64.apk` in the repository. Its embedded strings identify it as YggChat 0.2.0 / Yggdrasil v0.5.15 and show Go-mobile JNI bindings. The APK did not contain a license or source archive, so this repository does not claim an upstream license for that binary. Confirm the upstream distribution terms before publishing or redistributing an APK built from this project.

The Java compatibility bindings under `app/src/main/java/go/` follow the Go Authors' Go-mobile Java binding interface; the source notices are included in those files. The question/answer UI and local forum logic are original project code.
