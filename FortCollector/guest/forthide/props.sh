#!/system/bin/sh
set_p() { resetprop -n "$1" "$2"; }
set_p ro.product.manufacturer samsung
set_p ro.product.brand        samsung
set_p ro.product.model         SM-S928B
set_p ro.product.device        e3q
set_p ro.product.name          e3qxxx
set_p ro.product.board         pineapple
set_p ro.board.platform        pineapple
set_p ro.product.system.model  SM-S928B
set_p ro.product.vendor.model  SM-S928B
set_p ro.product.vendor.brand  samsung
# scrub obvious VM/debug tells (harmless if absent)
set_p ro.boot.selinux          enforcing
set_p ro.debuggable            0
set_p ro.secure                1
