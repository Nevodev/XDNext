package com.nevoit.xdnext.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TopBarHeight
import com.nevoit.material.core.component.VGap
import com.nevoit.material.core.layout.ListRowAccessory
import com.nevoit.material.core.layout.ListScope
import com.nevoit.material.core.layout.ListStack
import com.nevoit.material.core.layout.SectionScope
import com.nevoit.material.core.layout.isScrolledPast
import com.nevoit.material.core.utility.NavigationBarSpacer
import com.nevoit.material.core.utility.StatusBarSpacer
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.ui.home.IconBox
import com.nevoit.xdnext.ui.shared.AppTopBar
import com.nevoit.xdnext.ui.shared.BlurShade

/**
 * A settings page that is *pushed* over the shell: the same floating title bar the other pages draw,
 * over the same inset-grouped list the settings tab uses.
 *
 * The chrome is the app's own — [AppTopBar] pinned at the top, a [BlurShade] behind it that fades in
 * once the list has moved under it, and the bar's height reserved as the list's first item so the first
 * section scrolls all the way up instead of being clipped. `ui.timetable.ClassChangeScreen` and
 * `NotArrangedScreen` still draw a plain bar in a `Column`; these pages, which are where a user *changes*
 * things, follow `ui.energy` and `ui.schoolcard` instead.
 *
 * The rows are [com.nevoit.material.core.layout.ListStack] sections rather than the shell's `ListRow`,
 * and that is the point of this file: a settings page is a list of things with a state each — a switch, a
 * value, a page to open — which is what that component's rows are (leading icon, title and description, a
 * trailing slot, and a chevron accessory). The shell's own `Row`s are for reading.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: ListScope.() -> Unit,
) {
    val colors = MaterialTheme.colors
    val listState = rememberLazyListState()
    val backdrop = rememberLayerBackdrop {
        drawRect(
            color = colors.pageBackground,
            topLeft = Offset(-this.size.width, -this.size.height),
            size = Size(this.size.width * 3, this.size.height * 3),
        )
        drawContent()
    }
    val shadeVisible by listState.isScrolledPast(0.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground),
    ) {
        ListStack(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            item {
                StatusBarSpacer()
                VGap(TopBarHeight)
            }
            content()
            item { NavigationBarSpacer() }
        }

        BlurShade(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TopBarHeight),
            backdrop = backdrop,
            visible = shadeVisible,
        )

        AppTopBar(title = title, onBack = onBack)
    }
}

/**
 * One row that opens a page: an icon, a title with a line under it, and the accessory chevron.
 *
 * Written once because every category entry on the settings tab is exactly this, and the alternative —
 * four copies of the three slots — is where the chevron ends up missing from one of them.
 *
 * It is a `SectionScope` extension rather than a `ListScope` one because that is where rows live: a
 * settings page is a stack of titled sections, and a row outside one would have no heading to belong to.
 */
internal fun SectionScope.SettingRow(
    title: String,
    detail: String? = null,
    icon: String,
    onClick: () -> Unit,
) {
    Row(
        onClick = onClick,
        leading = { SettingIcon(icon) },
        accessory = ListRowAccessory.Chevron,
    ) {
        SettingText(title = title, detail = detail)
    }
}

/** The leading badge, sized to the 32 dp box a list row reserves for one. */
@Composable
internal fun SettingIcon(symbol: String) {
    IconBox(
        icon = symbol,
        size = 28.dp,
        background = MaterialTheme.colors.segmentedControlBackground,
        contentColor = MaterialTheme.colors.onSegmentedControlBackground,
    )
}

/** A row's text: the title, and the line under it when there is one. */
@Composable
internal fun SettingText(title: String, detail: String? = null) {
    Column {
        Text(text = title, style = MaterialTheme.type.body)
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.type.footnote,
                color = MaterialTheme.colors.contentVariant,
            )
        }
    }
}
