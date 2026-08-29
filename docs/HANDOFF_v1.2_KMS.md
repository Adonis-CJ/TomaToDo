# TomaTodo 开发上下文交接文档（HANDOFF）

> 最后更新：2026-08-29 · 会话：v1.2 KMS 升级 + 番茄钟结束醒目提醒
> 本文件是「上一个开发会话」的完整上下文快照，新会话只需读取本文件（+ `docs/UPGRADE_v1.2_KMS.md` 需求文档）即可接续工作。

## 1. 项目与基线

- 项目：TomaTodo —— 考研人 Android 平板效率应用（Kotlin 2.2 + Compose M3，AGP 9 / Gradle 9.1 / KSP，minSdk 26 / targetSdk 36，Room 2.8.4 + DataStore + Coil 2.7，手动 DI `AppContainer`）。
- 基线版本：v1.1.1（schema v2）。当前工作区处于 **v1.2.0 开发中，未提交**（git status 干净时是基线；现在有 21 个修改文件 + 13 个新增未提交）。
- 需求文档：[docs/UPGRADE_v1.2_KMS.md](UPGRADE_v1.2_KMS.md)（方案 + 实现状态与偏差记录 §12）。客户原始要求：① 卡片即独立可编辑 MD 文档（可渲染、LaTeX、插图、标签）；② 列表简略化 + 阅读/编辑双视角；③ 回收站防误删；④ 追加：番茄钟结束不明显，需解决；⑤ 其余由 agent 自行补充优化。

## 2. 已完成（代码全部编译通过、单测通过，未真机验证）

### 2.1 知识管理系统（KMS）

**存储架构：文件为源、数据库为索引**
- 每卡 = `filesDir/cards/{id}/note.md` + `assets/`（图片相对路径引用 `![](assets/xxx.jpg)`）；Room 只存元数据/派生索引（title/excerpt/wordCount/mdPath/deletedAt），列表滚动零文件 IO。
- 唯一写入口 `CardRepository`（`data/CardRepository.kt`）：保存 = 原子写（tmp+rename，覆盖失败降级 copy）→ 更新 Room 行 → 同步标签 → 失效缓存；观察流：`observeCards/observeTrash/observeTagsWithCount/observeCardTagLinks`；搜索 = searchMeta(LIKE) → 标签 → 正文缓存 LIKE 三级（未建 FTS，见 §5）。
- 标签独立表：`Tag(id,name UNIQUE)` + `CardTag(cardId,tagId)`（级联删除）；`renameTag` 重名自动合并（reassign 关联后删旧标签）。

**数据模型与迁移**
- `KnowledgeCard` v3 字段：`id,title,excerpt,wordCount,mdPath,deletedAt?,subjectId?,type,source?,masteryLevel,reviewCount,nextReviewAt,lastReviewedAt?,createdAt,updatedAt`（front/back/tags JSON 列已移除）。
- `CardImage` 实体与 `card_images` 表退役（图片并入 md 引用）。`CardImageDao.kt`/`CardImage.kt` 已删除，JSON 字段名 `cardImages` 仅保留用于旧备份导入解析。
- `DatabaseMigrations.MIGRATION_2_3`（`data/db/DatabaseMigrations.kt`）：SQLite 不支持 DROP COLUMN → 重建表。关键技巧：**重建前先备份影子表 `legacy_cards_v2`（front/back/tags）与 `legacy_card_images_v2`，并先把 `review_records` 复制-删除-重建以摘除对 knowledge_cards 的外键**（否则 DROP 父表级联清空复习记录）；`card_images` 行随级联清空但已备份。重建后恢复 review_records、建 tags/card_tags、DROP card_images。
- `CardFileMigrator`（`data/CardFileMigrator.kt`）：启动时消费影子表 → 逐卡生成 note.md（`buildLegacyNote`：标题=front 首行、`---` 分隔、back、`## 附图` 段落）→ 回填索引 → 删除影子表。幂等（影子表存在=迁移标记）；旧 `images/` 保留复制不移动。触发点：`TomaTodoApplication.onCreate`（`ensureCardMigration()` = ensureMigrated + purgeExpired）。

