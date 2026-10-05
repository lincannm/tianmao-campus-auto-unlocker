# 开锁

一个为「天猫校园」宿舍门锁写的辅助 App：**打开它就直接落在门锁页**，
路上的开屏广告、广告弹窗和更新提示由无障碍服务自动点掉；需要的话，它还能替你按一次「点击开锁」。
它作为天猫校园的配套工具独立运行，普通用户装好、开好授权就能用。

本项目由 Vibe Coding 产生。

## 特点

- **一键直达**：从桌面图标或小部件打开，经 deep link 直达门锁页，跳过首页那一整屏广告位；
- **自动跳过弹窗**：按文案精确识别「跳过 / 关闭」类控件与更新提示，替你点掉；
- **可选代按开锁**：桌面图标入口和小部件入口各有一个开关，可分别决定是否替你按那一下「点击开锁」；
- **桌面小部件**：1×1 / 2×1 / 2×2 / 4×2 四种尺寸，放上去还能自由缩放；
- **界面清晰**：初次使用向导、设置面板（状态 + 开关 + 动作日志）；
- **授权极简**：全程只需要在系统里打开一次无障碍服务，普通用户自己就能装好、开好。

## 工作原理

**用 deep link 把请求直接指向门锁页。** 天猫校园自己导出了处理这类地址的入口，
接受 `tmallcampus://…` 这种 URI scheme 形式的地址；App 把「打开某个 H5 页面」的请求
指向门锁页，于是首页和一整屏广告位都不必经过；剩下的开屏广告播在跳转之前，交给无障碍处理。

**匹配用精确文案，触发靠限定条件。** 跳过、关闭、更新提示各自的文案，来自对天猫校园安装包
（dex 字符串 + 资源表）的提取，规则写成精确文本匹配，不按屏幕坐标找按钮。规则的触发有三重限定：
只在发起跳转后的 **90 秒**窗口内、只在前台是目标应用时、且只命中「拒绝 / 跳过 / 关闭」这一类控件
——「去升级 / 立即安装 / 同意 / 允许」不在可命中范围内。

**代按开锁在无障碍树里定位，授权只生效一次。** 门锁页的「点击开锁」在无障碍树里是可查的节点：
先按精确文本找到它，再自下而上找可点的祖先控件点击。授权用一个取走即失效的标志位，
每次跳转最多按下一次。

**以系统设置判定无障碍状态。** 判定直接读系统的无障碍启用列表，而不是进程内的连接状态，
避免服务已经被系统摘掉、App 却以为还能跳过弹窗。

**读屏类声明让后台清理跳过本服务。** 无障碍服务以读屏类反馈类型声明，
系统在清理后台任务时会跳过这类服务，于是一般的后台清理不会连带把无障碍授权一起收走。

## 使用

1. 装好 App，打开一次 —— 它会提示「长按桌面图标 → 点设置完成向导」；
2. 长按桌面上的「开锁」图标，点弹出的「设置」，在向导里打开系统无障碍服务；
3. 之后从桌面图标或小部件打开，就会直接进到门锁页；面板里的两个开关决定要不要顺便替你按开锁。

## 出 Release（手动触发，push 不会自动构建）

仓库里配了 GitHub Actions（`.github/workflows/release.yml`），它的触发器**只有 `workflow_dispatch`**：
**任何 push 都不会构建、也不会发布**，出包完全由你决定。发一次版的流程：

1. 改 `app/build.gradle.kts` 里的 `versionName`（要一起改就再提 `versionCode`），提交并 push；
2. 到仓库的 **Actions → Release APK → Run workflow**（或用 `gh workflow run release.yml`）；
3. 几十秒到几分钟后，**Draft Release** 里会多一条 `v<versionName>`（例如 `v3.2`），
   附件是 release 签名的 APK；**确认没问题再手动点 Publish**。

规则与环境（都是脚本里写死的，出问题先看这几条）：

- **tag 不手填**，由代码里的 `versionName` 推出来（`v3.2`），保证「Release 名字」和「APK 里印的版本」一致；
  **同名 tag 已存在就直接失败**，不会悄悄覆盖已发布的包 —— 这时提一下 `versionName` 再触发；
- **APK 版本以 `app/build.gradle.kts` 为准**，workflow 不覆盖它；产物名是
  `UnlockAssistant-<versionName>-release.apk`；
- 签名用仓库里那把自签名密钥（`keystore/unlockassistant.jks`），和本地构建**同一把**，
  v1 + v2 + v3 全开 —— 可以直接覆盖安装已装的版本，不必卸载；
- 构建环境按仓库现状还原：**JDK 25**（守护进程 JDK 被 `gradle/gradle-daemon-jvm.properties` 钉在 25）、
  `platforms;android-37.0` + `build-tools`（`compileSdk 37` 要这一档）；
- 脚本跑完会打印 `apksigner verify --min-sdk-version 21` 的结果与 APK 的 SHA256，便于对账。

本地出包仍然是 `gradlew assembleRelease`（工作区里的包装脚本 `tools\build_app.ps1 -Task assembleRelease`），
两边的产物都是 release 签名，**口径一致**。

> Release workflow（English summary）：`.github/workflows/release.yml` runs **only** on manual
> `workflow_dispatch` — pushes never build or publish. Bump `versionName` in `app/build.gradle.kts`,
> push, then run the workflow; it builds a release-signed APK and creates a **draft** GitHub Release
> tagged `v<versionName>` for you to review and publish.

## 技术要点

- Kotlin + Jetpack Compose + Material Design 3，界面与配色由 Compose 主题统一；
- 无障碍服务在运行时把反馈类型再钉一遍，不依赖单一处的静态声明；
- 界面资源（应用图标、长按快捷方式图标、小部件图形）全部由脚本生成，不手改资源文件；
- 签名同时开启 v1 + v2 + v3，兼容覆盖面更广。
