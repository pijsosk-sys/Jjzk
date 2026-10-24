#!/bin/bash
# First-boot setup, run inside the Debian rootfs. Safe to re-run.
set -e
export DEBIAN_FRONTEND=noninteractive

echo ">>> Updating package lists"
apt-get update

echo ">>> Installing the Boddy desktop (this takes a while)"
apt-get install -y --no-install-recommends \
    xfce4-session xfwm4 xfce4-panel xfdesktop4 xfce4-settings xfconf \
    xfce4-terminal thunar mousepad xfce4-appfinder \
    tigervnc-standalone-server dbus-x11 at-spi2-core \
    firefox-esr synaptic \
    adwaita-icon-theme fonts-dejavu-core fonts-noto-color-emoji \
    ca-certificates curl wget xz-utils unzip sudo procps less nano git

# Firefox's content sandbox relies on kernel features proot can't provide.
mkdir -p /etc/firefox-esr
cat > /etc/firefox-esr/boddy.js <<'PREFS'
pref("security.sandbox.content.level", 0);
pref("browser.startup.homepage", "https://claude.ai");
PREFS

echo ">>> Installing Claude Code"
if ! command -v claude >/dev/null 2>&1 && [ ! -x /root/.local/bin/claude ]; then
    curl -fsSL https://claude.ai/install.sh | bash || echo "!!! Claude Code install failed; run it later from the desktop icon"
fi

/opt/boddy/make-launchers.sh

touch /opt/boddy/.setup-done
echo ">>> Boddy is ready"
