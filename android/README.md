# Forum — Android project

This directory is a standalone Gradle project for the Java Android app. Open it in Android Studio or build a debug APK from a terminal.

## Requirements and build

- JDK 17
- Android SDK Platform 35 (target API 34; minimum OS Android 5.0 / API 21)
- Gradle 8.9 wrapper is included; Android Gradle Plugin and SDK components may need to be downloaded on the first build

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The bundled native core is only `arm64-v8a`; x86 and 32-bit ARM need a separately built native core.

## Project layout

- `app/src/main/java/org/anonymous/forum/` — modular ask/feed/detail/settings screens, shared UI components and sidebar, local Q&A model, Forum identity/discovery/replica protocol, and the Yggdrasil foreground service.
- `app/src/main/java/net/yggchat/yggchat/` and `app/src/main/java/go/` — JNI compatibility bindings for the supplied YggChat 0.2.0 library.
- `app/src/main/jniLibs/arm64-v8a/libgojni.so` — bundled native YggChat/Yggdrasil core.
- `app/src/main/assets/lang/` — JSON localization catalog, grouped by UI section.
- `app/src/main/assets/network/bootstrap-nodes.json` — transport-only Yggdrasil peer URIs, with the pinned upstream source recorded in the JSON.

## Discovery, identity and local replication

The app has no external Forum server or member-directory service. Its discovery adapter attempts to call the local Yggdrasil Admin JSON API (`getTree`, then bounded, rotating `getNodeInfo` requests) over an app-private Unix socket. A routing-tree entry or direct transport peer is only a candidate—not a Forum member. A candidate is cached only if its NodeInfo contains a valid, signed `anonymous-forum` presence record whose Yggdrasil key and derived address match the key/address queried. The record includes the app's public signing key and public YggChat contact code; private keys are never placed in NodeInfo or sent over the network.

The app configures `AdminListen` to the private Unix-socket path and never requests a public or loopback TCP admin endpoint. This depends on the bundled native core actually starting Yggdrasil's Admin listener. Static inspection of the supplied APK confirms the Java/JNI `Node` methods used by this project (`configJSON`, `start`, `restart`, `statusJSON`, and the messaging methods), but the native binary exposes no clear `src/admin` listener/request-handler symbols; the socket's runtime availability therefore remains unverified and discovery may report unavailable until a core with Admin API support is used. When that happens, local Q&A remains available; only signed presences learned through another valid path can seed direct replication. A local app signing key is kept in Android Keystore when available; if Keystore operations fail, the app falls back to an app-private software key. App signatures bind presence and public record envelopes to the Forum identity. Direct messages are checked against the Yggdrasil address of their thread peer.

Verified participant metadata is cached locally with `lastSeen` and a six-hour expiry. “Recently active” means only that this device received a valid NodeInfo response or signed direct message within that TTL; it does not guarantee that the peer is still online, that the Forum app remains open, or that it can store data now. No public participant list, manual contact-code UI, QR flow or group-leave control is added. Contacts are created internally only when the transport needs to deliver a replica.

Questions and public answer notices always remain in the local Forum store. When signed participant records have been verified and YggChat delivery is available, they are gossiped to up to three deterministic, recently observed participants at a time. Receivers persist accepted public records locally and may relay them to their own replica targets. Private answer bodies are sent only to the question author's public YggChat contact code and are never included in public replication. Event IDs, signatures, size bounds, local deduplication and expiry limit replay and storage growth; they do not provide consensus, Sybil resistance or guaranteed delivery.

This is best-effort discovery, not a complete global DHT. The scan is limited to entries visible in the current Yggdrasil routing tree, runs in batches, and can miss nodes that are not represented there. Yggdrasil still needs an already-running node or at least one reachable transport peer; the bundled public peer URIs are third-party bootstrap suggestions whose availability is not guaranteed. They are transport infrastructure, not Forum hosts or confirmed members. Android may defer or stop background work, so no exact scan interval, persistent uptime or delivery time is promised. If the local Admin API or presence setup fails, the app reports discovery unavailable and keeps local Q&A data rather than treating network nodes as participants.

## Bootstrap peers

The bundled five TLS URIs were copied from the official [Yggdrasil public-peers list](https://github.com/yggdrasil-network/public-peers/tree/301831252b4dbf11a22ea479f6ac26c0763d39aa/europe), revision `301831252b4dbf11a22ea479f6ac26c0763d39aa` (2026-11-12): one listed in Saint Petersburg, two in Moscow, one in Helsinki, and one in Nuremberg. Operators can change or remove them. The app does not silently fetch a peer list from another service. An empty peer list leaves the Q&A interface local-only.

## Localization and licensing

Supported display names/codes are in `app/src/main/assets/lang/languages.json`. Translation files are grouped by `_common/`, `q/`, `l/`, `a/`, `settings/`, `network/`, `memory/` and `topics/`; missing entries fall back to English.

The original YggChat APK is not needed to compile this project. Before redistributing a build with the native library, review `THIRD_PARTY_NOTICES.md`; its upstream distribution terms still need confirmation.
