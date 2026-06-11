# OpenAVPlugin

Android 虚拟摄像头和麦克风插件，支持 LSPosed 和 Root 两种模式。

## 功能特性

### 虚拟摄像头
- 📹 播放本地视频文件作为虚拟摄像头
- 🌐 接收网络串流 (RTSP/RTMP) 作为输入
- 📱 将屏幕画面作为虚拟摄像头源

### 虚拟麦克风
- 🔇 静音模式（无声）
- 🎵 播放本地音频文件
- 🎤 系统音频捕获

### 应用管理
- 📋 查看已安装应用列表
- ⚙️ 为每个应用单独配置虚拟摄像头/麦克风
- 🔍 搜索和过滤应用

### 权限管理
- ✅ 自动检测权限状态
- 🔧 一键跳转到设置页面
- 📱 小米/MIUI 设备优化提示

## 支持平台

- **Android 版本**: 8.0 - 16.0
- **运行模式**:
  - LSPosed 模式（推荐）
  - Root 模式
  - 自动检测

## 安装方法

### 方法一：LSPosed 模式（推荐）

1. 下载 APK 文件
2. 安装到设备上
3. 打开 LSPosed 管理器
4. 进入「模块」页面
5. 找到 OpenAVPlugin 并启用
6. 选择要作用的目标应用
7. 强制停止目标应用或重启手机

### 方法二：Root 模式

1. 下载 Magisk 模块 ZIP 文件
2. 打开 Magisk 管理器
3. 从本地安装模块
4. 重启手机

## 使用方法

### 1. 配置权限

首次打开应用时，会自动跳转到权限设置页面：
- 开启「所有文件访问」权限
- 开启「通知」权限
- 关闭「电池优化」

### 2. 设置运行模式

进入「设置」页面，选择运行模式：
- **LSPosed 模式**：需要 LSPosed 框架
- **Root 模式**：需要 Root 权限
- **自动检测**：自动选择可用模式

### 3. 配置目标应用

1. 进入「应用管理」页面
2. 找到要配置的应用
3. 点击齿轮图标进入配置页面
4. 开启「虚拟摄像头」和/或「虚拟麦克风」

### 4. 添加媒体文件

1. 进入「数据源」页面
2. 添加本地视频/音频文件
3. 或添加网络串流地址

## 小米/MIUI 设备注意事项

1. **自启动权限**：设置 → 应用管理 → OpenAVPlugin → 自启动 → 开启
2. **电池优化**：设置 → 应用管理 → OpenAVPlugin → 省电策略 → 无限制
3. **后台运行**：将应用加入「锁定后台」列表
4. **MIUI 优化**：开发者选项中关闭「MIUI 优化」（如需要）

## 技术栈

- **语言**: Kotlin + C/C++
- **UI**: Jetpack Compose + Material 3
- **数据库**: Room
- **依赖注入**: Hilt
- **视频解码**: ExoPlayer
- **Hook 框架**: Xposed API
- **模块化**: Magisk Module

## 项目结构

```
OpenAVPlugin/
├── app/src/main/
│   ├── java/com/openavplugin/
│   │   ├── hook/           # LSPosed Hook 模块
│   │   ├── root/           # Root 模式支持
│   │   ├── provider/       # 数据源（视频/音频）
│   │   ├── ui/             # 界面
│   │   ├── data/           # 数据层
│   │   ├── permission/     # 权限管理
│   │   └── service/        # 后台服务
│   └── res/                # 资源文件
├── native/                 # Native 代码（Root 模式）
├── magisk_module/          # Magisk 模块
└── docs/                   # 文档
```

## 开发环境要求

- Android Studio Hedgehog+
- Kotlin 1.9+
- Gradle 8.0+
- NDK 25+（Root 模式）
- Min SDK: 26 (Android 8)
- Target SDK: 35 (Android 16)

## 编译方法

1. 克隆仓库
```bash
git clone https://github.com/lsp666-666/openavplugin.git
```

2. 用 Android Studio 打开项目

3. 同步 Gradle

4. 构建 APK
```bash
./gradlew assembleDebug
```

## 许可证

本项目仅供学习交流使用。

## 免责声明

本项目仅供学习和研究使用，用户需自行承担使用风险。请遵守当地法律法规。

## 联系方式

- GitHub Issues: [提交问题](https://github.com/lsp666-666/openavplugin/issues)
