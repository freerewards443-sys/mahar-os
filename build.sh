#!/bin/bash
###############################################################################
# Mahar OS 1.0 - one-shot ISO builder
#
# Builds "Mahar.iso" (Debian Bookworm based, amd64, hybrid ISO).
# Run on a Debian or Ubuntu machine with internet access:
#
#     sudo ./build.sh
#
# Takes roughly 30-90 minutes depending on machine and network speed.
###############################################################################
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
ISO_NAME="Mahar.iso"

echo "=================================================================="
echo " Mahar OS builder"
echo " Project : $PROJECT_DIR"
echo " Build   : $BUILD_DIR"
echo " Output  : $ISO_NAME"
echo "=================================================================="

# ---------------------------------------------------------------- 1. checks
if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: please run as root:  sudo ./build.sh"
    exit 1
fi

if [ ! -f /etc/debian_version ] && ! grep -qi "ubuntu" /etc/os-release 2>/dev/null; then
    echo "ERROR: this script needs Debian or Ubuntu as the build machine."
    exit 1
fi

for f in apps/browser/mahar_browser.py apps/player/mahar_player.py \
         apps/files/mahar_files.py desktop/welcome/mahar_welcome.py \
         desktop/icewm/preferences desktop/icewm/theme/mahar/default.theme \
         assets/wallpaper/mahar-wallpaper.png; do
    if [ ! -f "$PROJECT_DIR/$f" ]; then
        echo "ERROR: missing source file: $f"
        exit 1
    fi
done

# ------------------------------------------------------- 2. build dependencies
echo ">>> Installing build dependencies..."
apt-get update
# NOTE: we deliberately do NOT install Ubuntu's live-build package:
# it silently ignores --mode debian and can only build Ubuntu images.
# We install the real Debian live-build instead.
apt-get install -y \
    debootstrap debian-archive-keyring squashfs-tools xorriso \
    isolinux syslinux-efi \
    grub-pc-bin grub-efi-amd64-bin \
    mtools dosfstools \
    python3-pil wget

echo ">>> Installing the real Debian live-build (replaces Ubuntu's)..."
wget -q -O /tmp/live-build-debian.deb \
    http://ftp.debian.org/debian/pool/main/l/live-build/live-build_20230502_all.deb
dpkg -i /tmp/live-build-debian.deb || apt-get install -yf
rm -f /tmp/live-build-debian.deb
dpkg -l live-build | tail -1

# ------------------------------------------------- 3. assemble the build tree
echo ">>> Assembling build tree..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$BUILD_DIR/config"
cd "$BUILD_DIR"

