#!/bin/bash
# "Roblox Tools" desktop app: pick a Roblox file, unpack its scripts and open them.
export PATH="/root/.local/bin:$PATH"
action=$(zenity --list --title="Roblox Tools" --width=520 --height=300 \
    --text="What do you want to do?" --column=Action \
    "Open Roblox Studio" \
    "Start a new game I can edit with an AI agent" \
    "Build my game into a Roblox file" \
    "Open a Roblox file and look at its scripts" \
    "Put my edited scripts back into a Roblox file" 2>/dev/null) || exit 0

start=/root/Phone/Download/; [ -d "$start" ] || start=/root/
pick() { zenity --file-selection --filename="$start" --title="$1" --file-filter="Roblox files | *.rbxl *.rbxlx *.rbxm *.rbxmx" 2>/dev/null; }

case "$action" in
    # Studio has its own desktop icon, but people look for it here too, so hand over to it.
    "Open Roblox Studio") exec /opt/boddy/studio-app.sh ;;
    Start*)
        name=$(zenity --entry --title="New game" --width=420 \
            --text="Name for the new game (letters, numbers and dashes):" 2>/dev/null) || exit 0
        [ -n "$name" ] || exit 0
        if out=$(roblox-new "$name" 2>&1); then
            zenity --info --width=520 --text="$out" 2>/dev/null
            thunar "/root/roblox/$name" >/dev/null 2>&1 &
        else
            zenity --error --width=420 --text="$out" 2>/dev/null
        fi
        ;;
    Build*)
        dir=$(zenity --file-selection --directory --filename="/root/roblox/" \
            --title="Choose your game folder" 2>/dev/null) || exit 0
        if out=$(roblox-build "$dir" 2>&1); then
            zenity --info --width=520 --text="$out" 2>/dev/null
        else
            zenity --error --width=520 --text="$out" 2>/dev/null
        fi
        ;;
    Open*)
        file=$(pick "Choose a Roblox file (.rbxl, .rbxm...)") || exit 0
        dir="${file%.*}-scripts"
        if out=$(roblox-extract "$file" "$dir" 2>&1); then
            zenity --info --width=420 --text="$out" 2>/dev/null
            thunar "$dir" >/dev/null 2>&1 &
            mousepad "$dir/tree.txt" >/dev/null 2>&1 &
        else
            zenity --error --width=420 --text="$out" 2>/dev/null
        fi
        ;;
    Put*)
        file=$(pick "Choose the original Roblox file") || exit 0
        dir="${file%.*}-scripts"
        [ -d "$dir" ] || dir=$(zenity --file-selection --directory --title="Choose the scripts folder" 2>/dev/null) || exit 0
        if out=$(roblox-apply "$file" "$dir" 2>&1); then
            zenity --info --width=420 --text="$out\n\nTip: phone-save sends it to your phone's Downloads." 2>/dev/null
        else
            zenity --error --width=420 --text="$out" 2>/dev/null
        fi
        ;;
esac
