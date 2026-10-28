#!/bin/bash
# Builds install-claude-remote.sh: one file with everything in it, to run inside Boddy
# without updating the app:  bash ~/Phone/Download/install-claude-remote.sh
cd "$(dirname "$0")"
{
    echo '#!/bin/bash'
    echo '# Installs Claude Remote into Boddy. Run in the Boddy terminal: bash install-claude-remote.sh'
    echo 'set -e'
    echo 'tmp=$(mktemp -d)'
    for f in boddy-remote remote-control.sh boddy-guard.py remote-setup.sh; do
        echo "base64 -d > \"\$tmp/$f\" <<'B64'"
        base64 "$f"
        echo 'B64'
    done
    echo 'bash "$tmp/remote-setup.sh"'
    echo 'rm -rf "$tmp"'
} > install-claude-remote.sh
chmod +x install-claude-remote.sh
