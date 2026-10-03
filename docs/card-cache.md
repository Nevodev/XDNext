# 卡片缓存与刷新规范

聚焦页上的每张卡片（电量、校园卡……）都有一份落在设备上的缓存，并且都在应用启动时统一刷新一次。
这不是两张卡片的巧合，而是这个页面往后每加一张卡片都要遵守的形状。**新增卡片时请照本文件执行。**

课表（`data/timetable`）不在聚焦页上，但它遵守同一套规则，也是启动时统一刷新的模块之一：它的页面每次
打开都是新构建的，所以它必须画「已经加载好的那一份」，而不是自己去抓。

---

## 为什么要有缓存

校园网大多数接口只在校园网内可达，离开校园网时失败是常态而不是异常。没有缓存，用户看到的就是一片
「查询失败」；有缓存，用户看到的是上一次读到的数字，加一个「· 缓存」标记。这也是原始项目
（`traintime_pda`）的做法——它把每次查询结果写进 `EnergyInfo.json` 之类的文件，失败时回落到文件。

## 一条铁律：值和它的「时效」必须一起存

缓存里放的不只是数字，还有这个数字**是关于哪一天/哪一次**的。两类数据的老化方式不同：

| 数据 | 会过期吗 | 处理方式 |
|---|---|---|
| 电量余额 | 不会，昨天读的余额今天还是余额 | 跨天仍然可用，照常显示 |
| 校园卡余额 | 不会 | 同上 |
| **今日**支出 | **会**，它描述的是某一天 | 只在与读取日相同的那天才用；换天即丢弃，显示为「—」/「余额未知」 |
| 区间流水（校园卡页面） | 不会，但**只对问过的那段日期成立** | 存的时候连区间一起存；只有存下来的区间**包含**要问的区间才用 |

所以 `SchoolCard.json` 里存了 `knownDate`：那是这组「今日」数字描述的那一天。读缓存时若
`knownDate != today`，余额照给、当日金额一律置空——**绝不把昨天的数字贴到「今日支出」下面**。
证据在 `SchoolCardCacheTest`：`dropsTheDaysTotalsOnceTheDayHasPassed`。

金额一律用**整分 Long** 累加，不用 `Double`：`0.1 + 0.2 != 0.3`，一天的零星消费会把误差显出来。

### 值可以是一段区间：`SchoolCardTradeList.json`

页面要查的不是「这张卡的流水」，而是「**这几天**的流水」——问题里就带着区间，所以答案也必须带着它。
`SchoolCardTradeListCache` 把区间和行一起写进 `SchoolCardTradeList.json`，读取时只在存下的区间
**覆盖**（`covers`，含首含尾）要问的区间时才回答：

- 存了整月 → 问今天是命中（今天的行本来就在里面），这正是缓存有用的地方；
- 存了今天 → 问整月是 miss，因为那样答出来的列表会**悄悄少掉几天**，而屏幕上看起来和「这几天没有消费」
  一模一样。宁可重查一次。

区间流水**不**因为跨天而作废：这一天的行在当天下班之后仍然是这一天的行，文件里的区间就是它的说明。
校验的是覆盖，不是新旧。

## 每张卡片必须有的三件东西

1. **`XxxCache`** —— 落盘。沿用既有约定：
   - 文件放在 support 目录（`supportDirectoryPath()`），一张卡一个文件，文件名就是原始项目的名字；
   - **文件的修改时间就是「抓取于」时间戳**，不在文件里再存一个会跟文件本身打架的字段；
   - 读取失败一律当作 **cache miss**，绝不当成错误——截断的文件只该让用户重查一次，不该让卡片坏掉；
   - 解码字段给默认值，这样旧版本写下的文件仍能读（见 `readsAFileWrittenBeforeTheTotalsWereStored`）。

