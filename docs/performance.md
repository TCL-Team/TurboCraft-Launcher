# Performance / 性能

TurboCraft Launcher 的渲染栈与 ZalithLauncher 2 保持一致。本文记录从 ZL2 同步过来的
性能相关改动，以及拿到最高帧率的推荐设置。

## 从 ZalithLauncher 2 同步的内容

| 项目 | 说明 |
| --- | --- |
| **MobileGlues** 渲染器 | 直接在设备上把桌面 GL 翻译成 GLES（必要时走 ANGLE），绕开 Zink/VirGL 的软件路径，是手机端帧率最高的选择 |
| **SimpleFPEWrapper** 渲染器 | 另一条 GL 4.x 路径，兼容性更好，适合 MobileGlues 跑不起来的场景 |
| **渲染器配置框架** | `ConfigurableRenderer` + `game/renderer/config/*`，支持全局配置与「按版本覆盖」 |
| **渲染器配置对话框** | 渲染器列表右侧的齿轮图标，可调 ANGLE 后端、FSR1 超分、GL 版本、GLSL 缓存等 |
| **VirGL OSMesa 缓冲修复** | 之前把 `setbuffer` 符号当成颜色缓冲传给 OSMesa，首帧 flush 可能崩溃 |
| **NeoForge 早期窗口修复** | `config/fml.toml` 写入 `earlyWindowControl = false`，避免模组扫描期启动器卡死 |

## 推荐设置（按收益排序）

1. **设置 → 渲染器 → 选择 `MobileGlues`**（MC 1.17+）。
2. 点列表右侧的 **齿轮**：
   - `FSR1` → **Performance** 或 **Balanced**：内部按低分辨率渲染再超分，帧率提升最明显。
   - `ANGLE Backend` → **Enable When Possible**（部分 GPU 上默认就行）。
   - `GLSL Cache` 保持默认 32，别调太小，否则每次进游戏都要重编译着色器。
3. 如果 MobileGlues 在某些版本上黑屏/崩溃，换 **SimpleFPEWrapper**；再不行用 **NG-GL4ES**。
4. **内存分配**：设置 → 游戏 → Java 内存。默认值按设备内存自动给出（≥6 GB 设备为 2048 MB）。
   模组多可以适当提高，但不要超过设备可用内存的一半，否则 GC 反而更卡。
5. **Vulkan 驱动**（Adreno 设备）：优先用较新的 Turnip 驱动，比厂商自带驱动快不少。

## 排查

- 游戏内仍卡：先确认版本用的渲染器没有被「按版本覆盖」成别的（版本设置 → 渲染器）。
- 想确认实际使用的渲染器：启动日志里搜 `Renderer loaded:`。内置 Renderer 日志里搜
  `DLOPEN Renderer` 也能看到加载的库名。
