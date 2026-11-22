# 久坐喝水提醒 · sit-drink-reminder

一个**极小、离线、无广告**的 Android 定时提醒 App：到点提醒你起身活动、喝水。

- 包体目标 **< 500 KB**（只用 `AlarmManager` + `NotificationCompat`，不引入 appcompat / Material / WorkManager / 协程）
- 支持 **Android 8.0（API 26）及以上**，适配 Android 12 / 13 / 14 的精确闹钟与通知权限变化
- 数据只存在本机 `SharedPreferences`：**不联网、无账号、无广告、零统计 SDK**（连 `INTERNET` 权限都没申请）
- MIT License，欢迎二次开发

---

## 1. 功能

| 功能 | 说明 |
| --- | --- |
| 久坐提醒 | 独立开关；支持「按间隔」或「按时间点」两种方式 |
| 喝水提醒 | 独立开关；同样支持两种方式 |
| 自定义间隔 | **1 ~ 1440 分钟**任意值：预设 1/2/3/5/10/15/30/45/60/90/120/180，也可手动输入 |
| 提醒时段 | 自己设定每天**几点开始、几点结束**提醒（如 08:00–22:00），其余时间完全静音；支持跨夜 |
| 稍后提醒 | 通知上带「稍后 10 分钟」按钮，一键推迟，不用改设置 |
| 系统通知 | 标题如「该起来活动一下了」「该喝水了」，高优先级、可下拉展开 |
| 通知配置 | 提示音、震动开关（落到 NotificationChannel 上，Android 8.0+ 的正确做法） |
| 通知自检 | 检测渠道是否被系统降级为“静默”，提供一键重建与跳转系统设置 |
| 点击通知 | 打开 App 首页 |
| 后台提醒 | App 退到后台、进程被杀后仍会提醒 |
| 重启恢复 | 开机、覆盖安装、改系统时间/时区后自动重排提醒 |
| 首页状态 | 顶部总览「下一次提醒」+ 两张卡片（开关、状态胶囊、下次时间、规则摘要） |
| 设置页 | 开关、提醒方式、间隔（预设 + 自定义输入）、时间点增删（系统 TimePicker） |
| 可靠性提示 | 首页按优先级提示：通知权限 → 精确闹钟 → 电池优化 |
| 深色模式 | 跟随系统（`values-night`） |

---

## 2. 快速开始

### 环境要求

- Android Studio **Koala（2024.1.1）或更新**（需要支持 AGP 8.5）
- JDK **17**（Android Studio 自带 JBR 17，无需单独安装）
- Android SDK Platform **34**、Build-Tools 34.x

### 用 Android Studio 打开（推荐）

1. `File > Open`，选择本仓库根目录（`sit-drink-reminder/`）。
2. 首次打开会按 `gradle/wrapper/gradle-wrapper.properties` 下载 Gradle 8.7 并自动同步。
3. 连接手机（开启 USB 调试）或启动模拟器，点击 ▶ Run。

### 关于 Gradle Wrapper（重要）

本仓库**没有提交二进制的 `gradle-wrapper.jar`**（避免把二进制塞进仓库，也避免来源不明的 jar）。
Android Studio 的 Gradle 同步会直接使用 `gradle-wrapper.properties` 里的发行版地址，因此**不影响在 IDE 里构建**。

如果要用命令行 `./gradlew`，先生成一次 wrapper（需要本地装有 Gradle，或用 AS 的终端执行）：

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

### 命令行构建（已装 Gradle 8.7 + JDK 17）

```bash
gradle test          # 单元测试（调度计算）
gradle assembleDebug # 产出 app/build/outputs/apk/debug/app-debug.apk
gradle installDebug  # 直接装到已连接设备
```

---

## 3. 打包 APK

### Debug 包（直接装，体积略大）