**渲染层（Markwon 4.6.2 最终版，API 已逐一核实，勿凭记忆改！）**
- `ui/cards/render/MarkdownText.kt`：Compose 入口。渲染 = `CardTextUtils.prepareForRender`（后台）→ `markwon.toMarkdown(prepared)`（**注意：4.6.2 没有 `markdown(String)` 方法，是 `toMarkdown(String): Spanned`；`setParsedMarkdown(tv, Spanned)` 应用**）。
- **MarkwonTheme 在包 `io.noties.markwon.core.MarkwonTheme`**（不是 io.noties.markwon 下）！
- `TablePlugin.create { b -> ... }`（ThemeConfigure SAM，**无 context 参数**；TableTheme.Builder 方法见文件）。
- `JLatexMathPlugin.create(inlinePx, blockPx) { b -> b.theme().inlineTextColor(...).blockTextColor(...).blockFitCanvas(true) }` —— 颜色在 `theme()` 返回的 JLatexMathTheme.Builder 上。
- `ui/cards/render/KmsImagePlugin.kt`：**自研 Coil2 图片插件**（官方 markwon-image-coil 绑定 Coil 1.x 与项目冲突）。要点：`configureSpansFactory` 里 `builder.setFactory(Image::class.java) { configuration, props -> ... }`，`val destination = props.get(ImageProps.DESTINATION)`（RenderProps.get(Prop)，Prop 在 io.noties.markwon.Prop），span 由 `ImageSpanFactory().getSpans(...)` 得到（**方法名 getSpans，参数 RenderProps，返回 Object?**）；图片加载用 `context.imageLoader.execute(ImageRequest)`（data=drawable.destination，allowHardware=false）挂在协程，成功且 `drawable.isAttached` 时 `DrawableUtils.applyIntrinsicBoundsIfEmpty(loaded); drawable.setResult(loaded)`；`beforeSetText/afterSetText` 走 AsyncDrawableScheduler.unschedule/schedule。
- 相对路径：`prepareForRender` 把 `assets/xx.jpg` 替换为绝对路径（File(baseDir, path).absolutePath），ImageProps 那个 clickable 只对非空 destination 附加（点击跳全屏查看器）。

**已踩过的编译坑（改代码时警惕）**
- Kotlin 允许中文标识符 → 字符串 `"$公式$"` 会解析为模板变量报 Unresolved；需 `\$` 转义（CardDetailScreen placeholder 已修）。同类：`"$$snippet$$"` 里 `$s` 也是模板，已改 `"\$\$$snippet\$\$"`。
- KDoc 里 `assets/*。` 的 `/*` 会开启嵌套块注释报 Unclosed comment（BackupManager 已改）。
- `kotlinx.coroutines.flow.combine` 只有 2–5 参版本，6 个流需嵌套（CardsViewModel 改为 baseFiltered 4 流 + 2 流两级）。
- `@file:OptIn(...)` 引用的符号**必须**有 import（file 注解在 package 前，但 Kotlin 允许引用 import 的类）；CardDetailScreen/CardsScreen 已补 `import androidx.compose.material3.ExperimentalMaterial3Api`。
- `MainScreen` 引用 CardDetailScreen/TrashScreen 需 import（旧 CardEditScreen import 已替换）。

