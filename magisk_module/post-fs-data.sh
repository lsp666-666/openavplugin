#!/system/bin/sh
# OpenAVPlugin post-fs-data script

MODDIR=${0%/*}

# Set LD_PRELOAD for cameraserver and audioserver
# This injects our native libraries at startup

# For Android 8-10
if [ -f /system/bin/cameraserver ]; then
    setprop wrap.cameraserver "LD_PRELOAD=$MODDIR/system/lib64/libopenavplugin_cam.so"
fi

if [ -f /system/bin/audioserver ]; then
    setprop wrap.audioserver "LD_PRELOAD=$MODDIR/system/lib64/libopenavplugin_audio.so"
fi