```bash
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

### Release 包（开启 R8 混淆 + 资源压缩）

1. 生成签名文件（只做一次，**不要提交到仓库**）：

   ```bash
   keytool -genkeypair -v -keystore release.keystore \
     -alias sitdrink -keyalg RSA -keysize 2048 -validity 10000
   ```

2. 在**工程根目录**新建 `keystore.properties`（已在 `.gitignore` 中）：

   ```properties
   storeFile=release.keystore
   storePassword=你的密码
   keyAlias=sitdrink
   keyPassword=你的密码
   ```

3. 打包：

   ```bash
   gradle assembleRelease
   # app/build/outputs/apk/release/app-release.apk
   ```

   没有 `keystore.properties` 时也能构建，但产物是 `app-release-unsigned.apk`，需要手动签名才能安装。

4. 也可以用 Android Studio：`Build > Generate Signed App Bundle / APK`，完全不需要改代码。

### 体积说明

`release` 已开启：

- `isMinifyEnabled = true`（R8 代码压缩/裁剪）
- `isShrinkResources = true`（删除未引用资源）
- 唯一运行时依赖是 `androidx.core`，R8 只会保留真正被用到的类

预计 APK 在 **250 KB ~ 450 KB** 量级（随 AGP/依赖版本浮动）。如果你还想更小，见第 10 节「还能更小吗」。

---

## 4. 项目结构

```text
sit-drink-reminder/
├── .github/workflows/android.yml        # CI：跑单元测试 + 构建 debug APK
├── .gitignore
├── LICENSE                              # MIT
├── README.md
├── build.gradle.kts                     # 根构建脚本（只声明插件版本）
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts                 # 模块配置：SDK 版本、R8、可选签名
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml      # 权限 + 四大组件声明
        │   ├── java/com/example/sitdrinkreminder/
        │   │   ├── SitDrinkApp.kt              # Application：建通知频道 + 启动自愈重排
        │   │   ├── data/
        │   │   │   ├── ReminderType.kt          # 枚举：两类提醒的所有常量（id/频道/文案/图标）
        │   │   │   ├── ScheduleMode.kt          # 枚举：INTERVAL / FIXED_TIME
        │   │   │   ├── ReminderConfig.kt        # 不可变配置数据类
        │   │   │   ├── ScheduleMath.kt          # 纯计算：下次触发时间（可单测）
        │   │   │   └── SettingsRepository.kt    # SharedPreferences 读写（单例）
        │   │   ├── alarm/
        │   │   │   ├── ReminderScheduler.kt     # AlarmManager 排期 / 取消 / 全量重同步
        │   │   │   ├── ReminderReceiver.kt      # 到点：排下一次 + 发通知
        │   │   │   └── BootReceiver.kt          # 开机/覆盖安装/改时间/权限变化 → 重排
        │   │   ├── notify/
        │   │   │   └── NotificationHelper.kt    # 通知频道管理 + 构建通知
        │   │   ├── util/
        │   │   │   ├── AppPermissions.kt        # 通知/精确闹钟/电池优化：检测与跳转
        │   │   │   └── UiText.kt                # 界面文案拼装（下次提醒时间等）
        │   │   └── ui/
        │   │       ├── MainActivity.kt           # 首页
        │   │       ├── ReminderCardViews.kt      # 卡片控件引用（两处复用同一布局）
        │   │       └── ReminderSettingsActivity.kt # 单类提醒设置页
        │   └── res/
        │       ├── layout/                       # 4 个布局（首页/卡片/设置页/时间行）
        │       ├── drawable/                     # 卡片与提示条背景、矢量图标、启动图标
        │       ├── mipmap-anydpi-v26/            # 自适应图标（minSdk 26，无需 PNG）
        │       ├── values/                       # 中文文案、浅色配色、主题
        │       └── values-night/                 # 深色配色与主题
        └── test/java/com/example/sitdrinkreminder/data/
            └── ScheduleMathTest.kt               # 调度计算单元测试
```

一共 **15 个 Kotlin 文件**、**4 个布局**，没有第三方 UI 框架，适合拿去改造。

---

## 5. 架构与关键流程

### 5.1 一次提醒的完整链路

```text
用户设置（设置页 / 首页开关）
      │  SettingsRepository.write(config)        ← SharedPreferences
      ▼
ReminderScheduler.scheduleConfig()                 ← AlarmManager 只挂 1 个闹钟
      │  setExactAndAllowWhileIdle / setAndAllowWhileIdle（RTC_WAKEUP）
      ▼
        …… 到点 ……
      ▼
