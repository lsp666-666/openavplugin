#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <cerrno>

#define TAG "OpenAVPlugin-CameraNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ============================================================
// Inline hook implementation
// ============================================================

static int (*original_camera_open)(int camera_id, void** device) = nullptr;

// ARM64 trampoline: 16 bytes = 4 instructions
// LDR X16, #8; BR X16; .dq <target_address>
static void install_inline_hook(void* target_func, void* hook_func) {
    if (target_func == nullptr || hook_func == nullptr) return;

    // Calculate page-aligned address
    uintptr_t page_start = ((uintptr_t)target_func) & ~(getpagesize() - 1);
    size_t page_size = getpagesize();

    // Make the code page writable
    if (mprotect((void*)page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect failed for %p: %s", target_func, strerror(errno));
        return;
    }

    // Save original first 16 bytes (not needed for callable trampoline
    // since we kept the dlsym pointer; kept for potential restore)

    // Write ARM64 absolute jump:
    uint32_t* code = (uint32_t*)target_func;
    // LDR X16, #8   -> 0x58000050
    // BR X16        -> 0xD61F0200
    code[0] = 0x58000050;
    code[1] = 0xD61F0200;
    // Address of hook_func in next 8 bytes
    memcpy(&code[2], &hook_func, sizeof(void*));

    // Flush instruction cache
    __builtin___clear_cache((char*)target_func, (char*)target_func + 16);

    // Restore memory protection
    mprotect((void*)page_start, page_size, PROT_READ | PROT_EXEC);

    LOGI("Inline hook installed at %p -> %p", target_func, hook_func);
}

// ============================================================
// Hooked functions
// ============================================================

static int hooked_camera_open(int camera_id, void** device) {
    LOGI("camera_open intercepted for camera %d — injecting virtual device", camera_id);

    // TODO: Create a virtual camera device and return it
    // For now, return the original but log the interception
    if (original_camera_open) {
        int result = original_camera_open(camera_id, device);
        LOGI("Original camera_open returned %d, device=%p", result, *device);
        return result;
    }
    return -1;
}

// ============================================================
// Initialization
// ============================================================

__attribute__((constructor))
void init_hooks() {
    LOGI("Initializing camera hooks");

    // Get original function pointers from system library
    void* handle = dlopen("libcamera_client.so", RTLD_NOW);
    if (!handle) {
        LOGE("Failed to load libcamera_client.so: %s", dlerror());
    }

    if (handle) {
        void* camera_open_ptr = dlsym(handle, "camera_open");
        if (camera_open_ptr) {
            LOGI("Found camera_open at %p", camera_open_ptr);
            install_inline_hook(camera_open_ptr, (void*)hooked_camera_open);

            // Save original: the saved[4] instructions in saved_original
            // For now, use the dlsym'd pointer directly
            original_camera_open = (int(*)(int, void**))camera_open_ptr;
            LOGI("Camera open hook installed");
        } else {
            LOGI("camera_open not found in libcamera_client.so");
        }
    }

    // Also try hooking Camera2 native functions
    void* cameraserver_handle = dlopen("libcameraservice.so", RTLD_NOW);
    if (cameraserver_handle) {
        LOGI("Found libcameraservice.so");
        // CameraService::connect — the Binder-based camera connection
        void* connect_ptr = dlsym(cameraserver_handle, "_ZN7android13CameraService7connectERKNS_2spINS_13ICameraClientEEEiRKNSt3__112basic_stringIcNS6_11char_traitsIcEENS6_9allocatorIcEEEEi");
        if (connect_ptr) {
            LOGI("Found CameraService::connect at %p (hookable)", connect_ptr);
        }
    }
}

// ============================================================
// JNI interface
// ============================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_openavplugin_root_NativeInjector_initCameraHook(JNIEnv* env, jobject thiz) {
    LOGI("Camera hook initialized from Java (runtime re-init)");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_openavplugin_root_NativeInjector_isCameraHookActive(JNIEnv* env, jobject thiz) {
    return original_camera_open != nullptr ? JNI_TRUE : JNI_FALSE;
}
