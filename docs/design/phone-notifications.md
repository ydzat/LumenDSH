# 手机通知静音（2026-10-28）

目标：除 LumenDSH（以及承载 Agent 的 Termux / Termux:API）之外，其他应用不再弹出任何通知。

## 为什么不是 adb 一条命令

实测这台机器（Android 10，EMUI 10，未 root），三条「看起来该行」的路都不行：

| 尝试 | 结果 |
|---|---|
| `cmd appops set <pkg> POST_NOTIFICATION ignore` | appops 接受并保存（`cmd appops get` 显示 ignore），但 NMS 仍 `enqueueNotificationInternal` 并 `buzzBeepBlinkLocked hasValidSound=true`——该 op 在本 ROM 不生效 |
| `cmd notification suspend_package <pkg>` | 同样不阻断，通知照常响 |
| `settings put global zen_mode 1` | 写入后立即被系统改回 0；且 DND 的「允许的应用」白名单没有 shell 入口（`allow_dnd` 给的是通知策略访问权限，不是白名单） |

Android 13 起才有 `pm revoke ... POST_NOTIFICATIONS`；本机没有。

## 采用的做法

真正被系统执行的开关是 NMS 的「按应用允许通知」，也就是用户在「设置 → 通知 → 批量管理」里拨的那个 Switch。工具因此走同一条 UI 路径：

`integrations/phone/notif-mute.mjs`

- 通过 `am force-stop com.huawei.systemmanager` + `am start -n ...NotificationManagmentActivity` 打开「通知」，再点「批量管理」（批量页会记住滚动位置，所以必须先 force-stop，否则从底部开始）；
- UIAutomator dump 解析「应用标签 + 同行 Switch」，标签与 `最近未发送通知` / `N 分钟前` 这类副标题、以及页面 chrome 严格区分；
- 只拨需要拨的开关，拨完重读该行确认；滚动直到列表稳定（连续两次签名一致）；
- 最后完整走一遍列表，输出 `stillEnabled`（应当只剩白名单）与逐条动作，写进证据 JSON，可据此人工恢复。

白名单（默认，`--allow` 可改）：`LumenDSH Web`、`Termux`、`Termux:API`。后两个是 Agent 自己的运行载体：Termux 前台服务通知与 `termux-notification` 提醒都从它们发出。

## 结果（2026-10-28 实测）

- 批量页共 37 个应用，**34 个已关闭**，仍开启：`Termux:API`、`LumenDSH Web`、`Termux`（独立复查，非脚本自报）。
- 证据：`behavior/notif-mute-2026-10-28.json`。
- 副作用说明：`信息`（短信）也在关闭之列——按「只要我的通知」执行；来电不受影响（通话界面不是通知）。要恢复某个应用，在同一页面手动打开，或用证据文件里的 label 反查。

## 复跑

新装应用默认是「允许通知」，需要再跑一次静音：

```bash
cd ~/sliver-wolf-dev
node integrations/phone/notif-mute.mjs --dry-run    # 先看会动哪些
node integrations/phone/notif-mute.mjs --out behavior/notif-mute-<date>.json
```

`--dry-run` 只读列动作；`--allow` 覆盖白名单。
