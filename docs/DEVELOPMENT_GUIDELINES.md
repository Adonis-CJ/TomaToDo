# TomaTodo 开发规范（Development Guidelines）

> 版本：v1.0（2026-08-29）· 适用：本项目所有人工/Agent 开发会话
> 精简版位于仓库根目录 [AGENTS.md](../AGENTS.md)；冲突时以本文档为准。
> 项目背景见 [PRD.md](../PRD.md)；每次升级的方案与偏差记录见 `docs/UPGRADE_*.md`。

---

## 1. Git 规范

### 1.1 提交信息（Conventional Commits，中文 subject）

```
<type>(<scope>?): <subject>

<body>?（要点式，说明 what & why，不写流水账）
```

| type | 用途 |
|---|---|
| feat | 新功能 |
| fix | 缺陷修复 |
| docs | 仅文档 |
| refactor | 不改行为的重构 |
| perf | 性能优化 |
| test | 测试补充 |
| chore | 构建/依赖/杂项 |

- subject：一行、不超过 50 字符、结尾不加句号；中文书写（与仓库既有风格一致）。
- scope 可省略；跨模块大特性可不写 scope，但 body 必须分条列出改动域。
- body 用要点式（`- `），写「为什么」和验收相关事实（如“根因：inJustDecodeBounds 返回 null”）。

### 1.2 提交拆分

- **按功能域拆 commit**，一个 commit 必须可独立编译、可独立回滚。例：v1.2 拆为「KMS 升级」+「番茄钟提醒」两个 commit。
- 数据迁移、依赖变更与使用它们的代码放同一 commit（保证任意 HEAD 可构建）。
- 截图等二进制资产跟随对应功能 commit。

### 1.3 红线

- 禁止 `--no-verify`、`--no-gpg-sign` 跳过校验。
- 禁止改写已推送历史（amend/rebase published、force push main）。
- 提交前 `git status` 自查，不提交 `local.properties`、密钥、临时调试产物。
- push 需用户明确指示（Agent 会话默认只 commit 不 push）。

---

## 2. Kotlin / 架构规范

### 2.1 架构基线

- MVVM + Repository + 单向数据流：UI 状态由 `StateFlow` 下行，事件上行到 ViewModel。
- **唯一写入口**：业务数据的写操作走 Repository（如 `CardRepository` 是卡片唯一写入口），UI/VM 不得绕过直接操作 DAO 写数据。
- 手动 DI 经 `AppContainer`；新增依赖在 `AppContainer` 注册 lazy 单例。

### 2.2 协程与流

- UI 协程只在 `viewModelScope` / `rememberCoroutineScope`；**禁止 `GlobalScope`**。
- 阻塞 IO（文件、Bitmap、DB 之外的读写）必须 `withContext(Dispatchers.IO)`；`Room` DAO 自身可挂起。
- 对外只暴露 `StateFlow`（`asStateFlow()` 收敛可变性）；Compose 侧 `collectAsState()`。
- `combine` 超过 5 个流需嵌套（kotlinx 只有 2–5 参重载）。

### 2.3 命名与文件

- 包按功能组织（`ui/<feature>`、`data/`），不按层堆大杂烩。
- 文件内私有 Composable 用 `private fun`；跨页复用的放独立文件。
- 中文注释解释「为什么」；不写复述代码的注释。

---

## 3. 鲁棒性规范（本节为最高优先级，源自真实事故）

### 3.1 禁止静默吞错

```kotlin
// ✗ 反例：失败不可见（v1.3 图片嵌入 bug 的直接成因之一）
runCatching { ... }.getOrNull()

// ✓ 正例：记录 + 可感知
runCatching { ... }
    .onFailure { Log.e(TAG, "insertImage failed for card=$id", it) }
    .getOrNull()
```

- `runCatching` / `try-catch` 之后**必须**二选一：`onFailure` 打日志，或把失败映射为用户可见反馈（Snackbar/状态）。
- 捕获范围尽量小，不包整个函数体。
- `return null` 表达「用户主动取消」；「操作失败」必须带日志与提示，两者不得混用。

