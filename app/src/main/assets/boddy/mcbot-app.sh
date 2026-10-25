#!/bin/bash
# "Minecraft Bot" desktop app.
export PATH="/root/.local/bin:$PATH"
show() { zenity --info --width=460 --text="$(echo "$1" | head -c 1500)" 2>/dev/null; }

action=$(zenity --list --title="Minecraft Bot" --width=460 --height=420 \
    --text="Your helper player for Minecraft Java (DroidBridge).\nIn-game you can also type: boddy follow / come / mine oak_log 10 / stop" \
    --column=Action "Join my world" "Follow me" "Come to me" "Mine something" "Fight nearby mobs" \
    "Stop" "Status" "Leave the world" 2>/dev/null) || exit 0

case "$action" in
    "Join my world")
        port=$(zenity --entry --title="Join my world" --width=420 \
            --text="In DroidBridge, open your world to LAN. Type the port it shows in chat:" 2>/dev/null) || exit 0
        zenity --question --width=420 --title="Account" --ok-label="Offline (no account)" --cancel-label="Microsoft account" \
            --text="Join with no account (needs online mode off) or a second Microsoft account?" 2>/dev/null
        choice=$?
        [ "$choice" -eq 5 ] && exit 0  # dialog closed
        auth=offline; [ "$choice" -eq 1 ] && auth=microsoft
        show "$(mcbot start "$port" "$auth" 2>&1)"
        ;;
    "Follow me"|"Come to me")
        name=$(zenity --entry --title="$action" --text="Your Minecraft name (blank = nearest player):" 2>/dev/null) || exit 0
        cmd=follow; [ "$action" = "Come to me" ] && cmd=come
        show "$(mcbot $cmd $name 2>&1)"
        ;;
    "Mine something")
        what=$(zenity --entry --title="Mine" --text="Block and amount, e.g. oak_log 10" 2>/dev/null) || exit 0
        show "$(mcbot mine $what 2>&1)"
        ;;
    "Fight nearby mobs") show "$(mcbot attack 2>&1)" ;;
    "Stop") show "$(mcbot stop 2>&1)" ;;
    "Status") show "$(mcbot status 2>&1)" ;;
    "Leave the world") show "$(mcbot quit 2>&1)" ;;
esac
