#!/bin/bash
# "Roblox Studio (experimental)" desktop app. Runs /opt/boddy/studio behind a progress window instead of a
# terminal (two fewer processes, which matters under Android's process limit) and shows errors as dialogs.
STUDIO=${BODDY_STUDIO:-/opt/boddy/studio}
LOG_DIR="$HOME/.cache/roblox-studio"
SEEN="$HOME/.config/boddy"
TITLE="Roblox Studio"
start=/root/Phone/Download/; [ -d "$start" ] || start=/root/
MONITOR=/root/Phone/Download/Boddy-Studio-monitor.log; [ -d /root/Phone/Download ] || MONITOR="$LOG_DIR/Boddy-Studio-monitor.log"
RUNNING="$SEEN/studio-running"
mkdir -p "$SEEN" "$LOG_DIR"

escape() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }  # for zenity's Pango markup

# Copies the end of a launch log (and the session monitor) to the clipboard; Boddy passes the
# desktop clipboard on to the phone, so it can be pasted into a chat or an email.
copy_log() {
    command -v xclip >/dev/null || apt-get install -y -q --no-install-recommends xclip >/dev/null 2>&1
    report=$( echo "Roblox Studio on Boddy - log from $(date '+%Y-%m-%d %H:%M')"
              echo "=== launcher log (last 200 lines) ==="; grep -v "^Download progress: " "$1" 2>/dev/null | tail -n 200
              [ -s "$MONITOR" ] && { echo "=== session monitor (last 20 lines) ==="; tail -n 20 "$MONITOR"; } )
    if command -v xclip >/dev/null && printf '%s\n' "$report" | xclip -selection clipboard 2>/dev/null; then
        zenity --info --width=360 --title="$TITLE" --timeout=6 --text="Log copied. Paste it anywhere on your phone." 2>/dev/null
    else
        printf '%s\n' "$report" >"$start/Boddy-Studio-log.txt"
        zenity --info --width=400 --title="$TITLE" --text="Could not use the clipboard. The log was saved as Boddy-Studio-log.txt in $start." 2>/dev/null
    fi
}

# A run that Android stopped (or a crash of Boddy itself) cannot show an error, so say so on the next start.
if [ -e "$RUNNING" ] && ! kill -0 "$(cat "$RUNNING" 2>/dev/null)" 2>/dev/null; then
    rm -f "$RUNNING"
    previous=$(readlink -f "$LOG_DIR/latest.log")
    where=$(grep -E '^(Studio [0-9]+/[0-9]+:|Trying |Testing Windows|Installing (Box64|Wine|Hangover)|Launching real)' "$previous" 2>/dev/null | tail -n 1)
    text="Roblox Studio was stopped before it finished last time${where:+ (at: $where)}.\n\nThis usually means Android stopped Boddy: too many background processes or low memory. On Android 14+ turn on Developer options > Disable child process restrictions, and close other apps. Downloads continue where they stopped."
    [ "$(zenity --warning --width=520 --title="$TITLE" --ok-label="Continue" --extra-button="Copy log" \
        --text="$(printf '%s' "$text" | escape)" 2>/dev/null)" = "Copy log" ] && copy_log "$previous"
fi

if [ ! -e "$SEEN/studio-intro-shown" ]; then
    zenity --question --width=460 --title="Roblox Studio (experimental)" --ok-label="Try it" --cancel-label="Cancel" \
        --text="Runs the official Windows Roblox Studio through Linux/Wine compatibility software. First setup needs several GB of storage and downloads. Android graphics and sign-in remain unverified.\n\nIf Boddy keeps reconnecting, Android is stopping it: on Android 14+ turn on Developer options > Disable child process restrictions." 2>/dev/null || exit 0
    touch "$SEEN/studio-intro-shown"
fi

# The action can be given as an argument (e.g. from a desktop shortcut): studio-app.sh "Open Studio"
choice=${1:-}
[ -n "$choice" ] || choice=$(zenity --list --title="$TITLE" --width=420 --height=300 --column=Action \
    "Open Studio" "Open a place file" "Diagnostics" "Wine version" "Compatibility mode" "Reset Studio" 2>/dev/null) || exit 0
