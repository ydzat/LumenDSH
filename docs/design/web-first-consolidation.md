# Web-first consolidation (0.10.x → 0.11)

Status: design review, 2026-10-27. **Direction corrected**: the product target is
voice-first with the habitat as the default surface and the official WebUI as a
precision console, not the other way round — see
[autonomous companion](autonomous-companion.md). The parallel-implementation
inventory below still stands; its "WebUI main screen" framing is superseded.
It does not reopen milestone evidence.
This document exists because the client changed from "native habitat with a
secondary transcript" to "official DSH WebUI embedded in WebView, with a native
habitat shell". Several modules written for the old shape now exist in parallel
with official pages or with each other.

## 0. 一页摘要

- **原始设想**：语音优先、角色为中心的海拔空间（habitat），对话是次要的；
  Agent、记忆、任务都由同一个 DSH 后端负责，客户端只做呈现与设备能力。
- **当前实现**：官方 WebUI 成了主界面（会话、设置、插件、日程都由官方页面提供），
  原生端仍保留一整套旧形态（原生对话、日程、模型设置、记忆页、分享弹窗等）。
  结果：**两套主页、两套会话、三套日程、两个麦克风入口**，连接状态与设置分类也分叉。
- **结论（已修正）**：不是"删掉 WebUI 回到原生"，也不是"删掉原生全用网页"。默认界面是
  **Habitat + 语音**（角色、峰谷、对话、播报），**WebUI 是按需召唤的精确控制台**
  （会话/设置/插件/日程/审批/长文本）；Native shell 负责设备与生命周期；Companion 负责
  自主活动。凡是官方页面已经做好的不再自制；必须进官方页面的能力用最小插件组实现。
  详见 [autonomous companion](autonomous-companion.md)。
- **行动**：先做一个最小插件组（composer 注入 + 移动端壳 + 状态/存在感），再按清单删除平行实现；
  删除前保留开关与回退路径。

## 1. 原始设想（参考基线）

- `docs/development-plan.md`：voice-first、character-centered habitat，transcript secondary。
- `docs/design/m4-attention.md`：注意力编排（背景工作、用户说话、Agent 说话、阅读记忆、审批）
  是产品核心，不是装饰。
- `docs/architecture.md`：一个 Agent；客户端负责交互与设备；DSH 负责会话/工具/记忆；
  边界清晰、不复制 Agent 循环。
- `docs/silver-wolf-persona.md`：角色形象与声音是可替换呈现，身份与记忆连续。

这些设想没有过期；变的是**呈现载体**：官方 WebUI 提供了远比自研更完整的会话、审批、
设置、插件与日程页面，继续自研那些页面既违背"复用优先"，也造成平行实现。

## 2. 当前实现的真实形态

| 层 | 现在是什么 | 评价 |
| --- | --- | --- |
| 会话与工作台 | `WebChatActivity` 嵌入官方 WebUI（cookie 鉴权、断线重连、崩溃兜底） | 保留，作为唯一对话/设置入口 |
| 首页与存在感 | `MainActivity` + `HabitatView`（角色、峰谷、语音、工具状态） | 保留，但只保留"存在感 + 设备入口" |
| 原生对话 | `MainActivity` 内的 transcript、消息列表、审批、任务控制 | 与 WebUI 重复，应删除 |
| 日程 | 原生 `ScheduledTaskStore` + AlarmManager；官方"自动化任务"；companion 自主活动策略 | 三套，需收敛为两套（用户任务 vs 自主活动） |
| 时段 | tide 插件状态 → `PeriodWatcher` → `PeriodBridge` → 宿主 `/period`；首页读 SharedPreferences；WebUI 胶囊 | 数据源正确，但链路冗长、回退逻辑重复 |
| 语音输入 | 官方实验插件（本地模型在 android-arm64 不可用）；原生浮动麦克风 + `SpeechRecognizer`；原生 VoiceInput | 三套入口，需收敛 |
| 语音输出 | 原生 `CharacterSpeech`（远程 CPUFast）；`termux-tts-speak`（提示音） | 两套用途不同，保留但写清边界 |
| 设备操控 | 本地 adb（`phone.sh`）；app 设备桥（3084）；无障碍；MediaProjection | adb 为主，其余为回退 |
| 自主活动 | companion `PolicyStore` + `host.mjs` | 唯一自主活动策略，保留 |

