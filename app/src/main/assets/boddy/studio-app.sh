#!/bin/bash
# "Roblox Studio (experimental)" desktop app.
start=/root/Phone/Download/; [ -d "$start" ] || start=/root/
zenity --question --width=460 --title="Roblox Studio (experimental)" --ok-label="Try it" --cancel-label="Cancel" \
    --text="This tries to run Windows Roblox Studio on your phone. It downloads about 1.5 GB the first time, and it will probably be very slow or not start at all (no graphics chip access, and Studio's sign-in browser may not work).\n\nRoblox Tools is the reliable way to edit Roblox files." 2>/dev/null || exit 0
choice=$(zenity --list --title="Roblox Studio" --width=420 --height=300 --column=Action \
    "Open Studio" "Open a place file" "Reset Studio" "Uninstall Studio" 2>/dev/null) || exit 0
case "$choice" in
    "Open Studio") cmd=(/opt/boddy/studio) ;;
    "Open a place file")
        f=$(zenity --file-selection --filename="$start" --file-filter="Roblox place | *.rbxl *.rbxlx" 2>/dev/null) || exit 0
        cmd=(/opt/boddy/studio "$f") ;;
    "Reset Studio") cmd=(/opt/boddy/studio --reset) ;;
    "Uninstall Studio") cmd=(/opt/boddy/studio --uninstall) ;;
esac
# Show progress in a terminal so the user can see what's happening (and any error).
exec xfce4-terminal --disable-server --title="Roblox Studio" -x bash -c '"$@"; echo; read -rp "Press Enter to close."' _ "${cmd[@]}"
