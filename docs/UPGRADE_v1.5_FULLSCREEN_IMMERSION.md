# TomaTodo v1.5 升级需求文档 —— 全局全屏 · 沉浸态真全屏 · 计时防睡眠

> 状态：实现完成，待真机验证
> 前置：v1.4 图片尺寸令牌 + 双叶片翻页钟已交付（待真机验证）。
> 本轮由用户两条反馈驱动：
> 1. 「解决目前应用不全屏的问题，通知栏条始终显示在 app 界面上侧」→ 应用应始终全屏，状态栏不该常驻。
> 2. 「目前的沉浸态都没有覆盖全屏；阻止睡眠状态我还没检查，你检查一下工作是否做到位」→ 沉浸态未真全屏 + 防睡眠工作待核查补齐。

---

## §1 全局全屏：隐藏状态栏/导航栏（P0）

### 1.1 现状与根因

- `MainActivity` 只调用了 `enableEdgeToEdge()`：它仅让**内容延伸到系统栏后面**（边到边），并**不隐藏**系统栏本身 → 状态栏始终覆盖在界面上侧。
- `MainScreen` 外层 `AnimatedContent` 施加 `safeDrawingPadding()`，内容又被推回安全区内，进一步坐实「上侧留一条通知栏」。
- 平板是纯学习场景，无通知栏诉求；应整应用隐藏系统栏，需要时从屏幕边缘轻扫临时唤出（自动隐藏）。

### 1.2 方案

`MainActivity` 在 `onCreate`（edge-to-edge 之后）与 `onResume`（从设置/权限页等其他界面返回后恢复）统一执行：

```
WindowInsetsControllerCompat(window, decorView).apply {
    hide(WindowInsetsCompat.Type.systemBars())          // 状态栏 + 导航栏
    systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
```

- `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`：边缘轻扫临时显示系统栏，数秒无操作自动隐藏，不破坏全屏。
- `safeDrawingPadding()` 保留：系统栏隐藏时 inset 为 0 无副作用；临时唤出期间内容不被遮挡。

### 1.3 验收标准

- [ ] 全部页面（看板/番茄/复习/卡片/统计/设置/详情/回收站）不再显示状态栏与导航栏。
- [ ] 从屏幕边缘轻扫可临时唤出系统栏，随后自动隐藏。
- [ ] 弹出对话框（权限引导/图片查看器）、键盘输入后回到应用仍是全屏。
- [ ] `:app:compileDebugKotlin` 通过。

---

## §2 沉浸态真全屏（P0，结构性 bug 修复）

### 2.1 现状与根因

现状：`TimerScreen` 内部以本地 `remember` 维护 `immersive`，经 `onImmersiveChanged` 回调抬升到 `MainScreen` 的 `timerImmersive`（控制导航栏是否渲染）；`TimerScreen` 再用 `DisposableEffect` 自行隐藏系统栏。

问题链：
1. `timerImmersive` 变化 → `MainScreen` 外层 `AnimatedContent` 目标变化 → **内容子树重建** → `TimerScreen` 的本地 `immersive` 状态丢失（回到 `false`）。
2. 新组合的 `LaunchedEffect(immersive)` 立即回调 `onImmersiveChanged(false)` → `timerImmersive` 弹回 → 转场反向 → **震荡**。
3. `TimerScreen` 的系统栏 hide 与重建副本的 show/onDispose 竞态 → 沉浸态下状态栏时有时无，未能覆盖全屏。

### 2.2 方案：沉浸状态下沉为单一事实源

- `TimerViewModel` 新增 `isImmersive: MutableStateFlow<Boolean>` 与 `enterImmersive()/exitImmersive()`；`reset()` 同时退出沉浸。
- `MainScreen` 直接 `collectAsState()` 该流决定导航栏渲染，**删除回调参数** `onImmersiveChanged`。
- `TimerScreen` 同样收集该流渲染沉浸/普通内容；进入（计时 3s 自动 / 手动按钮）与退出（退出/结束按钮）都写流。
- 移除 `TimerScreen` 内隐藏/恢复系统栏的 `DisposableEffect`：系统栏已由 §1 全局隐藏，无需逐态操作，竞态随之消失。
- 重建不再丢状态：任何一份新组合都从同一个流读到当前沉浸态，转场期间双向一致。