# NOTE: new live-build auto-discovers "auto/config" at the build root
# (not config/auto/config like the old version).
cp -a "$PROJECT_DIR/config/auto"            auto
cp -a "$PROJECT_DIR/config/package-lists"   config/package-lists
cp -a "$PROJECT_DIR/config/hooks"           config/hooks
chmod +x config/hooks/live/*.hook.chroot auto/config

INC="$BUILD_DIR/config/includes.chroot"
mkdir -p "$INC"/opt/mahar/{browser,player,files} \
         "$INC"/usr/local/bin \
         "$INC"/usr/share/{applications,xsessions,pixmaps} \
         "$INC"/usr/share/backgrounds/mahar \
         "$INC"/usr/share/plymouth/themes/mahar \
         "$INC"/usr/share/icewm/themes/mahar \
         "$INC"/usr/share/icons/hicolor \
         "$INC"/etc/icewm \
         "$INC"/etc/default \
         "$INC"/etc/sudoers.d \
         "$INC"/etc/skel/.idesktop

# --- Mahar apps ---
cp "$PROJECT_DIR/apps/browser/mahar_browser.py"   "$INC/opt/mahar/browser/"
cp "$PROJECT_DIR/apps/browser/mahar_home.html"    "$INC/opt/mahar/browser/"
cp "$PROJECT_DIR/apps/browser/mahar_offline.html" "$INC/opt/mahar/browser/"
cp "$PROJECT_DIR/assets/mahar-browser-logo-256.png" "$INC/opt/mahar/browser/"
cp "$PROJECT_DIR/apps/browser/mahar-browser.desktop" "$INC/usr/share/applications/"
cp "$PROJECT_DIR/apps/player/mahar_player.py"     "$INC/opt/mahar/player/"
cp "$PROJECT_DIR/apps/player/mahar-player.desktop"  "$INC/usr/share/applications/"
cp "$PROJECT_DIR/apps/files/mahar_files.py"       "$INC/opt/mahar/files/"
cp "$PROJECT_DIR/apps/files/mahar-files.desktop" "$INC/usr/share/applications/"
cp "$PROJECT_DIR/desktop/welcome/mahar_welcome.py" "$INC/usr/local/bin/mahar-welcome"
chmod 755 "$INC/usr/local/bin/mahar-welcome"
cp "$PROJECT_DIR/desktop/welcome/mahar-welcome.desktop" "$INC/usr/share/applications/"

# --- system installer: desktop entry + passwordless sudo for the live user ---
cp "$PROJECT_DIR/desktop/install/install-mahar.desktop" "$INC/usr/share/applications/"
cp "$PROJECT_DIR/desktop/install/mahar-live.sudoers" "$INC/etc/sudoers.d/mahar-live"
chmod 0440 "$INC/etc/sudoers.d/mahar-live"

# --- IceWM desktop shell ---
cp "$PROJECT_DIR"/desktop/icewm/{preferences,toolbar,startup,keys,menu} "$INC/etc/icewm/"
chmod +x "$INC/etc/icewm/startup"
# theme -> /usr/share/icewm/themes/mahar/ (IceWM's real theme location)
cp "$PROJECT_DIR/desktop/icewm/theme/mahar/default.theme" "$INC/usr/share/icewm/themes/mahar/"
# select the theme (IceWM reads the one-line "theme" config file)
printf 'Theme="mahar"\n' > "$INC/etc/icewm/theme"
# start-button logo: IceWM loads start.xpm from the active theme dir.
# (Pillow 10+ cannot save XPM, so we write the XPM text manually.)
python3 - "$PROJECT_DIR/assets/icons/mahar-logo.png" "$INC" <<'EOF'
import sys
from PIL import Image

src, inc = sys.argv[1], sys.argv[2]
# Flatten the logo onto the dark taskbar color, then quantize to 16 colors
bg = Image.new("RGB", (24, 24), (27, 30, 36))
logo = Image.open(src).convert("RGBA").resize((24, 24), Image.LANCZOS)
bg.paste(logo, (0, 0), logo)
q = bg.quantize(colors=16, method=Image.MEDIANCUT)
w, h = q.size
used = sorted(set(q.getdata()))
glyphs = "0123456789abcdef"
cmap = {v: glyphs[i] for i, v in enumerate(used)}
pal = q.getpalette()
px = q.load()
with open(f"{inc}/usr/share/icewm/themes/mahar/start.xpm", "w") as f:
    f.write("/* XPM */\nstatic char * start_xpm[] = {\n")
    f.write(f'"{w} {h} {len(used)} 1",\n')
    for v in used:
        r, g, b = pal[v * 3:v * 3 + 3]
        f.write(f'"{cmap[v]} c #{r:02X}{g:02X}{b:02X}",\n')
    for y in range(h):
        f.write('"' + "".join(cmap[px[x, y]] for x in range(w)) + '",\n')
    f.write("};\n")
print("start.xpm installed")
EOF

# desktop icons (idesk) -> new user's home via /etc/skel
cp "$PROJECT_DIR/desktop/idesk/ideskrc" "$INC/etc/skel/.ideskrc"
cp "$PROJECT_DIR"/desktop/idesk/*.lnk "$INC/etc/skel/.idesktop/"
# launch idesk from the IceWM startup script if not already there
if ! grep -q "idesk" "$INC/etc/icewm/startup"; then
    cat >> "$INC/etc/icewm/startup" <<'EOF'

# desktop icons
if command -v idesk >/dev/null 2>&1; then
    ( sleep 2 && idesk ) &
fi
EOF
fi

# --- login / session / boot splash ---
cp "$PROJECT_DIR/desktop/nodm/nodm.conf"          "$INC/etc/default/nodm"
cp "$PROJECT_DIR/desktop/xsessions/mahar.desktop" "$INC/usr/share/xsessions/"
cp "$PROJECT_DIR"/desktop/plymouth/mahar.plymouth \
   "$PROJECT_DIR"/desktop/plymouth/mahar.script \
   "$INC/usr/share/plymouth/themes/mahar/"

# --- branding assets ---
cp -r "$PROJECT_DIR"/assets/hicolor/. "$INC/usr/share/icons/hicolor/"
cp "$PROJECT_DIR/assets/wallpaper/mahar-wallpaper.png" "$INC/usr/share/backgrounds/mahar/"
python3 - "$PROJECT_DIR/assets/icons/mahar-logo.png" "$INC" <<'EOF'
import sys
from PIL import Image
src, inc = sys.argv[1], sys.argv[2]
logo = Image.open(src).convert("RGBA")
logo.resize((512, 512), Image.LANCZOS).save(f"{inc}/usr/share/plymouth/themes/mahar/logo.png")
logo.resize((256, 256), Image.LANCZOS).save(f"{inc}/usr/share/pixmaps/mahar-logo.png")
print("logo assets installed")
EOF

echo ">>> Build tree ready."

# ------------------------------------------------------- 4. build the ISO
echo ">>> Running live-build (this takes a while, 30-90 min)..."
lb clean >/dev/null 2>&1 || true
lb config
lb build

# ------------------------------------------------------- 5. rename & checksum
BUILT_ISO="$(ls -1 live-image-amd64.hybrid.iso 2>/dev/null || true)"
if [ -z "$BUILT_ISO" ]; then
    echo "ERROR: live-build did not produce the expected ISO."
    exit 1
fi

mv "$BUILT_ISO" "$PROJECT_DIR/$ISO_NAME"
cd "$PROJECT_DIR"
sha256sum "$ISO_NAME" | tee "$ISO_NAME.sha256"

SIZE_MB=$(du -m "$ISO_NAME" | cut -f1)
echo "=================================================================="
echo " DONE: $PROJECT_DIR/$ISO_NAME  (${SIZE_MB} MB)"
echo " SHA256: $(cut -d' ' -f1 "$ISO_NAME.sha256")"
echo ""
echo " Next: flash to USB with Balena Etcher / Rufus (DD mode) or:"
echo "   sudo dd if=$ISO_NAME of=/dev/sdX bs=4M status=progress conv=fsync"
echo " (replace /dev/sdX with your USB stick - triple-check the letter!)"
echo "=================================================================="
