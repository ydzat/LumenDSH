# 本地语音识别模块（app 内识别 + host 插件分发 + 服务器训练）

Status: 2026-10-29 方案 v2。用户已确认：引擎本地、嵌入我们自己的程序/插件；声纹自适应做成
服务器训练服务，手机只负责应用；声纹注册已存在，直接复用。

## 1. 结论

- **识别引擎放 app 内部**。核对代码发现 app 早已带 sherpa-onnx 的 Android JNI 运行时
  （`~/sliver-wolf-build/deps/wake-1.13.8`：`java.jar` + `jni/arm64-v8a/libsherpa-onnx-jni.so`），
  并且其中的 `OfflineRecognizer` / `OfflineSenseVoiceModelConfig` / `Vad` /
  `SpeakerEmbeddingExtractor` / `KeywordSpotter` 全都有——ASR 不需要引入任何新依赖。
- **host 插件 `integrations/speech`** 负责「只该存在一份」的东西：运行时与模型文件的下载/校验、
  给 app 的低回环取模型路由、文件转写工具（Agent 用）、个人词典与样本库同步、训练服务客户端。
- **服务器**提供训练服务：收样本 → 微调 → 导出 ONNX → 用固定测试集做准入门禁 → 发布新模型版本；
  手机端只做下载与应用/回滚。
- **声纹复用现有注册**：`OwnerEnrollment` 已经录过（`owner-voice.bin` / `owner-wake.bin`），
  用 CAM++（`speaker.onnx`）+ 本地 `liblinear/libsvm` 适配器做本人判定，不需要再录一遍，
  也不需要再问用户。

## 2. 已完成的可行性验证（真机，2026-10-29）

- 官方 `sherpa-onnx-v2.0.1-android-aarch64-termux-shared` 在本机 Termux 直接运行
  （onnxruntime 1.28.2），自带 `sherpa-onnx-offline`、`-vad-with-offline-asr`、
  `-keyword-spotter`、`-offline-punctuation`、`-offline-speaker-diarization` 等。
- 已落盘：`~/.local/share/sliver-wolf/speech/runtime`（16 MB 运行包）与三套模型
  （SenseVoice int8 230 MB / Paraformer int8 80 MB / 标点 int8 77 MB），fp32 与导出脚本已删。
- 线程数很关键：这台机器 2 线程最快，4/8 线程反而更慢（大小核争用 + 降频）。
- **SenseVoice-Small int8**（229 MB）：中文带标点与 ITN，输出
  `重点呢想谈三个问题。首先呢就是这一轮全球金融动荡的表现。`；**RTF 0.32**（2 线程，6 s 语音约 1.9 s）、
  峰值 RSS **289 MB**、冷加载约 **8.5 s**。
- **Paraformer-zh small int8**（78 MB）：**RTF 0.11**（2 线程）、RSS 约 130 MB、无标点；
  搭配 CT-Transformer 标点模型（int8，77 MB，常驻后每句约 0.1–0.2 s）可补回标点。
- 流式 zipformer-zh-14M 也实测可用（有部分结果），但准确度略低，只作备选。
- 已提交的骨架：`integrations/speech`（引擎封装 + 路由 + `speech_transcribe` 工具）与
  `tooling/speech.mjs`（`status` / `install-runtime` / `prepare` / `transcribe`），
  真机跑通 `transcribe`。
- 结论：本地跑得动，准头可用；冷启动决定「引擎必须常驻」，这也是把实时识别放 app（进程内）、
  把批量文件转写放 host（一次性 CLI 可接受加载时间）的原因。

## 2.5 基线结果（2026-10-29，30 句工程术语语料，TTS 合成）

| 模型 | CER | 加个人词典后 CER | 术语命中（12 个） | 加词典后 |
|---|---|---|---|---|
| **SenseVoice-Small int8** | 17.6% | **2.7%** | 3/12 | **12/12** |
| Paraformer-zh small int8 | 18.6% | 18.3% | 1/12 | 2/12 |

错误分布非常集中：普通中文几乎全对，错的全是**英文术语与「银狼」这类专名**
（银郎/罗man/COT/蜂塔考/三次forest/电hub/代编…），这正是自学习词典（L2）要解决的；
把 2026-10-29 的观测变体写进 `integrations/speech/lexicon.json` 后，SenseVoice 的 CER 降到 2.7%。
Paraformer 的变体不同、未收录，说明**词典必须随实际引擎与用户声纹积累**，不能跨引擎照搬。

证据：`behavior/speech-eval-2026-10-29.json`；语料 `integrations/speech/corpus.json`；
复跑 `node tooling/speech-eval.mjs`（TTS 合成有缓存，重跑只做识别）。

## 3. 分工

| 层 | 位置 | 职责 |
|---|---|---|
| 实时识别 | **app**（sherpa-onnx JNI，已在依赖里） | 唤醒后一句 / 按下麦克风：录音 → VAD → OfflineRecognizer → 文本进输入框；声纹门控 |
| 资源与工具 | **host 插件 `integrations/speech`** | 运行时/模型下载与 SHA 校验、给 app 的 `/sliver-wolf/speech/models/<id>/<file>` 路由、`speech_transcribe` 工具、词典与样本库、训练服务调用 |
| 训练 | **服务器** | SenseVoice LoRA/部分层微调、导出 int8 ONNX、固定测试集 CER 门禁、版本发布与回滚元数据 |

