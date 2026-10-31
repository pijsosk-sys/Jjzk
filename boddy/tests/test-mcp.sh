#!/bin/bash
# Tests roblox-studio-mcp against a stand-in for Studio's plugin (same HTTP protocol as
# mcp-plugin/src/MockWebSocketService.luau), driving it over stdio as Claude Code does.
# Also checks that MCPStudioPlugin.rbxmx is up to date with mcp-plugin/src. Run from anywhere.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); BODDY=$(dirname "$HERE")
tmp=$(mktemp -d); trap 'kill "${plugin:-}" 2>/dev/null; rm -rf "$tmp"' EXIT

python3 -I "$BODDY/build-mcp-plugin" "$BODDY/mcp-plugin/src" "$tmp/plugin.rbxmx" >/dev/null
cmp -s "$tmp/plugin.rbxmx" "$BODDY/MCPStudioPlugin.rbxmx" ||
    { echo "FAIL: MCPStudioPlugin.rbxmx is out of date: run build-mcp-plugin"; exit 1; }

python3 -I "$HERE/fake_studio_plugin.py" & plugin=$!
sleep 1
out=$(timeout 60 python3 -I "$HERE/mcp_client.py" "$BODDY/roblox-studio-mcp")
echo "$out"
for want in 'initialize: Roblox_Studio' 'printed: print(workspace.Name)' 'isError=True' \
            'missing argument: command' 'unknown tool: Unknown tool: nope' 'server exited: 0'; do
    grep -qF -- "$want" <<<"$out" || { echo "FAIL: expected: $want"; exit 1; }
done
echo "PASS"
