#!/bin/bash
# First-boot setup, run inside the Debian rootfs. Safe to re-run.
# Lines starting with ">>> " are shown as the status in the app.
set -e
export DEBIAN_FRONTEND=noninteractive
APT="apt-get -y -o Dpkg::Use-Pty=0 -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold"

install -m644 /opt/boddy/apt-boddy.conf /etc/apt/apt.conf.d/99boddy
mkdir -p /etc/dpkg/dpkg.cfg.d && install -m644 /opt/boddy/dpkg-boddy.cfg /etc/dpkg/dpkg.cfg.d/99boddy
dpkg --configure -a || true   # recover from an interrupted earlier attempt

# The prebuilt Boddy image already has everything; only install what's missing.
missing=""
for p in $(grep -v '^\s*#' /opt/boddy/packages.txt); do
    dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "ok installed" || missing="$missing $p"
done
if [ -n "$missing" ]; then
    echo ">>> Step 3 of 4: Getting the app list"
    apt-get update
    echo ">>> Step 3 of 4: Installing apps (the long part)"
    $APT install --no-install-recommends $missing
else
    echo ">>> Step 3 of 4: Apps already installed"
fi

# Firefox's content sandbox relies on kernel features proot can't provide.
mkdir -p /etc/firefox-esr
cat > /etc/firefox-esr/boddy.js <<'PREFS'
pref("security.sandbox.content.level", 0);
pref("browser.startup.homepage", "about:blank");
pref("browser.aboutwelcome.enabled", false);
pref("layout.css.devPixelsPerPx", "1.25");
// Fewer background processes: less memory and faster on a phone.
pref("dom.ipc.processCount", 2);
pref("browser.tabs.unloadOnLowMemory", true);
pref("browser.sessionhistory.max_total_viewers", 0);
pref("browser.cache.memory.capacity", 65536);
pref("browser.sessionstore.interval", 60000);
pref("toolkit.telemetry.enabled", false);
pref("datareporting.healthreport.uploadEnabled", false);
PREFS

echo ">>> Step 4 of 4: Setting up the desktop"
/opt/boddy/make-launchers.sh
/opt/boddy/apply-theme.sh

echo ">>> Ready to launch Roblox Studio"