**UI**
- `CardsScreen`（列表）：摘要卡（科目色点+类型+标题+2行摘要+标签chips+待复习徽标+删除图标→Snackbar 撤销）、搜索框（250ms 防抖）、科目 Chip 行 +「更多筛选」抽屉（类型/只看待复习/标签多选取交集/管理标签入口）、网格/分组切换、标题行回收站入口。
- `CardDetailScreen`（阅读↔编辑）：详情 `CardDetailViewModel`（load/save/insertImage/moveToTrash/newCameraImage）；阅读视图 = MarkdownText 全渲染 + 元信息条 + 反标签；编辑视图 = OutlinedTextField + 工具栏（标题/加粗/斜体/删除线/列表/引用/代码块/表格/链接/公式菜单[10 个 LaTeX 快捷片]/模板菜单[空白·知识点·错题·数学结论]/拍照/相册）+ 宽屏≥700dp 左编辑右预览 + 元数据条（科目/类型/来源/标签联想 chips）；1.5s 防抖自动保存 + 变更快照比对（savedSnapshot）；新建空表单直接返回不建卡；移入回收站二次确认；图片点击 → ImageViewerDialog（复用）。
- `TrashScreen`：回收站列表（剩余/删除时间）+恢复/彻底删除/清空（confirm）。
- `MainScreen`：覆盖层路由 Triple(viewerOpen, trashOpen, timerImmersive)，`PhaseCompletionOverlay` 渲染在 AnimatedContent 之外（覆盖所有页）。
- 复习：`ReviewViewModel` 增加 `readNote/baseDirFor + ensureMigrated`；`ReviewScreen` 改造：`splitQuestionAnswer` 问面（无 `---` 时=标题+摘要）/答面，均 MarkdownText 渲染，图片可点全屏；按钮与艾宾浩斯逻辑不变。

**备份（BackupManager 全量重写）**
- 导出：`backup.json`（加 `schemaVersion:3`、`tags`/`cardTags` 数组，无 cardImages）+ `cards/` 整个目录树。
- 导入：v3 直接还原（导入前 deleteRecursively cards/ 防残留）；**旧 v1.1.1 ZIP 兼容**（`cardImages` JSON + `images/` 文件 → 文件化建卡，图片迁到 `cards/{id}/assets/`）。`import(json)` 是 public（SettingsViewModel 在用），返回文件化的卡数。路径写入有 `safeWrite` 防穿越。

### 2.2 番茄钟结束醒目提醒（用户追加需求）

- `timer/AppForegroundTracker.kt`：isForeground 标记（MainActivity onStart=true / onStop=false）。
- `ui/PhaseCompletionOverlay.kt`：监听 `TimerViewModel.events`（PhaseCompleted 且 visible && isForeground）→ 全屏脉冲圆环浮层（「专注完成！」+ 一键开始下一阶段 + 稍后再说 + 轻触关闭 + 30s 自动消失 + 长震动）。注意用了 `rememberUpdatedState(visible)` 防止长存协程闭包冻结。
- `timer/AlarmNotifications.kt`：CHANNEL_ID=timer_alarm_channel（IMPORTANCE_HIGH + 系统闹钟铃声 + 震动波形）、`show()`（FLAG_INSISTENT 循环响铃 + CATEGORY_ALARM + 点击回 MainActivity）、`cancel()`。
- `TimerService` 改造：`playCompletion(event)` 分流 —— 前台走 `alarmToneTriple()`（STREAM_ALARM 三连音 260ms×3，音色仍随 ringtoneId，音量随 volume）；后台走 AlarmNotifications.show（**不**再放 ToneGenerator 防双响）；`vibrate()` 改三段波形（0,350,220,350,220,700）。状态收集协程里 `if (running) AlarmNotifications.cancel()`（开始下一阶段即停响铃）；ACTION_STOP 也 cancel。
- Manifest 新增 `USE_FULL_SCREEN_INTENT` 权限。（AlarmNotifications show 里写注释提到 fullScreenIntent —— 实现上仅高优先级通知+亮屏渠道，未实际 setFullScreenIntent，与注释表述有出入，见 §5 待确认。）

## 3. 验证状态

- ✅ `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug` 通过（Git Bash，无报错输出即成功）。
- ✅ schema v3 已导出：`app/schemas/com.tomatodo.data.db.TomaTodoDatabase/3.json`（未跟踪，新文件）。
- ❌ **未真机验证**（环境无模拟器/设备）。客户用华为平板。
- 唯一单测：`ReviewLogicTest`（未改动，沿用）。

## 4. 未完成 / 下一步

1. **真机验证清单**（最重要）：
   - a. 从 v1.1.1 旧数据升级：图片/标签/复习排程完整（重点看迁移前后核对）。
   - b. 编辑页拍图/相册插入 → 光标处引用 → 阅读视图显示。
   - c. 后台完成提醒：响铃循环是否如期停止（开始下一阶段或回前台）。
   - d. 深色模式下公式/表格/代码块配色可读。