### 2.3 验收标准

- [ ] 计时开始 3s 自动进入沉浸态：整屏无状态栏、无导航栏、无侧边导航。
- [ ] 进入/退出沉浸无闪烁、无状态栏残影。
- [ ] 暂停自动退出沉浸；「结束计时」重置并退出沉浸；手动「退出沉浸」仅退出。
- [ ] 沉浸态轻触唤出控制层、3s 自动隐藏不受影响。
- [ ] `:app:compileDebugKotlin` 通过。

---

## §3 计时防睡眠（P0，核查结论：未实现，需补齐）

### 3.1 核查结论

- 全仓库无 `KEEP_SCREEN_ON` / `keepScreenOn` / `WakeLock` / `userActivity` 任何实现 → **防睡眠此前完全未做**，计时中屏幕会随系统超时息屏。
- 计时精度本身无虞：`TimerController` 以 wall-clock `endAt` 为基准（`endAt - System.currentTimeMillis()`），息屏/锁屏/切后台不丢精度（既有机制，本轮不改）。

### 3.2 方案

- `MainActivity` 在 `STARTED` 生命周期内收集 `TimerController.state`：
  - `isRunning == true` → `window.addFlags(FLAG_KEEP_SCREEN_ON)`（前台亮屏不自动息屏）；
  - `isRunning == false` → `clearFlags`（暂停/结束即归还，避免空转耗电）。
- 使用 `repeatOnLifecycle(STARTED)`：应用退后台自动停止维持，回到前台按当前运行状态恢复；悬浮窗后台计时场景不强制亮屏（由前台服务 + wall-clock 基准保证计时正确，结束有高优先级通知/全屏意图提醒）。

### 3.3 验收标准

- [ ] 计时运行中（含沉浸态）屏幕不随系统超时息屏。
- [ ] 暂停/重置后恢复系统默认息屏策略。
- [ ] 切后台再回前台，运行中仍保持亮屏。
- [ ] `:app:compileDebugKotlin` 通过；（真机）长时间专注实测不息屏。

---

## §4 遵循的既有规范（复用，不重复定义）

- 鲁棒性/静默吞错禁令、Compose 规则、Motion/颜色令牌：见 `DEVELOPMENT_GUIDELINES.md`。
- Git：Conventional Commits 中文 subject，按功能域拆分、各自可独立编译；禁 `--no-verify`。
- 提交前必过 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`。

---

## §5 实现状态与偏差记录

| 项 | 状态 | 说明 / 偏差 |
|---|---|---|
| §1 全局隐藏系统栏（onCreate + onResume） | ✅ | `hideSystemBars()` + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`；`safeDrawingPadding` 保留（隐藏时 inset=0） |
| §2 沉浸态单一事实源（ViewModel 流） | ✅ | `TimerViewModel.isImmersive` + `enter/exitImmersive`；`reset()` 同步退出沉浸 |
| §2 移除回调/竞态路径 | ✅ | 删除 `onImmersiveChanged` 回调与系统栏 `DisposableEffect`；`MainScreen`/`TimerScreen` 各自收集同一流 |
| §3 KEEP_SCREEN_ON 跟随计时运行态 | ✅ | `MainActivity.keepScreenOnWhileTiming()`：`repeatOnLifecycle(STARTED)` 收集 `TimerController.state` 增/清标志 |
| 验证：compile + unitTest | ✅ | `:app:compileDebugKotlin :app:testDebugUnitTest` BUILD SUCCESSFUL |
| 真机验证（全屏/沉浸/防息屏） | ⏳ 待客户 | 需华为平板 |

> 偏差记录：实现中如有与本节不符之处，完成后在此追加并注明原因。
