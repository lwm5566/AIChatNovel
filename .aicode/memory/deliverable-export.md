---
name: deliverable-export
description: 用户长期要求：每次改动完成后把最新 APK 与源码包复制到设备下载目录 /mnt/Download（含打包命令与命名）
---
# 交付物导出约定（用户长期要求）

**每次完成代码修改后，把最新的 APK 与源码包复制一份到设备下载目录。**

## 目标目录
容器内 `/mnt/Download/`（= 手机「下载」目录，可写；文件权限需 `chmod 644`，否则系统可能读不到）

## 产物与命名（固定名，覆盖旧版）
| 产物 | 下载目录路径 | 来源 |
|---|---|---|
| APK | `/mnt/Download/AIChatNovel-0.1.0-debug.apk` | `app/build/outputs/apk/debug/app-debug.apk` |
| 源码包 | `/mnt/Download/AIChatNovel-src-0.1.0.zip` | 项目源码打包（见下） |

版本号随 `app/build.gradle.kts` 的 `versionName` 更新时同步改。

## 打包源码的命令
```bash
cd ~/workspace
OUT=/tmp/AIChatNovel-src-0.1.0.zip
rm -f "$OUT"
zip -qr "$OUT" \
  AGENTS.md .gitignore build.gradle.kts settings.gradle.kts gradle.properties \
  gradle app/src app/build.gradle.kts app/proguard-rules.pro \
  docs .aicode/memory
cp -f "$OUT" /mnt/Download/AIChatNovel-src-0.1.0.zip && chmod 644 /mnt/Download/AIChatNovel-src-0.1.0.zip
```
**排除**：`build/`、`.gradle/`、`.kotlin/`、`.git/`、`local.properties`（含本机 SDK 路径）、`.aicode/permissions.json`。
**包含**：全部源码与资源、Gradle wrapper、`AGENTS.md`、`docs/`、`.aicode/memory/`（项目长期知识）。

## 步骤
1. 先跑 `./gradlew assembleDebug`（确保 APK 与当前源码一致）
2. 复制 APK + 打包源码包到 `/mnt/Download/`，`chmod 644`
3. 用 `md5sum` 校验源与目标一致，并在回复里报告路径与大小

## 注意
- 用户说的「移动」按**复制**执行——移动会破坏工作区，复制才符合意图。
- 若修改没动业务代码（例如只改文档），APK 内容不变，可说明后跳过打包或仍照做（以用户当次要求为准）。