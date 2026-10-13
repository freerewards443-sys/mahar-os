# Mahar OS — Troubleshooting guide (STEP 10)

## Build problems

**`lb build` fails with "no space left"**
live-build needs ~10 GB free (chroot + squashfs + ISO staging). Free space
or set `TMPDIR` to a bigger disk. Never build in a VM with a tiny disk.

**debootstrap fails / 404 on packages**
Your mirror may be stale: `apt-get update` on the host, check internet,
then re-run. Corporate proxies need `http_proxy`/`https_proxy` exported.

**"Unable to locate package ..."**
You are not on Bookworm (or your `sources.list` lacks contrib/non-free).
The script requires Debian 12 / Ubuntu 22.04+.

**grub-efi / syslinux errors**
Install the EFI deps from build.sh manually:
`sudo apt install grub-efi-amd64-bin syslinux-efi isolinux` and re-run.

**Build is slow**
Normal: 30–90 min. Most time is debootstrap + downloading packages.
Use a local Debian mirror (`--archive-mirror`) to speed it up.

**ISO is bigger than 1.2 GB — trim knobs**
1. Drop `vlc` for `mpv` + smaller Qt? (needs code change — biggest saver,
   ~150 MB)
2. Remove `firmware-misc-nonfree` (~50–100 MB, but some wifi may break)
3. In `config/auto/config` add `--apt-indices false` (~40 MB; then run
   `sudo apt update` manually inside the live session before installing)

## Boot problems

**"No bootable device" / USB not listed**
Re-flash with Balena Etcher, or Rufus in **DD mode** (not ISO mode).
Some old BIOSes need the stick written with `dd` (see README step 8).

**Secure Boot blocks the ISO**
Debian live images are signed, but cheap firmware is picky: disable
Secure Boot in firmware settings and try again.

**Black screen after the boot menu**
At the boot menu press `e`/`Tab` and remove `quiet splash` to see messages.
Common cause: missing GPU firmware — add `firmware-misc-nonfree`
(already included) or try the "failsafe" boot entry.

**Stuck at Plymouth splash**
Rare; boot with `splash` removed to watch the text log.

## Runtime problems (inside Mahar OS)

**Wi-Fi not working**
Click tray network icon → check "Enable Wi-Fi". Very new cards may need
firmware not in the ISO — connect via USB tethering/Ethernet, then
`sudo apt update && sudo apt install firmware-iwlwifi` (example).

**No sound**
Open `pavucontrol` (start menu → Settings → Volume), check the Output
Devices tab and that nothing is muted. `pasystray` in the tray controls
volume day-to-day.

**Browser shows a sandbox error**
You should never see this: the browser runs as user `mahar`, not root.
If you launched it with sudo, don't — QtWebEngine refuses root.

**An app won't start from its icon**
Open a terminal (Alt+Ctrl+T) and run it manually, e.g.
`python3 /opt/mahar/browser/mahar_browser.py`, and read the error.
Missing package? `sudo apt update && sudo apt install <package>`.

**Wrong screen resolution**
`xrandr` in a terminal lists modes; IceWM picks the best automatically.
For HiDPI, Qt apps respect `QT_SCALE_FACTOR=1.5` (set in
`/etc/icewm/startup` if you need it).

**Forgot: is there an installer?**
No — Mahar OS 1.0 is a live system by design (`--debian-installer false`).
It runs from USB/RAM and forgets changes on reboot unless you add
persistence (a `persistence` partition + the `persistence` boot parameter —
see the Debian Live manual).

## Getting help

Re-run the failing step with more output (`lb build` already logs to
`build.log` in the build dir), and search the exact error plus "live-build
bookworm" — the Debian Live mailing list archives cover nearly everything.