ReminderReceiver.onReceive()                       ← 广播（不需要 goAsync/WakeLock）
      ① 重算下次触发时间 → 写回 prefs → 立刻排下一次   （先排期，保证提醒链不断）
      ② NotificationHelper.showReminder()          ← NotificationCompat + 通知频道
      ▼
用户点击通知 → MainActivity
```

**为什么是「自调度链」而不是一次排几十个闹钟？**
每类提醒永远只有一个 `PendingIntent`（用 `data` URI + `requestCode` 双保险区分两类），触发时再排下一个。
好处：开机/改设置/改时区时只需要算一次「下次时间」；用户关掉提醒时只需 `cancel` 一次；不会出现「残留闹钟在关闭后仍然弹通知」。

### 5.2 恢复与自愈

| 场景 | 触发点 | 行为 |
| --- | --- | --- |
| 手机重启 | `BOOT_COMPLETED` → `BootReceiver` | 重算并重排（AlarmManager 的闹钟在重启后会清空） |
| 覆盖安装 | `MY_PACKAGE_REPLACED` | 同上 |
| 改系统时间/时区 | `TIME_SET` / `TIMEZONE_CHANGED` | 固定时间点模式按新时区重算 |
| 精确闹钟权限变化 | `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | 立即用精确/非精确模式重排 |
| App 启动 | `SitDrinkApp.onCreate` + `MainActivity.onResume` | 自愈式重排（被「强行停止」过的 App 闹钟会被系统清空） |

「间隔模式」在恢复时的策略（`ScheduleMath.ensureFuture`）：

- 触发时间还在未来 → 原样保留（不会因为打开一次 App 就把提醒往后推）
- 刚刚错过（5 分钟内）→ 立刻补发一次，避免整天漏掉
- 错过太久（比如关机一夜）→ 从现在重新计时，避免开机瞬间被一堆旧提醒轰炸

「时间点模式」永远取「严格晚于现在」的下一个时间点。

### 5.3 通知与权限

