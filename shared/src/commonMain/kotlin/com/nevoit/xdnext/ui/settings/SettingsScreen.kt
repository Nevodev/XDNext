package com.nevoit.xdnext.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nevoit.material.core.component.PageHeader
import com.nevoit.xdnext.ui.home.PageColumn
import com.nevoit.xdnext.ui.symbols.Symbol
import org.jetbrains.compose.resources.stringResource
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.settings_account_settings
import xdnext.shared.generated.resources.settings_account_settings_detail
import xdnext.shared.generated.resources.settings_section_account
import xdnext.shared.generated.resources.settings_section_timetable
import xdnext.shared.generated.resources.settings_timetable_style
import xdnext.shared.generated.resources.settings_timetable_style_detail
import xdnext.shared.generated.resources.settings_title

/**
 * 设置 — the shell's settings tab: the categories, and nothing else.
 *
 * The rows here are **entries rather than controls**. The original's settings page was one long scroll of
 * switches, value rows and dialogs — its own grouping was a heading over a card of six unrelated
 * controls — and this is the same information arranged one step out: a category is a page, the page
 * holds the controls that belong together, and the thing that changes is on the page named after it.
 *
 * Each entry is `SettingRow` (see [SettingsPage]): an icon, the category's name, a line saying what is on
 * the page, and a chevron. The chevron is the whole affordance — it is what tells a tap target apart from
 * a row that shows a value — which is why every entry carries one and the pages' own rows only carry one
 * when they actually open something.
 *
 * Pages are opened by the shell's navigator rather than by this file: the callbacks are handed in from
 * `AppNavHost`, which is the one place that knows what pages exist.
 *
 * The strings are read **before** the list is built rather than inside it, because a `Section`'s content
 * is a plain lambda that only *declares* rows: it runs outside composition, so a `stringResource` call in
 * there would have no composition to read from. What is captured is the resolved sentence; the `header`
 * lambdas above and below it are composable, which is why those read their own.
 */
@Composable
fun SettingsScreen(
    onOpenAccountSettings: () -> Unit,
    onOpenTimetableStyle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountTitle = stringResource(Res.string.settings_account_settings)
    val accountDetail = stringResource(Res.string.settings_account_settings_detail)
    val timetableTitle = stringResource(Res.string.settings_timetable_style)
    val timetableDetail = stringResource(Res.string.settings_timetable_style_detail)

    PageColumn(modifier = modifier) {
        item { PageHeader(title = stringResource(Res.string.settings_title)) }

        Section(header = { stringResource(Res.string.settings_section_account) }) {
            SettingRow(
                title = accountTitle,
                detail = accountDetail,
                icon = Symbol.Person,
                onClick = onOpenAccountSettings,
            )
        }

        Section(header = { stringResource(Res.string.settings_section_timetable) }) {
            SettingRow(
                title = timetableTitle,
                detail = timetableDetail,
                icon = Symbol.Calendar,
                onClick = onOpenTimetableStyle,
            )
        }
    }
}
