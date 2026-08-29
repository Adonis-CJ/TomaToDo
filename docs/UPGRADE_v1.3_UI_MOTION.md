# TomaTodo v1.3 升级需求文档 —— 视觉质感 · 动效 · KMS 修复

> 编写日期：2026-08-29 · 状态：开发中
> 需求来源：客户 ① UI 质感升级；② 动画优化；③ KMS 图片嵌入修复（图片选择器选图后无嵌入响应）；
> ④ 建立通用开发规范。前序版本：v1.2 KMS（见 [UPGRADE_v1.2_KMS.md](UPGRADE_v1.2_KMS.md)、[HANDOFF_v1.2_KMS.md](HANDOFF_v1.2_KMS.md)）。

---

## §1 图片嵌入修复（P0）

### 1.1 根因分析（已确认）

`CardRepository.insertImage`（data/CardRepository.kt）：

```kotlin
val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    ?: return@withContext null        // ← bug 在此
```

`inJustDecodeBounds = true` 时 `decodeStream` **按设计返回 null**（仅填充 bounds），
因此流成功打开时 `openInputStream(uri)?.use { ... }` 整体求值仍为 null，
elvis 恒触发 → `insertImage` **永远返回 null** → `onResult(id, null)` → UI 判断 `ref != null` 不成立 →
不插入 Markdown、不落图。**相册与拍照两条路径 100% 静默失败**，且无任何日志或用户提示。

### 1.2 修复方案

1. **解耦「流打开」与「解码结果」**：流判空单独做，bounds 解码结果不参与判空。
2. **竞态修复（相机路径）**：`discardCameraTemp` 在 `insertImage` 启动后立即删除临时文件，
   而 IO 线程可能尚未读取 → FileNotFound。改为 `insertImage` 增加 `cleanup` 回调，
   在协程 `finally` 中执行（读完后删，失败也删）。
3. **失败可感知**：捕获的异常写入 `Log.e`（禁止静默吞掉，见开发规范 §鲁棒性）；
   `ref == null` 时 UI 层 Snackbar 提示「图片插入失败」。

### 1.3 验收标准

- [ ] 新建卡片（未保存）直接选相册图片 → 卡片自动落盘，光标处插入 `![](assets/xxx.jpg)`，预览显示图片。
- [ ] 编辑已有卡片 → 相册/拍照插图 → 同上；连续插多张不串位。
- [ ] 插入失败（如 URI 不可读）时 Snackbar 提示，Logcat 有异常栈，应用不崩溃。
- [ ] 拍照后临时文件被清理（成功/失败/取消三条路径）。

---

## §2 UI 质感升级（P1）

**原则**：贯彻「墨·纸」设计系统（PRD §6.2）——纸面层次靠**发丝描边 + 低海拔阴影 + 克制圆角**表达，
不加色彩堆叠。所有改动双主题（浅/深）同时校验。

### 2.1 设计令牌补全

- `Theme.kt` 接入 `MaterialTheme(shapes = Shapes(...))`：small 8dp / medium 12dp / large 16dp；
  按钮显式 `RoundedCornerShape(8.dp)`（PRD：按钮 8px）。
- 新增 `theme/Motion.kt` 动效令牌（见 §3.1），时长/曲线全应用统一，消灭散落的 magic number。

### 2.2 知识卡片列表页（CardsScreen）

- **摘要卡**：纸卡底 + 1dp `outlineVariant` 发丝描边 + 1dp 海拔柔和阴影；按下 0.98 缩放回弹；
  顶行删除图标触控区扩大到 28dp 圆形（原 16dp 过小）；待复习徽标加圆点前缀。
- **搜索框**：改为软底样式（surfaceVariant 40% 底、无边框态透明描边、16dp 圆角），更贴近纸面。
- **空状态**：纯文本升级为 图标 + 主文案 + 辅助文案 的居中组合。
- **分组头**：色条 2dp→4dp 高度统一 16dp，加字重层次。

### 2.3 卡片详情/编辑（CardDetailScreen）

- 阅读视图：正文区限宽 **720dp 居中**（平板可读性），顶栏与正文间加发丝分隔线。
- 编辑视图：工具栏置于发丝分隔的条带内；自动保存状态文字（保存中…/已保存）用 AnimatedContent 交叉淡入。
- 元数据条/顶栏统一 hairline 分隔。

### 2.4 复习页（ReviewScreen）

- 复习卡容器与摘要卡同语言：纸卡 + 发丝描边 + 低海拔。
- 掌握度三按钮改次级样式排布，间距统一。

### 2.5 回收站（TrashScreen）

- 列表项统一摘要卡同款容器样式（描边+海拔+圆角 12dp）。

### 2.6 验收标准

- [ ] 卡片列表/复习/回收站容器质感一致（描边+阴影+圆角同一语言）。
- [ ] 深色模式下描边与阴影不突兀（阴影弱化、描边提亮）。
- [ ] 平板横屏阅读正文不超 720dp 行宽。
- [ ] 所有可点元素可视触控区 ≥ 28dp。

---

## §3 动画优化（P1）

### 3.1 动效令牌（theme/Motion.kt）

