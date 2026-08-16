# NetworkInformation (Network Monitor Service)

一个专为 Android 系统设计的轻量级网络监控与探活工具。本应用实现了**全网首创的状态栏图标化实时监控方案**，将国家/地区小国旗、网络延迟与丢包率直接以自定义图标形式整合至状态栏，助你一眼掌握当前网络状态。

---

## 🌟 核心亮点 (Key Features)

* **状态栏极简图标化监控（全网首创）**：打破传统通知栏仅有文字的局限，通过动态生成的自定义图标，将 **国家小国旗、实时延迟、丢包率** 直观呈现于状态栏与通知栏中，扫一眼便知当前节点连接状态。
* **精准 TCP 探活与测速**：基于 `generate_204` 的多线程探活机制，精准计算实时延迟（RTT）与网络丢包率，拒绝虚假延迟。
* **智能电源调度**：
  * **亮屏状态**：5秒高频实时刷新，保障网速极速响应。
  * **锁屏状态**：60秒极低频省电省流模式，极大优化后台功耗。
* **抗抖动抗误判算法**：通过优化探测阈值与逻辑，彻底消除因偶发网络波动引起的虚假丢包现象，数据更真实可靠。
* **原生轻量自适应**：采用原生 `RemoteViews` 自定义常驻通知栏，布局精简，支持点击快捷手动刷新。

## 🛠️ 技术栈 (Tech Stack)

* **开发语言**：Kotlin
* **核心组件**：Foreground Service (前台服务)、BroadcastReceiver (监听亮锁屏)
* **网络与并发**：Kotlin Coroutines (协程)、HttpURLConnection (HEAD 请求探活)
* **UI 渲染**：自定义 `Bitmap` 图标生成与 `RemoteViews` 布局

## 💡 开发初衷
我开发此应用的初衷是希望在处理复杂网络环境（如代理、多地数据中心连接）时，无需打开任何 App，仅通过手机状态栏的图标变化，就能即时捕捉到网络是否切换、连接质量是否波动。这份“一眼感知”的体验，是我最希望带给用户的价值。

## 📦 更新日志 (Changelog)

* **v1.0.0**：
  * 首发上线：实现状态栏小国旗图标化显示与延迟丢包实时追踪。
  * 优化测速算法，解决偶发超时误判丢包问题。
  * 规范通知栏 UI 布局及多模式图标切换。

## 🤝 反馈与参与
如果您喜欢这个项目，欢迎通过以下方式参与：
* Star 该项目以表示支持。
* 提交 Issue 反馈您遇到的问题或希望增加的新功能。

## 📄 许可证 (License)

本项目采用 [MIT License](LICENSE) 开源许可证。
<img width="1248" height="1972" alt="Image" src="https://github.com/user-attachments/assets/22e7ad26-afe5-499e-881c-c298d212b283" />
<img width="1248" height="1972" alt="Image" src="https://github.com/user-attachments/assets/01756c5e-461a-40cb-9c3d-5e3ae263f789" />
<img width="1248" height="1972" alt="Image" src="https://github.com/user-attachments/assets/734ad25d-03be-4aa6-9b5f-7c051bb0dd4d" />
<img width="1248" height="1972" alt="Image" src="https://github.com/user-attachments/assets/8437a2e5-6cd6-4484-bb95-caa6fc0b0535" />
