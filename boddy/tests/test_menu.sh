#!/bin/bash
# Drives studio-app.sh's menus with a stand-in zenity that picks a given row, against the real launcher,
# and checks what the launcher saved. Usage: test_menu.sh /path/to/boddy   (needs python3 only)
set -u
BODDY=$(cd "${1:-$(dirname "$0")/..}" && pwd)
fails=0
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails+1)); fi; }
# A fresh home for each run; zenity answers the --list with $PICK and records every call.
menu() {  # menu ACTION PICK
    export HOME=$(mktemp -d) PICK="$2" DISPLAY=:99
    mkdir -p "$HOME/.config/boddy" "$HOME/bin" && touch "$HOME/.config/boddy/studio-intro-shown"
    cat >"$HOME/bin/zenity" <<'Z'
#!/bin/bash
printf '%s\n' "$*" >>"$HOME/zenity.log"
case "$*" in *--list*) echo "$PICK" ;; esac
exit 0
Z
    chmod +x "$HOME/bin/zenity"
    PATH="$HOME/bin:$PATH" BODDY_STUDIO="$BODDY/studio" bash "$BODDY/studio-app.sh" "$1" </dev/null >"$HOME/out.log" 2>&1
}
DATA=.local/share/roblox-studio
menu "Graphics mode" "GPU (Adreno, experimental)"
check "Graphics mode lists the GPU mode" 'grep -q "GPU (Adreno, experimental)" "$HOME/zenity.log"'
check "picking it saves renderer=GPU" '[ "$(cat "$HOME/$DATA/renderer" 2>/dev/null)" = GPU ]'
check "and confirms it" 'grep -q "Graphics mode set to GPU" "$HOME/zenity.log"'
echo "$fails failed"; exit $((fails>0))