## 3. 平行实现清单与取舍

证据列指向当前代码/文档；"删除"指删除该实现而非数据或能力。

| # | 平行实现 | 证据 | 决策 |
| --- | --- | --- | --- |
| P1 | 原生对话视图 vs 官方 WebUI 会话 | `MainActivity` transcript/消息/审批；`WebChatActivity` | **删原生对话视图**；审批、任务控制、复制等全部留在官方页面；原生只保留语音发送入口 |
| P2 | 两套"首页"：原生 Habitat vs WebUI 落地页 | 首页显示"本机连接中断"，同一时刻 WebUI 正常 | **Habitat + 语音为默认**，WebUI 为控制台；统一连接状态来源，控制台可一键回语音 |
| P3 | 原生定时任务 vs 官方"自动化任务" vs companion 活动策略 | `ScheduledTaskStore`、`SettingsDrawer` 任务页；官方 automation 页；`policy.mjs` | **删原生定时任务**；用户任务交官方页面，Agent 自主活动交 companion；两类在文案与入口上明确区分 |
| P4 | 时段链路四段式：插件 → Watcher → Bridge 回退 → 宿主 → 首页 | `PeriodWatcher`、`PeriodBridge`、`host.mjs` `/period`、`HabitatView` | **保留插件单源 + 一处回退**；回退必须在 UI 标注"近似（无节假日）"，并删除重复的窗口计算（只留一份） |
| P5 | 语音输入三入口 | 官方实验插件；浮动麦克风；`VoiceInput`/唤醒服务 | **保留系统识别（浮动麦克风）+ 唤醒服务**；官方插件保留但默认关闭（本地模型不可用）；删除 Activity 内重复的识别路径 |
| P6 | 分享三路径 | 原生 `ShareInbox` 弹窗；`WebChatActivity` 注入；adb `input text` | **统一为 composer 插件注入**；原生分享弹窗删除，adb 输入仅作兜底 |
| P7 | 设置两套分类 | 原生抽屉（交互与声音/角色/记忆/模型/系统）vs 官方设置（模型/插件/工作区等） | **原生只留 app-only**：权限、语音提供商、宠物/角色、更新与配对；其余全部官方 |
| P8 | 记忆/模型/会话管理页 | 原生 `showMemory`、`showModelSettings`、`settingsSessions` | **删除**；官方已有对应页面，不再复制 |
| P9 | 设备桥三入口 | app bridge（3084）、`phone.sh`、`bridgecall.mjs`/`screenctl.mjs` | 保留 app bridge（仅 app 能力）+ adb（通用）；`screenctl` 合并进 `bridgecall` |
| P10 | 截屏/输入双通道 | MediaProjection + 无障碍；adb screencap/input | adb 主；a11y/MediaProjection 仅作未连线时的回退，UI 明示当前通道 |
| P11 | 状态展示两处 | 原生首页状态行；WebUI 顶部胶囊/连接态 | **单一状态源**（宿主 `/status` + 插件状态），两处渲染同一份数据 |
| P12 | 通知与提示音 | `CompletionService` 通知；`tone.sh`；`termux-notification` | 保留各自用途但统一触发源：Agent 回合结束走 tone；分享/安装等系统事件走通知 |

## 4. 冗余与可删功能（按收益排序）

1. 原生对话 transcript 与消息/审批 UI（P1）——与 WebUI 重复，维护成本最高。
2. 原生定时任务与任务列表（P3）——与官方 automation 重复，且 AlarmManager 行为难验证。
3. 原生模型设置、记忆页、会话管理页（P8）——官方已有且更新更快。
4. 原生分享弹窗（P6）——应由 composer 插件接管。
5. `screenctl.mjs`（P9）——已被 `bridgecall.mjs` 覆盖。
6. 原生 Activity 内的语音识别副本（P5）——保留服务与浮动入口即可。

