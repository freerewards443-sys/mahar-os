# Mahar OS — Testing guide (STEP 9)

## 1. Verify the download/build

```bash
cd mahar-os
sha256sum -c Mahar.iso.sha256   # must say "OK"
ls -lh Mahar.iso                 # expect ~0.9-1.2 GB
```

## 2. Test in a virtual machine first (safe, fast)

**QEMU (command line):**
```bash
sudo apt install qemu-system-x86
qemu-system-x86_64 -m 2048 -enable-kvm -vga virtio \
  -drive file=Mahar.iso,format=raw,readonly=on \
  -boot d
```
Also test the 1 GB floor: `-m 1024`.

**VirtualBox:** New VM → Type Linux, Version Debian 64-bit → 2048 MB RAM →
do NOT add a hard disk → Settings → Storage → add the ISO as optical drive →
enable EFI (Settings → System → "Enable EFI") for a UEFI test, then repeat
with EFI off for a BIOS test → Start.

**What to check in the VM:**
- [ ] Plymouth splash shows the Mahar logo, then the desktop appears
- [ ] Autologin as `mahar` (no password prompt)
- [ ] Wallpaper = Mahar OS branding; taskbar at bottom with Start button
- [ ] Start menu opens; clock + tray icons visible on the right
- [ ] Desktop icons: Mahar Browser / Mahar Files / Mahar Player
- [ ] Open each app from the desktop icon AND the start menu
- [ ] Browser: open example.com, new tab (Ctrl+T), bookmark it (Ctrl+D),
      open history (Ctrl+H), toggle dark mode
- [ ] Files: browse /, create a folder, copy/paste a file, search
- [ ] Player: play an MP3/MP4 (copy one in via shared folder or USB)
- [ ] Terminal (Alt+Ctrl+T), text editor, calculator from the menu
- [ ] Network icon in tray; reboot and shutdown from the start menu

## 3. Flash to a real USB stick

Use **Balena Etcher** (easiest) or **Rufus** in **DD mode**
(ISO mode sometimes breaks hybrid images).

Command line (Linux) — **triple-check `/dev/sdX`** with `lsblk` first:
```bash
sudo dd if=Mahar.iso of=/dev/sdX bs=4M status=progress conv=fsync
```

## 4. Boot a real laptop/PC

1. Plug in the USB, power on, open the boot menu
   (usually F12 / F9 / Esc / F2 — varies by brand).
2. Select the USB stick. If it fails, try the other entry
   (UEFI vs Legacy/BIOS) or disable Secure Boot (see TROUBLESHOOTING.md).
3. Run the same checklist as in the VM, plus:
   - [ ] Wi-Fi connects (tray network icon)
   - [ ] Sound works (tray volume icon → test in Mahar Player)
   - [ ] Screen resolution is correct
   - [ ] Time the boot: power-on → desktop (target < 30 s on USB 3.0)

## 5. Report template (for your notes)

```
Machine:
BIOS or UEFI:
Boot time (s):
ISO size (MB):
RAM used at idle (run `free -m` in terminal):
PASS / FAIL + notes:
```