## 4. 模型

| 角色 | 模型 | 体积 | 备注 |
|---|---|---|---|
| 主力 ASR | SenseVoice-Small int8 | ~230 MB | zh/yue/en/ja/ko，自带标点与数字规整；RTF 0.32（2 线程） |
| 轻量备选 | Paraformer-zh small int8 | ~78 MB | RTF 0.11；无标点，配标点模型 |
| VAD | Silero VAD | ~2 MB | **app 里已有**（`assets/wake/silero_vad.onnx`） |
| 声纹 | CAM++ speaker.onnx | ~7 MB | **app 里已有**（`assets/wake/speaker.onnx`） |
| 唤醒 | sherpa-onnx KWS | 已有 | 保持现状，不改唤醒链路 |
| 结束判定 | Smart Turn（可选） | ~9 MB | 后续再评估，不阻塞 |

模型不进 APK：host 下载一次、SHA 校验、app 通过本地回环取（离线网络无碍），
`~/.local/share/sliver-wolf/speech/models/` 为唯一真源。

## 5. 声纹与自学习（复用现有注册）

### L1 门控（直接用已有注册）
- 现有：`OwnerSpeaker` 算 CAM++ embedding（VAD 切净语音），`OwnerBackend` 用 liblinear/libsvm
  训出本人判别器，权重 AES-GCM 加密存 `owner-voice.bin`。
- 语音识别前/后各做一次判定：只有本人才把「音频 + 最终文本」写进自学习样本，别人说话只识别不学习。
- 唤醒继续用现有的 `owner-wake.bin`，不重复做。

### L2 文本自学习（收益最快）
1. 每次识别后记录 `rawText`；用户发送（或改后发送）记录 `finalText`。
2. 拼音/编辑距离对齐 diff，累计替换对；同一替换 ≥2 次才自动生效。
3. 识别结果先过词典（确定性替换），再可选走一次术语表纠错（银狼、LumenDSH、ZeroTier、
   GPT-SoVITS、magic-context、agent/dev、phone_task…）。
4. 词典存 host（插件侧 JSON，原子写），app 通过路由拉最新版；可导出/删除。

### L3 声学适配（服务器训练，手机应用）
1. app 积累本人「WAV + 确认文本」对（VAD 切段、相似度过滤、去重），达标（约 1–2 小时）后由 host 上传。
2. 服务器训练服务：`POST /train`（样本包）→ FunASR recipe 做 LoRA/部分层微调 → 导出 int8 ONNX
   → 在服务器用固定测试集与基线对比，CER 必须严格下降才 `POST /publish`；否则返回失败与原因。
3. host 收到 `published` 元数据（版本、SHA、CER 对比）→ 下载新模型目录 → 通知 app 切换；
   app 保留上一版，可一键回滚。
4. 诚实预期：个人数据量下 CER 相对下降约 5–15%，不是魔法；所以门禁比承诺重要。

## 6. 分期

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0（进行中） | `integrations/speech` 插件骨架 + 运行时/模型准备 + `tooling/speech.mjs` CLI；SenseVoice 落地 | `node tooling/speech.mjs status/transcribe` 真机出文本；30 句测试集 CER/RTF 基线 |
| P1 | app 内 `SpeechEngine`（OfflineRecognizer + VAD + 常驻服务进程）替换 `VoiceInput` 的识别段 | 真实口述：一句 6 s 语音 ≤1.5 s 出字；断句不截断；host 故障时回落系统识别 |
| P2 | 个人词典（L2）+ 术语表 + 数字/标点规整 | 词典命中日志、二次修改率下降 |
| P3 | 声纹门控接入现有注册（L1） | 本人/非本人判定分布与误判率 |
| P4 | 服务器训练服务 + 应用/回滚（L3） | 固定测试集 CER 严格下降才发布 |
| P5（可选） | 文件/会议批量转写工具、标点模型、Smart Turn | 真实任务 |

## 7. 风险

- **内存**：SenseVoice int8 常驻预计 400–600 MB；必须放在独立进程（`android:process`）里，
  空闲 5 分钟释放，避免拖垮 WebView（今天刚遇到 WebView 卡死）。
- **冷启动 8–10 s**：只在 app 打开/唤醒时预热一次；不能每句冷加载。
- **APK 体积**：模型不进 APK（已按此设计）；运行时已在依赖里，无新增。
- **许可**：SenseVoice / Paraformer（FunAudioLLM）Apache-2.0、sherpa-onnx Apache-2.0；
  再分发保留 NOTICE；训练服务只处理本机样本。
- **降级**：引擎失败自动回落系统 `SpeechRecognizer`；词典/训练不可用不影响基础识别。

## 8. 待办中的决定

- SenseVoice 发布包是 1 GB tar（内含 fp32 894 MB + int8 230 MB）；下载后只保留 int8 并删掉 fp32/脚本，可省约 1 GB。
- 训练服务部署位置：现有 Debian 服务器（8 核 15 GB，无 GPU），先 CPU 微调；样本与模型留在该机。
