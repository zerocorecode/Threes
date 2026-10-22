# Threes

原生 Android Threes 游戏，完全离线运行。包名遵循 MemoSeed 项目的命名规则：

- Release: `com.zerocode.threes`
- Debug: `com.zerocode.threes.debug`

## 当前功能

- 原版风格的 4x4 棋盘、长方形方块、圆角和 Numbers-Premium 数字字体
- Threes 单格推进、1+2 合并和相同高阶方块合并规则
- 12 张基础牌堆、下一张预览和 Bonus Card 概率
- 顶部左侧为当前分数与金色最佳分数卡片，右侧预览使用与当前分数相同的底色容器；最佳分数保存到本地，重启、撤销和新开局均保留纪录
- 预览卡片不显示标题，1/2 仅显示蓝/红牌面，3 显示数字，奖励牌继续显示加号；合并后的方块用 140ms 从 82% 恢复到正常大小
- 手势滑动、新游戏和撤销
- 滑动音效、合并成功随机互动音效、无法移动随机拒绝音效；支持静音
- 普通按钮使用 `click.wav`，AI 自动/暂停按钮使用 `deepClick.wav`
- 合并生成 48 及以上方块时显示黄色黑描边加分飘字；结束后保留计分方块的分值
- 手动模式与 AI 模式，点击灯泡直接切换
- AI 四方向评分、搜索深度、节点数和耗时，以及连续自动运行
- 棋盘中央的结束卡片：分数、最大方块、查看棋盘及再来一局

## 模块

- `app`: Jetpack Compose UI、状态管理和 Android 入口
- `game-core`: 不依赖 Android 的规则、棋盘、牌堆和对局状态
- `ai-core`: 仅 nneonneo 限时版、JNI 适配和无界面测试工具

## AI 模式

仅使用 nneonneo 限时版，界面统一显示「AI 模式」。灯泡在「手动模式」与「AI 模式」间切换，
白底保持一致，手动图标为黑色、AI 图标为蓝色。手动模式保留方向按钮占位，AI 自动按钮禁用。

默认每步预算 **200ms**、深度上限 **8**（包含根移动），不额外限制节点。
逐层加深，四个方向完整算完一层才更新结果；超时丢弃整层并返回上一层结果。
最浅一层保证完整返回合法方向，时间预算为协作式，总耗时可能略超预算。
方向评分仅用于当前局面内比较；深度是最后完整完成的上限，不代表穷举了全部分支。

计算在后台执行，提示计算期间仍可手动滑动；切换模式、撤销或重开会取消旧搜索。
自动运行期间禁用手势、模式切换、方向评分弹窗、撤销与重开，保留声音开关与暂停。
每次启动自动运行默认关闭棋盘音效，允许本次手动开启；暂停或结束后恢复手动模式的声音设置。手动声音设置开启时，自动运行中的暂停按钮仍播放 `deepClick.wav`。

### 原生引擎

基于上游提交 `c554820ad848a73b7cd34e358c974ab2d85bd792`，原始源文件和 MIT 许可证原样保留。
沿用 C++ 64 位棋盘查表、评估权重、概率截断、缓存和出牌模型；
通过构建时钩子加入时间预算与逐层加深，替换原版自适应深度。
当前牌堆与预告经过 JNI 适配；奖励牌只传入可见候选集合，不传入真实隐藏值。
独立的不限时模式及其它搜索算法已经移除。接入细节见 `ai-core/src/main/cpp/UPSTREAM.md`。

Android 原生部分需要 NDK `28.2.13676358` 和 CMake `3.22.1`；
APK 包含 arm64-v8a、armeabi-v7a、x86_64，使用静态 C++ 运行时。

### 无界面测试

不需要启动 Android、模拟器或 UI。桌面编译依赖 CMake 3.22.1 和 C++17 编译器；
Windows 默认使用 PATH 中的 MinGW `g++`。
可用 `-Pthrees.cxx=编译器绝对路径` 和 `-Pthrees.cmake=CMake绝对路径` 覆盖。

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :ai-core:benchmark `
  -PbenchmarkGames=3 -PbenchmarkSeed=42 `
  -PbenchmarkOutput=reports/benchmarks/ai-run-01
