#!/bin/bash
# Packs Studio Lite into one self-installing file for the Boddy desktop:
#   ./make-installer.sh [out]    (default: dist/install-studio-lite.sh)
# On the phone, in Boddy's Terminal:  bash ~/Phone/Download/install-studio-lite.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${1:-$HERE/dist/install-studio-lite.sh}
mkdir -p "$(dirname "$OUT")"
PAYLOAD=$(cd "$HERE" && tar --sort=name --owner=0 --group=0 --mtime=@0 -czf - \
    server.py check.luau studio-lite-app.sh web | base64 -w 76)

cat > "$OUT" <<'HEAD'
#!/bin/bash
# Installs (or updates) Studio Lite in the Boddy desktop. Your projects are kept.
set -euo pipefail
APPDIR=$HOME/.local/share/studio-lite/app
echo "Installing Studio Lite..."
command -v python3 >/dev/null || { echo "Studio Lite needs python3 (in Boddy's system already)."; exit 1; }
# Stop an older copy that is still running, so the new one starts next time.
pkill -f "$APPDIR/server.py" 2>/dev/null || true
rm -f "$HOME/.local/share/studio-lite/url"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
sed -n '/^__PAYLOAD__$/,$p' "$0" | tail -n +2 | base64 -d | tar -xzf - -C "$TMP"
rm -rf "$APPDIR.old"; [ -d "$APPDIR" ] && mv "$APPDIR" "$APPDIR.old"
mkdir -p "$(dirname "$APPDIR")" && mv "$TMP" "$APPDIR" && trap - EXIT
chmod +x "$APPDIR/studio-lite-app.sh" "$APPDIR/server.py"
rm -rf "$APPDIR.old"

# Menu entry and desktop icon. Not named boddy-*: Boddy clears those icons on every start.
ENTRY="[Desktop Entry]
Type=Application
Name=Studio Lite
Comment=Make Roblox games on your phone
Exec=$APPDIR/studio-lite-app.sh
Icon=applications-games
Terminal=false
Categories=Development;Boddy;"
for dir in /usr/share/applications "$HOME/Desktop"; do
    mkdir -p "$dir" 2>/dev/null || continue
    printf '%s\n' "$ENTRY" > "$dir/studio-lite.desktop" 2>/dev/null || continue
    chmod +x "$dir/studio-lite.desktop"
done
if [ -f "$HOME/Desktop/studio-lite.desktop" ]; then
    gio set "$HOME/Desktop/studio-lite.desktop" metadata::xfce-exe-checksum \
        "$(sha256sum "$HOME/Desktop/studio-lite.desktop" | cut -d' ' -f1)" 2>/dev/null || true
fi
echo "Done. Open \"Studio Lite\" on the desktop (or run $APPDIR/studio-lite-app.sh)."
exit 0
__PAYLOAD__
HEAD
printf '%s\n' "$PAYLOAD" >> "$OUT"
chmod +x "$OUT"
echo "$OUT ($(du -h "$OUT" | cut -f1))"
