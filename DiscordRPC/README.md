# Discord Rich Presence

启动器在前台运行时，在 Discord 个人资料里显示 **“正在玩 TurboCraft Launcher”**，附带
“开始时间”计时器和最多两个按钮（下载 / GitHub）。

不使用 Discord 机器人、不需要任何账号令牌或 OAuth：只通过 **Discord Social SDK** 的
本地 RPC 通道，把状态发给**同一台手机上已登录的 Discord 客户端**。

## 默认值放在哪

启动器**设置界面里没有**任何 Discord 选项（普通用户看不到、也改不了），所有默认值都在
一个文件里：`TCL/src/main/java/com/movtery/zalithlauncher/discord/DiscordPresenceConfig.kt`

| 常量 | 作用 |
| --- | --- |
| `ENABLED` | 总开关，默认 `true`，功能默认开启 |
| `APPLICATION_ID` | 兜底 Application ID（Secret 优先） |
| `TITLE` | Discord 卡片标题，默认 `TurboCraft Launcher`（不设置会显示成 “Game”） |
| `DETAILS` / `STATE` | 卡片上的两行文案（留空则该行不显示），默认第二行是 `Minecraft Java Edition` |
| `PRIMARY_BUTTON_LABEL` / `PRIMARY_BUTTON_URL` | 第一个按钮，默认 “Download” → GitHub Releases |
| `SECONDARY_BUTTON_LABEL` / `SECONDARY_BUTTON_URL` | 第二个按钮，默认 “GitHub” → 仓库主页 |

改完重新打包即可生效。

## Application ID 的来源

优先级（从上到下）：

1. 构建时注入的 `DISCORD_APP_ID`：
   - CI：GitHub 仓库 → Settings → Secrets and variables → Actions → 新建 secret
     `DISCORD_APP_ID`，值填 Discord Developer Portal 里应用的 Application ID；
   - 本地构建：环境变量 `DISCORD_APP_ID=...`，或者项目根目录放一个
     `.discord_app_id.txt`（已被 `.gitignore` 忽略）。
2. 没有 secret 时回退到 `DiscordPresenceConfig.APPLICATION_ID`。

两者都为空时 Rich Presence 静默关闭，其他功能不受影响。

## Discord 应用侧需要做的事

1. [Developer Portal](https://discord.com/developers/applications) → 新建 Application；
2. **General Information** → 填 App Icon / 名称，这就是别人看到的图标和标题；
3. **Rich Presence → Art Assets** 上传大图后，把图片名填到 `DiscordPresenceManager`
   的 `largeImage`（留空则使用应用图标）；
4. 复制 **Application ID** 填进上面的 secret。**不要**创建 Bot、不要复制 token。

## 关于 `libs/discord_partner_sdk.aar`

Discord Social SDK 是 Discord 的专有二进制（约 30 MB），**不提交到本仓库**（本仓库是
GPL-3.0，且仓库不携带大体积二进制）。`DiscordRPC/build.gradle.kts` 会在配置阶段自动
下载到 `DiscordRPC/libs/discord_partner_sdk.aar`（该目录已 gitignore）。

想换来源时：

```bash
./gradlew :TCL:assembleRelease -DdiscordSdkAarUrl=https://.../discord_partner_sdk.aar
# 或环境变量 DISCORD_SDK_AAR_URL
# 或者手动把 sdk 放到 DiscordRPC/libs/discord_partner_sdk.aar
```

## 注意事项

* SDK 的 AAR 带有语音通话相关声明，合并后会额外引入 `RECORD_AUDIO` 等权限；本功能
  只用 RPC，不会申请也不会使用麦克风。如果不想在应用商店页面看到麦克风权限，可以在
  `TCL/src/main/AndroidManifest.xml` 里用 `tools:node="remove"` 移除（未验证对 SDK
  内部前台服务的影响）。
* 每 ABI 约增加 7 MB 的 APK 体积（`libdiscord_partner_sdk.so`）。
* 若 Discord 未安装或未登录，SDK 会静默失败，启动器不受影响。
