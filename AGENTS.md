# AntiNet agent guide

## Project

AntiNet is an Android VPN client (`com.antinet.vpn`) built with Kotlin and
Jetpack Compose. It ships two selectable cores: Xray (`libv2ray.aar` from
AndroidLibXrayLite) and sing-box (`libbox.aar` from clever-vpn).

The app was rebuilt on the Cascade codebase. Keep the package, class prefix
`AntiNet` and user-facing Russian text consistent with the rest of the app.

## Build and validation

- The device has no JDK or Android SDK. Compile and test validation happens in
  GitHub Actions (`.github/workflows/build.yml`).
- Both AARs are downloaded by CI and are not committed (`app/libs` is ignored).
  The `go/*` classes are stripped from `libv2ray.aar` because libbox ships the
  same `go.Seq` runtime.
- Local commands, when an SDK is available:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug -PABI_FILTERS='arm64-v8a;x86_64'
```

## Architecture

- `engine/TunnelEngine.kt` is the contract for one running core. The service
  owns at most two instances: the main VPN tunnel and the optional hotspot
  tunnel. They must stay independent.
- `traffic()` returns bytes since the previous call. Xray counters are reset by
  `queryAllOutboundTrafficStats()`; sing-box uses per-connection deltas.
- Config builders (`XrayConfigBuilder`, `SingBoxConfigBuilder`) must stay pure
  and unit-testable. Do not move Android calls into them.
- UI state is shared in-process through `AntiNetRuntime`; do not add a binder
  for counters.
- `AntiNetVpnService.reload()` re-establishes a fresh TUN before starting the
  new core, then restarts the hotspot tunnel if it is enabled.

## Hotspot and Shizuku

- `shizuku/ShizukuUserService.kt` extends `IShizukuUserService.Stub`. It is
  **not** an Android `Service`: Shizuku instantiates it directly, it has a
  `@Keep` constructor with `Context`, and it must not be declared in the
  manifest.
- The service runs as shell (uid 2000) and uses hidden APIs through reflection
  plus HiddenApiBypass: `TestNetworkManager.createTunInterface`,
  `setupTestNetwork`, `TetheringManager.setPreferTestNetworks` and
  `startTethering`.
- `AntiNetApplication` must return early in the `:shizuku` process; it must not
  initialise the cores or Shizuku there.
- The test TUN is `10.163.0.2/24`. The main TUN is `10.10.10.1/30` and
  `fd00::1/126`.

## Product constraints

- No channel aggregation feature. Do not reintroduce SOCKS balancer code.
- Do not copy Shizzi code or assets. Hotspot sharing uses Shizuku only.
- Do not copy INCY branding or assets; the design is only inspired by it.
- Fragmentation and custom JSON rules are Xray-only. sing-box settings are
  `singBoxLogLevel`, `singBoxSniff`, `singBoxDomainStrategy`, `singBoxStack`.
- sing-box does not support mKCP; fail with a clear message instead.

## Tests

Unit tests live in `app/src/test` and use `org.json` on the JVM. Add tests for
config-builder behaviour rather than for Compose UI.
