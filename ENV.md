# TomaTodo 开发环境配置文档（ENV.md）

> 本文档记录本机开发环境的检查结果，以及开始开发 TomaTodo 前需要完成的配置项。
>
> - 检查日期：2026-08-28
> - 目标技术栈：Kotlin + Jetpack Compose + Room + DataStore（见 `PRD.md` §7）
> - 关联文档：`PRD.md`

---

## 1. 环境检查结果

### 1.1 组件状态总览

| 组件 | 状态 | 位置 / 版本 | 结论 |
|---|---|---|---|
| Android Studio | 已安装 | `D:\Android-Studio`（2026.1.3，build 261.26222.65.2613.15948027） | 可用 |
| Android SDK | 已安装（非标准路径） | `D:\android-studio-sdk` | 存在，但环境变量未指向 |
| SDK - platforms | 有 | `android-36.1` | 可作 compileSdk 36 |
| SDK - build-tools | 有 | `36.1.0`、`37.0.0` | 满足 |
| SDK - platform-tools | 有 | `D:\android-studio-sdk\platform-tools`（adb 37.0.0） | 满足 |
| SDK - cmdline-tools | **缺失** | — | 可选，命令行 / avdmanager 需要 |
| SDK - emulator | 有 | `D:\android-studio-sdk\emulator` | 存在，但缺系统镜像 |
| JDK（系统） | **过旧** | Java `1.8.0_421`（仅 JRE，无 `javac`） | **不可用于 Android 构建** |
| JDK（AS 自带 jbr） | 可用 | `D:\Android-Studio\jbr` = OpenJDK `25.0.2` | 满足 AGP 的 JDK 17+ 要求 |
| Gradle | 未装全局，有 wrapper 缓存 | `C:\Users\Gates\.gradle\wrapper\dists\gradle-9.0.0-bin` | wrapper 可用 |
| Git | 已安装 | `2.48.1.windows.1` | 满足 |
| adb（PATH 中） | 指向重复副本 | `D:\android-studio(adb)\platform-tools` | 需改为 SDK 自带的 |

### 1.2 关键发现

1. **环境变量全部缺失**：`JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 均未设置。
2. **SDK 位于非标准路径** `D:\android-studio-sdk`（标准路径是 `%LOCALAPPDATA%\Android\Sdk`），
   因此 Android Studio / Gradle **不会自动发现**，必须显式指定。
3. **系统 JDK 是 Java 8（仅 JRE）**，`javac` 不存在，无法编译 Android 项目；但 Android Studio 自带的
   `jbr`（JDK 25）可直接复用。
4. **Android Studio 文件夹名为 `Android-Studio`（首字母大写）**。Windows 不区分大小写，它与小写的
   `android-studio` 是**同一目录**（inode 相同），并非重复安装，本机只有一份。
5. **项目目录当前只有 `PRD.md`**，尚无 `local.properties`、`gradle` 文件、`app` 模块。

---

## 2. 待配置项

### 2.1 【必须】配置 Android SDK 路径

将 SDK 路径写入环境变量，让 Gradle / 命令行工具能找到它。

**方法 A：系统环境变量（推荐，GUI）**

1. `Win + R` 输入 `sysdm.cpl` → 回车。
2. 「高级」→「环境变量」→「系统变量」→「新建」，添加两个：
   - 变量名 `ANDROID_HOME`，值 `D:\android-studio-sdk`
   - 变量名 `ANDROID_SDK_ROOT`，值 `D:\android-studio-sdk`

**方法 B：PowerShell（快速，仅当前用户）**

```powershell
setx ANDROID_HOME "D:\android-studio-sdk"
setx ANDROID_SDK_ROOT "D:\android-studio-sdk"
```

> 设置后需**重开终端**（或注销重登）才会生效。

### 2.2 【必须】配置 JDK

Android Gradle Plugin（AGP 8.x）要求 JDK 17+，本机系统 Java 8 不可用。二选一：

**方案一（零安装，推荐）**：直接复用 Android Studio 自带的 jbr（JDK 25）。

```powershell
setx JAVA_HOME "D:\Android-Studio\jbr"
```

**方案二（最稳，适合命令行构建）**：安装 Temurin JDK 21 LTS
（<https://adoptium.net/>），安装后 `JAVA_HOME` 指向其安装目录（如 `C:\Program Files\Eclipse Adoptium\jdk-21...`）。

> 若后续命令行 Gradle 报 JDK 版本告警，优先采用方案二（JDK 21 是 AGP 最兼容的 LTS 版本）。

### 2.3 【必须】在项目中创建 `local.properties`

在项目根目录（`c:\Users\Gates\Desktop\TomaToDo`）新建 `local.properties`，内容：

```properties
sdk.dir=D:/android-studio-sdk
```

> 该文件让命令行 Gradle 和 Android Studio 都能定位 SDK；它**不应提交到 Git**（已在 `.gitignore` 惯例中忽略）。

### 2.4 【建议】修正 PATH 中的 adb 指向

当前 PATH 里是 `D:\android-studio(adb)\platform-tools`（一份手动放置的副本），建议改为 SDK 自带的：

1. `sysdm.cpl` → 环境变量 → 找到 Path。
2. 删除 `D:\android-studio(adb)\platform-tools`。
3. 新增 `%ANDROID_HOME%\platform-tools`（即 `D:\android-studio-sdk\platform-tools`）。
4. 可顺手删除冗余目录 `D:\android-studio(adb)`。

### 2.5 【可选】安装 cmdline-tools

用于命令行 `sdkmanager` / `avdmanager`（Android Studio 图形界面不需要）。

- 打开 Android Studio → Settings → Languages & Frameworks → Android SDK → SDK Tools。
- 勾选 `Android SDK Command-line Tools (latest)` → Apply。

### 2.6 【可选】创建平板模拟器（AVD）

TomaTodo 面向平板，运行测试二选一：

- **真机（推荐）**：Android 平板开启「开发者选项 → USB 调试」，USB 连接后 `adb devices` 确认。
- **模拟器**：需先下载系统镜像（`system-images`，当前缺失），再创建平板规格 AVD：

  1. Android Studio → Device Manager → Create Device → 选 Tablet 分类（如 Pixel Tablet）。
  2. 选择系统镜像（API 36）并下载。
  3. 创建后启动，验证平板横屏布局。

---

## 3. 验证清单

配置完成后，按顺序验证：

- [ ] `echo $ANDROID_HOME` 输出 `D:\android-studio-sdk`（Git Bash）或 `echo %ANDROID_HOME%`（CMD）。
- [ ] `java -version` 输出 JDK 17/21/25（非 1.8）。
- [ ] `adb --version` 能运行，且路径来自 `D:\android-studio-sdk\platform-tools`。
- [ ] 项目根目录存在 `local.properties`，内容为 `sdk.dir=D:/android-studio-sdk`。
- [ ] 在 Android Studio 中打开 `TomaToDo`，Gradle Sync 成功（无 SDK/JDK 报错）。
- [ ] 能创建或运行一个平板 AVD / 连接真机，`adb devices` 看到设备。
- [ ] 首次构建一个空 Compose 项目并成功跑起来。

---

## 附：环境速查表

| 项 | 值 |
|---|---|
| Android Studio | `D:\Android-Studio`（jbr：`D:\Android-Studio\jbr`，JDK 25） |
| Android SDK | `D:\android-studio-sdk` |
| compileSdk / platform | `android-36.1` |
| build-tools | `36.1.0` / `37.0.0` |
| Gradle（wrapper 缓存） | `9.0.0` |
| Git | `2.48.1` |
| 待设环境变量 | `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` |