2. **Migration 自动化测试**：目前 MIGRATION_2_3 是手写 SQL，无 MigrationTestHelper 用例；建议补 `app/src/androidTest`（需加 room-testing 依赖）或至少人工核对 `3.json` 与实体一致（编译期 Room 只校验打开时的 identity hash，不校验迁移）。
3. **提交 git**：建议拆两个 commit —— ① KMS 升级（数据/渲染/UI/备份/复习/文档）；② 番茄钟提醒（timer/ 与 MainScreen 相关）。用户要求时再 push（仓库 origin 为 GitHub，主分支 main）。
4. **M3 未实现项**（升级文档 P2）：`[[卡片标题]]` 双链与反链、模板可设置默认不弹选、Obsidian 笔记库单独导出、`images/` 旧目录最终清理（当前保留兼容）。
5. 版本号：`app/build.gradle.kts` 仍是 versionCode 1 / versionName "1.0"，发布前需更新（如 1.2.0 → 实际上目前 schema 已是 v3，建议 versionCode 2 / versionName "1.2"）。
6. README/Roadmap 未更新（看板科目筛选等旧项仍在；KMS 相关描述可后续补）。

## 5. 风险与注意

- **MIGRATION_2_3 正确性**：影子表→文件化在 Room 事务外完成（CardFileMigrator 内 withTransaction 只包 DB 部分；文件写入在事务中执行——实际代码写文件在 db.withTransaction 内，若中途失败 note.md 可能已写，重跑覆盖无副作用，OK）。
- **CardRepository.search**：正文读入 HashMap 全量缓存，卡多时内存线性增长；卡片写入会 invalidate 单卡，标签改名 invalidateTagCache。卡片量 10³ 内可接受。
- **Markwon 已停维护**（4.6.2 为最终版，2021）：API 冻结稳定；所有引用集中在 `ui/cards/render/` 单层，未来可替换渲染器。JLatexMath 个别宏不支持 → 渲染失败会抛异常但 UI 不崩（JLatexMathPlugin 默认 errorHandler 输出占位）。
- **Coil 2 与 markwon image-coil 冲突**：勿再引入 `io.noties.markwon:image-coil`。
- **KmsImagePlugin 线程**：图片加载用 Main.immediate 协程 scope + Coil execute（Coil 内部管理线程），setResult 需在主线程（是）。
- **AlarmNotifications**：注释声称 fullScreenIntent 但代码未调用 `setFullScreenIntent`；如需息屏强亮屏需加（Android 14+ 该权限受限，按需定夺）。
- **回收站清理**：purgeExpired 只在启动时触发一次；长期不重启的留存卡会滞留（可接受，已记录）。
- **PhaseCompletionOverlay 与沉浸模式**：专注完成时 state.isRunning=false 会自动退出 immersive，浮层显示于全屏 Box 之上，无冲突。

## 6. 关键文件速查

| 领域 | 文件 |
|---|---|
| 存储/写入口 | `data/CardRepository.kt`（唯一写入口+搜索+标签+图片+回收站）、`data/CardTextUtils.kt`（纯函数：派生/拆分/预处理/模板）、`data/CardFileMigrator.kt` |
| DB | `data/db/{TomaTodoDatabase,DatabaseMigrations,KnowledgeCardDao,TagDao}.kt`、`data/model/{KnowledgeCard,Tag}.kt` |
| 渲染 | `ui/cards/render/{MarkdownText,KmsImagePlugin}.kt` |
| 卡片 UI | `ui/cards/{CardsScreen,CardsViewModel,CardDetailScreen,CardDetailViewModel,TrashScreen}.kt`（ImageViewer.kt 复用未改） |
| 复习 | `ui/review/{ReviewScreen,ReviewViewModel}.kt` |
| 备份 | `data/BackupManager.kt`（全量重写） |
| 番茄提醒 | `timer/{AlarmNotifications,AppForegroundTracker,TimerService}.kt`、`ui/PhaseCompletionOverlay.kt`、`MainActivity.kt`、`AndroidManifest.xml` |
| 文档 | `docs/UPGRADE_v1.2_KMS.md`（需求+偏差记录）· 本文件 |
