#!/bin/bash
# First-boot setup, run inside the Debian rootfs. Safe to re-run.
# Lines starting with ">>> " are shown as the status in the app.
set -e
export DEBIAN_FRONTEND=noninteractive
APT="apt-get -y -o Dpkg::Use-Pty=0 -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold"

echo ">>> Step 3 of 4: Getting the app list"
dpkg --configure -a || true   # recover from an interrupted earlier attempt
apt-get update

echo ">>> Step 3 of 4: Installing the desktop and Firefox (the long part)"
$APT install --no-install-recommends \
    xfce4-session xfwm4 xfce4-panel xfdesktop4 xfce4-settings xfconf \
    xfce4-terminal thunar mousepad xfce4-appfinder \
    tigervnc-standalone-server dbus-x11 at-spi2-core \
    firefox-esr synaptic \
    adwaita-icon-theme gnome-themes-extra librsvg2-common \
    fonts-dejavu-core fonts-noto-color-emoji \
    ca-certificates curl wget xz-utils unzip zip sudo procps less nano git \
    xdotool scrot x11-utils python3

echo ">>> Step 3 of 4: Installing Java and Node (for Minecraft mods and Codex)"
$APT install --no-install-recommends openjdk-21-jdk-headless nodejs npm

# Firefox's content sandbox relies on kernel features proot can't provide.
mkdir -p /etc/firefox-esr
cat > /etc/firefox-esr/boddy.js <<'PREFS'
pref("security.sandbox.content.level", 0);
pref("browser.startup.homepage", "https://claude.ai");
pref("browser.aboutwelcome.enabled", false);
pref("layout.css.devPixelsPerPx", "1.25");
PREFS

echo ">>> Step 4 of 4: Installing Claude Code"
if [ ! -x /root/.local/bin/claude ]; then
    curl -fsSL https://claude.ai/install.sh | bash || echo "!!! Claude Code install failed; it will retry when you open it"
fi

echo ">>> Step 4 of 4: Installing Codex"
if ! command -v codex >/dev/null; then
    npm install -g @openai/codex || echo "!!! Codex install failed; it will retry when you open it"
fi

echo ">>> Step 4 of 4: Making it Boddy"
/opt/boddy/ai-setup.sh
/opt/boddy/make-launchers.sh
/opt/boddy/apply-theme.sh

echo ">>> Boddy is ready"
