# ControlAnything (Android)

Android app that auto-builds a phone dashboard of controls/outputs for a microcontroller project
(e.g. an ESP32 robot). The device runs a companion embedded library that hosts a WebSocket
server, advertises it over mDNS, and publishes a JSON schema describing its I/O; the app discovers
it, renders widgets, and lets the user rearrange them on a grid. The embedded library lives at
`C:\Users\Admin\Repos\Fun\ControlAnythingEmbedded` - **read-only** from this repo's sessions.

## Build / test

- Windows dev machine: `./gradlew.bat assembleDebug`, `./gradlew.bat testDebugUnitTest` (JVM unit
  tests only). No `local.properties`; set `ANDROID_HOME=$LOCALAPPDATA/Android/Sdk` per command.
- Kotlin 2.2, AGP 9.x, compile/target SDK 37, minSdk 26, Compose (Material3), Hilt + KSP, Room,
  kotlinx.serialization, OkHttp (WebSockets). Tests: JUnit 4, kotlinx-coroutines-test,
  OkHttp `mockwebserver3`. Versions in `gradle/libs.versions.toml`.
- `tools/fake_robot.py` (`pip install websockets zeroconf`) simulates a device: WebSocket server on
  port 81, advertised over mDNS, one of every widget type, streams outputs, logs received controls.
  Flags: `--port`, `--path`, `--retain-outputs`, `--advertise-ip`. From Git Bash, a `--path /x`
  argument gets mangled into a Windows path - run it from PowerShell or set `MSYS_NO_PATHCONV=1`.

## Wire protocol (must stay in sync with the embedded library)

- Transport today: WebSockets over Wi-Fi (device = server, one phone at a time). BLE is planned,
  not started; the design leaves room for it (see Architecture).
- Discovery: mDNS service `_controlanything._tcp` (15 chars, the DNS-SD max). Port from the SRV
  record (device default 81); WebSocket path from optional TXT `path` (default `/`). The app
  connects to the **first** device found.
- Framing: one ASCII text frame per message, `topic:value`, split on the **first** colon.
- Topics: `info` (retained JSON schema), `outputs/<leaf>` (device -> app), `controls/<leaf>`
  (app -> device, fire-and-forget). Anything else (e.g. `log/<level>`) is ignored for now.
- Retained: the device resends all retained topics to each new connection (`info` always;
  outputs undecided). The app sends every control's current value whenever `info` arrives.
- Subscriptions are local only - nothing is sent over the wire to subscribe.
- Liveness: WebSocket ping/pong, driven by the app (OkHttp `pingInterval`, 3 s).
- `info` shape: `device_name, project_id, controls[], outputs[]` plus optional `device_id`,
  `schema_hash`; each entry is `{topic: [..], display_name, type, widget: {type, min, max,
  default_value, orientation, color, suffix, mode}}`. `topic` is always a list; joystick uses `[x, y]`.
  Unknown keys are ignored.
- Controls: `toggle`, `button` (mode `rising|falling|state`), `slider` (min/max/default/orientation),
  `joystick` (axes normalized to [-1,1], +y = up; x and y sent as separate messages).
  Outputs: `numeric_readout` (suffix), `led_indicator` (color).
- Values are plain text: `true`/`false` or a float string.
- Unknown widget types / too-short topic lists are silently dropped by `InfoMapper`, not errors.

## Architecture (`app/src/main/java/com/brendan/controlanything/`)

Layers, bottom up - each knows nothing about the ones above it:

- `data/discovery` - `DeviceDiscovery` (per transport: `discover(): Flow<DeviceEndpoint>` +
  `requiredPermissions`), Hilt `@IntoSet`-bound and merged in `DiscoveryViewModel`.
  `DeviceEndpoint` is a sealed type with one case per transport (`WebSocket` now); exhaustive
  `when`s over it (`DefaultTransportFactory`, `transportLabel()`) are where a new transport plugs in.
  `MdnsDeviceDiscovery` = NSD + `WifiBindingHelper` (binds the process to Wi-Fi so no-internet APs
  work) + multicast lock; needs `ACCESS_LOCAL_NETWORK` on API 37+.
- `data/pubsub` - protocol-agnostic core. `Transport` = one connection (state, buffered `incoming`
  channel, `send`, `close`); `WireCodec` = `topic:value` framing; `PubSubClient` (singleton) routes
  incoming messages to per-topic flows with a replay-1 last-value cache that's reset per connection,
  so late subscribers still get retained values. `websocket/WebSocketTransport` is the OkHttp impl.
  Cleartext `ws://` is allowed via `res/xml/network_security_config.xml`.
- `data/device` - the ControlAnything topic scheme. `DeviceRepository` (singleton) parses `info`
  (`InfoJson.kt` DTOs -> `InfoMapper.parseInfo` -> `DeviceInfo`), adds `outputs/`/`controls/`
  prefixes (callers use **leaf** names), and owns `controlValues` - the app is the source of truth
  for control state and resends it on every `info` arrival (values for unchanged controls of the
  same project carry over; joysticks reset to 0).
- `domain/model` - `ControlDef` / `OutputDef` sealed classes, `TopicValue` (Bool/Number, with
  `toWire()`), enums. Widget identity key = `topic` (joystick: `topicX`).
- `domain/grid` - `GridEngine`: pure, JVM-testable placement (overlap, clamp, rescale on column
  change, `nextFreeCell`, `reconcile` saved vs. new widgets). Keep Android imports out of it.
- `data/layout` + `data/db` - Room persistence of layouts keyed by `projectId` (not schema hash),
  so positions survive firmware changes per topic. Destructive migration is intentional; bump the
  DB version and commit the exported schema in `app/schemas/`.
- `ui/discovery` - request all discoveries' permissions -> discover -> connect to first endpoint;
  navigates when `deviceInfo` arrives. Discovery keeps running after connecting on purpose
  (stopping it would release the Wi-Fi process binding).
- `ui/dashboard` - `DashboardViewModel` reconciles layout on every new `DeviceInfo`, parses output
  values per `OutputDef`, and forwards control changes to `DeviceRepository.setControl`.
  `DashboardGrid` is a custom `Layout` with square cells, fixed column count (2-12, default 4),
  unbounded rows (must be in a vertical scroll). `WidgetFrame` wraps every widget and owns edit-mode
  drag/resize handles so individual widgets never know about edit mode. Orientation is locked per
  project while the dashboard is shown.

## Adding a widget type

1. Add a case to `ControlDef`/`OutputDef` (+ any enum) and any new fields to `WidgetJson`.
2. Map it in `InfoMapper` and add tests in `InfoMapperTest`.
3. Give it a `defaultSpan()` in `DashboardViewModel` (and output value parsing there); for a
   control, add its default(s) to `seedValues()` in `DeviceRepositoryImpl`.
4. Create `ui/dashboard/widgets/<Name>Widget.kt` and add a branch in `DashboardScreen`.
5. Add an example to `tools/fake_robot.py`, and mirror the change in the embedded library.

## Conventions

- Conventional-commit messages (`feat:`, `fix:`, `test:`).
- KDoc comments explain *why* (design rationale), not what; match that density.
- Screens split into a stateful `XScreen` (ViewModel) and a stateless `XContent` with `@Preview`s.
