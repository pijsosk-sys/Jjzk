#!/bin/sh
# Runs the game like run.sh and screen-records the virtual display while it plays.
#   record.sh <out.mp4> <rex jar> <schematic> [-Drexsmoke.* options]
# Add -Drexsmoke.thirdPerson=true to film the player from behind.
set -e
OUT=$(cd "$(dirname "$1")" && pwd)/$(basename "$1"); shift
"$(dirname "$0")/run.sh" "$@" > "${OUT%.*}.log" 2>&1 &
RUN=$!
# xvfb-run -a picks the display number; read it from the game's environment once it is up.
PID=""
while [ -z "$PID" ] && kill -0 $RUN 2>/dev/null; do sleep 2; PID=$(pgrep -f '^java .*KnotClient.*--quickPlaySingleplayer' | head -1); done
[ -n "$PID" ] || { echo "game did not start, see ${OUT%.*}.log"; exit 1; }
DISPLAY_NUM=$(tr '\0' '\n' < /proc/$PID/environ | sed -n 's/^DISPLAY=//p')
XAUTH=$(tr '\0' '\n' < /proc/$PID/environ | sed -n 's/^XAUTHORITY=//p')
sleep 20   # skip the loading screens
XAUTHORITY="$XAUTH" ffmpeg -loglevel error -y -f x11grab -framerate 15 -video_size 1280x720 -i "$DISPLAY_NUM" \
  -c:v libx264 -preset veryfast -crf 26 -pix_fmt yuv420p "${OUT%.*}.mkv" &
REC=$!
wait $RUN || true
kill -INT $REC 2>/dev/null; wait $REC 2>/dev/null || true
# Matroska survives the display vanishing mid-frame when the game quits; hand back an mp4.
ffmpeg -loglevel error -y -i "${OUT%.*}.mkv" -c copy -movflags +faststart "$OUT" && rm -f "${OUT%.*}.mkv"
echo "recorded $OUT"
