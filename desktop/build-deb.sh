#!/bin/bash
# Build mahar-browser .deb package (run from repo root)
set -e
VER="${1:-1.1}"
PKG="/tmp/mahar-browser_${VER}"
rm -rf "$PKG"
mkdir -p "$PKG/DEBIAN" "$PKG/opt/mahar-browser" "$PKG/usr/bin" \
         "$PKG/usr/share/applications" \
         "$PKG/usr/share/icons/hicolor/64x64/apps"

cat > "$PKG/DEBIAN/control" <<EOF
Package: mahar-browser
Version: $VER
Section: web
Priority: optional
Architecture: all
Depends: python3, python3-pyqt5, python3-pyqt5.qtwebengine
Maintainer: Mahar OS <noreply@mahar.os>
Description: Mahar Browser - lightweight tabbed web browser
 Custom tabbed browser for Mahar OS with bookmarks, history, downloads,
 ad-blocker, dark mode and IP changer (proxy).
EOF

cp apps/browser/mahar_browser.py "$PKG/opt/mahar-browser/"
cp apps/browser/mahar_home.html apps/browser/mahar_offline.html \
   "$PKG/opt/mahar-browser/"
cp assets/mahar-browser-logo-256.png "$PKG/opt/mahar-browser/"
cp assets/hicolor/64x64/apps/mahar-browser.png \
   "$PKG/usr/share/icons/hicolor/64x64/apps/"
printf '#!/bin/sh\nexec /usr/bin/python3 /opt/mahar-browser/mahar_browser.py "$@"\n' \
  > "$PKG/usr/bin/mahar-browser"
chmod 755 "$PKG/usr/bin/mahar-browser"
cp desktop/install/mahar-browser-deb.desktop \
   "$PKG/usr/share/applications/mahar-browser.desktop"

chmod 755 "$PKG/DEBIAN"
chmod 644 "$PKG/DEBIAN/control"
# fix permissions: dirs 755, files 644 (dpkg strict about this)
find "$PKG/opt" "$PKG/usr" -type d -exec chmod 755 {} +
find "$PKG/opt" "$PKG/usr" -type f -exec chmod 644 {} +
chmod 755 "$PKG/usr/bin/mahar-browser"
dpkg-deb --build "$PKG" "mahar-browser_${VER}_all.deb"
echo "Built: mahar-browser_${VER}_all.deb"
