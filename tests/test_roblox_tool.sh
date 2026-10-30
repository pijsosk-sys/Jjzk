#!/bin/bash
# Checks roblox-tool's project commands without the network: a stub `rojo` on PATH records how it
# was called (fetch_tool skips the download once rojo is on PATH) and writes what `rojo init` writes.
set -uo pipefail
tool=$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/boddy/roblox-tool
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
bin=$work/bin; mkdir -p "$bin"
for n in roblox-new roblox-build roblox-serve roblox-setup; do ln -s "$tool" "$bin/$n"; done
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok: $1"; else echo "  FAIL: $1 (want '$3', got '$2')"; fail=1; fi; }

cat > "$bin/rojo" <<'STUB'
#!/bin/bash
echo "$@" >> "$ROJO_CALLS"
if [ "$1" = init ]; then
    d=${4:-.}; mkdir -p "$d/src/server" "$d/src/client" "$d/src/shared"
    printf '{"name":"x","tree":{"$className":"DataModel"}}\n' > "$d/default.project.json"
    echo 'print("Hello world, from server!")' > "$d/src/server/init.server.luau"
elif [ "$1" = build ]; then
    printf 'fake place\n' > "$3"
fi
STUB
chmod +x "$bin/rojo"
export PATH="$bin:$PATH" ROJO_CALLS=$work/calls
: > "$ROJO_CALLS"
[ -x /root/.local/bin/rojo ] && { echo "SKIP: a real rojo shadows the stub"; exit 0; }

# roblox-new scaffolds a place project and tells the owner the next command.
out=$(roblox-new my-game "$work/games" 2>&1); check "roblox-new exit" "$?" "0"
check "project created" "$([ -f "$work/games/my-game/default.project.json" ] && echo yes)" "yes"
check "rojo init --kind place" "$(grep -c -- 'init --kind place' "$ROJO_CALLS")" "1"
case "$out" in *roblox-build*) echo "  ok: tells the owner to build";; *) echo "  FAIL: no build hint"; fail=1;; esac

# A second run must not silently overwrite the owner's work.
roblox-new my-game "$work/games" >/dev/null 2>&1; check "refuses to overwrite" "$?" "2"
# A name with a path in it would escape the games folder.
roblox-new ../evil "$work/games" >/dev/null 2>&1; check "rejects path in name" "$?" "2"
roblox-new "" "$work/games" >/dev/null 2>&1; check "rejects empty name" "$?" "2"

# roblox-build names the place after the project folder and builds inside it.
: > "$ROJO_CALLS"
out=$(roblox-build "$work/games/my-game" 2>&1); check "roblox-build exit" "$?" "0"
check "built my-game.rbxl" "$([ -f "$work/games/my-game/my-game.rbxl" ] && echo yes)" "yes"
check "rojo build -o used" "$(grep -c -- '^build -o' "$ROJO_CALLS")" "1"
case "$out" in *phone-save*) echo "  ok: tells the owner how to get the file";; *) echo "  FAIL: no phone-save hint"; fail=1;; esac

# An explicit output path is honoured.
roblox-build "$work/games/my-game" "$work/out.rbxl" >/dev/null 2>&1
check "explicit output" "$([ -f "$work/out.rbxl" ] && echo yes)" "yes"

# Building somewhere that is not a project must say so rather than invoke rojo.
mkdir -p "$work/empty"; : > "$ROJO_CALLS"
err=$(roblox-build "$work/empty" 2>&1); check "non-project exit" "$?" "2"
case "$err" in *roblox-new*) echo "  ok: points at roblox-new";; *) echo "  FAIL: unhelpful message"; fail=1;; esac
check "rojo not called" "$(wc -l < "$ROJO_CALLS" | tr -d ' ')" "0"

# Usage text must list every command the symlinks provide.
usage=$("$tool" not-a-command 2>&1)
for c in roblox-extract roblox-apply roblox-new roblox-build roblox-serve roblox-setup; do
    case "$usage" in *"$c"*) ;; *) echo "  FAIL: usage omits $c"; fail=1;; esac
done
[ "$fail" = 0 ] && echo "PASS: roblox-tool project commands (new, build, serve) behave as documented."
exit "$fail"
