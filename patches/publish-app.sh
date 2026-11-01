#!/bin/bash
# "Publish to Roblox" desktop app: pick a place file, send it, and say whether it really landed.
# Everything the website used to be needed for -- finding the experience, its starting place, the ids --
# is looked up by roblox-publish; only the API key still comes from create.roblox.com.
export PATH="/opt/boddy/phone-bin:/usr/local/bin:$PATH"
TITLE="Publish to Roblox"
PUB=/usr/local/bin/roblox-publish
[ -x "$PUB" ] || PUB=/opt/boddy/roblox-publish
CREDS="$HOME/.config/roblox-publish/credentials"

term() { # run a command in a terminal window and wait for it
    xfce4-terminal --title="$TITLE" --geometry=90x28 --hold -x bash -lc "$1" 2>/dev/null ||
        x-terminal-emulator -e bash -lc "$1" 2>/dev/null
}

if [ ! -f "$CREDS" ]; then
    zenity --question --width=460 --title="$TITLE" --ok-label="Set it up" --cancel-label="Not now" \
        --text="Publishing is not set up yet.\n\nIt asks for your Roblox username, lists your experiences so you can pick one, and then needs an API key from create.roblox.com. No ID numbers to find." 2>/dev/null || exit 0
    term "$PUB --wizard; echo; read -rp 'Press Enter to close '"
    [ -f "$CREDS" ] || exit 0
fi

# The newest place file wins as the default, since that is nearly always the one meant.
start=$(ls -t "$HOME"/roblox/*/*.rbxl "$HOME"/*.rbxl "$HOME"/roblox/*.rbxl 2>/dev/null | head -1)
file=$(zenity --file-selection --title="$TITLE" --file-filter='Place files | *.rbxl *.rbxlx' \
    ${start:+--filename="$start"} 2>/dev/null) || exit 0
[ -n "$file" ] || exit 0

mode=$(zenity --list --radiolist --width=520 --height=260 --title="$TITLE" \
    --text="Publishing <b>$(basename "$file")</b>" --column=Pick --column=How --column=v \
    TRUE "Publish live"      "Players get it" \
    FALSE "Save only"        "Studio sees it, players keep the live version" \
    --hide-column=3 2>/dev/null) || exit 0
[ "$mode" = "Save only" ] && flag=--save-only || flag=

term "$PUB $flag '$file'; echo; read -rp 'Press Enter to close '"
