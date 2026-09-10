# AgentTerm

**Gesture-first terminal for AI agents on Android.** A phone-shaped terminal for
long-running coding agents — Claude Code, Codex, OpenCode, pi, Herdr — whether
they run in a local shell on the device or over SSH on a machine you control.

UX concept inspired by [Moshi](https://getmoshi.app) — see
[docs/SPEC.md](docs/SPEC.md) for the full feature spec. **AgentTerm is an
original implementation**: it is not a fork of Termux, does not depend on Termux
code or packages, and is not affiliated with Moshi.

## What you get (MVP)

- **A real local terminal** — `/system/bin/sh` + toybox on a real PTY via our
  own JNI bridge (`app/src/main/cpp/pty.c`). No root, no Termux.
- **SSH sessions** — password & private-key auth (keys encrypted with the
  Android Keystore), PTY allocation, saved connections, multiplexer preflight
  detection (`tmux` / `herdr` / `zellij`).
- **Own VT engine** — pure-Kotlin xterm parser in `:core`: 16/256/truecolor SGR,
  alt screen, scroll regions, bracketed paste, mouse SGR, focus events,
  OSC 0/2/7/52, DEC special graphics, UTF-8 + emoji. Unit-tested on the JVM
  (no Android SDK required to run `./gradlew :core:test`).
- **Gestures everywhere** — bind taps, double/triple-taps, swipes, two-finger
  swipes, pinch, scroll-past-bottom, header soft/hard pull-downs, toolbar
  long-presses and D-pad corner slots to actions or custom shortcuts. Defaults
  mirror Moshi: double-tap paste, swipe window switching, pinch font size,
  two-finger swipe for panes/sessions, D-pad Interrupt + Backspace slots.
- **Shortcuts** — build panels of terminal sequences with a simple builder or
  the advanced binding grammar: `C-b, S-t`, `F12, h`, `C-dash`, `text:/clear`,
  with live validation.
- **D-pad overlay** — arrows honoring application-cursor mode, configurable
  corner slots.
- **Agent palette** — one tap to launch/attach `codex`, `opencode`, `pi`,
  `claude`, `herdr` on the host.
- **Hardware keyboard** — Cmd+K shortcuts, Cmd+O picker, Cmd+1-9 switch
  sessions, Cmd+V paste, Cmd+W close.

## Build

GitHub Actions builds the APK: push to `main` (or run the `build` workflow
manually) and grab **agentterm-apk** from the artifacts.

Local (any machine with a JDK, no Android SDK needed):

```bash
./gradlew :core:test        # terminal engine + gesture + shortcut tests
```

`docs/PLAN.md` has the architecture decisions and the Moshi parity matrix.
`docs/SPEC.md` maps every Moshi feature page to our implementation.

## License

MIT for AgentTerm's own code. Dependencies keep their licenses: sshj &
Compose (Apache-2.0), Bouncy Castle (MIT), JetBrains Mono (OFL).