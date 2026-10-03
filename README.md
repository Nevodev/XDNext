# XDNext

A Kotlin Multiplatform / Compose Multiplatform rewrite of
[BenderBlog/traintime_pda](https://github.com/BenderBlog/traintime_pda) ("XDYou") — an open-source
student information client for Xidian University: timetable, exams, scores, dorm utilities, campus
card, library, experiments, sports, attendance and more.

Android first. iOS and desktop are planned for later, which is why the business logic lives in
`commonMain` from the start.

## Status

**Slice 1 — login, automatic login, and both interactive login steps.** Complete, and verified on a
device against the live campus server.

A first login completes end to end: password login, the slider captcha, and second-factor codes (SMS or
enterprise WeChat). Later launches restore the stored session without any of it.

The slider captcha is solved automatically when the matcher is confident, and by dragging otherwise. The
handoff is invisible: the drag prompt only appears if the automatic attempt is rejected.

Both prompts are rendered from **the root of the composition** — `ui.login.LoginInteractions`, mounted in
`App` beside the shell — and not from the login screen. A module's background refresh can be asked for a
drag or a code long after the login screen is gone, and the brokers park their caller on a deferred while
they wait; with nothing mounted to answer them the login never finishes, holds the session's login lock
for as long as it waits, and leaves every later login queued behind it. The symptom is a spinner that
never stops on whichever screen is showing the module that was waiting, which is how the omission was
found: the timetable's first refresh on a device never returned.

**Slice 2 — the home shell.** Layout first, and now two live cards. `HomeScreen` is a bottom bar over
three tabs (`CrossfadeNavContainer`): 聚焦 (campus information), 工具箱 (the school's other systems) and
设置. Both campus cards read a real backend and are cached on the device; everything else is a
placeholder. The page's own header is the date and the weekday.

聚焦 is laid out like a phone's home screen: a four-column grid whose tiles declare a footprint — the
timetable as a full-width banner two rows tall, electricity, library and the campus card as square
two-by-two widgets, and everything else as a single cell that flows around them. The packing lives in
`ui.home.packTiles` and is covered by its own tests, because neither `LazyVerticalGrid` nor
`LazyVerticalStaggeredGrid` can span a tile *vertically*: the former stretches whatever shares the
widget's row to the widget's height, and the latter only knows "one lane" or "the whole line".

The login *screen* is still not on screen: `App` renders the shell directly until the navigator owns the
root and `Login -> Home` is one of its transitions. What `App` does mount is the two interactive prompts
above, and that is deliberate rather than a halfway house — they are needed by background refreshes, not
by the screen that collects a password.

The plan is to grow this slice by slice, in dependency order, rather than laying down empty screens for
every module up front. Every later backend (eHall, yjspt, library, energy, …) sits behind the same IDS
session established here, so this is the one piece that has to be exactly right.

**Slice 3 — the electricity query.** Complete at the backend and wired to the campus page's electricity
tile; the full electricity page is not built. `data.energy` speaks the energy management system's API end
to end — the OAuth hop through IDS, the signed and encrypted `content` parameter, the meter list, the
last month of electricity readings and the last year of water ones — and keeps the reading, the
remain-over-time history and the low-balance reminder's settings on the device. See
[The energy query](#the-energy-query).

**Slice 4 — the campus card.** The card's balance and today's spending behind the campus page's payment
tile, and 校园卡 itself: the flows of a date range, with the range changed from a calendar, and today's
spending total above them. The payment QR code is **not** built — it needs the card system's virtual-card
page and a barcode to render it from. See [The campus card](#the-campus-card).

Both live cards are cached on the device and refreshed once at start-up. That is a rule rather than two
implementations: **a new card is expected to follow it**, and [`docs/card-cache.md`](docs/card-cache.md)
is the checklist — what to store beside a value, why a day's figure and a balance age differently, where
the refresh is triggered from, and what tests to write.

**Slice 5 — the timetable.** 我的日程表 is on screen and reads the real registrar: the eHall login hop,
the semester the registrar considers current, the term start day, the timetable, the courses with no
time arrangement, and the schedule adjustments, which are folded into the grid before anything is
drawn. The page has the week strip with its 5×5 overview, the 61-block week grid with its period
column, date row, class cards and current-time line, the empty state, the two list pages the overflow
menu opens, the status banner and the error summary. It is verified against the live registrar **on a
device**: one refresh returned `2026-2027-1` starting `2026-09-07`, with 30 arrangements across 15
courses, one schedule adjustment and 19 teaching weeks, and the page drew that week, switched weeks in
step with the strip, and dropped the current-time line on a week that does not contain today. See
[The timetable](#the-timetable).

**Slice 6 — the physics experiment system, the grid it draws on, and the settings pages.** 实验信息 is on
screen and reads the lab site, `wlsy.xidian.edu.cn`, which is not behind IDS: it has a login form of its
own, its pages are GB18030, and the marks it publishes are **pictures** rather than numbers. The module
therefore does what the original did — logs in with a student number and a password of its own, reads the
booking table, reads one plan-grid page per course for the teachers, walks the report system's
five-event handshake for the marks, and recognises each mark by hashing its pixels against a table
generated from the lab's own images. The page is opened from the campus page's 实验信息 tile, which is
where the original opened it from; the bookings *also* arrive on the timetable through `TimetableOverlay`,
so a lab sits in the same 61-block coordinate system as a lecture and merges with one that clashes. See
[The physics experiment system](#the-physics-experiment-system).

The settings tab became a list of **categories** (`ui.settings`) rather than one long scroll: 账号设置
holds the account this app signs in with and the lab site's own, and 课表设置 holds the four switches the
grid's appearance is made of — the preferences the timetable's drawing code had been reading defaults for
since Slice 5. Both are pages pushed over the shell by the navigator, not dialogs, and they draw the same
floating title bar over a blurred copy of the page that 电量 and 校园卡 do.

Their text — and the experiment page's — lives in `composeResources/values/strings.xml` rather than in a
Kotlin object of `const val`s. See [Strings](#strings).

Not yet implemented, in rough order of what comes next:

1. the **home shell's own data**: the timetable tile still draws a placeholder number rather than today's
   arrangements, and the settings categories stop at the two that have something behind them — the
   original's notification, cache and about pages have no module to configure yet.
2. the timetable's remaining three sources and three menu entries: exams, other experiments and locally
   added courses are all projected onto the same grid by the original, and 添加课程信息, 生成日历文件 and
   导出到系统日历 are the entries that go with them. The seams for all of it are in place — see
   [The timetable](#the-timetable); the physics experiment system is the first source to arrive through
   `TimetableOverlay`, and the other experiment system is a second implementation of
   `ExperimentDataSource` rather than a new grid.
3. the electricity page itself: the two charts, the cache notice and the reminder threshold dialog.
4. the postgraduate (yjspt) branch of every academic module — the login flow already probes for it, and
   the timetable is the first module that has to answer it.
5. course-reminder notifications, the home-screen widget and system-calendar export.

## Modules

| Module | Contents |
|---|---|
| `shared` | Everything except the Android entry point. |
| `androidApp` | Activity, `Application`, manifest. |

Inside `shared/src/commonMain/kotlin/com/nevoit/xdnext`:

| Package | Responsibility |
|---|---|
| `core.concurrent` | `SingleFlight`, so overlapping refreshes share one request. |
| `core.crypto` | AES-CBC/PKCS#7 primitive, backed by `cryptography-kotlin`. |
| `core.image` | Image decoding (`expect`/`actual`) for the captcha images. |
| `core.log` | Kermit logger plus a redaction helper. |
| `core.navigation` | The page back stack: `Navigator`, `NavigationHost`, per-page `ViewModelStore`s. |
| `core.platform` | The entire platform surface: clock, support directory, HTTP engine. |
| `core.store` | `SecureStore` (secrets) and `SettingsStore` (preferences). |
| `data.energy` | The energy management system: models, the signed/encrypted protocol, the file cache, the session, the state holder. |
| `data.experiment` | The physics experiment system: the lab site's WebForms login and booking page, the per-course teacher lookup, the report system's score images and their recognition, `PhysicsExperiment.json`, the session, the state holder, and the projection of a booking onto the week grid. |
| `data.fetch` | `FetchResult` — a value that is either fresh or served from the cache, with the reason. |
| `data.ids` | The IDS/CAS protocol: endpoints, both AES schemes, page parsing, HTTP calls, captcha signing, the second-factor client, models. |
| `data.net` | Ktor client factory, persistent cookie store, URL resolution. |
| `data.schoolcard` | The campus card system: the OAuth landing page and its `openid`, the balance scrape, the range transaction query, the two caches (balance + today's totals, and a window's flows), the tile's state holder and the page's. |
| `data.session` | `IdsSessionRepository` (the login state machine), `CredentialStore`, and the two brokers that hand interactive steps to the UI. |
| `data.timetable` | The registrar's timetable: the eHall endpoints, the row parser and the schedule-adjustment merge, the 61-block grid's arithmetic, the `ClassTable.json` cache, the session and the state holder. |
| `di` | Koin modules. |
| `ui.home` | The shell: the bottom bar, the three pages, and the home-screen grid packer. |
| `ui.experiment` | 实验信息 — the three lists a booking's sittings fall into, the mark's picture, the sentences around them, and the overlay that hands the bookings to the timetable's grid. |
| `ui.icon` | Material Symbols as an icon font: `ProvideMaterialSymbols` and `SymbolIcon`. |
| `ui.login` | Login screen, captcha dialog, second-factor dialog, and their view model. |
| `ui.schoolcard` | 校园卡 — the range picker, the flow table and the page's summary, plus the wording its two headings are built from. |
| `ui.settings` | 设置 — the tab's category entries, and the two pages behind them: 账号设置 (the IDS account and the lab site's credentials) and 课表设置 (the four switches the grid's appearance is made of). |
| `ui.timetable` | 我的日程表 — the week strip and its 5×5 overview, the 61-block grid with its period column, date row and class cards, the current-time line, the class palette, and the two list pages. |
| `ui` | `AppNavHost` and `AppDestination`: which pages exist and what opens each one. |

## Icons

Icons come from **Material Symbols Sharp**, shipped as a font and selected by *ligature name* — the
glyph is produced by writing the icon's name as text, so `SymbolIcon(Symbol.Visibility, …)` renders the
eye. Both variants are bundled: outlined (`material_symbols_sharp_24_400`) as the default and filled
(`…_filled`) for selected states.

This is the same font, library and approach as the Rephoto project in this workspace, using
[`dev.tclement.fonticons`](https://github.com/tclement0922/fonticons) — which is fully multiplatform, so
the icons keep working when iOS and desktop targets are added.

Anything that draws an icon must sit inside `ProvideMaterialSymbols`, which `App` already does; the
library deliberately throws rather than rendering a missing icon as tofu.

The two remaining Rephoto fonts (`geist_mono_regular`, `playwrite_us_trad_regular`) are text fonts for
its photo editor and are **not** copied — nothing here uses them, and unused Compose resources are
packaged into the APK.

## Typography

The type scale is `MaterialTypeStandard` in `com.nevoit.material.theme`, and every style in it is set in
`FontFamily.Default` — the device's own face — so text follows the system. One call site is not: the
campus cards' numbers are set in a **static instance of Google Sans Flex**, cut at medium weight and
fully rounded.

The file in `composeResources/font/` is generated from the family's variable font rather than shipped
as it is, because a variable font would cost 4 MB to draw eight numbers:

```sh
fonttools varLib.instancer \
    GoogleSansFlex-VariableFont_GRAD,ROND,opsz,slnt,wdth,wght.ttf \
    wdth=100 wght=500 GRAD=0 ROND=100 opsz=20 slnt=0 --static \
    --output=shared/src/commonMain/composeResources/font/google_sans_flex_w100_r100_500.ttf
```

`--static` drops the variation tables and leaves a 132 KB static font, whose `name` table is rewritten
to say what it is ("Google Sans Flex Rounded Medium"). It is applied at the two card call sites
(`rememberCardValueFontFamily`) and **not** to `MaterialType.title3`, because that style is also the
slider captcha's heading — a face chosen for the card numbers should not silently restyle a login
dialog. Google Sans Flex is under the SIL Open Font License 1.1, and the notice travels inside the file
(`name` IDs 13/14), which is what that licence asks for. The family has no CJK coverage, so the Chinese
in a card value falls through to the system face and only the digits and Latin change.

## Strings

User-visible text is a **resource**, not a Kotlin `const`: `composeResources/values/strings.xml`, read
through `stringResource(Res.string.…)`. That is this project's `strings.xml` — Compose Multiplatform's
resource directory, the same file shape and the same `%1$s` placeholders — and it is where every new
sentence goes.

Two rules come with it, and both are about where the text *isn't*:

- **a screen has no sentences in it.** It reads a resource at the point it draws, so the wording of a
  page is one file to look at and one file to translate. Parameterised strings take their arguments
  rather than being concatenated, so a translation can move a value within its sentence;
- **`data` carries no text at all.** A cache hint is an enum whose `key` travels through
  `FetchResult.hintKey`; the sentence for it is mapped from that value where it is drawn
  (`ExperimentCacheHint.messageRes`). A failure with nothing to show is classified by the same function
  the cache notice uses, so the page prints a sentence rather than an exception's `toString()`, and a
  booking whose teacher the plan grid did not name carries `null` rather than the word 未提供.

What this costs is one import per string (they are extension properties on `Res.string`, so `Res` alone
does not bring them into scope), which is why a page that shows twenty strings opens with twenty import
lines. That is the price of not having a second string table.

The modules from the earlier slices — `ui.timetable.TimetableStrings`, `ui.home`'s tile labels,
`EnergyCacheHint.message` and `TimetableCacheHint.message`, and the sentences inside
`ui.login` and `ui.schoolcard` — still carry their text in Kotlin. They are the older arrangement; moving
them is mechanical, and it is the follow-up rather than a second convention to add to.

## Theming

Colours are generated, not written down. The theme seeds
[MaterialKolor](https://github.com/jordond/MaterialKolor)'s colour maths with the device's own accent —
`android.R.color.system_accent1_600`, the tone Material 3 itself uses for `primary` — and derives the
entire palette from the resulting `TonalSpot` scheme, so nothing follows the wallpaper by accident.
Below Android 12, where the resource does not exist (`minSdk` here is 29), the Material 3 baseline
seed stands in.

Only `material-color-utilities` is taken, **not** MaterialKolor's Compose wrapper: the wrapper hands
back an `androidx.compose.material3.ColorScheme` and depends on Material 3, which this project does not
use. The palette lives in `com.nevoit.material.theme`, and `materialColorsFromScheme` — covered by
`MaterialColorsTest` — is the one place where scheme roles become palette roles.

The timetable generates a second, smaller palette the same way: sixteen seed colours — the project's own
Tailwind tokens — each harmonized towards that same accent and run through the same `TonalSpot` scheme,
so the grid's cards are Material container pairs too. See [The timetable](#the-timetable).

### Components

With Material 3 out of the picture, the widgets the screens use live in
`com.nevoit.material.core.component`: text, icon, divider and gap, plus surface, the button family, a
labelled text field, a checkbox, a chip, both progress indicators and a dialog. They are deliberately
thin — colours from `MaterialTheme.colors`, shapes from `MaterialTheme.specs`, text styles from
`MaterialTheme.type` — and each implements only what its call sites actually use, so there is no
half-used parameter and no second design system to keep in sync.

`ui-tooling` is excluded for the same reason: the preview runtime pulls Material 3 in with it. Only the
`@Preview` *annotation* is kept. Adding tooling back — or rendering previews in the IDE — means
accepting Material 3 in the APK again.

## Navigation

`core.navigation` is a single page stack — `Navigator` holds `PageEntry`s with stable ids,
`NavigationHost` renders every entry and owns the transitions, system back, saveable state and a
`ViewModelStore` per page. A page keeps its state and view models while it is merely covered, and both
are released after its exit animation. There is no route graph and no deep-link parsing: a destination
is a value, and the host dispatches on it with a `when`.

`ui.AppNavHost` is where that stack is actually used: `AppDestination` names every page, the shell is the
root, and each branch renders one screen. Adding a page is three edits — a variant, a branch, and
whatever opens it — and the `when` has no `else`, so a variant without a screen fails the build rather
than opening a blank one. Pages do not navigate each other: a screen is handed the callback it needs
(`onBack`, or an `onOpen…` collected in `ui.home.TileActions`), which keeps every route in one file and
lets a screen be built without a navigator around it.

Because the shell is the root, a page opened from a card lands **over** the tabs: their state, scroll
positions and loaded cards stay alive underneath, and back returns to them instead of leaving the app.
A page drawn over the shell has no bottom bar to leave by, so it shows its own back control:
`ui.home.BackButton`, which lives there rather than in any one page because every overlay page needs it
and none of them owns it. It is passed into whatever bar the page draws — the timetable's own, whose
actions are a menu and an error light — since `com.nevoit.material` knows nothing about routes or icons.

It was ported from `Bilineo/app/src/main/java/com/nevoit/bilineo/core/navigation`, which was written
for Android. Three things had to change:

| Bilineo (Android) | Here (Compose Multiplatform) |
|---|---|
| destinations were `Parcelable`, saved with a `listSaver` of parcels | destinations are `@Serializable`; the stack is saved as `Long`s and JSON strings, which is what `rememberSaveable` carries on every platform |
| `androidx.activity.compose.BackHandler` called from the host | a `PlatformBackHandler` expect/actual: the Android actual delegates to `androidx.activity.compose.BackHandler`, and platforms without a back gesture will be no-ops |
| slide distance from `LocalWindowInfo` | measured by the host itself with `BoxWithConstraints`, so two hosts side by side each slide by their own pane width |

That last one is also the answer for the wide-screen two-pane layout the original Flutter app had: it
is two `NavigationHost`s, not a special mode. Everything else — the lifecycle owners, the animation,
the pointer barrier on covered pages — moved across unchanged.

A hand-rolled back handler is also why no `ui-backhandler` artifact is needed: it is experimental,
deprecated in favour of `NavigationEventHandler`, and would exist only for Android's sake.

Common code here avoids `SnapshotStateList` on purpose — the visible-page list is a plain `List` in a
state holder. On Android that type implements `android.os.Parcelable`, and a common source set cannot
resolve a supertype that only exists in the Android source set, which surfaces as *"Cannot access
'android.os.Parcelable' which is a supertype of 'SnapshotStateList'"* during IDE analysis. Keep
`mutableStateListOf` out of saveable state, or give it an explicit `Saver`.

`NavigatorTest` covers the stack itself and the saved-state encoding, because the encoding is the part
the port changed.

## Startup

`XdNextApplication.onCreate` installs the log writer, initialises `PlatformContext` and starts Koin — and
then it starts `AppWarmUp`, which is the interesting part.

Koin's bindings are lazy, so each one is built wherever it is first asked for, and the first thing that
asks is the composition: `App` injects the login view model, and the Home shell injects three
repositories. That put a Ktor client, a KeyStore read and three cache reads with their JSON decodes — the
timetable's `ClassTable.json` among them — on the critical path of the first frame, synchronously, on the
main thread.

That work cannot simply be deferred, which is why it is *moved* instead. The three repositories read their
caches **in their constructors** on purpose: a cached card belongs on the first frame rather than after a
coroutine has run, and off campus a cache may be the only timetable there will ever be. Deferring it
would trade a slow first frame for an empty one that fills in, which is the worse trade at the one moment
there is nothing else on screen. `AppWarmUp` therefore builds the same singletons in the 150–300 ms
between `Application.onCreate` and the first composition, on `Dispatchers.Default` and across four
concurrent steps. A step that has not finished by then is not a failure — the composition asks Koin for
the same singleton and waits on Koin's own lock for the remainder — and a step that throws is logged and
dropped, because a warm-up is an optimisation and must never be able to fail a launch.

The course palette is generated the same way from the other end: `ProvideClassPalette` computes it on
`Dispatchers.Default` *after* the first composition has been committed, and a card drawn before it exists
falls back to the theme's own container pair. See [Theming](#theming). The one startup cost still on the
main thread is the two Material Symbols fonts, which Compose Resources reads where they are named —
inside `ProvideMaterialSymbols`.

Every phase reports itself against a single mark, tagged `[Startup]`: `Application.onCreate`, each step of
the warm-up, the first composition, the icon fonts and the palette. The gap between **first composition**
and **first frame** is what composition itself cost, which is the number to watch whenever something is
added to that tree.

## The energy query

`data.energy` is the first business backend behind the IDS session, and it is the one that has to be
exact, because every later module follows the same shape. A refresh is five calls, each depending on a
cookie or an identifier the previous one produced: the OAuth hop through IDS (whose redirect chain ends
with a one-time `code`), `OauthGetUserInfo`, `H5UserIDLogIn` (which yields the node owning the meters),
`H5QueryMeterList`, and `GetMetRead` — the electricity meter's last month, then the water meter's last
year. The system is reachable **only from the campus network**, so showing the cached reading after a
failed refresh is the normal path off campus rather than an exceptional one.

The IDS session grew two methods for it, and they are the original's own two: `loginForService` returns
the *first* `Location` of the chain (the login lock is released at that point, because the lock exists
only to stop two logins stealing each other's single-use ticket), and `followRedirects` walks the chain
to its end and hands back the last response, which is where the `code` lives.

Three things in this protocol are not guessable from the original source. Each is pinned by a
measurement rather than a reading:

- **The `content` parameter is percent-encoded twice.** The original applies `Uri.encodeComponent` to
  its Base64 and hands it to Dio, and Dio encodes every query value again (`encodeMap(..., isQuery =
  true)`, dio ≥ 5.0.2). `reference/probe/energy_encoding_probe.dart` replays the five
  calls through a capturing adapter against the reference's own locked dio and prints the request line,
  headers and body of each; that is where the `%252F`/`%253D` in `EnergyApiTest` comes from. The server
  un-escapes twice, and a single pass would send it Base64 it cannot read.
- **Its AES is a third scheme.** Key and IV are both the ASCII `1234567812345678`, and the padding is
  standard PKCS#7 — unlike the IDS password scheme, which hand-rolls its padding over a 64-byte prefix
  and derives its key from the login page. Vectors from Node's `crypto` and from the reference's own
  `encrypter_plus` agree with `EnergyCryptoTest`.
- **The electricity meter is picked positionally.** The original read the *first* row's `MediumCode`
  and assumed the other row was the other medium, instead of searching for the electricity code. It is
  reproduced as it stands: a node that ever returned three meters would otherwise be interpreted
  differently here than there.

The on-disk side mirrors the original too: `EnergyInfo.json` in the support directory, whose
*modification time* is the "fetched at" stamp — so a copied file cannot lie about its age — and
`ElectricityHistory.json`, a remain-over-time series keyed by the meter's own reading day rather than by
when the app ran, capped at fifteen points. A cached value is never an error state: the refresh failure
becomes an `EnergyCacheHint` beside the value, which is the original's behaviour and the right one.

The state holder exposes a single immutable snapshot instead of the original's four separate signals,
so a frame cannot be composed from two different fetches. The date arithmetic is the original's too:
`package:time`'s `shift` overflows an impossible day into the next month (2026-03-31 minus a month is
2026-03-03), where `java.time` would clamp it, and the electricity window's start moves by those days.

## The campus card

`data.schoolcard` is the second backend behind the IDS session, and the one whose entry point is not an
API call: the card system (`v8scan.xidian.edu.cn`, the site behind the card's payment QR code) hands out
its session as a hidden `openid` input on the page IDS redirects to. So a refresh is: log in for the card
service through IDS, walk the redirect chain to its end, read `openid` off the page that chain landed on,
then read `openMyAccount?openid=…` for the balance — HTML, scraped — and POST `queryCardSelfTradeList`
for the day's transactions — JSON. The handle is reused for five minutes, as in the original.

Two things differ from `EnergySession` on purpose:

- **a day's totals are cached with the day they describe.** `SchoolCard.json` holds the balance, and —
  only while it is still true — today's spending, stamped with the date those numbers are for. A balance
  read yesterday is still the balance; "spent 12.30 today" is not, so a cache from another day yields a
  balance and an *unknown* spending figure, and the tile says so. The alternative, caching the number
  without its day, is a wrong answer under the label 今日支出 that nothing on screen could reveal.
- **the two reads are independent.** A transaction query that fails still leaves a balance worth showing,
  and the original's own card page and home page each did only one of them. Neither is thrown away for the
  other's sake — half a card is a card, and only a pass where *both* reads failed becomes a failure. That
  is also what triggers the retry, since a stale handle is refused by both requests; what is *not*
  inherited is the original's `forceRefresh`, which re-entered the very check it was meant to bypass.

Both live cards are cached on the device and refreshed once per process, from `ui.home.CardRefresher`.
The guard lives on that object rather than in a composition, because the focus page is unmounted when the
user leaves the tab and rebuilt on the way back — which is what made the electricity card re-query on
every visit. See [`docs/card-cache.md`](docs/card-cache.md).

The money is summed in whole cents, and the day's rule is the original's own
(`school_card_window.dart`'s `moneySunUp`): a purchase is negative and a top-up positive. What is *not*
inherited is letting the two cancel out and then choosing the wording from the sign — that turns a
12.30 元 day into an "income" of 187.70 元 as soon as a 200 元 top-up lands. So the tile reports spending
and top-ups separately, and 今日支出 means spending.

Two defects in the original are not reproduced, and both are the kind that only show up as a wrong
number:

- its retry after a failure was supposed to mint a fresh `openid`, but `forceRefresh` re-entered the same
  five-minute validity test it was meant to bypass, so the retry repeated the identical request;
- its balance came from positional navigation (`li > :nth-child(2) > :nth-child(2)`) with a **sentinel
  string** as the failure value, which the home card then rendered as a balance. Here the page is still
  read positionally — that is the layout the code was written against — but a page that does not match
  falls back to the first amount in its own text, and finding nothing returns null so the tile can say
  so.

What remains unmeasured is the account page's exact markup: unlike the energy protocol, there is no
recorded wire capture for it, so the positional path is a reading of the original's source rather than a
measurement. The parsing is therefore tested against markup of that shape, and against a page that does
not match at all.

### The card page is a range query

校园卡 (`ui.schoolcard.SchoolCardScreen`) is the original's `school_card_window.dart`, and what that window
is is a *question*: the transactions between two days. So the page holds the days as well as the rows
(`SchoolCardRange`), the summary at the top sums **those** rows — not a second reading of the card — and
changing the days re-queries the list:

- **the days are picked from a calendar dialog** (`SchoolCardRangePicker`), which is the original's
  `calendar_date_picker2` range dialog in the parts that matter: a month with arrows either side of its
  name, a 7-column grid, two taps to choose a window, 确定/取消 underneath. Days after today are not
  selectable, because the card system has nothing to say about them, and a window reaching into next week
  would answer with a partial list that looks complete.
- **the page opens on today**, so its summary says 今日支出 and shows the same figure the campus tile does;
  widening the range relabels it 本区间支出. The original opened on the month-to-date instead. Both are the
  same query — what the page opens on is a choice about what to show first, not about what can be asked.
- **a new range clears the rows** before the query runs (`SchoolCardFlowsRepository.selectRange`). Rows
  describe a window, so drawing an earlier window's list under a new heading would be the same wrong answer
  a stale total would be. A refresh of the *same* range keeps them, which is what "refreshing" means.
- **the rows are filtered to the requested range** after they arrive (`transactionsIn`), rather than
  trusted from the server: the two dates are the server's idea of the window, and a row it returns outside
  them would otherwise be drawn under a heading that named other days. The same pass produces the summary's
  figure, so the number above the table is the table's own arithmetic rather than a second reading of it.

A second cache file backs it: `SchoolCardTradeList.json` holds a window's rows **together with the window**,
and is reused only when the stored window *covers* the requested one. That is the "a value is stored with
what it describes" rule applied to a query rather than to a day — rows with no window are the same mistake
as a spending figure with no date — and covering is what makes the cache useful rather than merely safe: a
visit that read the whole month has already read today's rows as part of it.

The page asks for a query when it opens, instead of only when it has nothing, because spending changes
while the app sits there; the cache is what covers the seconds that query takes, off campus included. A
failure never blanks what is on screen: the rows stay, with the reason
(`SchoolCardCacheHint.flowsMessage`) under them.

## The timetable

`data.timetable` is the third backend behind the IDS session, and the first that is *shaped* rather than
merely fetched. A refresh is a sequence whose later steps depend on earlier answers: log in for the
registrar's CAS service and walk the redirect chain (the chain is what sets eHall's own session cookie —
the endpoints are refused without it), read `dqxnxq` for the semester the registrar currently considers
current, read `cxjcs` for the day that semester starts teaching, then `xskcb` for the timetable,
`cxxsllsywpk` for the courses it never placed, and `xsdkkc` for the schedule adjustments.

Four things about it are decisions rather than transcription:

- **The semester is settled before the timetable is asked for**, and it is the *newer* of the
  registrar's current semester and the one last used on the device. The remote code normally wins — the
  term rolls over without anything on the device changing — and the local one wins only when it is
  further ahead, which is how a user who has deliberately looked at a future semester keeps looking at
  it. The original split this across a `SemesterController` and a preference; the same rule runs here
  inside one refresh, which is one login fewer.
- **A semester the registrar has not published is a result, not a failure.** eHall serves an empty
  timetable and says why in `extParams`, and the page has an empty state that names the semester rather
  than an error.
- **The schedule adjustments are read last, and their failure is tolerated.** The original logged that
  one endpoint and carried on, and a timetable without its 调课 annotations is worth far more than no
  timetable.
- **The adjustments are folded in before anything is drawn.** `TimetableParser.mergeChanges` is a port
  of the loop at the end of the original's `_getEhall`, and it is the most intricate thing in the
  module: a 补课 for a course the timetable has never mentioned brings the course with it, a 停课 clears
  the weeks it names, a 调课 clears them and adds the class again at its new day and periods, and an
  adjustment the registrar has issued *both ways* cancels out instead of producing two classes.

The page's own geometry is the original's, and it is not what it looks like. The grid is **61
equal-height blocks**, not eleven equal periods: each period is five blocks, 午休 and 晚休 are three
each, and a class that starts after a break therefore starts three blocks down rather than at a multiple
of five. That is why a period's position is a function and not a multiplication, and why a period's
start and its predecessor's end are not inverses at the boundary. A class's rectangle, an exam's and a
two-hour experiment's all share that one coordinate system, which a period-based one could not do.

What arrives next goes through two seams rather than through edits to this code. **`TimetableOverlay`**
is a source of extra blocks for a given week: exams, physics experiments, other experiments and locally
added courses each become one, and the grid sorts and merges them exactly as it does courses — the
original hard-coded all four loops inside one method, so adding a source could change how courses were
laid out. **`ClassBlockEntry`** is what a card holds: a merged card keeps every arrangement in it, and
every `when` over the sealed type fails to compile until a new variant is described, which is what the
detail sheet and the calendar export will need.

The grid's colours are generated too, and the original's sixteen-swatch list survives only as sixteen
**seeds** — one per course, in the original's order, so a timetable's first course is still red. The
seeds themselves are the project's own Tailwind tokens (`Red500`, `Stone500`, …) rather than Flutter's
hand-picked Material 2014 tones, because a seed is all that is needed once the tones are derived: it is
harmonized towards the system accent and then run through the same `TonalSpot` scheme the chrome uses,
so a card is a Material `primaryContainer` fill with `onPrimaryContainer` written on it, and the grid
belongs to the device's palette. The whole set is generated once per launch at the root —
`ProvideClassPalette`, mounted above the navigation host in `App` — because it is a pure function of the
accent and costs thirty-two schemes, sixteen colours in two themes; nothing is cached on disk, which is
what makes it follow the wallpaper, and generation happens off the main thread, after the first
composition has been committed (see [Startup](#startup)). The one thing the palette no longer does is adjust a colour per
class: a finished class is now the same two colours at half opacity. That styling is **off** by default,
so a finished class looks exactly like a running one until the setting is turned on; the four switches
behind it and the current-time line's own three are on 课表设置 (`ui.settings.TimetableStyleScreen`), which
writes them through `ui.timetable.TimetableAppearanceStore` — the store exists so the change reaches the
grid that is already composed *underneath* the settings page, rather than the next time it is opened.

Nothing in the timetable's request shape is measured the way the energy protocol's was: there is no
recorded wire capture for these five calls, so the headers, the form-encoded bodies and the row field
names are a reading of the original's `network_client.dart` and `classtable_session.dart`. What *is*
verified is everything downstream of it, and the server's acceptance: a device on the campus network
completed the whole sequence — login, `dqxnxq`, `cxjcs`, `xskcb`, `cxxsllsywpk`, `xsdkkc` — and the
grid, the week strip, the palette and the current-time line were all drawn from what came back. The
same run is what showed the period column's width being overridden into full width, a defect no unit
test could see and every screenshot could.

One asset is deliberately not carried over: the original's empty-week illustration. Its own
`assets/README.MD` puts it outside free software, and the page draws the sentence without it.

## The physics experiment system

`data.experiment` is the fourth backend, and the first that is **not** behind IDS. The physics lab is a
WebForms application of its own at `wlsy.xidian.edu.cn`, and the student signs into it with their student
number and a *separate* password — which is why this module reads neither the IDS session nor the IDS
password. What the settings page stores is a pair of credentials, and the account defaults to the one
this app signed in with: the same identity, in a system that has never heard of IDS.

The module has two readers, and they are one value: `ui.experiment.ExperimentScreen`, reached from the
campus page's 实验信息 tile, and `ui.experiment.rememberExperimentOverlays`, which hands the bookings to
the timetable's grid as an overlay so a lab is placed by the same arithmetic as a lecture. Both read
`ExperimentRepository`; neither owns a fetch.

A refresh is five steps, each depending on what the last one answered:

1. `POST PhyEws/default.aspx`, the login postback. Its view-state values are the original's own literals
   — they are stable for this application and a request that looked them up again would be a *different*
   request from the one that has been logging in successfully for years. A successful login answers
   **302**, so the status is the verdict here, and a refusal's own sentence is in the page it sends back
   (`login1_Label1`, with its `<font>`/`<br>` markup turned into a sentence);
2. the `Cookie` header, rebuilt from that response. The original dropped the `HttpOnly` cookies and
   **substituted a fixed value for the student-name cookie** ("This guy find out the secret"), and both
   are reproduced as they stand: the site checks that the cookie is present, and the real value is the
   student's name in Chinese, which a hand-built header would have to encode;
3. `GET PhyEws/student/select.aspx` — the booking table. Its columns are positional in the original's own
   order (1 the course, 3 the time, 4 the day, 5 the room, 9 the reference), and every one of them is read
   from a `<span>` inside the cell. The page writes the day as **`9/15/2026`** — month, day, year, which
   the original's `DateTime(dateNums[2], dateNums[0], dateNums[1])` says too — and the time column as
   `星期三下午15:55-18:10` or `星期四晚上18:30-20:45`, whose weekday is what confirms the date's order: the
   first reading of it here was the other way round, and every booking vanished from the grid;
4. the **teachers**, which are not on that page at all. `PhyEws/student/course.aspx` carries a plan grid
   for *one course at a time*, and switching it is a postback of the page's own hidden fields — so a
   refresh reads the grid once and posts it back once per course that has a booking;
5. the **marks**, from the report system on the same host. This is not a page: `wgyreport.dll` is a "UNI
   GUI" application whose state is built by replaying input events, so a session has to send a window-size
   report, a mouse move, an activate, a resize and a click carrying the credentials before the same
   endpoint will answer a data query. The marks themselves are *pictures* — `9.5`, `已上传`, `未上传` —
   and are identified by hashing the top-left 50 × 20 pixels of each image (FNV-1a over the opaque
   pixels' RGB) and looking the hash up in a table generated from the lab's own images, which is the
   original's `generated/score_hashes.g.dart`, copied value for value. A mark the table does not know is
   reported as unrecognised rather than guessed at, and the page offers to show the picture it came from.

The teachers are the one thing the original did differently: it read the plan grid **once per booking**
inside a pool of three, which is the same grid three times over. Reading it once per refresh and posting
it from course to course is the same answers with one page read instead of one per booking.

Where the module is deliberately **not** the original's:

- **a booking whose teacher cannot be found is kept**, with 未提供 as its teacher. The original threw, and
  because the lookups ran inside one `Future.wait`, a single missing name lost every booking in the list.
  The same goes for a booking whose course is not in the plan grid's option list: the original asserted
  non-null there and crashed;
- **the three sections are classified against the clock.** The original compared against *now plus one
  day*, which filed a sitting that ends this evening as 已完成 from midnight onwards — a booking the user
  is about to walk into, drawn under a heading that says it is over;
- **one unreadable row is one row lost.** The original parsed a row's date with `int.parse` and indexed
  its cells directly, so a row the site spelled differently threw and took the whole list with it;
- **the score-hash window is clamped to the image.** The original read 50 × 20 pixels unconditionally,
  which throws on an image that is smaller — the failure being a page with no list at all, over a mark.

What a device has now shown, and what it has not:

- **measured**: the host is HTTP-only — Android's cleartext policy refuses it, which is why the module
  ships a one-host exemption in `network_security_config.xml` rather than a blanket
  `usesCleartextTraffic`; the login postback is accepted (the booking page comes back); the course-name
  column parses; the date column is `M/d/yyyy`, cross-checked against the weekday the page prints beside
  it; and the time column is the two shapes above, which the afternoon/evening marker test separates
  correctly;
- **unmeasured**: the plan grid behind the teachers (its `TimeN_*`/`Teacher_*` spans were never seen — the
  pairing and the selector are a reading of the original), the report system's five-event handshake, and
  the score-image table's coverage of the pictures the lab serves today.

Everything downstream of the parse is verified by tests rather than by a device: the sitting arithmetic,
the pixel hash, the grouping, the cache, and the grid's own placement — and the failure modes are
contained by construction, because a booking that cannot be read is one booking logged and dropped
rather than a list, and a mark that cannot be recognised is reported as unrecognised rather than shown.

## Deliberate deviations from the original

These are choices, not oversights. Each one is something the original either could not do or got
wrong in a way that is worth not reproducing.

- **A physics experiment booking is never lost to a missing detail.** The original threw when a booking's
  teacher was not in the plan grid, when its course was not in the grid's option list at all, and when a
  row's date did not parse — and every one of those threw away the *whole* list, because the lookups ran
  inside a single `Future.wait`. Here a missing teacher reads 未提供 and an unreadable row is one row
  lost; see [The physics experiment system](#the-physics-experiment-system).

- **A mark is never invented.** The lab publishes scores as pictures, and the original identified them by
  hashing pixels against a table generated from the lab's own images. That table is a snapshot — a mark
  the lab re-renders hashes to nothing here — so an unrecognised picture is reported as unrecognised
  rather than shown as a score, and the page offers to show the picture itself, which is the only thing
  that makes the answer actionable.

- **Material 3 is not a dependency.** The screens are built on `com.nevoit.material` instead — see
  [Theming](#theming). Keeping the official Material 3 would have meant two material design systems in
  one app, and the vendored one is the one that owns the palette.

- **Credentials are encrypted at rest.** The original stored `idsPassword`, `sportPassword`,
  `experimentPassword` and `schoolNetQueryPassword` as **plaintext** in SharedPreferences, with no
  Keystore or Keychain usage anywhere. Here they go through `SecureStore`, backed on Android by an
  AES-256-GCM key held in the Android Keystore.
- **Passwords stay out of the logs.** The original's Dio logger printed request bodies and only
  filtered by URL host and a few query keys, so form-encoded passwords leaked into logs that its
  crash reporter emailed to the maintainer.
- **Login state is observable, not global.** The original kept a mutable global `loginState` that
  every module read and wrote. Here it is a `StateFlow` on the repository.
- **No custom preference-file compatibility.** The original's preference file
  (`FlutterSharedPreferences`, `flutter.`-prefixed keys) is read by its shipped home-screen widget.
  This app has its own storage, so it does not inherit that constraint.
- **The login form's hidden fields are parsed by a tested function.** In the original this scraping
  shared a file with the entire network flow and had no tests, so a server-side template change broke
  login with no failing test and an unhelpful error.
- **The captcha solver's missing pause was found.** The original awaited 200–400 ms before each
  verification, and the server refuses a submission that arrives too soon — running the original's own
  code with that line removed scores 0/8 against the live server, and 8/8 with it. That one line was the
  entire reason automatic solving appeared broken. Two further defects were fixed while looking: the
  match score was an unbounded regression slope rather than a correlation, and the search range collapsed
  for full-size piece images. [`tools/README.md`](tools/README.md) has the bisect table and, more
  usefully, the two measurement traps that made this take far longer than it should have.
- **Interactive login steps are injected, not global.** The original answered second-factor challenges
  through a mutable process-global handler installed by whichever screen was mounted, so a background
  session refresh could fail to authenticate for reasons that had nothing to do with the server. Here
  both the captcha and the second factor arrive as dependencies and publish their state.

- **The low-balance reminder is a boolean and a threshold, not a sentinel.** The original encoded
  "reminder off" as a threshold of `-1` and derived two signals from it; here the two facts are one
  `LowElectricityWarning(enabled, threshold)`. Its `refreshElectricityInfo({force})` parameter, which
  nothing in the original ever read, is not carried over either.

- **The caches report *why* they are being shown.** `FetchResult.hintKey` is the original's, and the
  energy hints keep the original's own translation keys — but each variant also carries the sentence the
  UI shows today, because this app has no i18n layer yet. One variant of the original is deliberately
  absent: it could report "不在校园网" because its own HTTP layer raised a dedicated
  `NotSchoolNetworkException`, and nothing here can tell that failure apart from any other transport
  failure. It is reported as a network failure whose message names the campus network, which is the
  actionable half.

- **A make-up session is added once.** The timetable's adjustment merge left a 补课 entry for a course
  already in the timetable in its work list after applying it, so its loop ran again and added the same
  session a second time; the loop only ended because a "no progress" guard had been added to stop it
  spinning. The duplicate is visible — the two copies merge into one card, and that card then claims
  "还有 1 个日程" about a class the student has once — so the entry is removed once applied and the loop
  is a single pass. The same port also keeps the class-detail index and the position in the arrangement
  list in separate variables; the original reused one for both and compared it against a list position
  to decide whether it had cleared any week, a comparison that was only meaningful by accident. The
  decision it made is kept, it is just made from a boolean.

- **A timetable row the registrar spells differently is skipped, not fatal.** The original indexed
  straight into `int.parse(i["KSJC"])` and would throw on a row whose field arrived as a number, on a
  week-flag string with a stray character, and on any row answered differently that semester — losing
  every other row with it. Each unreadable row is logged and dropped here, because a timetable is one
  screen of a much larger refresh.

- **The finished-class styling is a value, not a static.** The original's `CurrentTimeIndicatorConfig`
  and `CompletedClassStyleConfig` were mutable statics that read themselves out of the preference file
  and wrote themselves back, which is why nothing could draw a grid with them without a preference
  store. They are one immutable `TimetableAppearance` here, defaulted to the original's values and
  handed to the drawing code — the settings page that owns the preferences will supply it.

- **The week strip browses; only a tap selects.** The original's strip was a `PageView` that wrote the
  chosen week back whenever it settled on a page, so dragging towards week 15 repainted the grid fifteen
  times on the way and the tint followed the finger rather than the selection. Here the strip only
  scrolls — the selection is changed by a tap, or by the grid being swiped — and it uses the project's
  own velocity-preserving snap fling (`com.nevoit.material.core.interaction.rememberSnapFlingBehavior`)
  so a week button is never left half-cut at the edge, which would read as "this is the week" when it is
  not. The tint follows the *selection* rather than the strip's position, so a scrolled-away week keeps
  it. This is a deliberate departure, chosen by the product owner rather than inherited.

## Building and testing

```sh
./gradlew :androidApp:assembleDebug     # debug APK
./gradlew :shared:testAndroidHostTest   # unit tests
```

Tests include golden-vector checks for all three AES schemes and exact-value checks for the
second-factor protocol. The AES expected values come from Node's `crypto`, an implementation that
shares no code with `cryptography-kotlin`; see [`tools/README.md`](tools/README.md) for how to
regenerate them. The energy scheme's vectors were confirmed a second time from the reference's own
`encrypter_plus`, and the request shapes its tests pin come from the same probe's recorded wire bytes.

The captcha's answer payload and signing are covered by exact-value tests, because that part *is*
verifiable and a mistake there is rejected without explanation.

The timetable's arithmetic is covered the same way, and for the same reason: the block geometry, the
week-flag decoding, the schedule-adjustment merge, the generated course palette — its seeds and their
order, that harmony pulls a seed towards the accent and never away, that the light and dark roles flip
the way a container pair has to — and the opacity behind the finished-class styling all have tests,
because each of them fails by drawing something *plausible* — a class three blocks from where it
belongs, one card where there are two, a course colour that has drifted off the theme. What no test here
can reach is the five eHall calls themselves; see [The timetable](#the-timetable) for what that leaves
unmeasured.

**Read the log before guessing.** `appLog` is a Kermit logger, and Kermit ships with *no* writers
configured — so until `installPlatformLogging()` is called (it is, from `XdNextApplication.onCreate`)
every line this app reports is discarded, and a run on a device is a run with no evidence at all. That
cost a diagnosis once: a refresh that never returned looked like a hung request and was in fact a login
parked on an unanswered dialog. `adb logcat -d -s XDNext` is the whole of it.

Beyond the unit tests, [`tools/server-probes/`](tools/server-probes) holds small Java programs that
query the **real** campus servers. They validate the parts no unit test can reach — the exact login-page
markup, the accepted login request shape, whether the server accepts a signed captcha answer, and which
slider positions it will take — and [`tools/README.md`](tools/README.md) records what they measured.
Run them first when login breaks.

The energy query is verified end to end **on a device against the live server**: the campus page's
electricity tile shows the account's real balance, which it can only have obtained by logging in through
IDS, following the OAuth redirect to read its one-time `code`, listing the node's meters and reading the
electricity meter. That is what confirms the AES scheme, the twice-encoded `content` parameter and every
response field name — the server accepting them, rather than the original's source saying so.

Two things in it remain unmeasured: the water reading — nothing on screen depends on it yet, so a failure
there would be invisible — and a node that returns more than the two meters the original assumed.
`reference/probe/energy_encoding_probe.dart` is the tool to reach for when the request shape is what is
in question.

## Reference source

A copy of the original Flutter app is expected at `reference/traintime_pda-main/` for consultation.
It is git-ignored and is not needed to build.

## Licensing

The original is MPL-2.0, with some files under MIT or Apache-2.0 and a vendored module under
BSD-3-Clause. Note that the original's `assets/README.MD` states the project is not free software and
lists per-asset licensing — relevant if this port is ever redistributed with those assets.
