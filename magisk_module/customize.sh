#!/system/bin/sh
# OpenAVPlugin installation script

ui_print "Installing OpenAVPlugin..."

# Check Android version
API=$(getprop ro.build.version.sdk)
if [ "$API" -lt 26 ]; then
    ui_print "Error: Requires Android 8.0 or higher"
    abort
fi

# Detect architecture
ARCH=$(getprop ro.product.cpu.abi)
if [ "$ARCH" = "arm64-v8a" ]; then
    LIB_DIR="lib64"
elif [ "$ARCH" = "armeabi-v7a" ]; then
    LIB_DIR="lib"
else
    ui_print "Error: Unsupported architecture: $ARCH"
    abort
fi

ui_print "Architecture: $ARCH"
ui_print "Android API: $API"
ui_print "Installation complete!"
