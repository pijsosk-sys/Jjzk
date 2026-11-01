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
menu "Screen colours" "16-bit"
check "Screen colours warns that 16-bit leaves Studio's 3D view black" 'grep -q "3D view stays black" "$HOME/zenity.log"'
check "picking 16-bit saves it for start-desktop.sh" '[ "$(cat "$HOME/.config/boddy/vnc-depth")" = 16 ]'
menu "Screen colours" "24-bit"
check "picking 24-bit saves it" '[ "$(cat "$HOME/.config/boddy/vnc-depth")" = 24 ]'
for p in Lower Potato Low; do
    menu "Performance preset" "$p"
    check "Performance preset: picking $p saves it" '[ "$(cat "$HOME/$DATA/preset" 2>/dev/null)" = $p ] && grep -q "preset set to $p" "$HOME/zenity.log"'
done
check "Performance preset lists Low, Lower and Potato" 'grep -q "Low.*Lower.*Potato" "$HOME/zenity.log"'
menu "" "Performance preset"
check "main menu offers Performance preset" 'grep -q "Performance preset" "$HOME/zenity.log"'
echo "$fails failed"; exit $((fails>0))
