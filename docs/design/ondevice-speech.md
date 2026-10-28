# 本地语音识别模块（嵌入 app / host 插件，含声纹自学习）

Status: 2026-10-29 方案。目标来自用户：识别模块必须**嵌入我们自己的程序（或插件）**、**本地运行**，
最好能**根据用户的声纹逐渐提升**（不强制）。

## 1. 结论先行

识别引擎放 **host 侧插件**（跑在 Termux 里的 DSH host），用官方 sherpa-onnx 的 **Android/Termux
原生运行库**；app 只负责录音、把 PCM 推到本地回环、把识别结果放回输入框。这样：

- 纯本地：音频不出手机，模型不进 APK（APK 不膨胀几十 MB）；
- 一个引擎同时服务日常口述、文件转写、会议旁听和 Agent 自己的转写工具；
- 声纹注册/自学习数据集中在一处，方便迭代和备份；
- 以后即使换成 app 内嵌（AAR），接口不变。

**已经实测**：sherpa-onnx v2.0.1 官方 `android-aarch64-termux-shared` 包直接在本机 Termux 跑起来
（`sherpa-onnx-version` → 2.0.1 / onnxruntime 1.28.2），自带 `sherpa-onnx-offline-websocket-server`、
`-vad-with-offline-asr`、`-keyword-spotter`、punctuation、speaker-diarization 等二进制。

## 2. 为什么是 sherpa-onnx（而不是自己搭 Python）

| 选项 | 结论 |
|---|---|
| **sherpa-onnx 官方 Android/Termux 二进制** | ✅ 直接可用（本轮已验证）；同一套 C API 也能编成 app 的 AAR（官方发布 `sherpa-onnx-2.0.1.aar`，49 MB）；覆盖 ASR / VAD / 标点 / 声纹 / KWS |
| Python + FunASR / sherpa-onnx wheel | Termux 上装 wheels 困难（无 glibc、需编译），维护成本高，弃 |
| DSH 官方 voice bundle（sherpa-onnx-node） | npm 只发 linux-x64/arm64(glibc)/win/darwin，Android bionic 加载不了，`node_modules` 里也没有平台包；不可用 |
| Marmalade STT（第三方 app） | 能用但不是我们的代码，无法做声纹自学习；只作对照基准 |

## 3. 模型选型

