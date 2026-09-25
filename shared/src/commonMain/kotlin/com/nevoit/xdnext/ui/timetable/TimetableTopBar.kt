package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nevoit.material.core.component.IconButton
import com.nevoit.material.core.component.Surface
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.ProvideContentColor
import com.nevoit.xdnext.ui.shared.AppTopBar
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon

internal data class TimetableMenuActions(
    val onOpenNotArranged: () -> Unit,
    val onOpenClassChanges: () -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
internal fun TimetableTopBar(
    onBack: () -> Unit,
    menu: TimetableMenuActions? = null,
    onShowErrors: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    AppTopBar(
        title = TimetableStrings.PAGE_TITLE,
        onBack = onBack,
        trailing = {
            if (onShowErrors != null) {
                IconButton(onClick = onShowErrors) {
                    ProvideContentColor(MaterialTheme.colors.error) {
                        SymbolIcon(
                            name = Symbol.Error,
                            contentDescription = TimetableStrings.LOAD_ERROR,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (menu != null) {
                IconButton(
                    symbol = Symbol.MoreHoriz,
                    contentDescription = "更多",
                    onClick = {
                        menuOpen = true
                    },
                )
            }
            if (menuOpen && menu != null) {
                TimetableMenu(
                    actions = menu,
                    onDismiss = { menuOpen = false },
                )
            }
        }
    )
}

@Composable
private fun TimetableMenu(actions: TimetableMenuActions, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 64.dp + 4.dp, end = 8.dp)
                .widthIn(min = 200.dp, max = 280.dp)
                .clip(MaterialTheme.specs.cardShape),
            color = MaterialTheme.colors.elevatedCardBackground,
            contentColor = MaterialTheme.colors.content,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                MenuItem(TimetableStrings.MENU_NOT_ARRANGED) {
                    onDismiss()
                    actions.onOpenNotArranged()
                }
                MenuItem(TimetableStrings.MENU_CLASS_CHANGED) {
                    onDismiss()
                    actions.onOpenClassChanges()
                }
                MenuItem(TimetableStrings.MENU_REFRESH) {
                    onDismiss()
                    actions.onRefresh()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, style = MaterialTheme.type.body)
    }
}
