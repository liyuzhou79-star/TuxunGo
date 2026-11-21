# TuxunGo 代码地图

给维护者的一张地图：哪个文件管什么、改功能时先看哪里。路径都相对仓库根目录。

## 总览

- `app/` — 唯一的 Android 模块（Kotlin + Jetpack Compose + Material 3，minSdk 29，仅 arm64-v8a）
- `.github/workflows/build-debug.yaml` — 每次推送到 main 自动跑：编译 KataGo、跑单元测试、出带测试签名的 debug 包，并断言测试证书指纹
- `.github/workflows/build-release.yaml` — 手动 dispatch（参数 `tag`，如 `v2.38.0`）：从 Secrets 还原正式签名、出正式包
- `release-notes/` — 每版一份发布说明，文件名 `release-notes-<版本>.md`
- `tools/check_tsumego_data.py` — 死活题数据自检：解析 App 内置 8 道题，与逐手验证过的参考数据逐项核对（改题目数据后先跑一遍）

## 规则与棋谱（纯 Kotlin，有单元测试）

- `app/src/main/java/com/tuxungo/android/go/GoGame.kt` — 规则核心：落子、提子、禁着点、全局同形禁着（positional superko）、停一手两次终局、认输/超时、中国规则数目（`score` / `scoreWithDead`）、死子确认后的定稿（`finishScoringWith`）
- `go/Sgf.kt` — SGF 导出（含逐手胜率注释的复盘导出也在这里组装）
- `go/SgfImport.kt` — SGF 导入：主线解析（多局/让子/摆子/矩形），导入前用 GoGame 逐手回放校验，对不上就截断并标记
- `engine/GameReview.kt` — 复盘数据：逐手统计 `TurnStat` 的编解码、恶手/妙手/转折手判定（`ReviewMath`）

## 引擎层

- `engine/KataEngine.kt` — GTP 引擎进程管理：启动、落子、genmove（`lastGenmoveResigned` 区分认输与停一手）、进程死亡检测与重启
- `engine/AnalysisEngine.kt` — 分析协议（JSON）：候选点、胜率、目差、领地归属（ownership，注意是「行棋方视角」，转黑方视角在 `AnalysisMath`）；`analyze` 支持流式暂定结果回调；`AnalysisEngineHolder` 是主界面空闲预热的引擎交接点
- `engine/ModelStore.kt` — 模型库：内置轻量/真人风格模型、强力模型（按需下载）、用户导入模型的校验与解析、昵称、删除与恢复
- `engine/ModelDownload.kt` — 强力模型的下载（断点与取消）
- `engine/EngineAssets.kt` — 把打包进 APK 的模型文件释放到 filesDir（资源用中性扩展名 `.model` 防打包器改名）
- `engine/ModelHeader.kt` — 模型文件头解析；`TerritoryEstimate` 是死子判定（按整块棋的气与归属均值）
- `engine/SideAiStore.kt` — 双方 AI 配置（模型、访问量、模拟人类、定级档位 `RANKS` 与对应的人类模型 profile）

## 数据与设置

- `SettingsStore.kt` — 全部用户设置（`AppSettings`）：主题、棋盘皮肤、音效、形势判断参数、智能裁判、终局自动存图等
- `GameStore.kt` — 进行中对局的持久化（配置、着法、逐手统计、棋钟快照），进程被杀后恢复
- `LibraryStore.kt` — 棋谱库：每局一个 JSON 文件存在 filesDir/library，文件夹在 folders.json
- `SeriesStore.kt` / `RankedStore.kt` — 表演赛系列赛比分（按模型名记账）、定级战绩（按档位记胜负），都是 SharedPreferences 小存储
- `BackupStore.kt` — 数据备份/恢复：把若干 prefs 文件与棋谱库打包成一个文件（模型文件不在内）
- `UpdateChecker.kt` — 手动检查更新（App 仅有的两个联网点之一，另一个是强力模型下载，都必须用户手动触发）

## 界面（ui/）

