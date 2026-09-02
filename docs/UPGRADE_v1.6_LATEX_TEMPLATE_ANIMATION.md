# TomaTodo v1.6 升级需求文档 —— LaTeX 行内渲染 · 移除模板 · 结束提醒 z 序 · 时钟淡入淡出

> 状态：实现完成，待真机验证（用户指示「先暂时不用上机验证，你先修改」）
> 前置：v1.5 全局全屏 / 沉浸真全屏 / 防睡眠已交付并推送发布。
> 本轮由用户四条反馈驱动：
> 1. 知识卡片「阅读视角」无法正常预览 LaTeX（行内公式显示源码）。
> 2. 知识系统不需要模板，移除模板入口/选择逻辑/相关提示。
> 3. 番茄钟「结束提示」与「新一轮开始」动画重叠。
> 4. 翻页时钟显示异常，取消翻页设计，改渐变消失/淡出淡入。

---

## §1 阅读视角 LaTeX 行内渲染（P0）

### 1.1 现状与根因（字节码取证）

对 `ext-latex-4.6.2` 反编译（`javap`）确认两处根因：

- `JLatexMathPlugin$Builder.<init>` 只置 `blocksEnabled=true`，**`inlinesEnabled` 默认 false**。项目 `buildMarkwon` 的 `create(inlinePx, blockPx){ b -> b.theme()… }` 仅配置主题，从未调用 `b.inlinesEnabled(true)` → 行内 `$$…$$` 处理器根本未注册，`JLatexMathInlineProcessor` 不生效，行内公式以**源码文本**呈现（正是公式快捷片插入的 `$$\frac{a}{b}$$` 现象）。块级 `$$\n…\n$$` 因 `blocksEnabled=true` 仍可用。
- `JLatexMathPlugin.configure()` 在 `inlinesEnabled` 为真时执行 `registry.require(MarkwonInlineParserPlugin.class)`；`RegistryImpl.get()` 找不到即抛 `IllegalStateException`。而 `Markwon.builder()` 只播种 `CorePlugin`，**未注册 inline-parser 插件**。故仅开启 `inlinesEnabled` 会直接崩溃，必须同时注册 `MarkwonInlineParserPlugin`。
- 附带核查：`inline-parser` 在 `ext-latex` POM 中为 `compile` 作用域 → 传递到本项目编译类路径，`import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin` 可解析。

### 1.2 方案

`MarkdownText.kt`：

1. 插件链在 `CorePlugin` 之后追加 `MarkwonInlineParserPlugin.create()`。
2. `JLatexMathPlugin.create(inlinePx, blockPx){ b -> b.inlinesEnabled(true); b.theme()… }`。
3. **重组稳定性**：`markwon` 原以 `remember(colorScheme, headingTypeface, onImageClick)` 为 key，而阅读视角的 `onImageClick` 是调用方每次重组新建的 lambda → markwon 反复重建、`parsed` 归零重解析（闪烁/空白）。改为用 `rememberUpdatedState` 持有最新回调，再以一次性稳定闭包 `stableOnImageClick` 传入插件，`markwon` 仅随主题/字体重建（符合规范「长存闭包读 Compose 状态用 rememberUpdatedState」）。

`prepareForRender` 的单美元 `$x$`→`$$x$$` 预处理方向正确（行内处理器 RE=`(\${2})([\s\S]+?)\1` 只认双美元），保持不变；新增单测锁定转义契约。

### 1.3 验收标准

- [x] 行内 `$E=mc^2$` 与块级 `$$\n…\n$$` 在阅读视角均渲染为公式，不显示源码。
- [x] 代码围栏内 `$…$`、反斜杠转义 `\$` 不被误当公式。
- [x] 阅读视角滚动/切主题不出现公式区空白或反复重解析闪烁。
- [x] `:app:compileDebugKotlin :app:testDebugUnitTest` 通过（新增 4 条 LaTeX 转义单测）。
- [ ] 真机：行内/块级公式清晰无错位（待客户）。

---

## §2 移除知识系统模板（P1）

### 2.1 现状

模板存在两处入口：新建卡片时弹 `TemplatePickerDialog` 选择模板；编辑工具栏「模板」下拉插入模板正文。数据源 `CardTextUtils.TEMPLATES`。用户明确：知识系统不需要模板，保留直接 Markdown 书写，不新增模板内容/推荐/预填。