- 两类提醒各自一个 `NotificationChannel`（`reminder_sit` / `reminder_drink`，`IMPORTANCE_HIGH`），用户可以单独静音其中一类。
- Android 8.0+ 的**声音/震动是频道属性**，所以开关变化时会「删除频道 → 用新参数重建」，并记录一个签名（`notification_signature`）避免每次启动都覆盖用户在系统里的自定义。
- Android 13+ 首次开启提醒或点首页提示条时申请 `POST_NOTIFICATIONS`；被拒绝两次后按钮会直接跳到系统通知设置页，不会出现「点了没反应」。
- Android 12+ 若未授予精确闹钟权限，会自动降级为非精确闹钟，并继续按 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` 监听授权结果。

---

## 6. 技术选型与取舍

### 6.1 提醒调度：精确 vs 省电

| 方案 | 准时性 | 耗电 | 需要权限 | 本项目结论 |
| --- | --- | --- | --- | --- |
| `AlarmManager.setExactAndAllowWhileIdle` | 高（Doze 下也允许唤醒，但仍受系统 15 分钟粒度限制） | 极低 | Android 12+ 需 `SCHEDULE_EXACT_ALARM` | **默认方案**（有权限时） |
| `AlarmManager.setAndAllowWhileIdle` | 中（可能延迟几分钟，极端 Doze 下更久） | 极低 | 无 | **自动降级方案**（无权限时） |
| `AlarmManager.setAlarmClock` | 最高，且免权限 | 低 | 无 | 不采用：状态栏会常驻闹钟图标，语义也像闹钟 |
| `WorkManager` 周期任务 | 低（最小周期 15 分钟，且「尽力而为」不准点） | 低 | 无 | 不采用：不准点，且会引入约 300 KB 依赖 |
| 前台服务常驻 | 高 | 高 | 常驻通知 | 不采用：与「低耗电、轻量」目标冲突 |

一句话总结：**用系统闹钟拿到可靠性，用「无权限就降级」拿到兼容性，不用常驻服务换取体积与耗电。**

### 6.1.1 间隔与提醒时段

- 间隔下限放到了 **1 分钟**，方便“贴脸测试”或短时高强度提醒；但低于 5 分钟时设置页会给出提示：
  Doze/深度休眠下系统会把 `allow-while-idle` 闹钟合并到 9~15 分钟粒度，这是系统行为，不是 App 的问题。
- 「提醒时段」是**全局**开关（两类提醒共用）：落在时段外的触发时间会被顺延到下一个时段开始，
  而不是丢弃。开始 = 结束视为全天；`start > end` 视为跨夜（22:00 → 次日 06:00）。
- 「稍后提醒」属于用户显式动作，**不受提醒时段限制**（不然点了“稍后”却被推到明天，反而更烦）。

### 6.2 其他取舍

| 决策 | 选择 | 原因 |
| --- | --- | --- |
| UI 框架 | 平台 `Activity` + 原生 View（不用 appcompat/Material） | 省掉约 1 MB 依赖；本项目只有 2 个页面 |
| 通知 | `NotificationCompat`（`androidx.core`） | 需求明确要求；同时兼容各版本通知行为 |
| 数据存储 | `SharedPreferences` | 数据量极小，不需要 Room/DataStore |
| 异步 | 无协程/无 RxJava | 所有操作都是毫秒级同步操作，不需要 |
| 精确闹钟权限 | `SCHEDULE_EXACT_ALARM`（不用 `USE_EXACT_ALARM`） | 后者只允许闹钟/日历类应用，滥用会被商店拒绝 |
| 备份 | `allowBackup=false` | 隐私优先，也避免系统云备份参与「离线」承诺 |

---

## 7. 权限与系统版本适配

`AndroidManifest.xml` 声明的权限（**没有 INTERNET**）：

| 权限 | 用途 | 从哪个版本需要 |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | 发提醒通知 | Android 13 / API 33 起为运行时权限 |
| `VIBRATE` | 通知震动 | 一直 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复提醒 | 一直 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟 | Android 12 / API 31 |

| 系统版本 | 变化 | 本项目处理 |
| --- | --- | --- |
| 8.0 (26) | 通知必须走 Channel；后台限制 | `minSdk=26`，全部通知都建频道 |
| 9 (28) | 后台限制加强 | 不用后台 Service，只用广播 + 通知 |
| 11 (30) | 包可见性 | 未查询其他应用，无需 `<queries>` |
| 12 (31) | 精确闹钟权限、`PendingIntent` 必须显式可变性、通知 trampoline 限制 | 精确/非精确双路径；`FLAG_IMMUTABLE`；通知直接 `getActivity` 打开首页 |
| 12 (31) | 后台启动 Activity 受限 | 通知点击由系统代理启动，符合规则 |
| 13 (33) | 通知运行时权限、单声道通知、主题图标 | 运行时申请 + 被拒后跳系统设置；提供 `<monochrome>` 图标 |
| 14 (34) | 精确闹钟默认不授予（新安装）、前台服务类型 | 未授权时降级 + 首页提示授权；不使用前台服务 |
| 15 (35) | 强制 edge-to-edge（targetSdk 35 才生效） | 当前 `targetSdk=34`；若要上架 Play 升到 35 时，本项目的 `fitsSystemWindows` 布局仍可用，建议再回归一次视觉 |

---

## 8. 可靠性说明（务必读）

Android 对后台任务的限制会让「绝对准点」不可保证，请知会使用者：

1. **省电策略/厂商 ROM**：华为、小米、OPPO、vivo 等需要在系统里给本 App 「允许后台运行 / 自启动 / 不限制耗电」。首页的「电池优化」入口会跳到系统设置。
2. **Doze（打盹）模式**：`setExactAndAllowWhileIdle` 在 Doze 下依然触发，但系统限制为**最少 9~15 分钟间隔**，因此 5 分钟级别的间隔在深度休眠时可能被合并延迟。
3. **「强行停止」**：用户手动「强行停止」App 会清空其所有闹钟，直到下次打开 App 才会恢复（`SitDrinkApp.onCreate` 会自愈）。这是系统行为，无法绕过。
4. **非精确模式**：未授予精确闹钟权限时提醒可能延迟几分钟；这是为了不滥用高权限所做的取舍。
5. **错过的提醒**：最近 5 分钟内错过的会补发一次；错过更久的不会补发，而是从当前时刻重新计时，避免「一次弹出好几条」。

### 通知只进通知栏、没有横幅/声音/震动？

MIUI / HyperOS 等 ROM 会给新安装（尤其是侧载 APK）的应用默认套用「静默通知」：
通知能到通知栏，但没有横幅、没有提示音、不震动。这**不是** App 的 bug，而是系统把通知渠道降级了。

App 已在首页「通知与可靠性」区域做自检：

- 显示「系统把通知降级为静默」时，点 **重建通知渠道（修复静默）**；
- 如果重建后仍然静默，说明是系统级开关，按钮会自动跳到该通知频道的系统设置页；
- 随时可以点 **发送一条测试通知** 立刻验证横幅/声音/震动。

手动修复路径（红米/小米示例）：
`设置 → 应用设置 → 应用管理 → 久坐喝水提醒 → 通知管理`，打开「悬浮通知 / 横幅」「声音」「震动」，
再进入「通知类别」分别确认 `久坐提醒`、`喝水提醒` 两个频道。

---

## 9. 数据与隐私

| 项目 | 说明 |
| --- | --- |
| 存储位置 | `getSharedPreferences("sit_drink_reminder_settings")` |
| 存储内容 | 开关、提醒方式、间隔分钟数、时间点列表、下次触发时间戳、通知/权限偏好 |
| 网络 | 无 `INTERNET` 权限，代码中没有任何网络 API |
| 账号/广告/统计 | 无 |
| 备份 | `allowBackup=false`，不进入云端备份 |
| 卸载 | 随 App 一起删除 |

调试时查看本地数据（仅 debug 包）：

```bash
adb shell run-as com.example.sitdrinkreminder \
  cat shared_prefs/sit_drink_reminder_settings.xml
