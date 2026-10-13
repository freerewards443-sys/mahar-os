# Mahar OS 1.0 — build your own bootable ISO

A lightweight, Windows-like Linux OS built from scratch on **Debian Bookworm**
with the official **live-build** tool. Boots on any x86_64 PC/laptop from USB.

**Output:** `Mahar.iso` (hybrid ISO — works on USB sticks and DVDs)
**Login:** user `mahar`, password `mahar` (autologin is on)

---

## STEP 1 — Project architecture

```
mahar-os/
├── build.sh                    # STEP 8: one-shot builder -> Mahar.iso
├── README.md                   # this file (steps 1-10)
├── TESTING.md                  # STEP 9
├── TROUBLESHOOTING.md          # STEP 10
├── apps/                       # STEPS 2-4: custom apps (Python + PyQt5)
│   ├── browser/  mahar_browser.py   + mahar-browser.desktop
│   ├── player/   mahar_player.py    + mahar-player.desktop
│   └── files/    mahar_files.py     + mahar-files.desktop
├── desktop/                    # STEP 5: Windows-like shell (IceWM)
│   ├── icewm/    preferences, menu, toolbar, keys, startup, theme/mahar/
│   ├── idesk/    desktop icons (browser/files/player)
│   ├── nodm/     nodm.conf (autologin as mahar)
│   ├── xsessions/ mahar.desktop
│   ├── plymouth/ boot splash theme (logo + script)
│   └── welcome/  mahar_welcome.py (welcome/settings app)
├── assets/                     # STEP 6: branding
│   ├── make_assets.py          # regenerates wallpaper + icons
│   ├── logo-source/            # master Mahar "M" logo
│   ├── icons/                  # 512px app icons
│   ├── hicolor/                # freedesktop icon theme tree (16-256px)
│   └── wallpaper/              # 1920x1080 Mahar OS wallpaper
└── config/                     # STEP 7: live-build configuration
    ├── auto/config             # lb config (Bookworm, iso-hybrid, MAHAR_ISO label)
    ├── package-lists/mahar.list.chroot
    └── hooks/live/0100-mahar-setup.hook.chroot
```

**How it fits together:** `build.sh` copies `apps/` + `desktop/` + `assets/`
into `config/includes.chroot/` (the overlay live-build injects into the ISO),
then runs `lb config` + `lb build`. IceWM is the window manager — its taskbar,
start menu, system tray and clock give the Windows feel; a custom `mahar`
theme, the Mahar wallpaper, idesk desktop icons and a Plymouth boot splash
complete the branding. The three Mahar apps live in `/opt/mahar/` on the ISO.

**Design choices (why):**
- **IceWM over a custom Python shell** — a hand-rolled PyQt shell looks nice
  in screenshots but breaks on real hardware (focus bugs, no tray protocol).
  IceWM is 5 MB, rock solid, and already Windows-like.
- **nodm over LightDM** — lighter, instant autologin, fewer moving parts.
- **python-vlc over GStreamer/QtMultimedia** — one backend plays everything.

---

## STEP 2 — Mahar Browser (`apps/browser/mahar_browser.py`)

PyQt5 + QtWebEngine. Tabs, address bar (URL or search), back/forward/reload,
bookmark bar + manager (SQLite), history with search (SQLite), download
manager with progress, basic ad-blocker (request interceptor with an embedded
blocklist, toggleable, blocked-count in the status bar), dark mode (dark
chrome + page filter), zoom, find-in-page, settings dialog, about dialog.

## STEP 3 — Mahar Player (`apps/player/mahar_player.py`)

PyQt5 + python-vlc (libVLC). Plays MP4/MKV/AVI/MP3 and more, playlist with
shuffle/repeat and persistence, seek bar, volume + mute, fullscreen
(double-click / F11), subtitle file loading + track switching, playback
speed, drag & drop, dark theme.

## STEP 4 — Mahar Files (`apps/files/mahar_files.py`)

