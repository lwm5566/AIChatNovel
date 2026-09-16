---
name: build-env
description: AIChatNovel 容器内构建环境与已验证命令（含 buildToolsVersion 坑）；构建/测试历史结果见 docs/ai/PHASE_LOG.md
---
# AIChatNovel 构建环境（容器内已验证）

> 阶段构建/测试的真实结果记录在 `docs/ai/PHASE_LOG.md`；当前进度见 `docs/ai/CURRENT_PHASE.md`。

## 工具链位置
- JDK 17: `/usr/lib/jvm/java-17-openjdk-arm64`
- Android SDK: `/root/android/sdk`（cmdline-tools/latest、platforms/android-35、build-tools/35.0.0、platform-tools）
- Gradle 8.11.1: 项目自带 wrapper（`./gradlew`）；本地发行版在 `/root/tools/gradle-8.11.1/bin/gradle`
- 项目 `local.properties`：`sdk.dir=/root/android/sdk`
- `~/.gradle/gradle.properties`：`android.aapt2FromMavenOverride=/root/android/sdk/build-tools/35.0.0/aapt2`
  （aarch64 上必须用 ARM64 静态版 aapt2，来自 github.com/lzhiyong/android-sdk-tools 35.0.2）

## 已验证的命令
```bash
cd ~/workspace
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`（约 11MB）。

## 坑（已定位并验证）
- **必须显式写 `buildToolsVersion = "35.0.0"`**。不写时 AGP 8.7.3 取默认的 34.0.0，而容器只装了 35.0.0，报
  `Failed to install the following SDK components: build-tools;34.0.0`。
- Gradle Kotlin DSL 里 `java.util.Properties` 的 `java` 会被插件扩展遮蔽 → 需在文件顶部 `import java.util.Properties`。

## 版本组合（已验证）
AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.10.01 / Navigation Compose 2.8.4 / Lifecycle 2.8.7 /
Activity Compose 1.9.3 / OkHttp 4.12.0 / kotlinx-serialization-json 1.7.3 /
compileSdk 35 / buildToolsVersion 35.0.0 / minSdk 26 / Java 17。