#!/system/bin/sh
# re-assert after boot completes, in case something reset them.
MODDIR=${0%/*}
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
. "$MODDIR/props.sh"