```

工具只运行 nneonneo 限时版，默认使用与 App 相同的 200ms/步和深度上限 8。
`-PbenchmarkMillis=500` 可调整预算，`-PbenchmarkNodes=数值` 可添加节点限制。
`-PbenchmarkMaxMoves=20000` 为步数保护；`-PbenchmarkMaxGameSeconds=0` 默认不限制单局总时间。
达到保护限制的对局明确记为未结束，不混入终局均分或达成率。

输出目录相对于项目根目录，也支持绝对路径；已有结果不会覆盖。
保存逐局 `games.csv`、逐步 `moves.csv`、环境参数 `environment.txt` 和中文汇总 `summary.md`。
固定种子和预热后串行运行用于减少干扰；时间预算仍受 JIT、GC 和系统负载影响，重跑不保证逐步一致。

保留的历史报告：[六种算法初筛](reports/benchmarks/2026-10-22-comparison/conclusions.md)、
[200ms 同预算终局对比](reports/benchmarks/2026-10-22-timed-200ms/conclusions.md)。
后者中限时版五个种子全部领先，均分 224,219.4 对 17,978.4，最高合到 6144。
这些为桌面小样本结果；历史报告中的其它算法现已移除，其命令不适用于当前代码。

运行 `.\gradlew.bat :ai-core:test` 验证固定深度四方向评分与原始源文件一致、全部 65,536 行的左右移动、
随机完整棋盘的四方向结果、未知牌信息隔离、整层超时回退和取消行为。

## 构建

```powershell
.\gradlew.bat test lintDebug assembleDebug
```

应用显示名称与项目名称统一为 `Threes`。APK 沿用 MemoSeed 的命名规则：
`Threes_v版本号_版本码_构建类型.apk`。
`version.properties` 维护语义版本号，当前为 `0.1.0`；版本码按北京时间自动生成八位 `yyMMddHH`，
同一小时内不变，跨小时更新。Debug 与 Release 的版本号、版本码一致，包名可共存。

例如 2026 年 10 月 22 日北京时间 17 时构建的调试 APK：

```text
app/build/outputs/apk/debug/Threes_v0.1.0_26102217_debug.apk
```

Release 签名从项目同级的 `../_android_signing/Threes.properties` 读取，
其中 `storeFile=Threes.jks` 相对于 `_android_signing` 目录解析。
Debug 沿用 MemoSeed 的规则：存在该配置时使用同一套 Release 证书，否则使用默认调试证书。
Debug 保留 `.debug` 包名后缀，与 Release 可共存。
更换证书后，旧证书签名的同包名应用无法直接覆盖更新；安装失败时应保留原应用和数据。
该目录包含证书和密码，不提交到 Git；换电脑时需从加密备份恢复到相同的相对位置。
运行 `./gradlew assembleRelease`（Windows 使用 `.\gradlew.bat assembleRelease`）生成已签名 APK。
对应发布 APK 为 `app/build/outputs/apk/release/Threes_v0.1.0_26102217_release.apk`。

## Android Studio 运行配置

打开项目并完成 Gradle 同步后，在绿色运行按钮旁的下拉列表选择：

| 配置 | 行为 |
| --- | --- |
| `Threes-Run` | 构建当前变体，在 AS 选定设备上覆盖安装并启动；运行前校正已连接模拟器为北京时间 |
| `Threes-Build-Debug` | 仅构建 Debug APK |
| `Threes-Build-Release` | 仅构建已签名 Release APK |
| `Threes-Build-All` | 构建 Debug 和 Release APK |
| `Threes-Install-Debug` | 清理构建产物，重新构建并安装 Debug |
| `Threes-Install-Release` | 清理构建产物，重新构建并安装 Release |
| `Threes-Install-All` | 清理构建产物，重新构建并依次安装 Debug、Release |

这些配置均保存在 `.run/`，不依赖本机 `.idea/workspace.xml`。
Install 系列使用 Gradle 的安装任务，目标为已连接且可安装的设备；覆盖安装保留应用数据，
`clean` 只清理本项目的构建产物。日常调试选择 `Threes-Run`，并在 Build Variants 中使用 `debug`。
命令行对应 `:app:assembleDebug`、`:app:assembleRelease`、`:app:assemble`、
`:app:clean :app:installDebug`、`:app:clean :app:installRelease`、
`:app:clean :app:installDebug :app:installRelease`。
版本码边界检查可运行 `.\gradlew.bat :app:verifyBuildVersionCode`。