- `MainActivity.kt` — 导航总调度、主题、首次启动引导与「本版更新」、桌面长按快捷入口、空闲预热分析引擎
- `ui/SetupScreen.kt` — 主页：开局设置、最近棋谱、每日一题卡；连点吉祥物五次打开个人统计页
- `ui/GameScreen.kt` — 对局页（全项目最大的文件）：对局驱动、AI 托管、分析/形势判断/数目、智能裁判、AI 认输、走势小图、表演赛解说流、系列赛、结果弹窗都在这里
- `ui/BoardView.kt` — 棋盘绘制：皮肤纹理、领地着色、候选点、落子放大镜、手数、涟漪动画
- `ui/GameUi.kt` — 游戏级视觉组件（2.60.0）：渐变质感按钮 GameButton、深度卡片 GameCard、棋盘胡桃木框 BoardFrame、棋子头像 StoneAvatar、几何图标 GameIcon
- `ui/ReviewScreen.kt` — 复盘：走势图、逐手回放、分支试下、领地归属图（按手数缓存）
- `ui/LibraryScreen.kt` — 棋谱库：搜索/筛选/多选、终局缩略图（后台渲染、缓存有上限）
- `ui/GuessScreen.kt` — 猜一选训练：随机对局、棋谱库出题、错题本
- `ui/TsumegoScreen.kt` — 死活题（题目数据在文件内，解法全部经过规则推演验证）、限时挑战
- `ui/ModelsScreen.kt` — 模型库界面：下载、导入、测速、昵称、删除
- `ui/SettingsScreen.kt` — 设置（分组二级页：对局与操作 / 外观 / 声音 / 形势判断 / 数据与更新）
- `ui/OnboardingScreen.kt` — 新手指南七页
- `ui/PhysicalClockScreen.kt` — 实体棋盘棋钟
- `ui/EditScreen.kt` — 摆棋编辑器：自由摆放黑白子，产出自定义开局（`GameConfig.customStones`）直接开局
- `ui/StatsScreen.kt` — 个人统计页：从棋谱库与训练进度汇总总对局、黑白胜负、定级战绩等（主页连点吉祥物五次打开）
- `ui/GameConfig.kt` — 对局配置数据类（贴目、让子、AI 访问量、自定义开局等），各界面共用
- `ui/MenuScreens.kt` — 二级页共用的屏幕骨架（返回键 + 标题 + 副标题），保证全 App 导航观感一致
- `ui/Mascot.kt` — 吉祥物与全套自绘图标（`AppIconKind`）、空状态组件
- `PositionShare.kt` — 局面图生成与分享/存相册

## 单元测试（app/src/test/，每次构建全跑）

共 14 个测试文件、78 个用例，钉住纯逻辑的对外行为：

- `go/GoGameTest`（11）— 落子/提子/禁着点/劫争等规则核心
- `go/LoadPositionTest`（3）— 摆棋局面加载的合法性校验
- `go/ScoringTest`（2）— 中国规则数目与死子定稿
- `go/SgfTest`（7）— SGF 导出格式（尺寸/编码/停一手/让子/结果/注释转义）
- `go/SgfImportTest`（10）— SGF 导入解析（主线/变化/多局/让子/摆子/矩形/回放截断/结果文案）
- `engine/KataEngineVertexTest`（3）— GTP 顶点坐标转换（跳过 I、y 自上而下）
- `engine/ModelHeaderTest`（8）— 模型文件头解析 + 死子判定（整块均值）+ 展示用归属加深
- `engine/AnalysisMathTest`（4）— 胜率/归属的行棋方→黑方视角转换
- `engine/SideAiStoreTest`（4）— 定级阶梯 RANKS 的构成、顺序与显示名
- `GameStoreTest`（2）— 对局存档编解码
- `ReviewMathTest`（11）— 逐手统计编解码、恶手/妙手判定、棋谱库手顺编码、稀疏统计拒绝、整局讲评汇总
- `UpdateCheckerTest`（4）— 检查更新的版本号逐段数字比较
- `RankedStoreTest`（4）— 定级战绩文本解析的容错
- `RankEstimateTest`（5）— 棋力估计的中心档位、区间与数据门槛
- 另有仓库脚本 `tools/check_tsumego_data.py` — 死活题数据与验证副本逐项核对（手动跑）

## 改东西时先看哪里

- 改规则/数目 → `GoGame.kt`，跑单元测试（`app/src/test/`）
- 改 AI 行为/棋力 → `SideAiStore.kt`（配置）+ `GameScreen.kt` 的引擎驱动部分 + `KataEngine.kt`
- 改分析呈现 → `AnalysisEngine.kt`（数据）+ `GameScreen.kt` / `ReviewScreen.kt`（界面）
- 加设置项 → `SettingsStore.kt` 的 `AppSettings` + `SettingsScreen.kt` 对应分组；需要备份的 prefs 文件要登记进 `BackupStore.PREFS_FILES`
- 加训练玩法 → 参考 `GuessScreen.kt` / `TsumegoScreen.kt` 的自包含结构
