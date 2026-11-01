#!/bin/bash
# Runs the Boddy app's VNC client against TigerVNC's Xvnc on a PC and checks what it draws.
#   test_vnc.sh APP_JAR KOTLIN_STDLIB_JAR [ORIGINAL_APP_JAR]
# APP_JAR is the app's classes.dex as a jar (dex2jar). For a 24-bit and a 16-bit Xvnc (as start-desktop.sh
# starts it) it shows a test picture, takes the client's framebuffer once the screen is still, and compares it
# with the server's own screenshot, pixel by pixel. With ORIGINAL_APP_JAR it also compares the bytes and client
# CPU time of both while the screen changes. Needs Xvnc, ImageMagick (convert, display, import), javac.
set -u
JAR=$1 KT=$2 ORIG=${3:-}
T=$(mktemp -d); fails=0
# dex2jar writes classes without the stack map frames a JVM checks (Android does not use them): skip that check.
NOVERIFY="-XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote -XX:-BytecodeVerificationLocal"
check() { if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; fails=$((fails+1)); fi; }
javac -Xlint:-options -cp "$JAR" -d "$T" "$(dirname "$0")/VncClientTest.java" 2>&1 | grep -v JAVA_TOOL
W=1280 H=720
# A desktop-like picture: flat panels and text, gradients, and a noisy area like a 3D view.
convert -size ${W}x${H} xc:'#2b2d31' \
    -fill '#3a3d44' -draw "rectangle 0 0 $W 40" -fill '#e8e8e8' -draw "rectangle 40 80 600 650" \
    \( -size 500x300 gradient:'#1e6fd9'-'#f4a742' \) -geometry +680+80 -composite \
    \( -size 500x260 xc: +noise Random -blur 0x0.6 \) -geometry +680+400 -composite \
    -fill black -pointsize 18 -annotate +60+120 'Workspace  Camera  Terrain  Lighting  Players' \
    -annotate +60+160 'local part = Instance.new("Part") part.Anchored = true' "$T/a.png"
convert "$T/a.png" -flop -modulate 100,80,40 "$T/b.png"
start_x() {  # DISPLAY DEPTH: Xvnc as start-desktop.sh starts it
    Xvnc :$1 -geometry ${W}x${H} -depth $2 $([ "$2" = 16 ] && echo -pixelformat rgb565) -SecurityTypes None \
        -localhost -nolisten tcp -rfbport $((5900+$1)) -AlwaysShared -ZlibLevel=1 >"$T/xvnc$1.log" 2>&1 &
    XPID=$!
    for _ in $(seq 50); do [ -e /tmp/.X11-unix/X$1 ] && break; sleep 0.1; done
}
compare() {  # A.rgb B.rgb TOLERANCE -> number of pixels differing by more than TOLERANCE in a channel
    python3 -c "
import sys;a=open(sys.argv[1],'rb').read();b=open(sys.argv[2],'rb').read();t=int(sys.argv[3])
if len(a)!=len(b):print(-1);sys.exit()
print(sum(1 for i in range(0,len(a),3) if max(abs(a[i]-b[i]),abs(a[i+1]-b[i+1]),abs(a[i+2]-b[i+2]))>t))" "$@"
}
for depth in 24 16; do
    D=$((40+depth)); start_x $D $depth
    DISPLAY=:$D display -window root "$T/a.png"
    out=$(java $NOVERIFY -cp "$JAR:$KT:$T" VncClientTest dump $((5900+D)) "$T/client.rgb" 2>&1 | grep -v -e JAVA_TOOL -e "BytecodeVerification.*deprecated")
    DISPLAY=:$D import -window root -depth 8 "rgb:$T/server.rgb"
    echo "     depth $depth: $out"
    # 16-bit colours are widened to 8 bits a channel by Xvnc for the client and by import for the screenshot,
    # which may round differently.
    tol=$([ $depth = 16 ] && echo 8 || echo 0)
    diff=$(compare "$T/client.rgb" "$T/server.rgb" $tol)
    check "depth $depth: client framebuffer matches the screen ($diff pixels differ)" '[ "$diff" = 0 ]'
    check "depth $depth: no client error" '! grep -q -i -E "exception|error" <<<"$out"'
    if [ -n "$ORIG" ] && [ $depth = 24 ]; then
        for which in original:$ORIG patched:$JAR; do
            name=${which%%:*} jar=${which#*:}
            ( for i in $(seq 20); do DISPLAY=:$D display -window root "$T/$([ $((i%2)) = 0 ] && echo a || echo b).png"; sleep 0.2; done ) &
            loop=$!
            r=$(java $NOVERIFY -cp "$jar:$KT:$T" VncClientTest measure $((5900+D)) 5 2>&1 | grep -v -e JAVA_TOOL -e "BytecodeVerification.*deprecated")
            wait $loop
            echo "     $name client, screen changing: $r"
            eval "${name}_bytes=$(sed -n 's/.*bytes=\([0-9]*\).*/\1/p' <<<"$r")"
        done
        check "ZRLE sends fewer bytes than Raw (${patched_bytes:-?} vs ${original_bytes:-?})" '[ "${patched_bytes:-0}" -gt 0 ] && [ "$patched_bytes" -lt "$original_bytes" ]'
    fi
    kill $XPID; wait $XPID 2>/dev/null
done
echo "$fails failed"; exit $((fails>0))
