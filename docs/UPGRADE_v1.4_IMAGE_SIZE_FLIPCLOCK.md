# TomaTodo v1.4 升级需求文档 —— 图片尺寸调整 · 沉浸翻页时钟重构

> 状态：实现完成，待真机验证
> 前置：v1.2 KMS 文件化已上线；v1.3 图片嵌入修复（选图/拍照后无响应）已交付并真机验证通过。
> 本轮由用户两条反馈驱动：
> 1. 「图片嵌入搞定了，但现在无法调整大小」→ 嵌入图片只能满宽渲染，需要可配置的显示尺寸。
> 2. 「顺便重构一下沉浸态翻页时钟的效果，之前的翻页时钟效果不理想」→ 现翻页钟动效生硬、无立体感。

---

## §1 图片嵌入尺寸调整（P0）

### 1.1 现状与根因

- 图片以卡片内相对引用存储：`![附图](assets/xxx.jpg)`；`CardRepository.insertImage` 压缩（长边 1600px / JPEG 85）后落盘，返回 `assets/<uuid>.jpg`。
- 渲染走 `MarkdownText`（Markwon）→ 自研 `KmsImagePlugin`（Coil 2）。加载后调用
  `DrawableUtils.applyIntrinsicBoundsIfEmpty(loaded)`，即**按图片原始内建尺寸渲染**，仅受 `AsyncDrawable` 超宽兜底缩放影响——用户无法控制大小。
- **关键发现**：Markwon core 自带完整尺寸解析链——`ImageSpanFactory` 构造 `AsyncDrawable` 时读取
  `ImageProps.IMAGE_SIZE`，交由 `ImageSizeResolverDef.resolveImageSize()` 计算边界：
  - `ImageSize == null` → 超宽则等比缩到画布宽，否则原尺寸（当前行为）；
  - `width = N%` → 目标宽 = 画布宽 × N/100，高度按原图宽高比推导；
  - `MarkwonConfiguration.Builder.build()` 默认注入 `ImageSizeResolverDef`（已反编译核实）。
  该机制平时只被官方 HTML 插件填充，纯 Markdown 图片恒为 null。**我们自持 `Image` span 工厂，可在解析期自行填充，无需新增依赖。**

### 1.2 方案

**尺寸令牌（Markdown 内、可手写可 UI 写入）**：在图片引用的 URL fragment 上携带宽度百分比，语法 `#w=<整数>`，语义 = 画布宽度的百分比。

```
![](assets/xxx.jpg)          → 满宽（默认，现状不变）
![](assets/xxx.jpg#w=50)     → 50% 宽，等比高
![](assets/xxx.jpg#w=25)     → 25% 宽
```

规则：
- 仅当 fragment 匹配 **`w=1..100` 的整数**时才被识别为尺寸令牌；其余一律视为普通路径的一部分，零误伤（选 `#w=` 而非裸数字，避免与真实 URL fragment 冲突）。
- 尺寸令牌只影响渲染显示，**不改变物理文件与相对引用**；去除令牌即恢复满宽。
- 100 与缺省等价（满宽），但允许显式写出便于 UI 回读。

**实现链路**：

| 环节 | 改动 |
|---|---|
| `CardTextUtils` | 新增纯函数：`splitImageSize(target)`（剥离并解析令牌）、`withImageSize(target, percent)`（写入/替换/移除令牌）、`imageTargets(md)`（按序抽取全部图片引用）、`SIZE_PRESETS`。便于单测。 |
| `prepareForRender` | 重写相对路径为绝对路径时**保留** `#<令牌>`（现有正则 `([^)]+)` 会整体吞掉，需拆分路径与 fragment 再拼接）。 |
| `KmsImagePlugin` | `configureSpansFactory` 中：解析 `DESTINATION` → 拆出干净路径与百分比 → `props.set(DESTINATION, 干净路径)` + `props.set(IMAGE_SIZE, ImageSize(Dimension(pct,"%"), null))` → 委托 `ImageSpanFactory`。点击回调回传干净路径。 |
| 点击/查看器 | `CardDetailScreen` / `ReviewScreen` 的 `onImageClick` 现以「相对引用 indexOf」匹配，绝对路径恒不相等（潜在多图集索引错误）。改为**按绝对路径匹配**，且先剥离尺寸令牌再解析文件。 |

**编辑端交互**（让「调整大小」可发现、可操作）：
- 编辑工具栏新增「图片尺寸」入口（`DropdownMenu`），预设 25% / 50% / 75% / 100%。
- 选择后，对**光标所在行的图片引用**应用该百分比（`applyImageSizeAtCursor`，纯 `TextFieldValue` 函数）；光标不在任何图片上时 `Snackbar` 提示「请把光标放到要调整的图片所在行」。
- 插入图片（相册/拍照）默认满宽，不携带令牌，行为与现状一致。

### 1.3 验收标准

- [ ] `![](assets/x.jpg#w=50)` 在阅读/复习/编辑预览三处均渲染为约一半宽、等比高、不拉伸。
- [ ] 无令牌图片仍满宽，观感与 v1.3 一致（回归）。
- [ ] 工具栏选 50% 后，光标所在图片引用被改写为 `#w=50`，1.5s 防抖保存后重进详情仍生效。
- [ ] 多张图片卡片，点击任意一张，全屏查看器定位到**该张**（修复既有索引偏差）。
- [x] `CardTextUtils` 新增纯函数有单测覆盖（含非法/越界/无令牌分支）。
- [x] `:app:compileDebugKotlin :app:testDebugUnitTest` 通过。

---

