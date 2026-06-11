#!/system/bin/sh
# OpenAVPlugin service script

MODDIR=${0%/*}

# Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# Additional setup after boot
# This can be used for dynamic configuration