```

---

## 10. 二次开发指南

### 加第三种提醒（例如「护眼提醒」）

1. `ReminderType` 里加一个枚举值（新 `requestCode`、`notificationId`、`channelId`、文案资源）。
2. `res/values/strings.xml` 补标题/通知文案/频道名。
3. `SettingsRepository.defaultsFor()` 加默认间隔与默认时间点。
4. 首页布局再 `<include layout="@layout/view_reminder_card">` 一次，`MainActivity.bindCard()` 里绑上即可。
   —— 调度、通知、开机恢复、设置页全部自动复用，**不需要改调度代码**。

### 改默认值

- 默认间隔 / 默认时间点：`SettingsRepository.defaultsFor()`
- 间隔可选范围：`ScheduleMath.MIN_INTERVAL_MINUTES` / `MAX_INTERVAL_MINUTES`
- 间隔弹窗预设：`ScheduleMath.INTERVAL_PRESETS`
- 通知文案：`strings.xml` 里的 `notification_*`

### 改包名 / 应用名

1. `app/build.gradle.kts` 的 `namespace` 与 `applicationId`
2. `AndroidManifest.xml` 中组件类名的相对路径（`.ui.MainActivity` 等不用改）
3. Kotlin 源码的 `package` 声明与目录结构
4. `app_name` 字符串

### 还能更小吗？

- **去掉 `androidx.core`**：改用平台 `Notification.Builder`，可再省约 100~200 KB；但会降低跨版本兼容性，且需求里明确要求使用 `NotificationCompat`，故未采用。
- 把 `interface` 换成 `values/` 默认语言已是中文，若只面向国内可忽略多语言资源。
- 开启 `android.resourceConfigurations` 裁剪其他语言（本项目未使用第三方带翻译的库，收益很小）。

---

## 11. 测试与验收

### 单元测试

```bash
gradle test
```

`ScheduleMathTest` 覆盖：间隔计时、开机补排（刚错过补发 / 错过太久重新计时）、固定时间点跨天与跨月、边界值、`HH:mm` 解析。

### 手工验收清单

| # | 验收项 | 操作 | 期望 |
| --- | --- | --- | --- |
| 1 | 能设置久坐提醒 | 首页打开久坐提醒 → 修改提醒时间 → 选「每 5 分钟」 | 首页显示「下次提醒：今天 xx:xx（还有 5 分钟）」 |
| 2 | 能设置喝水提醒 | 同上，选「按时间点」并添加一个 2 分钟后的时间 | 首页显示对应时间点 |
| 3 | 能收到通知 | 等待到点 | 通知栏出现「该起来活动一下了」/「该喝水了」，有声音/震动（按开关） |
| 4 | 点击通知打开 App | 点击通知 | 打开首页 |
| 5 | 后台可提醒 | 按 Home 键 / 从最近任务划掉 App，等待到点 | 仍能收到通知 |
| 6 | 重启后恢复 | `adb reboot` 后等待到点 | 仍能收到通知（无需打开 App） |
| 7 | 关闭后不再提醒 | 关闭开关后等待 | 不再有通知 |
| 8 | 声音/震动开关 | 关闭「提示音」后等待 | 通知静音（频道已重建） |
| 9 | 通知权限被拒 | 在系统设置里关掉通知权限 | 首页出现提示条，点击可跳转授权 |
| 10 | 精确闹钟未授权 | 系统设置里撤掉「闹钟和提醒」 | 首页提示条出现；提醒仍会到达（可能延迟） |
| 11 | 深色模式 | 系统切换深色 | 界面配色跟随 |
| 12 | 安装包体积 | `ls -lh app/build/outputs/apk/release/` | 建议 < 500 KB |

### 用 adb 观测调度是否生效

```bash
# 1) 看闹钟是否真的排上了（能看到 sitdrink://reminder/SIT 与 when=...）
adb shell dumpsys alarm | grep -i sitdrink