## §2 沉浸态翻页时钟重构（P0）

### 2.1 现状问题（现 `timer/FlipClock.kt`）

1. **单叶片整翻**：上半片绕中轴从 0° 整翻到 -180°，中途换内容 + 镜像抵消。视觉上像「整张卡片对折」，而非真实分页钟的「上页落下、下页落位」。
2. **生硬收尾**：`shown` 在动画末帧一次性更新并 `snapTo(0)`，新值「凭空跳出」，无过渡。
3. **无立体感**：无透视（未设 `cameraDistance`）、无翻页明暗、无中轴凹槽细节，整体扁平。
4. **结构脆弱**：`FlipClock` 用 `index == digits.length - 3` 猜冒号位置，`H:MM:SS`（6 位）会错位。

### 2.2 重构方案（双叶片分页）

将每张数字卡拆为 **静态底层 + 两个翻页叶片**，模拟真实 Solari 分页钟的两段运动：

- **静态顶层**：显示新值上半（始终垫底，被上叶片遮盖 → 上叶片翻走后露出）。
- **静态底层**：显示旧值下半（被下叶片最终覆盖）。
- **上叶片（旧值上半）**：`rotationX 0° → -90°`，绕自身底边（卡片中轴）向观者翻落，露出新值上半。仅在阶段一可见。
- **下叶片（新值下半）**：`rotationX +90° → 0°`，绕自身顶边（卡片中轴）由竖直落位，盖住旧值下半。仅在阶段二可见。
- 两段各用独立缓动：阶段一 `Accelerate`（被释放下坠），阶段二 `Decelerate`（落位减速），时长各走 `Motion` 令牌。
- 动画完成后 `previous = current`、进度归零；叶片与静态层同位同尺寸，交接帧无跳变。

立体感增强：
- `graphicsLayer { cameraDistance }` 引入透视（远离默认值，形变更自然）。
- 翻页叶片叠加随翻折角度变化的明暗遮罩（翻起变暗、落位恢复），强化体积感。
- 保留中轴凹槽细线与圆角、哑光黑卡面（沿用现有配色，符合「墨·纸」暗场景）。

结构修复：
- `FlipClock` 改为按 `:` 分段渲染：每段一组 `FlipCard`，段间插 `FlipColon`，天然兼容 `MM:SS` 与 `H:MM:SS`。
- 动效时长/曲线取自 `ui/theme/Motion.kt`，不写 magic number（遵循开发规范）。

### 2.3 验收标准

- [ ] 秒位每秒翻页一次：上页落下露新值、下页落位盖旧值，两段连贯无跳帧。
- [ ] 翻页具透视与明暗变化，非平面翻转。
- [ ] `H:MM:SS` 与 `MM:SS` 冒号位置均正确。
- [ ] 暂停/继续、跳过阶段后时钟数值与翻页状态一致，无残影。
- [x] 时长/曲线走 `Motion` 令牌；`:app:compileDebugKotlin` 通过。
- [ ] （真机）沉浸黑底观感自然，无明显锯齿/闪烁。

---

## §3 遵循的既有规范（复用，不重复定义）

- 鲁棒性：所有解析（尺寸令牌、分段）对非法输入安全降级，不抛异常；遵循 `DEVELOPMENT_GUIDELINES §3` 禁止静默吞错。
- Compose：组合期无副作用，`remember`/`LaunchedEffect` 带 key；颜色/圆角/动效走令牌。
- Git：Conventional Commits 中文 subject，按功能域拆分、各自可独立编译；禁 `--no-verify`；push 需用户指示。
- 提交前必过 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`。

---

## §4 实现状态与偏差记录

| 项 | 状态 | 说明 / 偏差 |
|---|---|---|
| §1 `CardTextUtils` 尺寸令牌纯函数 + 单测 | ✅ | `splitImageSize`/`withImageSize`/`imageTargets`/`SIZE_PRESETS`；8 条单测通过 |
| §1 `prepareForRender` 保留 fragment | ✅ | `rewriteLine` 图片分支先剥离令牌、绝对化路径后拼回 |
| §1 `KmsImagePlugin` 注入 IMAGE_SIZE | ✅ | 解析 `#w=NN` → `props.set(IMAGE_SIZE, ImageSize(Dimension(pct,"%"), null))`，复用内置 `ImageSizeResolverDef` |
| §1 查看器索引修复（详情 + 复习） | ✅ | 改为按绝对路径匹配 + 先剥离令牌；顺带修复恒开第一张的既有偏差 |
| §1 编辑工具栏「图片尺寸」交互 | ✅ | 预设 25/50/75/100；`applyImageSizeAtCursor` 纯函数，光标无图时 Snackbar 提示 |
| §2 双叶片翻页卡重构 | ✅ | 静态页 + 上瓣 0→-90° / 下瓣 +90°→0°，透视 + 随角度遮光 |
| §2 `FlipClock` 分段渲染修复 | ✅ | 按 `:` 分段、段间冒号，`H:MM:SS` 正确 |
| 验证：compile + unitTest | ✅ | `:app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL |
| 真机验证（图片三尺寸 / 翻页观感） | ⏳ 待客户 | 需华为平板 |

> 偏差记录：
> 1. 令牌语法定稿为 `#w=NN`（早期草稿曾写裸数字 `#NN`），避免与真实 URL fragment 冲突；正文已同步。
> 2. `cameraDistance`：Compose `graphicsLayer` 的该参数单位是「图层最长边的倍数」（默认 8 倍 ≈ 几乎无透视），并非像素距离。实现采用 3 倍，获得适度纵深，与方案意图一致。
