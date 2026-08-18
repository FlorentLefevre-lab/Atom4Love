<!--
TITRE (champ titre GitHub) :
[HELP WANTED] iOS port — the hard part is the radio, not the UI

LABELS : help wanted, ios, kotlin-multiplatform
ACTION : épingler l'issue (Pin issue)
À COMPLÉTER avant publication : npub, email de contact. Supprimer ces commentaires.
-->

## What Atom4Love is

Not a feed, not a profile, not a swipe queue. Atom4Love is an Android app for
**proximity encounters** with no central server: two phones within a few metres
compute their affinity independently, from birth-ephemeris parameters, and
neither one learns that the other looked.

Identity is a deterministic keypair (no account, no biometrics). Location never
leaves the device — only an opaque hexagonal cell address does. The encrypted
booth (Noise, ported to pure Kotlin) opens *after* two people have recognised
each other in the room, never as a way to discover someone.

It's AGPL-3.0, alpha-published (v0.2.2), and it works today on real hardware:
BLE discovery and the encrypted booth have been validated cross-device, both
directions. Read the [README](README.md) — it's honest about what isn't done.

**There is no iOS client. That's what this issue is about.**

## The state of the code, without spin

The repo is a **single `:composeApp` Gradle module**. The source tree is already
named `commonMain` / `androidMain` / `iosMain`, but **only Android compiles
today** — the naming anticipates the port, the build hasn't been converted yet.
I'd rather you learn that here than after cloning.

Some things are genuinely ready to cross over: `secp256k1-kmp` (ACINQ) is
already multiplatform, the Noise implementation is 29 files of pure Kotlin with
no Android dependency, and the D1/D4 resonance maths is plain arithmetic. Other
things are not: Hilt, Room, WorkManager, OkHttp, and a locally patched Uber H3
AAR all need replacing or bridging. [ROADMAP.md](ROADMAP.md) lists them by
difficulty.

## The actual problem — and why it's interesting

The obvious framing is "write SwiftUI on top of shared Kotlin". That framing is
wrong, and pretending otherwise would waste your time.

Atom4Love's encounter layer rides on four transports. On iOS, two of them don't
exist:

| Android | iOS |
|---|---|
| BLE advertising + scan, 17-byte payload | Possible, but the payload doesn't fit as-is, and background advertising is heavily constrained (overflow area, service UUID stripped) |
| Bluetooth Classic RFCOMM (78–102 KB/s) | Unavailable outside the MFi programme |
| Local Wi-Fi TCP sockets | Portable |
| Wi-Fi Direct (15.5 MB/s) | Doesn't exist — MultipeerConnectivity / AWDL is the nearest thing |

So the real question is: **how do you carry a 4D cell address, a presence token,
a polarity, a Mayan seal and a phase inside what CoreBluetooth actually lets you
advertise — while staying interoperable with the Android devices already doing
it?**

That's a protocol design problem with a hard external constraint, not
boilerplate. If it sounds like your kind of puzzle, this is the project.

## What we're looking for

Someone to **own the iOS client** rather than land one patch:

- Design the iOS side of the proximity layer (the item above)
- Choose the replacement for RFCOMM / Wi-Fi Direct
- Build the SwiftUI surface: Map (Here / World), Board, Core, booth
- Keychain-backed storage for the sealed core
- Push back on the shared API — nobody with iOS experience has reviewed it yet,
  and it will be wrong in places

**Required:** Swift, SwiftUI, real CoreBluetooth experience. That last one
matters more than everything else here.

**Not required:** Kotlin. NOSTR knowledge. Any interest in Mayan calendars —
the Tzolkin layer is deterministic arithmetic, the README explains it and is
upfront that it's a design choice, not a physics claim.

## Compensation

Straight answer: **this is unpaid volunteer work.** No salary, no bounty, no
equity. If that's a dealbreaker, it's a fair one — better now than in three
weeks. Note that iOS development also means you'd be supplying your own Mac and
developer account, which is a real cost to weigh.

What you get instead:

- **Ownership.** Credit as iOS lead in the README, commit rights, and a real say
  in technical direction — including on the two architecture decisions still
  open (the D2 temporal rotation, and first-contact trust).
- **A problem worth solving.** Cross-platform opportunistic radio with a privacy
  constraint is genuinely hard and genuinely unusual.
- **A codebase that stays free.** AGPL-3.0, DCO sign-off, **no CLA** — nobody
  collects your rights, and there will never be a proprietary fork.
- **Visibility.** You won't be PR #4,000 in someone else's queue.

## Getting started

```bash
git clone https://github.com/FlorentLefevre-lab/Atom4Love.git
cd Atom4Love && ./gradlew assembleDebug
```

No API key, no account, no config. To see proximity actually work you need
**two physical Android devices** with Bluetooth on — the emulator covers
everything else.

Then comment here. The most useful opening message isn't "I'm interested", it's
**your read on the CoreBluetooth constraint above** — even a sceptical one.
"This can't work in the background on iOS, here's why" is a valuable answer and
I'd rather hear it early.

## Contact

- This issue
- NOSTR: `<npub>`
- Email: `<email>`

Partial help is welcome too: the H3 cinterop binding, the Ktor migration, or a
review of the shared API are all useful without owning the whole port.