case "$choice" in
    "Open Studio") args=() ;;
    "Open a place file")
        f=$(zenity --file-selection --filename="$start" --file-filter="Roblox place | *.rbxl *.rbxlx" 2>/dev/null) || exit 0
        args=("$f") ;;
    "Diagnostics")
        "$STUDIO" --diagnose 2>&1 | zenity --text-info --title="$TITLE diagnostics" --width=760 --height=520 2>/dev/null
        exit 0 ;;
    "Wine version")
        wf=$HOME/.local/share/roblox-studio/wine-major
        now=$(cat "$wf" 2>/dev/null)
        mark() { [ "$now" = "$1" ] && echo "in use" || echo "$2"; }
        pick=$(zenity --list --title="$TITLE" --width=520 --height=300 \
            --text="Which Wine to run Studio with? Change this if Windows will not start." \
            --column=Version --column=Status \
            "Newest" "$([ -z "$now" ] && echo 'in use, default' || echo default)" \
            "10" "$(mark 10 'older, often emulates better')" \
            "9" "$(mark 9 'older still')" \
            "8" "$(mark 8 'oldest offered')" 2>/dev/null) || exit 0
        [ -n "$pick" ] || exit 0
        [ "$pick" = Newest ] && pick=""
        if out=$("$STUDIO" --use-wine"$pick" 2>&1); then
            zenity --info --width=460 --title="$TITLE" \
                --text="$(printf '%s\n\nChoose Open Studio to install it. The first start takes a while again.' "$out" | escape)" 2>/dev/null
        else
            zenity --error --width=460 --title="$TITLE" --text="$(printf '%s' "$out" | escape)" 2>/dev/null
        fi
        exit 0 ;;
    "Compatibility mode")
        rf=$HOME/.local/share/roblox-studio/runtime
        now=$(cat "$rf" 2>/dev/null); [ -n "$now" ] || now=box64
        pick=$(zenity --list --title="$TITLE" --width=480 --height=260 --column=Mode --column=Status \
            "Box64" "$([ "$now" = box64 ] && echo 'in use, recommended' || echo recommended)" \
            "Hangover" "$([ "$now" = hangover ] && echo 'in use' || echo 'for comparison only')" 2>/dev/null) || exit 0
        case "$pick" in
            Box64) "$STUDIO" --use-box64 ;;
            Hangover) "$STUDIO" --use-hangover ;;
            *) exit 0 ;;
        esac
        zenity --info --width=400 --title="$TITLE" --text="Compatibility mode set to $pick. Choose Open Studio to use it." 2>/dev/null
        exit 0 ;;
    "Reset Studio")
        zenity --question --width=420 --title="$TITLE" --ok-label="Reset" \
            --text="Start Studio with a fresh Windows setup? The current one is kept as a backup." 2>/dev/null || exit 0
        args=(--reset) ;;
    *) exit 0 ;;  # OK pressed with nothing selected
esac

work=$(mktemp -d)
echo $$ >"$RUNNING"
trap 'rm -rf "$work" "$RUNNING"' EXIT
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
    failing=
    while IFS= read -r line; do
        # An error can run over several lines; everything after "LAUNCH FAILED:" belongs to it, apart
        # from the launcher's own closing notes about where the log is.
        if [ -n "$failing" ]; then
            case "$line" in "Launcher log: "*|"Log copied to Downloads"*) ;; *) printf '%s\n' "$line" >>"$work/error" ;; esac
            continue
        fi
        case "$line" in
            "Download progress: "*)  # "Download progress: 42% Downloading Roblox Studio: 610 MB of 1.5 GB at ..."
                rest=${line#Download progress: }
                say "${rest%%\%*}" "# ${rest#*\% }" ;;
            "Studio crashed after"*)
                zenity --info --width=420 --title="$TITLE" --timeout=20 --text="Studio closed straight away, so Boddy is trying another way to run it. This can take a while; you don't need to do anything." 2>/dev/null & ;;
            "Trying "*) say "# ${line%% (*}..." ;;
            "Installing Box64"*) say 1 "# Installing the x86 translator (one time)..." ;;
            "Installing Wine"*) say 2 "# Installing the Windows compatibility layer..." ;;
            "Installing Hangover"*) say 2 "# Installing the Windows compatibility layer..." ;;
            "Waiting for the internet"*) say "# Waiting for the internet..." ;;
            "Testing Windows command"*) say 5 "# Setting up Windows compatibility (the first time takes several minutes)..." ;;
            "Could not check for Studio updates"*) say "# Offline: opening the installed Studio..." ;;
            "Desktop enlarged"*) say 95 "# Making room on the desktop..." ;;
            "Launching real Roblox Studio"*) touch "$work/started"; say 100 ;;
            "LAUNCH FAILED: "*) failing=1; printf '%s\n' "${line#LAUNCH FAILED: }" >>"$work/error" ;;
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
    text="Roblox Studio could not start:\n\n$(head -c 3000 "$work/error")"
else
    text="Roblox Studio closed unexpectedly (exit code $(cat "$work/exit" 2>/dev/null || echo "$status"))."
fi
log=$(readlink -f "$LOG_DIR/latest.log")
case "$(zenity --error --width=520 --title="$TITLE" --text="$(printf '%s' "$text" | escape)" \
        --extra-button="Copy log" --extra-button="Show log" 2>/dev/null)" in
    "Copy log") copy_log "$log" ;;
    "Show log") mousepad "$log" >/dev/null 2>&1 & ;;
esac
