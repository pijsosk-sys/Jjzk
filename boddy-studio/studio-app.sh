#!/bin/bash
# "Roblox Studio (experimental)" desktop app.
start=/root/Phone/Download/; [ -d "$start" ] || start=/root/
zenity --question --width=460 --title="Roblox Studio (experimental)" --ok-label="Try it" --cancel-label="Cancel" \
    --text="Runs the official Windows Roblox Studio through Linux/Wine compatibility software. First setup needs several GB of storage and downloads. A Wine startup test runs before Studio is installed. Android graphics and sign-in remain unverified.\n\nFailures are saved to Downloads/Boddy-Studio-launch.log." 2>/dev/null || exit 0
choice=$(zenity --list --title="Roblox Studio" --width=420 --height=300 --column=Action \
    "Open Studio" "Diagnostics" "Open a place file" "Reset Studio" 2>/dev/null) || exit 0
case "$choice" in
    "Diagnostics") cmd=(/opt/boddy/studio --diagnose) ;;
    "Open Studio") cmd=(/opt/boddy/studio) ;;
    "Open a place file")
        f=$(zenity --file-selection --filename="$start" --file-filter="Roblox place | *.rbxl *.rbxlx" 2>/dev/null) || exit 0
        cmd=(/opt/boddy/studio "$f") ;;
    "Reset Studio") cmd=(/opt/boddy/studio --reset) ;;
    *) exit 0 ;;  # OK pressed with nothing selected
esac
# Show progress in a terminal so the user can see what's happening (and any error).
exec xfce4-terminal --disable-server --title="Roblox Studio" -x bash -c '"$@"; echo; read -rp "Press Enter to close."' _ "${cmd[@]}"
