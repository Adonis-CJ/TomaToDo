# AGENTS.md

TomaTodo —— 考研人 Android 平板效率应用（Kotlin 2.2 + Compose M3，minSdk 26 / targetSdk 36）。

## 必读

1. [docs/DEVELOPMENT_GUIDELINES.md](docs/DEVELOPMENT_GUIDELINES.md) —— 开发规范（Git 提交、鲁棒性、Compose、Room），**冲突时以它为准**。
2. [docs/UPGRADE_v1.3_UI_MOTION.md](docs/UPGRADE_v1.3_UI_MOTION.md) —— 当前进行中的升级及历史偏差记录。

## 硬性规则（速览）

- **禁止静默吞错**：`runCatching`/`catch` 必须 `onFailure` 打日志或反馈用户；「用户取消」与「操作失败」不得都静默返回 null。
- **动画参数走 `ui/theme/Motion.kt` 令牌**，不写 magic number；颜色一律取 MaterialTheme/Color.kt 令牌。
- **卡片数据唯一写入口是 `data/CardRepository`**，UI/VM 不得直接写 DAO。
- Compose：组合期无副作用；`remember`/`LaunchedEffect` 必须带 key；长存闭包读 Compose 状态用 `rememberUpdatedState`。
- Room schema 变更必须配套 Migration + 导出 schema JSON。
- 提交前必过 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`（Git Bash）。
- Git：Conventional Commits 中文 subject，按功能域拆 commit；禁 `--no-verify`；禁改写已推送历史；push 需用户指示。
- 字符串模板 `$` 转义陷阱与 KDoc `/*` 陷阱见规范 §4.4。

## 常用命令

```bash
./gradlew :app:assembleDebug          # 构建 APK
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest   # 提交前检查
```

## 关键架构事实

- 「墨·纸」暖色设计系统（PRD §6.2）：纸白底、墨色字、朱砂单一强调，禁冷蓝紫。
- KMS v1.2：卡片文件为源（`filesDir/cards/{id}/note.md` + assets/）、Room 为索引；列表滚动零文件 IO。
- Markwon 4.6.2 API 已逐一核实（`toMarkdown`/`setParsedMarkdown`、`MarkwonTheme` 包路径、自研 KmsImagePlugin），改动前先读 `docs/HANDOFF_v1.2_KMS.md`。
