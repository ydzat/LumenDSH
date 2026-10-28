# 语音识别准确度调研与路线（2026-10-28）

目标：在不改产品形态（语音为主、原生 app、6GB 手机、无 root）的前提下，把日常口述的
识别准确度提上去，并像输入法那样**随使用自动校准**。

## 1. 现状（先定位问题在哪）

- 两条语音入口都用 Android 系统 `SpeechRecognizer`：
  - `VoiceInput.java`（Habitat/唤醒词路径，带重试与超时）；
  - `WebChatActivity.startVoice()`（WebUI 内语音按钮，取 `RESULTS_RECOGNITION[0]`）。
- `voice_recognition_service` 当前是 Google 的
  `com.google.android.googlequicksearchbox/...GoogleRecognitionService`（云端）。
- 终端把文本 POST 到 host 的 `/sliver-wolf/companion/composer`（companion host），
  **这是唯一可编程插入点**：任何校正都可以在这里做，App 只负责把文本送上来。
- 现状能力缺口：只取第一名假设、无热词、无上下文、无标点整理、无个人纠错记忆，
  识别错了就一路错到模型和记忆里。

## 2. 输入法们的「自动校准」其实由四件事组成

1. **多假设 + 语言模型重打分**：引擎给 n-best，用领域/上下文 LM（或大模型）选
   最像人话的那个，而不是永远取第一名。
2. **上下文偏置 / 热词**：把用户词表（人名、项目名、术语）喂给识别器或重排器。
   Whisper 用 `initial_prompt`；FunASR/Paraformer 有原生 hotword；RNN-T 用
   biasing tree。Google/系统识别器不开放热词口，所以对我们只能在重排/后处理侧做。
3. **后处理纠错**：同音字/别字纠正（中文拼写纠错 CSC）、标点与数字规整（ITN）。
   开源代表：`pycorrector`（KenLM + MacBERT/ERNIE 系列 CSC）、各类「ASR 文本
   LLM 纠错」项目。
4. **自学习**：用户每次修改（错→对）被累积成个人词典与语言模型，下一次自动纠正。
   输入法（讯飞/搜狗）的「自学习词库 / 个人热词」本质就是这一层。

## 3. 调研结果：哪些能直接用，哪些只能参考

| 候选 | 形态 | 对我们可用性 | 关键数据 |
|---|---|---|---|
| **Marmalade STT**（`maxwhipw/marmalade-stt-android`，MIT，F-Droid） | 独立的 Android 离线 STT 引擎 App，可注册成系统 `RecognitionService`；支持中文（Qwen3-ASR） | **最直接**：装上并在系统里选为语音输入后，我们 app 的 `SpeechRecognizer` 自动走它，**不改 app 代码**；可用 `settings put secure voice_recognition_service` 切换 | 中文 Qwen3-ASR：FLEURS 9.2% CER、AISHELL-1 2.7% CER；模型 987 MB、约为 Parakeet 的 5.2× 延迟；Android 9+；有「名字替换」类文本清理 |
| **DSH 官方 voice-input bundle**（`@deepseek-ai/dsh-experimental-voice-input-bundle`，SenseVoice ONNX） | Host 端本地 ASR，`sherpa-onnx-node` | **在手机宿主上跑不起来**：`sherpa-onnx-node` 只发 linux-x64/arm64(glibc)/win/darwin 预编译，Termux 是 Android/bionic；且模型目录为空 | 模型未下载、无 Android 原生包 |
| **dsh-live-voice**（wads-dev） | 社区插件，浏览器侧 STT（Whisper/Qwen3 in browser、或 HTTP 端点）+ TTS | 参考用：它的「Transcription context（词表）」和多引擎 HTTP 契约值得抄；架构是浏览器麦克风，不适用于原生 app | Apache-2.0；测试于 DSH 0.2.2-alpha |
| **dsh-voice**（GooDAnDReaDY） | 社区插件，浏览器侧 VAD 切分 + 多 provider 回退（Deepgram→Groq→本地 whisper.cpp） | 参考用：回退链与 VAD 切分的设计 | MIT |
| **dsh-voice-input-plugin**（Zhangbo-cn） | 社区插件，浏览器 Web Speech API 逐字输入 | 参考用：逐字上屏的交互 | — |
| **FunASR / SenseVoice**（服务器侧） | CPU 可跑的中文 ASR（SenseVoice-Small 多语种 + 标点/ITN；Paraformer 原生热词） | 备选：服务器 8 核 15GB 无 GPU，可实时；但要把 app 从 `SpeechRecognizer` 改成「录音→上传→取文本」 | 官方 bundle 用的就是 SenseVoice，说明质量路线被认可 |
| **pycorrector**（服务器/宿主） | 中文同音/形近纠错（KenLM、MacBERT4CSC） | 可作纯后处理；CPU 可接受 | 依赖模型文件，适合放服务器或宿主 |
| **whisper.cpp / faster-whisper**（服务器） | 离线 ASR | 备选；中文不如 SenseVoice/Qwen3 专精，但生态成熟 | — |