### 3.2 标准库语义陷阱

- 调用「返回值在特定模式下恒为 null」的 API（如 `inJustDecodeBounds = true` 的 `decodeStream`）时，
  **不得把其返回值用于空判断**；判空对象必须是流/资源本身。
- 链式调用 `?.use {}` 的结果 = lambda 最后一行，先把中间结果赋值再判断，避免误判。

### 3.3 异步资源生命周期

- 临时文件、流、Bitmap 的清理时机必须与**实际消费完成**对齐：在协程 `finally` / `use` 块内清理，
  不得在异步任务启动后同步立即删除（竞态）。
- Bitmap 用完 `recycle()`；流必须 `use {}`。

### 3.4 边界校验

- 只在系统边界做校验：用户输入、外部 URI/Intent、备份 JSON、DB 迁移数据。
- 内部模块间信任调用方，不为「不可能的 null」加防御（Kotlin 类型系统已表达）。

---

## 4. Compose UI 规范

### 4.1 状态与副作用

- 组合期**无副作用**；副作用进 `LaunchedEffect` / `SideEffect`，且**必须带正确 key**。
- `remember` 计算带 key（依赖变化才重算）；跨重组读取的回调用 `rememberUpdatedState` 防冻结。
- 长存协程闭包捕获 Compose 状态时，用 `rememberUpdatedState` 包一层（v1.2 PhaseCompletionOverlay 教训）。

### 4.2 设计系统

- 颜色一律取 `MaterialTheme.colorScheme` / `theme/Color.kt` 令牌；**禁止硬编码 hex**（科目标色除外，存 DB）。
- 圆角/间距按 PRD §6.2：卡片 12、按钮 8、标签 4；间距 4dp 网格（8/16/24/32）。
- **动画参数一律走 `theme/Motion.kt` 令牌**，新代码禁止散写 `tween(237)` 类 magic number。
- 图标用 `ImageVector` 线性风格；UI 禁用 emoji 图标（PRD §6.3）。
- 每处 UI 改动双主题（浅/深）过一遍；深色阴影弱化、描边提亮。
- 可点元素可视触控区 ≥ 28dp，争取 48dp。

### 4.3 列表性能

- Lazy 列表必须提供稳定 `key`；筛选/重排用 `animateItemPlacement()`。
- 列表项数据取自 Room 索引字段，**不在滚动路径做文件 IO**（文件为源、DB 为索引架构约束）。
- 长图/大图先降采样再渲染（`insertImage` 长边 1600px 基线）。

### 4.4 Kotlin 字符串陷阱（历史踩坑，写代码时警惕）

- 字符串模板中 `$` 后跟中文/字母会被解析为变量：`"$公式$"` 编译错 → 用 `\$` 转义。
- KDoc 中 `assets/*。` 的 `/*` 会开启嵌套注释 → 改写为 `assets` 目录表述或转义。

---

## 5. 数据与 Room

- **schema 变更三件套**：实体改动 → 手写 Migration（SQLite 无 DROP COLUMN 需重建表，注意外键级联与影子表备份）→ 导出 schema JSON（`room.schemaLocation`）。
- 迁移必须幂等、可重入（影子表/标记位判定）；迁移逻辑与文件 IO 的顺序要显式记录在 UPGRADE 文档。
- 版本号同步：发版时更新 `versionCode`/`versionName`。

## 6. 测试与验证

- 纯函数（文本处理、排程算法）抽到 `object`/顶层函数便于单测；现状基线：`ReviewLogicTest`、`CardTextUtils`。
- 涉及逻辑新增/修复时补对应单测；UI 改动列出人工验证清单（写入 UPGRADE 文档 §5）。
- 每次提交前：`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` 通过（Git Bash，无报错输出即成功）。

## 7. 文档流程

1. 升级先写 `docs/UPGRADE_vX.Y_<主题>.md`（需求 → 方案 → 验收标准 → 实现状态表），实现完成后回填状态。
2. 交接上下文写 `docs/HANDOFF_*.md`（新会话冷启动入口）。
3. README 的特性/结构/技术栈随版本同步。
