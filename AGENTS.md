# OpenAVPlugin — Agent Guide

## Project Overview

OpenAVPlugin is an Android virtual camera and microphone injection tool.
It provides fake video/audio streams to target apps via two modes:

- **LSPosed mode**: Java-level Xposed hooks (Camera2 API, AudioRecord)
- **Root mode**: Native C++ hooks (dlopen function interposition) + Magisk module

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9.20 + C/C++ |
| UI | Jetpack Compose + Material 3 |
| DI | Dagger Hilt 2.48 (KSP) |
| Database | Room 2.6.1 (KSP) |
| Media | ExoPlayer 2.19.1 |
| Config | DataStore Preferences |
| Hook | Xposed API 82 (compileOnly) |
| Native | CMake / NDK (arm64-v8a, armeabi-v7a) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 16) |

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (with ProGuard)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Run tests
./gradlew test

# Run a specific module test
./gradlew :app:testDebugUnitTest

# Build native libs only
./gradlew :app:externalNativeBuildDebug
```

## Project Structure

```
openavplugin/
├── app/src/main/java/com/openavplugin/
│   ├── hook/           # LSPosed Xposed hooks
│   │   ├── HookEntry.kt         — Entry point (IXposedHookLoadPackage)
│   │   ├── CameraHooker.kt      — Camera2 + Camera1 hooking
│   │   └── AudioHooker.kt       — AudioRecord + MediaRecorder hooking
│   ├── root/           # Root mode
│   │   ├── RootChecker.kt       — Root/LSPosed detection
│   │   └── NativeInjector.kt    — Native .so injection
│   ├── provider/       # Media sources
│   │   ├── VideoSource.kt       — Video source interface
│   │   ├── audio/               — Audio source implementations
│   │   └── video/               — Video source implementations
│   ├── ui/             # Compose UI
│   │   ├── MainActivity.kt      — @AndroidEntryPoint activity
│   │   ├── NavHost.kt           — Navigation graph
│   │   ├── home/                — HomeScreen + ViewModel
│   │   ├── apps/                — AppListScreen, AppConfigScreen
│   │   ├── settings/            — SettingsScreen + ViewModel
│   │   ├── sources/             — SourceManagerScreen
│   │   ├── permissions/         — Permission screens
│   │   └── theme/               — Material 3 theme
│   ├── data/           # Data layer
│   │   ├── ConfigManager.kt     — DataStore preferences
│   │   ├── SharedConfigManager.kt — Shared storage config
│   │   └── db/                  — Room (AppRule, RuleDao, AppDatabase)
│   ├── di/             # Hilt modules
│   ├── permission/     # Permission helpers
│   └── service/        # Foreground capture service
├── native/             # C++ native hooks (root mode)
│   ├── CMakeLists.txt
│   ├── libopenavplugin_cam/     — Camera HAL hooks
│   └── libopenavplugin_audio/   — Audio capture hooks
├── magisk_module/      # Magisk module packaging
├── app/proguard-rules.pro       # ProGuard rules
└── app/build.gradle.kts         # Module build config
```

## Key Conventions

- **Strings**: All user-facing strings go in `res/values/strings.xml`, referenced via `stringResource(R.string.xxx)`.
- **DI**: All ViewModels use `@HiltViewModel` + `@Inject constructor`. Activities use `@AndroidEntryPoint`.
- **Coroutines**: `viewModelScope.launch` for async work; `Dispatchers.IO` for DB/IO operations.
- **Room**: DAO methods return `Flow<List<T>>` for reactive queries, `suspend` for one-shot operations.
- **State**: Compose screens use `viewModel.collectAsState()` for reactive updates.
- **Navigation**: Single `NavHost` in `NavHost.kt` with string routes.
- **Xposed Hooks**: All hook classes must be in `hook/` package and kept by ProGuard.
- **Native code**: CMake builds two separate shared libs. Only used in root mode.

## Testing

Testing infrastructure is not yet set up. When adding tests:

- **Unit tests**: `app/src/test/` — use JUnit 5 + MockK for ViewModel tests
- **Instrumented tests**: `app/src/androidTest/` — use Room in-memory database for DAO tests
- **Native tests**: Not yet configured

## ProGuard / R8

Release builds minify with `proguard-android-optimize.txt` + custom `proguard-rules.pro`.
Key rules: keep all Xposed hook classes, Room entities/DAOs, Hilt internals, native methods, and Compose runtime.

## CI/CD

CI is not yet configured. When adding:
- Build and lint check on push/PR
- Unit test run on PR
- APK artifact upload on tag

## Notes

- The app is currently **single-language** (Chinese Simplified). Add `values-en/strings.xml` for English support.
- The project root has `reasonix.toml` for the Reasonix coding agent — not part of the Android build.
- No existing tests — test coverage is the top priority for contributions.
