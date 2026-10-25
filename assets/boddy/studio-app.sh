#!/bin/bash
# "Roblox Studio" desktop app: open blank, open a file, or manage the install.
export PATH="/root/.local/bin:$PATH"
STUDIO=/opt/boddy/studio
LOG=/root/.cache/roblox-studio/launcher.log

start=/root/Phone/Download/; [ -d "$start" ] || start=/root/

menu() {
    zenity --list --title="Roblox Studio" --width=560 --height=360 \
        --text="What do you want to do?" --column=Action \
        "Open Studio" \
        "Open a place file (.rbxl / .rbxlx)" \
        "Check for Studio updates" \
        "Reset Studio (fix a broken install)" \
        "Uninstall Studio" \
        "View launcher log" 2>/dev/null
}

run_with_progress() {
    local title=$1; shift
    ( "$@" 2>&1 | tee -a "$LOG" ) |
        zenity --progress --title="$title" --pulsate --auto-close --width=460 \
               --text="Working... first run installs box64, wine and Studio (~400 MB)." 2>/dev/null
}

action=$(menu) || exit 0
case "$action" in
    "Open Studio")
        run_with_progress "Starting Studio" "$STUDIO"
        ;;
    "Open a place file"*)
        file=$(zenity --file-selection --filename="$start" --title="Pick a Roblox place" \
            --file-filter="Roblox place | *.rbxl *.rbxlx" 2>/dev/null) || exit 0
        run_with_progress "Starting Studio" "$STUDIO" "$file"
        ;;
    "Check for Studio updates")
        run_with_progress "Checking for updates" "$STUDIO" --update
        zenity --info --width=380 --text="Done. Launch Studio to use the new build." 2>/dev/null
        ;;
    "Reset Studio"*)
        zenity --question --width=420 --text="This wipes the wine prefix and reinstalls Studio. Continue?" 2>/dev/null || exit 0
        run_with_progress "Resetting Studio" "$STUDIO" --reset
        ;;
    "Uninstall Studio")
        zenity --question --width=420 --text="Remove Studio, the wine prefix and the cache?" 2>/dev/null || exit 0
        "$STUDIO" --uninstall 2>&1 | tee -a "$LOG" >/dev/null
        zenity --info --width=380 --text="Removed." 2>/dev/null
        ;;
    "View launcher log")
        [ -s "$LOG" ] || echo "(no log yet)" >"$LOG"
        mousepad "$LOG" >/dev/null 2>&1 &
        ;;
esac
