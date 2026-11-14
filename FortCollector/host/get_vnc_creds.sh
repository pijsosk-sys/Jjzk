#!/system/bin/sh
# Recover droidvm's guest VNC port + password from the live QEMU process.
# droidvm passes both inline on the qemu command line:
#   -vnc 127.0.0.1:PORT,password-secret=vnc-password
#   -object secret,id=vnc-password,data=PLAINTEXT_PASSWORD
# Needs root on the phone (droidvm already requires it to run).
#
# Run ONE of:
#   adb shell su -c 'sh /sdcard/get_vnc_creds.sh'      # from PC
#   su -c 'sh /sdcard/get_vnc_creds.sh'                # in Termux on the phone
#
# Prints: PORT, PASSWORD, and the exact collector command to run.

set -u

pid=""
for p in $(ls /proc 2>/dev/null | grep -E '^[0-9]+$'); do
    cmd=$(cat /proc/$p/cmdline 2>/dev/null | tr '\0' ' ')
    case "$cmd" in
        *qemu-system-aarch64*|*assets/bin/*/droidvm*)
            case "$cmd" in *"-vnc "*) pid="$p"; break;; esac
        ;;
    esac
done

if [ -z "$pid" ]; then
    echo "no running qemu/droidvm VM with a -vnc arg found. Start the VM first." >&2
    exit 1
fi

cmd=$(cat /proc/$pid/cmdline | tr '\0' ' ')

# -vnc 127.0.0.1:PORT,...   (PORT is RFB display number OR a port; droidvm uses a port here)
vncarg=$(echo "$cmd" | grep -oE '\-vnc [^ ]+' | head -1 | cut -d' ' -f2)
port=$(echo "$vncarg" | sed -E 's/^[0-9.]*://; s/,.*$//')

# -object secret,id=vnc-password,data=PASSWORD
pass=$(echo "$cmd" | grep -oE 'secret,id=vnc-password,data=[^ ,]+' | head -1 | sed -E 's/.*data=//')

echo "pid=$pid"
echo "PORT=$port"
echo "PASSWORD=$pass"
echo
echo "# on your PC:"
echo "adb forward tcp:5900 tcp:$port"
echo "python3 collector_vnc.py --host 127.0.0.1 --port 5900 --password '$pass' --sink ws://127.0.0.1:8765"
