<p align="center">
  <a href="ROADMAP.md">Français</a> · <b>English</b>
</p>

# Roadmap

> **Legend** — ✅ done · 🚧 in progress · 📋 planned · 💡 exploratory
>
> No dates: Atom4Love runs on volunteer time. Order matters, schedule doesn't.
> What exists today is described in the [README](README.en.md), under "Project
> status" — this page looks forward.

## v0.2.2 — where we are

Alpha published. The full journey holds up on real hardware: forging the core,
deriving keys, BLE radar, encrypted booth. See the README for detail.

Two architecture decisions remain open, and they are the most structural ones
in the project:

- 🚧 **D2 temporal rotation** — v0 broadcasts the static H3 index
  (`CellRotation.None`). The formula isn't settled.
- 🚧 **First-contact trust** — the booth encrypts and attests, but nothing yet
  prevents someone from presenting themselves as another person at the very
  first encounter.

---

## Step 1 — Extracting the multiplatform core 📋

The repo is a **single `:composeApp` module**, with a source tree already named
`commonMain` / `androidMain` / `iosMain` but where only Android compiles. The
naming anticipates the port; the build still has to be converted.

The goal is to isolate everything that isn't UI into a genuinely multiplatform
`:shared` module, so a second front-end can plug into it.

**Already multiplatform, nothing to do:**

- ✅ `secp256k1-kmp` (ACINQ) — elliptic curves are already KMP
- ✅ The pure-Kotlin Noise port (29 files) — no Android dependency
- ✅ The PBKDF2 → scrypt → SHA-256 → secp256k1 derivation chain
- ✅ D1 (personal phase) and D4 (Tzolkin oracle) — plain arithmetic

**To migrate, by increasing difficulty:**

| Current | Target | Difficulty |
|---|---|---|
| OkHttp | Ktor | Low |
| Room, DataStore | SQLDelight or Room KMP + multiplatform-settings | Medium |
| Hilt | Koin (or manual injection) | Medium |
| WorkManager | `expect`/`actual` per platform | Medium |
| Android Keystore | `expect`/`actual` ↔ iOS Keychain | High |
| H3 4.4.0 (patched Java AAR) | cinterop to the C library on iOS | High |

**Tasks:**

- 📋 Convert the Gradle build to KMP, declare the `iosArm64` and
  `iosSimulatorArm64` targets
- 📋 Split `:composeApp` into `:shared` + `:androidApp`
- 📋 Migrate the dependencies above
- 📋 Expose the shared API in a shape that's comfortable from Swift
  (`suspend` → `async/await`, sealed classes → enums)
- 📋 Run the existing JVM tests under `commonTest`

---

## Step 2 — iOS client 📋

**We're looking for someone to build and own this.** See the pinned
`[HELP WANTED]` issue.

This is not a SwiftUI skin over shared logic: the transport layer has to be
rethought. The booth's four mediums do not carry over.

| Android medium | Situation on iOS |
|---|---|
| BLE advertising / scan | Possible, but the 17-byte payload doesn't fit as-is, and background advertising is heavily constrained (overflow area, service UUID stripped) |
| Bluetooth Classic RFCOMM | Unavailable outside the MFi programme — needs replacing |
| Local Wi-Fi (TCP sockets) | Portable |
| Wi-Fi Direct | Doesn't exist — MultipeerConnectivity / AWDL is the nearest thing |

- 📋 `iosApp` target consuming the `:shared` framework
- 📋 Redesign the proximity announcement within CoreBluetooth's constraints,
  **without breaking interoperability with Android devices** — this is the
  heart of the problem
- 📋 Choose the replacement for RFCOMM / Wi-Fi Direct
- 📋 SwiftUI surface: Map (Here / World), Board, Core, booth
- 📋 Keychain storage for the sealed core
- 📋 TestFlight distribution
- 💡 App Store — AGPL compatibility with Apple's terms is a known, non-trivial
  question. F-Droid remains the reference channel on Android.

---

## Step 3 — Ecosystem 💡

- 💡 F-Droid inclusion, with IzzyOnDroid as an interim channel
- 💡 Desktop target (Compose Multiplatform)
- 💡 D2 rotation settled and deployed on both sides
- 💡 Deeper integration with Astroport.ONE stations

---

## Where help matters most

| Area | Skills | Effort |
|---|---|---|
| **iOS client** | Swift, SwiftUI, CoreBluetooth | Large — needs an owner |
| **iOS transport** | CoreBluetooth, MultipeerConnectivity | High, and it's the crux |
| KMP extraction | Kotlin, Gradle, Ktor | Medium |
| H3 via cinterop | C, Kotlin/Native | Small, but blocking |
| D2 rotation | Geometry, cryptography | Open to discussion |
| NIP coverage | NOSTR | Small, parallelisable |
| Translation, accessibility | fr / en / es, Compose | Small, welcoming |

See [CONTRIBUTING.md](CONTRIBUTING.md). Commits are signed off (`git commit -s`,
DCO), there is no CLA, and the code stays AGPL-3.0.