# 2) 看通知频道是否创建成功
adb shell dumpsys notification --noredact | grep -i reminder_sit

# 3) 进入 Doze 再退出，验证休眠下仍能触发
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce
```

> 注：`ReminderReceiver` 是 `exported=false`，所以不能用 `adb shell am broadcast` 直接触发（会被系统拒绝），
> 验证请把间隔临时调成 5 分钟。

---

## 12. 上传到你的 GitHub

### 方式 A：命令行（HTTPS）

```bash
cd sit-drink-reminder

git init
git branch -M main
git add .
git commit -m "feat: 初始版本 - 久坐与喝水提醒（AlarmManager + NotificationCompat）"

# 把 <your-name> 换成你的 GitHub 用户名
git remote add origin https://github.com/<your-name>/sit-drink-reminder.git

git push -u origin main
```

### 方式 B：GitHub CLI（自动建仓库）

```bash
cd sit-drink-reminder
git init && git branch -M main
git add . && git commit -m "feat: 初始版本"

gh repo create sit-drink-reminder --public --source=. --remote=origin --push
```

### 方式 C：SSH

```bash
git remote add origin git@github.com:<your-name>/sit-drink-reminder.git
git push -u origin main
```

### 安全提醒（重要）

- **不要**把 GitHub Token、密码、keystore 写进代码或提交历史。本项目已用 `.gitignore` 忽略：
  `local.properties`、`keystore.properties`、`*.jks`、`*.keystore`。
- 推送认证用 SSH key 或系统凭据管理器；用完的临时 token 及时撤销。
- 已经误提交过的敏感文件，光删文件不够，需要 `git filter-repo` 清理历史并**更换密钥**。

---

## 13. 已知限制

- 本项目的默认分支代码经过静态检查（资源引用 / 布局 ID / 字符串占位符 / 清单一致性），
  但**作者本地没有 JDK 与 Android SDK，未做真机编译**。首次导入 Android Studio 时若遇到
  AGP / Gradle 版本提示，按提示对齐到 AGP 8.5.2 + Gradle 8.7 即可；`.github/workflows/android.yml`
  会在 pushed 后自动构建，可用来验证。
- 极端省电的定制 ROM 上仍可能延迟，见第 8 节。
- 未实现「稍后提醒」和「历史记录」，它们不在 1.0 的设计目标内；如需要可基于
  `ReminderScheduler.nextTriggerFor()` 扩展。

---

## 14. 许可证

[MIT License](LICENSE) · Copyright (c) 2026 Your Name

如果这个项目帮到了你，欢迎点个 Star ⭐，并把默认值改成适合你自己的工作节奏。