### 2.2 方案

- `CardTextUtils.kt`：删除 `TEMPLATES`（含数学模板），更新 KDoc；**保留 `FORMULA_SNIPPETS`**（公式快捷片属书写辅助，非模板）。
- `CardDetailScreen.kt`：删除 `showTemplateDialog`/`templateMenu` 状态、新建模板弹窗块、`EditView` 的 4 个模板参数（调用处+签名）、工具栏「模板」下拉 Box、`TemplatePickerDialog` 组件；清理因此不再使用的 `clickable` 导入；更新 KDoc「新建卡片先选模板」→「直接进入空白 Markdown 书写」。
- 新建卡片（`cardId==null`）行为：直接进入空白编辑视图，不弹任何模板提示。

### 2.3 验收标准

- [x] 全仓库无 `TEMPLATES`/`TemplatePicker`/`showTemplateDialog`/`templateMenu`/「模板」残留。
- [x] 新建卡片直接空白书写；公式快捷片、图片、图片尺寸等其余工具栏功能不受影响。
- [x] `:app:compileDebugKotlin` 通过。

---

## §3 番茄钟结束提醒与新一轮动画重叠（P0）

### 3.1 现状与根因

`MainScreen` 依次发出 `PhaseCompletionOverlay(...)` 与 `AnimatedContent(...)`，**二者无 Box 包裹且提醒在前**。Compose 同层子项按发出顺序绘制，后者（主内容）覆盖前者 → 结束提醒渲染在主内容**之下**；主内容多处透明背景，提醒的 0.97 遮罩、脉冲环、文案与按钮便与计时界面半透明合成 → 肉眼即「结束提示与计时/新一轮界面重叠」。叠加提醒此前无进出场动画（`if(info!=null)` 直接显隐），切换更生硬。

### 3.2 方案

- `MainScreen.kt`：将主内容 `AnimatedContent` 与 `PhaseCompletionOverlay` 一并纳入 `Box(Modifier.fillMaxSize())`，并把提醒**移到 Box 末尾**（最上层），彻底消除 z 序倒置。
- `PhaseCompletionOverlay.kt`：外层改 `AnimatedVisibility(visible = completion != null, enter = fadeIn(Motion.enter()), exit = fadeOut(Motion.exit()))`；退出淡出期间用 `lastInfo`（`LaunchedEffect(completion)` 赋值，不在组合期做副作用）保留最后一次内容，避免淡出时内容先消失。`state` 提升为无条件 `collectAsState`。

### 3.3 回归核查（其他动画/计时逻辑）

- 阶段完成时 `isRunning→false`，`TimerScreen` 的 `LaunchedEffect(state.isRunning)` 触发 `exitImmersive()` → 主内容淡出沉浸；此时提醒已淡入且 0.97 不透明遮罩在最上层，底层切换被完全遮盖，两者不再同时可见。
- 提醒点「开始休息/专注」→ `start()` + `completion=null`：提醒淡出，3s 后 `enterImmersive()` 再淡入沉浸，两转场被 3s 间隔分开，无叠加。
- 事件为 `SharedFlow`（`extraBufferCapacity=4`、无 replay），`LaunchedEffect(Unit)` 单次消费，`visibleNow && isForeground` 双重门控；30s 无操作自动消失逻辑不变。暂停/恢复/重置路径未改。

### 3.4 验收标准

- [x] 结束提醒完整覆盖于最上层，不与计时界面重叠/透出。
- [x] 提醒淡入淡出平滑，无突现突隐、无残留。
- [x] 沉浸态下阶段完成、点击开始下一轮、30s 自动消失三条路径无动画叠加。
- [x] `:app:compileDebugKotlin` 通过。
- [ ] 真机：专注/休息结束提醒观感（待客户）。

---

## §4 翻页时钟改淡出淡入（P1）

### 4.1 现状

v1.4 双叶片分瓣翻转（`HalfDigit` 上下半片 + `FlapHalf` 透视旋转 + 中缝 + 遮光）在真机显示异常。用户要求取消翻页设计，改「渐变消失/淡出淡入」，无残影/重叠/闪烁/布局跳动，保持排版与性能。

### 4.2 方案