## 4. 这台设备的硬约束（实测）

- Android 10（SDK 29）、EMUI 10、无 root；`POST_NOTIFICATION` appops 等系统面
  可通过 adb 验证（本轮已证明 adb 能读写 `voice_recognition_service`）。
- 内存 5.7 GB、可用约 1.6 GB → Qwen3-ASR 987 MB 模型能用但吃紧；SenseVoice
  Small（~250 MB）更稳。
- 服务器：8 核 / 15 GB / 无 GPU，适合 SenseVoice 级 CPU 推理，不适合大模型 ASR。
- 规则边界：我们自己不能装 APK（用户手动装 Marmalade 才行）；不改系统权限。

## 5. 建议路线（按性价比排序）

### L0 · 立刻可做：n-best + 大模型纠错 + 自学习词典（1–2 天）

1. App 侧：`EXTRA_MAX_RESULTS=3`，把 3 个假设一起 POST 到
   `/sliver-wolf/companion/composer`（新字段 `source:"voice"`）。
2. Host 侧：companion host 里加**一次短调用**（`ctx.llm` 流式接口或直连 provider）
   做三件事：选最合理假设 → 修同音字/术语 → 补标点。上下文只给：最近几轮对话、
   项目词表（银狼、LumenDSH、ZeroTier、GPT-SoVITS、DSH、magic-context、agent/dev、
   phone_task…）。返回仍走 `composer/pending`，用户先看到再发送。
3. **自学习**：记录「识别原文 vs 用户最终发送」的 diff；高频替换对写进个人词典
   （host 侧 `~/.local/share/sliver-wolf/speech/dictionary.json`），下次校正优先用。
4. **可量化**：固定 30 句测试集（含项目术语、人名、数字、中英混说），记录 CER 与
   纠错前后差异；这就是后续所有方案的基线。

### L1 · 一周内：换引擎实测（用户装一个 App）

- 用户手动安装 Marmalade STT（F-Droid/GitHub，MIT），把中文引擎设为 Qwen3-ASR；
  系统「语音输入」选它（Android 10 需先把语音助手设为「无」才会显示该设置），
  adb 可直接 `settings put secure voice_recognition_service
  app.marmalade.stt/...`。
- 用同一 30 句测试集对比：Google 云端 vs Marmalade/Qwen3-ASR（离线、隐私更好）。
- 若 Marmalade 明显更好 → 保留它当默认引擎，L0 的纠错层继续叠加。

### L2 · 可选：服务器 ASR + 热词（1–2 周）

- 服务器跑 FunASR SenseVoice（或 Paraformer + 热词），暴露带鉴权的 HTTP 接口；
  App 增加「录音 16k WAV → POST → 取文本」路径，替代 `SpeechRecognizer`。
- 收益：热词原生支持、标点/ITN 稳定、可换模型；代价：离线不可用、延迟 +0.5–2s。

## 6. 推荐结论

先做 **L0**：它不需要用户装任何东西，能在现有 Google 识别之上把「同音字、项目术语、
标点」这三类最常见的错误压下去，并且自带自学习，正好对应「输入法式自动校准」。
同时把 30 句测试集建起来，L1 才有可比性。L1 是「换更好的耳朵」，L0 是「换更聪明的
大脑」；两者叠加收益最大。L2 只在 L1 仍不满意时再上。

## 7. 参考

- Marmalade STT：https://github.com/maxwhipw/marmalade-stt-android
- DSH 官方 voice bundle：`@deepseek-ai/dsh-experimental-voice-input-bundle`（本机 runtime 内）
- dsh-live-voice：https://github.com/wads-dev/dsh-live-voice
- dsh-voice：https://github.com/GooDAnDReaDY/dsh-voice
- pycorrector：https://github.com/shibing624/pycorrector
- FunASR：https://github.com/modelscope/FunASR