```kotlin
object Motion {
    val DurationShort = 120; val DurationMedium = 220; val DurationLong = 320; val DurationXL = 450
    val EaseStandard = FastOutSlowInEasing
    val EaseEnter = CubicBezier(0.05f, 0.7f, 0.1f, 1f)   // 强调减速
    val EaseExit  = CubicBezier(0.3f, 0f, 0.8f, 0.15f)   // 加速退出
    // 常用组合：fade+slide（覆盖层进出场）、pressScale spring 参数
}
```

原则：进场用 `EaseEnter`（内容从下 12dp 浮入）、退场用 `EaseExit`、按压回食用 spring(dampingRatio 0.7)。
既有动画（TaskCard/FlipClock/图表/完成浮层）已自洽，**不做无谓重写**，仅在触达的文件内对齐令牌。

### 3.2 页面级转场（MainScreen）

- 目的地切换：内容区加 AnimatedContent——fade + 自下 12dp 浮入（Medium/EaseEnter），退场纯 fade（Short/EaseExit）。
- 覆盖层（卡片详情/回收站）：自右 16dp 滑入 + fade（Medium/EaseEnter），退出反向；沉浸模式切换沿用现有 fade。

### 3.3 列表微动效（CardsScreen）

- 网格项入场：fade + 自下 12dp 浮入，index 阶梯延迟（每项 +35ms，上限 8 项，滚动复用不重放长延迟）。
- 筛选/分组切换重排：`animateItemPlacement()` 平滑归位。

### 3.4 状态微动效

- 自动保存指示（CardDetailScreen）：AnimatedContent 交叉淡入。
- 复习翻面（ReviewScreen）：AnimatedContent 显式 fade + 自下滑入答面（对齐「翻开」隐喻）。

### 3.5 验收标准

- [ ] 页面/覆盖层切换有方向感、不闪跳；快速连续点击导航不卡顿。
- [ ] 列表首次加载有层次入场；筛选切换项平滑归位。
- [ ] 全应用无新增 magic-number 动画参数（统一走 Motion）。
- [ ] 「开发者选项-动画缩放 ×0.5/关闭」下功能不受影响。

---

## §4 通用开发规范（P0）

全文见 [DEVELOPMENT_GUIDELINES.md](DEVELOPMENT_GUIDELINES.md)，要点：

1. **Git**：Conventional Commits 中文 subject；按功能域拆 commit；禁 --no-verify / 改写已推送历史。
2. **鲁棒性**：**禁止静默吞错**——`runCatching` 必须 `onFailure` 打日志或反馈用户（§1 即反面教材）；
   异步资源（临时文件/流）生命周期与协程对齐，finally 清理。
3. **Compose**：状态单向流；remember/LaunchedEffect 必须带 key；组合期无副作用；
   动画一律用 Motion 令牌；深浅色双主题校验。
4. **Room**：schema 变更必须配套 Migration + 导出 schema 文件。
5. **文档**：升级先行（UPGRADE 文档）→ 实现 → HANDOFF 交接更新。

### 验收标准

- [ ] DEVELOPMENT_GUIDELINES.md 与 AGENTS.md 落库，后续会话可加载执行。

---

## §5 实现状态与偏差记录

> 2026-08-29 开发完成回填。

| 项 | 状态 | 说明 |
|---|---|---|
| §1.2 图片嵌入修复 | ✅ 已完成 | 解耦流判空/解码（CardRepository）；相机临时文件 cleanup 入 ViewModel finally；失败 Log.e + Snackbar |
| §2.1 设计令牌 | ✅ 已完成 | Theme shapes 接入；Motion.kt 新增（时长/曲线/弹簧/阶梯延迟） |
| §2.2 CardsScreen 质感 | ✅ 已完成 | 摘要卡描边+阴影+按压回弹；软底搜索框；空状态组件；删除触控区 28dp |
| §2.3 CardDetail 质感 | ✅ 已完成 | 正文限宽 720dp 居中；顶栏/工具栏/元信息 hairline 分隔；保存状态交叉淡入 |
| §2.4 Review 质感 | ✅ 已完成 | 复习卡容器统一（描边+阴影）；掌握度三按钮等宽 |
| §2.5 Trash 质感 | ✅ 已完成 | 列表项容器统一 |
| §3.2 MainScreen 转场 | ✅ 已完成 | 目的地切换 fade+自下浮入；覆盖层自右滑入/反向退出；沉浸切换淡入淡出 |
| §3.3 列表微动效 | ✅ 已完成 | StaggerIn 阶梯入场（封顶 8 项）+ animateItem 重排 |
| §3.4 复习翻面动效 | ✅ 已完成 | 答面自下滑入 + 按钮区交叉淡入 |
| §4 开发规范 | ✅ 已完成 | DEVELOPMENT_GUIDELINES.md + AGENTS.md 落库 |
| 编译/单测 | ✅ 通过 | compileDebugKotlin + testDebugUnitTest + assembleDebug |
| 真机验证 | ⏳ 待客户 | 华为平板：图片插入三路径、深色模式、动画缩放 |

> 偏差：① 相册/拍照失败的 Snackbar 提示随修复一并落在 CardDetailScreen（§1 依赖项，非独立功能）；② 该文件内 §2.3 质感改动与修复同文件，提交时归入 fix commit 并在 commit body 注明。范围外未动：PRD 遗留 P2 项（双链、字体子集化等）。