2. **`XxxRepository`** —— 状态持有者：
   - **构造函数里同步读一次缓存**，这样第一帧就有东西可画，不必等协程；
   - 只有一个不可变快照 `StateFlow`，一帧不可能由两次不同的抓取拼出来；
   - 失败时：**有缓存就不是 error**（缓存只在没有缓存时才置 `error`），并带上失败原因（`XxxCacheHint`）；
   - 刷新中保留屏幕上的旧值（`isLoading = true` 但不清空数据）。
   - 如果这个页面查的是**用户选的区间/条件**，那它还要遵循两条额外的规矩，见下面「带参数的查询」。

3. **`XxxTile`** —— 只负责画，**不负责抓**。
   - 大数字只放数值本身或 `—`，状态文案（「正在查询」「余额未知」「点击重试」）放小标题那一行；
   - 缓存值要能看出来：小标题追加 `· 缓存`（`freshnessNote`）；
   - 点击 = 手动刷新这一张卡。

## 刷新只在两个地方发生

**一是启动时（每进程一次）统一刷新**，二是用户点击。

启动加载集中在 `CardRefresher`（`ui/home/CardRefresher.kt`）：它把这个进程里所有卡片的 repository
依次刷新一遍，并由自己的 `hasLoaded` 标记保证只做一次。新增卡片时：

```kotlin
class CardRefresher(
    private val energy: EnergyRepository,
    private val schoolCard: SchoolCardRepository,
    private val newCard: NewCardRepository,      // 1. 加构造参数
) {
    suspend fun loadOnce() {
        if (hasLoaded) return
        hasLoaded = true

        energy.refreshElectricityInfo()
        schoolCard.refreshSchoolCard()
        newCard.refreshNewCard()                 // 2. 加一行
    }
}
```

调用点是 `FocusScreen` 里的 `LaunchedEffect(refresher) { refresher.loadOnce() }`，新卡片同时要在
`HomeScreen` 构造 `CardRefresher` 的地方补上依赖。

物理实验（`data/experiment`）也在这个列表里，但它**排在最后**：它画在课表网格和它自己的页面上，
第一屏不等它；而它是这里最慢的一个——要登录第二个系统，再为每门课读一页拿教师名字。没有存密码时
它立刻失败，这正是新装应用的状态。

### 为什么"只刷一次"的标记必须放在 CardRefresher 里

这一条踩过坑，务必照做：**聚焦页在切走 tab 时会被卸载**——`CrossfadeNavContainer` 的
`ManualAnimatedVisibility` 在淡出结束后就把这一页从 composition 里拿掉了——切回来时重新挂载。
所以在页面内部写 `LaunchedEffect(Unit)` 会在**每次点开聚焦时都跑一遍**（电量卡片当初就是这样变成
"每次点开都刷新"的），`remember` 也救不了：它随 composition 一起消失。

只有 `CardRefresher` 活得比页面久（它由 `HomeScreen` 这个常驻的 shell 持有，`remember(energy,
schoolCard)` 构造一次，并间接持有 app 级的 repository）。所以"这个进程已经刷过"这句话只能由它来说。

**不要在 tile 或页面里写 `LaunchedEffect` 去抓数据。** 抓取的时机是上面那两处，别的地方一律只读。

**有缓存也要刷新。** 启动时不做 `if (缓存 == null)` 之类的判断：缓存只是「第一帧先画什么」，
不是「这次要不要查」。先画旧值，网络回来再替换。反过来，因为有缓存，**失败不需要自动重试**——
重试的入口是用户点击，避免离线时反复打接口；也因此这里不做定时刷新，要看新数据就点一下卡片。

### 页面例外：打开时自己查一次

上面两条说的是**聚焦页上的卡片**。页面（`EnergyScreen`、`SchoolCardScreen`……）不一样：打开一个页面是
用户明确的、一次性的动作，所以页面在打开时自己查一次
（`LaunchedEffect(repository) { if (!state.isLoading) repository.refresh() }`）——缓存是「第一帧先画什么」，
不是「这次要不要查」。其余规矩照旧：不做定时刷新、不做自动重试，失败后重新查的入口是用户点刷新或重新选
一次条件。

页面查的是用户选的参数（日期区间、周次……）时，多三条：

