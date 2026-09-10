# AgentTerm Feature Spec

Distilled from Moshi's public docs (https://getmoshi.app/docs). AgentTerm
reimplements the UX concept in an original codebase. Each section cites the
source page; ✅ = in MVP, ⏳ = roadmap.

## 1. Gestures — source: https://getmoshi.app/docs/gestures
Bind taps, swipes, pinches, drags, long-presses on four surfaces:
- **Terminal body**: tap, double-tap, triple-tap, swipe, pinch, scroll-past-bottom
- **Terminal header**: soft pull (session switcher) / hard pull (minimize)
- **Toolbar buttons**: tap / double-tap / long-press variants
- **D-pad corner slots**: top-left (Interrupt) and top-right (Backspace) — hide,
  delete, interrupt, or custom shortcut

Defaults (AgentTerm matches): ✅ double-tap paste; swipe left/right = window
prev/next; two-finger side = pane prev/next; two-finger vertical = session/
workspace; pinch = font size; scroll past bottom = hide keyboard; header soft =
picker; header hard = close.

Configuration UI at Settings → Input → Gestures with **Reset all**. ✅

## 2. Keyboard, toolbar & shortcuts — source: https://getmoshi.app/docs/keyboard
- Toolbar: Enter, Backspace, Paste, keyboard toggle, shortcut panel, D-pad ✅
- Shortcut builder:
  - **Simple**: pick Ctrl/Opt/Shift + key or custom text ✅
  - **Advanced grammar**: `C-`/`Ctrl+`, `M-`/`Opt+`/`Alt+`, `S-`/`Shift+`,
    `F1`–`F12`, named keys, `,`/space separators, `dash`/`plus` literals,
    `text:…` literal mode; case preserved ✅
  - Examples: `C-c`, `C-b, T`, `C-b, S-t`, `F12, h`, `S-Tab`, `C-dash`,
    `text:/clear` ✅
- D-pad: arrows + two configurable corner slots ✅
- Hardware keyboard: Cmd+K (shortcuts), Cmd+O (picker), Cmd+N (new), Cmd+W
  (close), Cmd+V (paste), Cmd+1-9 (switch session) ✅ (N→via home)
- Option-as-Meta toggle ✅ (setting exists; hardware Meta handled)
- Auto-hide toolbar with hardware keyboard ⏳

## 3. Connections & auth — source: https://getmoshi.app/docs/connections
- Fields: name, host (IP/DNS/Tailscale), port, username, auth (password/key) ✅
- Credentials stored separately, encrypted (AgentTerm: Android Keystore AES-GCM) ✅
- Key auth: import private key, rejects public keys (shows error) ✅
- Connection type Auto/SSH/Mosh/ET: SSH only in MVP ⏳ Mosh/ET
- Agent forwarding: ⏳
- VPN: no built-in VPN needed — any OS VPN works ✅ (nothing to do)

## 4. Multiplexers — source: https://getmoshi.app/docs/multiplexer
- Detection on connect via `command -v tmux/herdr/zellij` probe ✅ (SSH preflight)
- Session picker listing detected sessions ⚠️ (probe results shown; attach via
  `tmux attach` — full per-multiplexer pickers are roadmap)
- Swipe gestures drive the hierarchy (windows/panes/sessions) ✅ via prefix chords
- Pinch zoom pane ⚠️ font-size only; zoom-pane shortcut exists
- Remote approve/deny from inbox ⏳ (needs hooks daemon)
- Recent directories ⏳

## 5. tmux — source: https://getmoshi.app/docs/tmux
- Attach/create via `tmux new-session -A -s <name>` ✅ (shortcuts/agent palette)
- Windows: prefix+n/p; zoom prefix+z; pane cycles prefix+o/; ✅ (default shortcuts)
- Troubleshooting: non-interactive PATH — probe uses exported PATH ✅

## 6. Home / app structure — source: https://getmoshi.app/docs/introduction
- Home shows saved connections + active sessions ✅ (AgentTerm adds local console)
- Terminal = live session with scrollback, paste controls, toolbar, D-pad ✅
- Settings: appearance, input, gestures, shortcuts, connections ✅
- Inbox (agent events) ⏳ — AgentTerm ships the Agent palette as the MVP
  equivalent for launching/attaching codex/opencode/pi/claude/herdr

## 7. Recommended setup
Install mosh/tmux on host → save SSH key connection → use tmux as durable
workspace ✅ (mosh transport itself is roadmap).