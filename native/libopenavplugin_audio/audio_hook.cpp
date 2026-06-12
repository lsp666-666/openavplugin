#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>
#include <sys/mman.h>
#include <unistd.h>
#include <cerrno>

#define TAG "OpenAVPlugin-AudioNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ============================================================
// Inline hook implementation
// ============================================================

static int (*original_audio_record_start)(void* record) = nullptr;
static int (*original_audio_record_read)(void* record, void* buffer, int size) = nullptr;

static void install_inline_hook(void* target_func, void* hook_func) {
    if (target_func == nullptr || hook_func == nullptr) return;

    uintptr_t page_start = ((uintptr_t)target_func) & ~(getpagesize() - 1);
    size_t page_size = getpagesize();

    if (mprotect((void*)page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect failed for %p: %s", target_func, strerror(errno));
        return;
    }

    // Save original 16 bytes as backup (not a callable trampoline)
    uint32_t* code = (uint32_t*)target_func;

    // ARM64 absolute jump inline hook:
    // LDR X16, #8   -> 0x58000050
    // BR X16        -> 0xD61F0200
    code[0] = 0x58000050;
    code[1] = 0xD61F0200;
    memcpy(&code[2], &hook_func, sizeof(void*));

    __builtin___clear_cache((char*)target_func, (char*)target_func + 16);

    mprotect((void*)page_start, page_size, PROT_READ | PROT_EXEC);

    LOGI("Inline hook installed at %p -> %p", target_func, hook_func);
}

// ============================================================
// Hooked functions
// ============================================================

static int hooked_audio_record_start(void* record) {
    LOGI("audio_record_start intercepted — marking as virtual");
    // Let the original execute but log the interception
    if (original_audio_record_start) {
        return original_audio_record_start(record);
    }
    return 0; // pretend success
}

static int hooked_audio_record_read(void* record, void* buffer, int size) {
    LOGI("audio_record_read intercepted — injecting silence (%d bytes)", size);
    // Fill buffer with silence
    if (buffer && size > 0) {
        memset(buffer, 0, size);
    }
    return size;
}

// ============================================================
// Initialization
// ============================================================

__attribute__((constructor))
void init_hooks() {
    LOGI("Initializing audio hooks");

    void* handle = dlopen("libaudioclient.so", RTLD_NOW);
    if (!handle) {
        LOGE("Failed to load libaudioclient.so: %s", dlerror());
        // Try alternative library names
        handle = dlopen("libaudioflinger.so", RTLD_NOW);
    }

    if (handle) {
        // Try multiple possible symbol names
        const char* start_syms[] = {
            "AudioRecord_start",
            "_ZN7android11AudioRecord5startEv",
            "android_media_AudioRecord_start",
            nullptr
        };
        const char* read_syms[] = {
            "AudioRecord_read",
            "_ZN7android11AudioRecord4readEPvi",
            "android_media_AudioRecord_read",
            nullptr
        };

        void* start_ptr = nullptr;
        for (int i = 0; start_syms[i]; i++) {
            start_ptr = dlsym(handle, start_syms[i]);
            if (start_ptr) {
                LOGI("Found AudioRecord start at %p (symbol: %s)", start_ptr, start_syms[i]);
                break;
            }
        }

        void* read_ptr = nullptr;
        for (int i = 0; read_syms[i]; i++) {
            read_ptr = dlsym(handle, read_syms[i]);
            if (read_ptr) {
                LOGI("Found AudioRecord read at %p (symbol: %s)", read_ptr, read_syms[i]);
                break;
            }
        }

        // Install hooks
        if (start_ptr) {
            original_audio_record_start = (int(*)(void*))start_ptr;
            install_inline_hook(start_ptr, (void*)hooked_audio_record_start);
        }

        if (read_ptr) {
            original_audio_record_read = (int(*)(void*, void*, int))read_ptr;
            install_inline_hook(read_ptr, (void*)hooked_audio_record_read);
        }

        LOGI("Audio hooks installed: start=%s read=%s",
             start_ptr ? "YES" : "NO", read_ptr ? "YES" : "NO");
    }
}

// ============================================================
// JNI interface
// ============================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_openavplugin_root_NativeInjector_initAudioHook(JNIEnv* env, jobject thiz) {
    LOGI("Audio hook initialized from Java (runtime re-init)");
    // Re-run initialization in case it wasn't done at constructor time
    init_hooks();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_openavplugin_root_NativeInjector_isAudioHookActive(JNIEnv* env, jobject thiz) {
    return (original_audio_record_read != nullptr) ? JNI_TRUE : JNI_FALSE;
}