PyQt5 file manager. Two-pane (directory tree + file list), icon/details views
with image thumbnails, breadcrumb bar, back/forward/up, copy/cut/paste/delete
(implemented with shutil — no clipboard daemon needed), rename (F2), new
folder, recursive search, preview pane, properties dialog, hidden-files
toggle, context menus, keyboard shortcuts.

## STEP 5 — Windows-like desktop shell (`desktop/`)

- **Taskbar** at the bottom with Start button (Mahar logo), window list,
  system tray, 24h clock — `desktop/icewm/preferences`
- **Start menu** with Mahar apps, Settings submenu, logout/reboot/shutdown —
  `desktop/icewm/menu`
- **Theme `mahar`** — dark taskbar/menus, blue `#1a73e8` accents —
  `desktop/icewm/theme/mahar/default.theme`
- **Quick-launch toolbar** — `desktop/icewm/toolbar`
- **Desktop icons** for the three apps — `desktop/idesk/` (idesk)
- **Wallpaper** auto-set at login — `desktop/icewm/startup` (+ feh)
- **Welcome app** — system info + app launcher — `desktop/welcome/`
- **Autologin** as `mahar` — `desktop/nodm/nodm.conf`

## STEP 6 — Theme files, icons, wallpaper (`assets/`)

- Logo: AI-generated glowing "M" (`assets/logo-source/mahar-logo.png`)
- App icons: browser (globe), player (play), files (folder) — 512px masters
  plus a full hicolor tree (16→256px) installed to
  `/usr/share/icons/hicolor/`
- Wallpaper: 1920×1080 "Mahar OS — Simple. Fast. Yours."
- Regenerate everything: `python3 assets/make_assets.py`

## STEP 7 — live-build configuration (`config/`)

- `config/auto/config` — Bookworm, amd64, `iso-hybrid`, ISO volume label
  `MAHAR_ISO`, bootloaders syslinux (BIOS) + grub-efi (UEFI), quiet splash
  boot, user `mahar`, hostname `maharos`
- `config/package-lists/mahar.list.chroot` — Xorg, IceWM, idesk, nodm,
  NetworkManager, PulseAudio, feh, xterm, mousepad, galculator, PyQt5,
  QtWebEngine, python-vlc, VLC, Plymouth, firmware, fonts
- `config/hooks/live/0100-mahar-setup.hook.chroot` — sets `mahar:mahar`
  password, locks root, installs Plymouth theme, refreshes icon caches

## STEP 8 — Build script (`build.sh`)

```bash
cd mahar-os
sudo ./build.sh
```

One script does everything: checks the machine, installs build tools
(including the **real Debian live-build** — Ubuntu's own live-build cannot
build Debian images, so the script replaces it automatically), assembles
the ISO filesystem from `apps/`+`desktop/`+`assets/`, runs the Debian
Bookworm bootstrap + build, and renames the output to **`Mahar.iso`**
(+ `.sha256`). Needs Debian/Ubuntu, root, internet, ~10 GB free disk,
30–90 minutes.

## STEP 9 — Testing

See **TESTING.md**: checksum verify → test in a VM (QEMU/VirtualBox) →
flash to USB → boot a real laptop → checklist (desktop, apps, wifi, sound).

## STEP 10 — Troubleshooting

See **TROUBLESHOOTING.md**: build failures (disk space, debootstrap,
EFI packages), boot problems (secure boot, black screen), and runtime fixes
(no wifi, no sound, browser sandbox).

---

## Honest notes (please read)

- **Size:** the ISO should land around **0.9–1.1 GB** (QtWebEngine + VLC are
  the heavy parts). If it exceeds 1.2 GB, trim knobs are in TROUBLESHOOTING.md.
- **RAM:** idles well under 1 GB, but Chromium-based browsing with many tabs
  will feel the 1 GB floor — that is true of every OS shipping a Chromium
  engine. Mahar Files/Player themselves are light.
- **Boot time:** ~15–25 s on USB 3.0; older USB 2.0 sticks are slower.
- **Ad-blocker** is a domain blocklist, not uBlock Origin — it kills most
  banner/trackers, not every anti-adblock script.
- IceWM cannot do true rounded window corners; the theme gets as close as
  IceWM allows (flat modern look, blue accents).