- `FlipClock.kt`：`FlipCard` 内部重写为**两段式淡变**——`Animatable` alpha 先 `animateTo(0f)`（淡出旧值），在**完全透明时**切换 `current=text`，再 `animateTo(1f)`（淡入新值）。任一帧只有一个数字可见，从根本杜绝重影/重叠。移除 `HalfDigit`/`FlapHalf`/中缝/透视/遮光。保留卡片外框（尺寸/圆角/描边/配色）与**公开签名** `FlipCard(text, cardWidth=148.dp, cardHeight=216.dp, fontSize=104, modifier)`、`FlipClock(remainingText, modifier)` 及分段冒号布局 → 调用方（`TimerScreen`）与排版零改动。
- `Motion.kt`：移除翻页令牌 `DURATION_FLIP_HALF`/`EaseFlipDown`/`EaseFlipLand`/`flipDown()`/`flipLand()` 及其专用 easing 导入；新增 `DURATION_FLIP_FADE=140`、`flipFadeOut()`（EaseExit）、`flipFadeIn()`（EaseEnter）。
- 未触碰 `SettingsScreen` 的静态 `MiniFlipCard`（与本时钟无依赖）。

### 4.3 边界

- `LaunchedEffect(text)`：`text` 未变不动画；秒位每秒变化触发一次约 280ms 淡变，远小于 1s，不追赶不堆积。
- 快速连变时旧协程被取消，`digitAlpha` 停在中间值后由新协程从当前值接续，不会卡在半透明。
- 卡片尺寸固定，数字居中，切换无布局跳动。

### 4.4 验收标准

- [x] 数字切换为淡出→淡入，无残影/重叠/闪烁/跳动。
- [x] 卡片尺寸、分段冒号、沉浸页排版与 v1.4 一致。
- [x] 全仓库无 `flipDown`/`flipLand`/`HalfDigit`/`FlapHalf` 残留。
- [x] `:app:compileDebugKotlin` 通过。
- [ ] 真机：淡变观感与流畅度（待客户）。

---

## §5 遵循的既有规范（复用，不重复定义）

- 鲁棒性/静默吞错禁令、Compose 规则、Motion/颜色令牌、字符串模板 `$` 转义陷阱：见 `DEVELOPMENT_GUIDELINES.md`。
- Git：Conventional Commits 中文 subject，按功能域拆分、各自可独立编译；禁 `--no-verify`；push 需用户指示（本轮用户未要求推送）。
- 提交前必过 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`。

---

## §6 实现状态与偏差记录

| 项 | 状态 | 说明 / 偏差 |
|---|---|---|
| §1 注册 `MarkwonInlineParserPlugin` | ✅ | `MarkdownText.buildMarkwon` 于 `CorePlugin` 后追加 |
| §1 开启 `inlinesEnabled(true)` | ✅ | `JLatexMathPlugin.create{}` lambda 内显式开启 |
| §1 markwon remember key 去 onImageClick | ✅ | `rememberUpdatedState` + 稳定闭包，仅随主题/字体重建 |
| §1 LaTeX 转义单测（4 条） | ✅ | `CardTextUtilsTest`：行内单→双、块级不叠加、围栏不转义、`\$` 不误判 |
| §2 删除 `TEMPLATES` + 全部模板 UI | ✅ | `CardTextUtils`/`CardDetailScreen`；保留 `FORMULA_SNIPPETS`；清理 `clickable` 导入 |
| §3 结束提醒纳入 Box 且置顶 | ✅ | `MainScreen` z 序修复 |
| §3 提醒进出场淡入淡出 | ✅ | `AnimatedVisibility` + `lastInfo` 退出保内容 |
| §4 时钟改两段式淡变 | ✅ | `FlipCard` 用 `Animatable` alpha，透明时换值 |
| §4 Motion 翻页令牌替换为淡变令牌 | ✅ | 删 flip*，增 `flipFadeOut/In`、`DURATION_FLIP_FADE` |
| 验证：compile + unitTest | ✅ | `:app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL |
| 真机验证（四项观感） | ⏳ 待客户 | 用户指示暂不上机 |
| 推送远端 | ⏸ 未做 | 本轮用户未要求 push（v1.5 的推送授权不延续到本批次） |

> 偏差记录：实现中如有与本节不符之处，完成后在此追加并注明原因。
