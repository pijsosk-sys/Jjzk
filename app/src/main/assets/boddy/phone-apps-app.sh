#!/bin/bash
# "Phone Apps" desktop app: pick any app on the phone and open it.
apps=$(/opt/boddy/boddy-ctl phone-apps 2>&1)
if [ -z "$apps" ] || [[ "$apps" == unknown* ]] || [[ "$apps" == error* ]]; then
    zenity --error --width=380 --text="Couldn't get the phone's apps.\n$apps" 2>/dev/null
    exit 1
fi
choice=$(echo "$apps" | zenity --list --title="Phone Apps" --width=420 --height=560 \
    --text="Open an app on your phone" --column=App 2>/dev/null) || exit 0
[ -n "$choice" ] || exit 0
out=$(/opt/boddy/boddy-ctl phone-app "$choice" 2>&1) ||
    zenity --error --width=380 --text="$out" 2>/dev/null
