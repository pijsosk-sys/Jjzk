#!/bin/bash
# Installs Antigravity from the Linux ARM64 tarball you download from Google.
set -e
echo "Opening the Antigravity download page."
echo "Pick: Linux -> Download for ARM64. Come back here when it has finished."
firefox-esr https://antigravity.google/download >/dev/null 2>&1 &
read -rp "Press Enter once the download is complete... "

TARBALL=$(ls -t /root/Downloads/*ntigravity*.tar.gz 2>/dev/null | head -1)
if [ -z "$TARBALL" ]; then
    echo "Couldn't find an Antigravity .tar.gz in ~/Downloads."
    read -rp "Press Enter to close."
    exit 1
fi

echo "Installing $TARBALL"
rm -rf /opt/antigravity /tmp/ag && mkdir -p /tmp/ag
tar -xzf "$TARBALL" -C /tmp/ag
# The tarball holds a single top-level folder; move whatever it is to /opt/antigravity.
mv "$(find /tmp/ag -mindepth 1 -maxdepth 1 -type d | head -1)" /opt/antigravity
BIN=$(find /opt/antigravity -maxdepth 1 -type f -iname 'antigravity*' -perm -u+x | head -1)
[ -n "$BIN" ] && [ "$BIN" != /opt/antigravity/antigravity ] && ln -sf "$BIN" /opt/antigravity/antigravity

/opt/boddy/make-launchers.sh
echo "Antigravity installed. Use the Antigravity icon on the desktop."
read -rp "Press Enter to close."
