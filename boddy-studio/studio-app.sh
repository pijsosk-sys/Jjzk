#!/bin/bash
# "Roblox Studio (experimental)" desktop app. Runs /opt/boddy/studio behind a progress window instead of a
# terminal (two fewer processes, which matters under Android's process limit) and shows errors as dialogs.
STUDIO=${BODDY_STUDIO:-/opt/boddy/studio}
LOG_DIR="$HOME/.cache/roblox-studio"
SEEN="$HOME/.config/boddy"
TITLE="Roblox Studio"
start=/root/Phone/Download/; [ -d "$start" ] || start=/root/
mkdir -p "$SEEN" "$LOG_DIR"

if [ ! -e "$SEEN/studio-intro-shown" ]; then
    zenity --question --width=460 --title="Roblox Studio (experimental)" --ok-label="Try it" --cancel-label="Cancel" \
        --text="Runs the official Windows Roblox Studio through Linux/Wine compatibility software. First setup needs several GB of storage and downloads. Android graphics and sign-in remain unverified.\n\nIf Boddy keeps reconnecting, Android is stopping it: on Android 14+ turn on Developer options > Disable child process restrictions." 2>/dev/null || exit 0
    touch "$SEEN/studio-intro-shown"
fi

# The action can be given as an argument (e.g. from a desktop shortcut): studio-app.sh "Open Studio"
choice=${1:-}
[ -n "$choice" ] || choice=$(zenity --list --title="$TITLE" --width=420 --height=300 --column=Action \
    "Open Studio" "Open a place file" "Diagnostics" "Reset Studio" 2>/dev/null) || exit 0
case "$choice" in
    "Open Studio") args=() ;;
    "Open a place file")
        f=$(zenity --file-selection --filename="$start" --file-filter="Roblox place | *.rbxl *.rbxlx" 2>/dev/null) || exit 0
        args=("$f") ;;
    "Diagnostics")
        "$STUDIO" --diagnose 2>&1 | zenity --text-info --title="$TITLE diagnostics" --width=760 --height=520 2>/dev/null
        exit 0 ;;
    "Reset Studio")
        zenity --question --width=420 --title="$TITLE" --ok-label="Reset" \
            --text="Start Studio with a fresh Windows setup? The current one is kept as a backup." 2>/dev/null || exit 0
        args=(--reset) ;;
    *) exit 0 ;;  # OK pressed with nothing selected
esac

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkfifo "$work/progress"
zenity --progress --title="$TITLE" --width=440 --auto-close --percentage=0 \
    --text="Starting..." <"$work/progress" 2>/dev/null &
zenity_pid=$!

# The launcher runs in its own process group so Cancel can stop it together with its downloads.
setsid bash -c 'echo $$ >"$1"; "$2" "${@:4}"; echo $? >"$3"' _ \
    "$work/pid" "$STUDIO" "$work/status" "${args[@]}" 2>&1 |
(
    # Only this reader holds the progress pipe, so the window sees end-of-input if the launcher stops early.
    # zenity may already be closed (Studio started, or Cancel); keep reading so the launcher never blocks.
    exec 3>"$work/progress"
    trap '' PIPE
    say() { printf '%s\n' "$@" >&3 2>/dev/null; }
    while IFS= read -r line; do
        case "$line" in
            "Studio "[0-9]*/[0-9]*:*)
                n=${line#Studio }; n=${n%%:*}
                say "$(( ${n%/*} * 90 / ${n#*/} ))" "# Downloading Roblox Studio (${n%/*} of ${n#*/})..." ;;
            "Installing Hangover"*) say 2 "# Installing the Windows compatibility layer..." ;;
            "Testing Windows command"*) say 5 "# Setting up Windows compatibility (the first time takes several minutes)..." ;;
            "Could not check for Studio updates"*) say "# Offline: opening the installed Studio..." ;;
            "Desktop enlarged"*) say 95 "# Making room on the desktop..." ;;
            "Launching real Roblox Studio"*) touch "$work/started"; say 100 ;;
            "LAUNCH FAILED: "*) printf '%s\n' "${line#LAUNCH FAILED: }" >>"$work/error" ;;
            "Studio process exited with code "*) printf '%s\n' "${line#Studio process exited with code }" >"$work/exit" ;;
        esac
    done
) &
reader_pid=$!

# Closing the progress window before Studio starts means Cancel.
wait "$zenity_pid"
if [ ! -e "$work/started" ] && [ -s "$work/pid" ] && kill -0 "$(cat "$work/pid")" 2>/dev/null; then
    kill -TERM -- "-$(cat "$work/pid")" 2>/dev/null
    wait "$reader_pid"
    exit 0
fi

if [ -e "$work/started" ] && [ ! -e "$SEEN/studio-signin-tip-shown" ]; then
    touch "$SEEN/studio-signin-tip-shown"
    zenity --info --width=420 --title="$TITLE" --timeout=30 \
        --text="To sign in, choose <b>Login via Browser</b> in Studio, sign in in the Firefox window that opens, then allow Firefox to open the Roblox Studio link." 2>/dev/null &
fi

wait "$reader_pid"
status=$(cat "$work/status" 2>/dev/null || echo 1)
[ "$status" = 0 ] && exit 0

if [ -s "$work/error" ]; then
    text="Roblox Studio could not start:\n\n$(head -c 1500 "$work/error")"
else
    text="Roblox Studio closed unexpectedly (exit code $(cat "$work/exit" 2>/dev/null || echo "$status"))."
fi
# Pango markup: escape what the launcher printed.
text=$(printf '%s' "$text" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g')
if [ "$(zenity --error --width=520 --title="$TITLE" --text="$text" --extra-button="Show log" 2>/dev/null)" = "Show log" ]; then
    mousepad "$LOG_DIR/latest.log" >/dev/null 2>&1 &
fi