## 5. 需要出现在官方页面里的东西：自制插件组

官方页面没有"角色/存在感"，也没有把外部文本/语音安全注入 composer 的稳定接口。
建议只做 **一个小插件组 `@lumendsh/webui`（三个包，可按需启用）**：

| 插件 | 职责 | 为什么必须自制 |
| --- | --- | --- |
| `webui-composer` | 向 composer 注入文本（分享、语音转写），可选直接发送；提供稳定事件/命令 | JS 注入 React 受控组件不可靠；这是唯一需要官方页面内部 API 的事 |
| `webui-shell` | 移动端安全区、返回键、顶部状态（连接/时段/槽位）、快捷入口 | 官方页面是桌面布局；社区 mobile-drawer 要求 Node 26，不兼容 |
| `webui-presence` | 在官方页面角落显示角色/峰谷的轻量存在感（可选，默认关） | 原生产品核心是角色；WebUI 里完全没有 |

复用优先：tide 胶囊、STS、官方设置/日程/审批/文件页面都不自制；`webui-shell` 只做
官方页面缺失的移动适配与状态展示，不复制官方导航。

## 6. 从手机视角看到的设计问题

在真机上按用户路径走了一遍（原生首页 → 官方界面 → 设置 → 语音）：

1. **两个主页两套状态**：原生首页写"本机连接中断"，WebUI 同时正常。用户无法判断哪个是真的。
2. **胶囊重叠**：官方页面顶部"梁文谷 · 6:56:59"与"今日 ¥0.02 / 设置图标"叠在一起，违反层级与避让。
3. **两个麦克风**：官方 composer 的插件麦克风 + 原生浮动麦克风，功能重叠、状态不一致。
4. **设置分类分叉**：原生"交互与声音"与官方设置里的语音/模型设置语义重叠，用户不知道该去哪。
5. **角色缺席**：进入 WebUI 后角色完全消失，与原设计"角色为中心"冲突；需要 presence 或明确的模式切换。
6. **返回语义混乱**：悬浮"⌂"、系统返回、侧栏汉堡三套导航都改变上下文，缺少一致的层级模型。
7. **日程心智模型**：用户任务、Agent 自主活动、时段显示是三个概念，但散落在三处。

## 7. 迁移顺序（可回退）

1. **P0 插件组脚手架**：建立 `@lumendsh/webui` 三个包的最小骨架，先只启用 `webui-composer`，
   让分享/语音走注入；保留现有路径作开关回退。控制台是召唤式，不抢默认入口。
2. **P1 状态统一**：宿主 `/status` 增加 `surface`（web/habitat）与 `channel`（adb/a11y）字段；
   原生首页与 WebUI 顶部读同一份数据。
3. **P2 删除平行 UI**：按第 4 节顺序删原生对话、定时任务、模型/记忆/会话页、分享弹窗；
   每删一项先禁用（开关）观察一个版本，再移除代码。
4. **P3 存在感**：Habitat 改为角色空间，WebUI 可选 presence 插件；返回键模型固定为
   "WebUI ← 角色空间 ← 退出"。
5. **P4 文档与验收**：更新 `architecture.md` 责任表、`project-status.md`、M10 结项记录；
   为删除项补回归证据。

## 8. 待用户确认

- 是否接受 "WebUI 为主屏、Habitat 为角色空间" 的方向（而不是反过来）。
- 原生对话删除后，语音消息是否允许直接发送（不经 composer 确认）。
- 是否需要 `webui-presence`（在官方页面留一个轻量角色/状态）。
- 原生定时任务删除前，是否需要把现有任务导入官方 automation。

## 9. 参考项目

- DSH 官方 WebUI 与客户端插件机制（官方设置/日程/审批/插件页）：作为默认实现，不复制。
- deepseek-harness-android（`docs/reuse-audit.md` pinned revision）：角色/悬浮/任务/通知的原生做法，
  仍可借鉴其交互细节；其会话与后端绑定方式不再适用。
- awesome-dsh 社区插件：语音、日程、移动端适配均已评估；能用的直接用，不能用的记录不兼容原因。
