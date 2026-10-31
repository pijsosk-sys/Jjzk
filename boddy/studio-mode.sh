#!/bin/bash
# "Roblox Studio + Claude": Roblox Studio on the left of the screen and Claude Code on the right,
# connected through the Roblox Studio MCP (roblox-studio-mcp and the plugin the Studio launcher
# installs), so Claude can look at the open place and build in it.
#   studio-mode.sh            start both and place them side by side
#   studio-mode.sh --claude   the Claude Code side (run inside its terminal window)
export DISPLAY=${DISPLAY:-:1} HOME=${HOME:-/root}
export PATH="/opt/boddy/phone-bin:/root/.local/bin:$PATH"
export BROWSER=/opt/boddy/open-on-phone
PROJECT=${BODDY_STUDIO_PROJECT:-/root/Projects/Roblox-Studio}
MCP=/opt/boddy/roblox-studio-mcp
TITLE="Claude for Roblox Studio"
SHARE=64  # percent of the screen's width for Studio (STUDIO_SHARE in /opt/boddy/studio)

claude_side() {
    mkdir -p "$PROJECT/.claude" && cd "$PROJECT" || exit 1
    if ! command -v claude >/dev/null; then
        echo "Installing Claude Code..."
        curl -fsSL https://claude.ai/install.sh | bash
    fi
    # Register the Roblox Studio MCP server once, for every project.
    if ! claude mcp get Roblox_Studio >/dev/null 2>&1; then
        claude mcp add --scope user Roblox_Studio -- python3 "$MCP" >/dev/null &&
            echo "Connected Claude Code to Roblox Studio (MCP server Roblox_Studio)."
    fi
    [ -f CLAUDE.md ] || cat > CLAUDE.md <<'MD'
# Roblox Studio on a phone

Roblox Studio is open beside you and connected through the Roblox_Studio MCP tools:
`run_code` (run Luau in Studio and get what it prints), `insert_model`, `get_console_output`,
`start_stop_play`, `run_script_in_play_mode` and `get_studio_mode`.

- Build by running Luau with `run_code`: create Parts, Models, Scripts (set `.Source`), etc. in the
  open place. Check what is there first (e.g. list `workspace:GetChildren()`).
- Studio runs on a phone through emulation and draws 3D on the processor: it is slow. Batch work
  into a few larger `run_code` calls, keep scenes light (few parts, no huge terrain), and expect
  each call to take seconds.
- Use `start_stop_play` to test, `get_console_output` to read errors, then stop play mode again.
- If a tool says Studio is not connected: Studio is still starting, no place is open, or the MCP
  plugin needs permission to reach localhost (Studio asks once).
MD
    # The read-only tools need no confirmation; Claude asks before changing the place.
    [ -f .claude/settings.json ] || cat > .claude/settings.json <<'JSON'
{
  "permissions": {
    "allow": ["mcp__Roblox_Studio__get_studio_mode", "mcp__Roblox_Studio__get_console_output"]
  }
}
JSON
    clear
    echo "Claude Code for Roblox Studio. Studio opens on the left (the first start takes a few minutes)."
    echo "Ask for something to build, e.g.: make an obby with 10 checkpoints."
    echo
    # Continue the last conversation here, so restarting Studio Mode keeps your work.
    if ls "$HOME/.claude/projects/$(tr / - <<<"$PROJECT")"/*.jsonl >/dev/null 2>&1; then
        claude --continue
    else
        claude
    fi
    echo
    read -rp "Claude Code closed. Press Enter to close this window."
}

# Places Studio's Wine desktop window on the left and Claude on the right once Studio is up (its
# first start can take minutes); Studio's launcher has already sized its desktop for the left part.
arrange() {
    local studio="" claude="" W H left
    for _ in $(seq 900); do
        studio=$(xdotool search --name '^RobloxStudio - ' 2>/dev/null | head -n 1)
        [ -n "$studio" ] && break
        # Gave up: no Studio launcher or Studio left (it failed, or was closed before it showed).
        pgrep -f 'studio-app\.sh|/opt/boddy/studio( |$)|RobloxStudioBeta' >/dev/null || return
        sleep 2
    done
    [ -n "$studio" ] || return
    sleep 2  # the launcher may still be resizing the screen
    read -r W H < <(xdotool getdisplaygeometry)
    left=$(( W * SHARE / 100 ))
    xdotool windowmove "$studio" 0 0 2>/dev/null
    claude=$(xdotool search --name "^$TITLE\$" 2>/dev/null | head -n 1)
    [ -n "$claude" ] && xdotool windowmove "$claude" "$left" 0 windowsize "$claude" $(( W - left )) $(( H - 72 )) 2>/dev/null
}

if [ "$1" = "--claude" ]; then claude_side; exit; fi

command -v xdotool >/dev/null || apt-get install -y -q --no-install-recommends xdotool >/dev/null 2>&1
# Studio, in its own Wine window sized for the left part of the screen (see BODDY_STUDIO_LAYOUT).
BODDY_STUDIO_LAYOUT=split setsid /opt/boddy/studio-app.sh "Open Studio" >/dev/null 2>&1 &
# Claude Code, unless it is already open from an earlier start.
xdotool search --name "^$TITLE\$" >/dev/null 2>&1 ||
    setsid xfce4-terminal --disable-server --title="$TITLE" -x bash /opt/boddy/studio-mode.sh --claude >/dev/null 2>&1 &
arrange