| 角色 | 模型 | 体积 | 说明 |
|---|---|---|---|
| 主力 ASR | **SenseVoice-Small int8** | ~230 MB（发布包 1 GB，含 fp32） | zh/yue/en/ja/ko，自带标点与数字规整；中文明显强于同尺寸 Whisper；[社区插件](https://github.com/rootryanlau-art/dsh-voice-transcribe)实测 RTF 0.033、样句 CER 0 |
| 备选 ASR | Paraformer-zh small int8 | ~78 MB | 更省内存，速度快，适合低端/热路径；热词（hotword）原生支持 |
| VAD | Silero VAD | ~2 MB | 断句、去静音；省电也提高识别质量 |
| 结束判定 | Smart Turn v3（可选） | ~9 MB | 「说完没有」的判断，减少抢话/截断 |
| 唤醒 | sherpa-onnx KWS（自定义「银狼」） | ~10 MB | 比现在用整句 ASR 常驻省电、误唤醒更低 |
| 声纹 | CAM++ / 3D-Speaker embedding | ~7 MB | 注册一次，用于本人门控与自学习数据筛选 |

模型分发：不进 APK，首次使用时下载（带 SHA-256 固定校验，支持镜像），落在
`~/.local/share/sliver-wolf/speech/models/`，与 DSH 官方 voice bundle 的做法一致。

## 4. 架构

```
┌────────────── Android app ──────────────┐        ┌──────── Termux: DSH host ────────┐
│ VoiceInput / WebChatActivity.startVoice │        │ speech 插件（host 侧）            │
│   录音 16k mono PCM，VAD 前置已可省      │  WS    │  · sherpa-onnx 引擎（进程内/子进程）│
│   ── binary frames ────────────────────►│◄──────►│  · VAD/断句、标点、数字规整        │
│   文本/部分结果 ◄──────────────────────│        │  · 声纹 embedding + 个人词典       │
│   （复用现有本地回环 WS，无新端口）      │        │  · 自学习样本库（本地加密）        │
└─────────────────────────────────────────┘        │  · 工具：speech_transcribe(file)   │
                                                   └──────────────────────────────────┘
```

- **传输**：host 插件注册一条 WS 路由（现有 `ctx.webServer`），app 复用已有本地回环连接；
  语音帧走二进制，文本结果走 JSON。不新增对外端口。
- **降级**：host 不可用时 app 落到系统 `SpeechRecognizer`（现状），保证可用性。
- **不替换唤醒**：唤醒链路先保持现状（已验证可用），模块先接管「唤醒后那一句」和手动麦克风；
  KWS 作为后续优化，不在这里冒险。

## 5. 声纹自适应（用户的可选项）

三层，逐层上线，默认关闭到开启都可由用户开关：

### L1 · 声纹门控（最有把握，几乎零风险）
1. 注册：跟读 3 句提示音，约 40 秒，算 CAM++ embedding 存本地。
2. 每句话算 embedding，与注册向量余弦相似度 ≥ 阈值判为本人。
3. 用途：只对本人语音做自学习；唤醒词只认本人（可选）；多说话人场景不污染词典。

### L2 · 文本自学习（收益最快，纯本地、可解释）
1. 记录每次「ASR 原文 → 用户最终发送/修改后的文本」的差异对。
2. 用拼音/编辑距离对齐，挖出稳定替换对（≥2 次确认才自动生效），存个人词典：
   `词典 = {错词: 正词, 拼音, 次数, 最后使用}`。
3. 识别后先过词典（确定性替换），再可选走一次纠错（术语表 + 近几轮上下文）。
4. 效果可量化：词典命中率、用户二次修改率下降。

### L3 · 声学适配（真·声纹提升，最后做，收益有限但真实）
1. 积累本人「音频 + 最终文本」对（相似度过滤、去重、只留长句），目标 1–2 小时。
2. 在服务器（8 核 15 GB，无 GPU）用 FunASR 官方 recipe 做 LoRA/部分层微调 SenseVoice-Small，
   冻结编码器主体，只训练小适配器；训练在 CPU 上跑夜间任务。
3. 导出 ONNX int8，在手机固定测试集上验证 CER 必须**严格下降**才发布，否则回滚。
4. 手机端下载「声纹适配包」（几 MB），加载在基础模型之上；用户可随时删除。

> 诚实预期：L1/L2 通常带来用户可感知的提升（专有名词、数字、口头习惯）；
> L3 在只有个人数据时，CER 相对下降大约 5–15%，不是魔法，所以放在最后、由指标把关。

## 6. 分期与验收

| 阶段 | 内容 | 验收（真实任务/证据） |
|---|---|---|
| P0（1–2 天） | 插件骨架：Termux 二进制 + 模型下载校验 + `speech_transcribe(file)` 工具 + 30 句测试集与 CER/RTF 基线 | 手机 CPU 上 RTF ≤0.2；与现用 Google 识别同集对比 CER 有数字 |
| P1 | 流式 ASR 服务 + app 端录音接入（替换唤醒后那一句） | 真实口述：延迟 ≤1.2 s/句、断句不出错；降级可用 |
| P2 | 个人词典（L2）+ 标点/数字规整 + 领域词表（银狼、LumenDSH、ZeroTier、GPT-SoVITS…） | 词典命中与二次修改率下降的日志 |
| P3 | 声纹注册与门控（L1）+ 多说话人过滤 | 本人/非本人相似度分布与误判率 |
| P4（可选） | 声学适配（L3）+ 适配包下载/回滚 | 固定测试集 CER 严格下降才发布 |

## 7. 风险与约束

- **内存**：手机可用约 1.5 GB；SenseVoice int8 常驻约 400–600 MB，必须按需加载（说完即释放或 5 分钟空闲卸载），并放在独立进程/服务里，避免拖垮 WebView。
- **电量**：常驻 ASR 不划算；默认只在「唤醒后 / 按下麦克风」时开引擎，KWS 常驻另评估。
- **模型许可**：SenseVoice / Paraformer 为 Apache-2.0（FunAudioLLM），再分发需保留 NOTICE；CAM++ 权重 Apache-2.0。打包前逐项核对。
- **兼容**：Android 10 (SDK 29)、arm64、无 root；官方 Termux 包正是 aarch64。
- **隐私**：音频与样本默认只在本机；导出训练集是显式动作，带加密与删除入口。
- **不阻塞**：识别引擎故障时自动回落系统识别，不改变现有交互。

## 7.5 本机实测（2026-10-29，真机）

| 项 | 结果 |
|---|---|
| 运行时 | sherpa-onnx v2.0.1 官方 `android-aarch64-termux-shared`，在 Termux 直接运行（onnxruntime 1.28.2） |
| 测试模型 | Paraformer-zh small int8（78 MB） |
| 识别质量（自带 test_wavs） | 中文句子全对：`重点呢想谈三个问题首先呢就是这一轮全球金融动荡的表现`；中英混说也对（`yesterday was 星期一 today is tuesday 明天是星期三`）；**无标点**（需再加标点模型） |
| 推理速度 | RTF **0.30**（4 线程，10.8 s 音频 ≈ 3 s）；一句 6 s 的话约 1.8 s |
| 模型加载 | 约 **8.5 s**（110 MB 读盘 + ORT 会话创建）→ 必须常驻进程，不能每句冷启动 |
| 内存 | 峰值 RSS **约 130 MB**（Paraformer-small；SenseVoice 预计 400–600 MB） |

结论：本地跑得动、准头可用，瓶颈是「冷启动 8.5 s」和「常驻内存」，因此引擎必须是驻留服务而不是一次性命令。

## 8. 需要你拍板的三点

1. 引擎放 **host 插件**（推荐，文件转写/Agent 工具也能共用）还是 **app 内嵌**（离线独立、但 APK 变大）？
2. SenseVoice 首次下载约 230 MB（模型包 1 GB，只留 int8），可以接受吗？还是先用 78 MB 的 Paraformer？
3. 声纹注册流程（跟读 3 句、约 40 秒）是否可以接受？默认关闭、你点头再开。
