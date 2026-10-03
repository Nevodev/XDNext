package com.nevoit.xdnext.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nevoit.xdnext.ui.timetable.TimetableAppearanceStore
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import xdnext.shared.generated.resources.Res
import xdnext.shared.generated.resources.settings_completed_style
import xdnext.shared.generated.resources.settings_completed_style_detail
import xdnext.shared.generated.resources.settings_indicator_enabled
import xdnext.shared.generated.resources.settings_indicator_enabled_detail
import xdnext.shared.generated.resources.settings_indicator_label
import xdnext.shared.generated.resources.settings_indicator_label_detail
import xdnext.shared.generated.resources.settings_section_class_card
import xdnext.shared.generated.resources.settings_section_time_indicator
import xdnext.shared.generated.resources.settings_timetable_style
import xdnext.shared.generated.resources.settings_today_highlight
import xdnext.shared.generated.resources.settings_today_highlight_detail

/**
 * 课表设置 — how the grid draws itself.
 *
 * The four switches are the four facts of [com.nevoit.xdnext.ui.timetable.TimetableAppearance] that can
 * be *decided*; the rest of that value is the geometry and the opacity the drawing code is tuned to, and
 * the original's numeric keys for those (its HSL saturation and brightness factors) have no counterpart
 * here at all — see [com.nevoit.xdnext.ui.timetable.TimetableAppearance].
 *
 * The page writes through the store rather than into the preference file, because a change has to reach
 * the grid that is **already composed underneath this page**: the timetable is a covered page, not a
 * rebuilt one, so a settings write that only landed on disk would not be drawn until it was reopened.
 *
 * One section is three lines rather than one, and that is not cosmetic: 显示时间标签 and 高亮今日列 only
 * mean anything while 显示当前时间指示线 is on — the original's indicator is a single widget, and its
 * config's three booleans are read by it. They are still shown as ordinary switches rather than hidden
 * or disabled, because each is independent: turning the line off and on again should not also change
 * which of its parts are drawn.
 */
@Composable
fun TimetableStyleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store: TimetableAppearanceStore = koinInject()
    val appearance by store.appearance.collectAsState()

    SettingsPage(
        title = stringResource(Res.string.settings_timetable_style),
        onBack = onBack,
        modifier = modifier,
    ) {
        Section(header = { stringResource(Res.string.settings_section_time_indicator) }) {
            SwitchRow(
                checked = appearance.currentTimeIndicatorEnabled,
                onCheckedChange = { enabled ->
                    store.update { it.copy(currentTimeIndicatorEnabled = enabled) }
                },
            ) {
                SettingText(
                    title = stringResource(Res.string.settings_indicator_enabled),
                    detail = stringResource(Res.string.settings_indicator_enabled_detail),
                )
            }
            SwitchRow(
                checked = appearance.showTimeLabel,
                onCheckedChange = { shown -> store.update { it.copy(showTimeLabel = shown) } },
            ) {
                SettingText(
                    title = stringResource(Res.string.settings_indicator_label),
                    detail = stringResource(Res.string.settings_indicator_label_detail),
                )
            }
            SwitchRow(
                checked = appearance.showTodayColumnHighlight,
                onCheckedChange = { shown ->
                    store.update { it.copy(showTodayColumnHighlight = shown) }
                },
            ) {
                SettingText(
                    title = stringResource(Res.string.settings_today_highlight),
                    detail = stringResource(Res.string.settings_today_highlight_detail),
                )
            }
        }

        Section(header = { stringResource(Res.string.settings_section_class_card) }) {
            SwitchRow(
                checked = appearance.completedClassStyleEnabled,
                onCheckedChange = { enabled ->
                    store.update { it.copy(completedClassStyleEnabled = enabled) }
                },
            ) {
                SettingText(
                    title = stringResource(Res.string.settings_completed_style),
                    detail = stringResource(Res.string.settings_completed_style_detail),
                )
            }
        }
    }
}