- **参数进快照，和结果一起更新。** 用户改了区间，旧行必须在同一帧里清掉：一屏数据配上另一个标题，和把
  昨天的数字贴在「今日支出」下面一样错。`SchoolCardFlowsRepository.selectRange` 就是这么做的；而
  **同一个**区间上的刷新要保留旧行——那才是「刷新」的意思。
- **缓存按参数存，读取时校验参数覆盖。** 见上面 `SchoolCardTradeList.json` 那一节：值描述的是一段区间，
  所以区间必须跟着值一起落盘，且只有存下的区间**包含**要问的区间才用。
- **别把服务端的筛选当成筛选。** 两个日期只是「服务端理解的窗口」；回来的行要按自己的区间再过滤一次
  （`transactionsIn`），否则越界的行会画在一个点名了别的日期的标题下面。

## 新增一张卡片的检查清单

- [ ] `data/<feature>/` 下建 `XxxCache`、`XxxModels`（含金额/时间的规范化）、`XxxApi`、`XxxSession`（含失败回落与 `XxxCacheHint`）、`XxxRepository`。
- [ ] 缓存文件写进 support 目录；修改时间当时间戳；读不懂就是 miss。
- [ ] **凡是「今天/本期」这类带时效的值，缓存里必须同时存它描述的那一天/那一期**，读取时校验。
- [ ] repository 构造时同步读缓存；失败且有缓存不置 `error`。
- [ ] 写 `XxxCacheTest`：同日命中、**跨天丢弃时效值**、旧格式文件仍可读、坏文件是 miss。
- [ ] 写 `XxxRepositoryTest`：首帧显示缓存、错误不清空已有值、成功后清 `cacheHint`。
- [ ] tile 只画不抓；状态文案在小标题；缓存值带 `· 缓存`。
- [ ] 在 `CardRefresher` 加构造参数和一行刷新，并在 `HomeScreen` 构造处补依赖；**不要**用页面里的 `LaunchedEffect`。
- [ ] 有页面的话（不只是 tile）：页面自己在打开时查一次；查的是用户选的参数时，参数进快照、和结果一起清掉旧值，
      并按上面的规矩单独存一份「参数 + 结果」的缓存。
- [ ] 在 `README.md` 的 slice 清单和模块表里登记这个模块。

## 现有实现对照

| 卡片 | 缓存文件 | 时效字段 | 仓库 |
|---|---|---|---|
| 电量 | `EnergyInfo.json` | 无（余额不跨天失效）；另存 `ElectricityHistory.json` 曲线 | `data/energy/EnergyRepository.kt` |
| 校园卡 | `SchoolCard.json` | `knownDate`：当日支出/收入/笔数描述的那一天 | `data/schoolcard/SchoolCardRepository.kt` |
| 校园卡页面（区间流水） | `SchoolCardTradeList.json` | 区间本身：只在存下的区间**包含**要问的区间时才用 | `data/schoolcard/SchoolCardFlowsRepository.kt` |
| 课表 | `ClassTable.json` | `semesterCode`：这份课表属于哪个学期 | `data/timetable/TimetableRepository.kt` |
| 物理实验 | `PhysicsExperiment.json` | 无有效期字段；但它属于**一份账号**，所以换账号时 `ExperimentRepository.onCredentialsChanged()` 先删文件再重查 | `data/experiment/ExperimentRepository.kt` |

课表的缓存文件就是原始项目的那个名字、那份裸 `ClassTableData` JSON（它自带的桌面小组件也读这个文件），
所以两边抓下来的文件可以直接对拍。它的「时效字段」不像校园卡那样在读取时校验：学期换掉之后，只有一次
成功的刷新才能拿到新的学期与课表，读取时无从判断，因此照规矩先画旧值、网络回来再替换。原始项目在显式
切换学期时会删掉这个文件，那个入口（学期切换）还没有做。

两张卡的 hint 文案都在各自的 `XxxCacheHint` 枚举里：既带原始项目的 i18n key（保留着，等以后有 i18n
层可以直接用），也带今天 UI 实际显示的那句话。
