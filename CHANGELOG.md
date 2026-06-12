# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2025-06-12

### Added
- Initial release of OpenAVPlugin
- Virtual Camera support via LSPosed (Camera2 API hooking)
- Virtual Microphone support via LSPosed (AudioRecord hooking)
- Root mode with native C++ hooks (camera HAL + audio capture)
- Magisk module packaging for root-mode deployment
- Jetpack Compose UI with Material 3 design
- Per-app configuration (enable/disable virtual camera/microphone per target app)
- Multiple video source types: local file, network stream (RTSP/RTMP), screen capture
- Multiple audio source types: silence, local file, system audio capture
- Room database for per-app rule storage
- DataStore preferences for global configuration
- Dagger Hilt dependency injection
- Runtime permission management with permission guide
- Xiaomi/MIUI device optimization tips
- Chinese (Simplified) user interface and documentation

[1.0.0]: https://github.com/lsp666-666/openavplugin/releases/tag/v1.0.0
