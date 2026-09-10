# AgentTerm Engineering Plan

## Goals
A gesture-configurable, agent-native Android terminal:
- full local terminal (like Termux) **but an original codebase** —
  no Termux code, no `com.google.android.material`-style heritage, no Termux packages
- SSH sessions so agents (codex/opencode/pi/herdr/claude) running on a host are
  drivable from the phone
- Moshi-style UX: bindable gestures across terminal body / header / toolbar /
  D-pad; shortcut builder with advanced binding grammar; D-pad with Interrupt &
  Backspace slots; session picker; multiplexer awareness

## Non-goals (MVP)
- Mosh transport (needs a bundled mosh-client binary; roadmap)
- Agent hooks / inbox / push notifications (roadmap)
- Dictation, image paste, diff viewer (roadmap)
- Play Store signing pipeline (debug APK via CI for now)

## Architecture decisions
| Decision | Choice | Why |
|---|---|---|
| Terminal engine | **Own VT parser** in pure Kotlin (`:core`) | "completely new" requirement; JVM-testable without Android SDK; full control of truecolor/OSC features agents emit |
| Local shell | `/system/bin/sh` + toybox (Android built-ins) | zero bundled GPL binaries, clean licensing; busybox bundling is a roadmap upgrade |
| PTY | **Own JNI bridge** (openpty/fork via bionic in `app/src/main/cpp/pty.c`) | a real terminal needs a real tty; 100 lines of C, no Termux code |
| SSH | sshj (Apache-2.0, pure Java) + Android Keystore secrets | no GPL/OpenSSL static binaries in the APK |
| Builds | **GitHub Actions only**; local dev box runs JVM tests only (`:app` included when `ANDROID_HOME`/`CI` set) | predictable CI; no Android SDK needed on dev machines |
| UI | Jetpack Compose + Canvas terminal view | modern, fast to build gesture-heavy screens |

## MVP scope checklist
- [x] Gradle multi-module skeleton (`:core` JVM, `:app` Android)
- [x] VT engine: parser (colors 16/256/truecolor, alt screen, scroll regions,
      bracketed paste, mouse SGR, focus events, OSC 0/2/7/52, DEC graphics,
      UTF-8/emoji), screen model w/ scrollback, sessions bridge
- [x] Gesture engine: taxonomy, defaults mirroring Moshi, JSON config, reset
- [x] Shortcut engine: advanced binding grammar + xterm key emitter (59 tests green)
- [x] Local PTY session, SSH session (password/key), connection store + Keystore
- [x] Compose UI: Home, Connection editor, Terminal (header/toolbar/D-pad/sheets),
      Settings, Gesture settings, Shortcuts + builder
- [x] GitHub Actions: core tests + debug APK artifacts
- [x] README + spec docs
- [ ] On-device smoke test (install APK via installer, check no crash)
- [ ] Release APK signing (roadmap)

## Delegation log
- **subagent A** (spawned): README/docs/PLAN/SPEC drafts — status: in flight
- **subagent B** (spawned): Settings / Gesture settings / Shortcuts screens —
  status: delivered (4 files, ~1060 LOC), reviewed, compile-checked via CI

## Moshi parity matrix
| Feature | Moshi | AgentTerm MVP | Status |
|---|---|---|---|
| Gestures: body/header/toolbar/D-pad | yes | yes (defaults matched) | ✅ |
| Custom shortcuts (simple + advanced grammar) | yes | yes (grammar-compatible) | ✅ |
| D-pad w/ configurable corner slots | yes | Interrupt + Backspace slots | ✅ |
| SSH (password + key) | yes | yes (Keystore-encrypted) | ✅ |
| Mosh transport | yes | — | ⏳ roadmap |
| ET transport | yes | — | ⏳ roadmap |
| Local terminal | no (SSH-only) | yes | ✅ (differentiator) |
| tmux/herdr detection + picker | yes | preflight probe + attach | ⚠️ basic |
| Swipe = window switch | yes | sends tmux prefix chords | ✅ |
| Pinch = font size (or zoom pane) | yes | font size | ✅ |
| Two-finger swipes (panes/sessions) | yes | pane/session switch chords | ✅ |
| Header soft/hard pull | yes | picker / close | ✅ |
| Hardware keyboard shortcuts | yes | Cmd+K/O/V/W/1-9 | ✅ |
| Agent hooks / inbox / approvals | yes | — | ⏳ roadmap |
| Dictation | yes | — | ⏳ roadmap |
| Image paste | yes | — | ⏳ roadmap |
| Modern terminal rendering (truecolor etc.) | implied | yes, own engine | ✅ |
| Custom themes / fonts | pro | font scale; fonts roadmap | ⚠️ partial |

## Risks & mitigations
- **Own parser gaps** → 59 unit tests incl. real agent sequences; CI runs them;
  osc52/clipboard + DSR wired for agent integration
- **emulator-free testing** → JVM tests for engine; on-device smoke test after
  APK build; review via `am start` + logcat
- **sshj behavior on Android** → explicit bcprov dep; fallback docs
- **CI flakiness (NDK/CMake)** → pinned versions, cached via setup-gradle