package com.nevoit.xdnext.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.PageHeader
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.layout.VerticalStack
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.xdnext.core.log.appLog
import com.nevoit.xdnext.ui.symbols.Symbol
import com.nevoit.xdnext.ui.symbols.SymbolIcon

/**
 * 其他功能: the campus services this app does not implement itself, as links.
 *
 * The original's `toolbox_page.dart` is a list of seven addresses opened in the **system browser** —
 * there is no WebView anywhere in that project, whatever its `webview_list_tile.dart` is called — and this
 * is the same list. They are the same strings, including the OAuth and CAS hops the two systems need, so a
 * card here lands where the original's row landed.
 *
 * Laid out as a **two-column grid built from ordinary layout**, not a lazy grid: eight fixed cards neither
 * need recycling nor know their height, and a `Column` of `Row`s keeps every card the same width and the
 * last odd card half-width instead of stretched across the row. It is placed in a **single item** of the
 * page's [VerticalStack], so the page scrolls as one block.
 */
@Composable
fun ToolboxScreen() {
    val uriHandler = LocalUriHandler.current

    VerticalStack {
        item { PageHeader(title = "其他功能", extraPadding = true) }

        item {
            Column(
                modifier = Modifier.padding(horizontal = ToolboxSpacing),
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing),
            ) {
                ToolboxLinks.chunked(COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing)) {
                        row.forEach { link ->
                            ToolboxCard(
                                link = link,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    // A URL with no activity behind it is a device without a browser, and
                                    // that is not worth taking the app down for.
                                    runCatching { uriHandler.openUri(link.url) }
                                        .onFailure { appLog.w(it) { "Could not open ${link.url}" } }
                                },
                            )
                        }
                        // A half-filled last row keeps the column width rather than stretching its card.
                        repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // The shell floats its navigation bar over the page, so the last row has to be able to clear it.
        item {
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(FloatingBarSpace)
            )
        }
    }
}

/** One card: the service's icon, its name, and the address it opens. */
@Composable
private fun ToolboxCard(
    link: ToolboxLink,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(CardHeight),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tinted like the campus page's own shortcut tiles, so a link reads as the same kind of thing.
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colors.segmentedControlIndicator
            ) {
                SymbolIcon(
                    name = link.icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                )
            }
            Text(
                text = link.name,
                style = MaterialTheme.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A campus service, as the original listed it. */
private data class ToolboxLink(
    val name: String,
    val icon: String,
    val url: String,
)

/**
 * The addresses, in the original's own order.
 *
 * Every one of them is copied from `lib/page/toolbox/toolbox_page.dart`; the OAuth and CAS hops inside
 * them are what make the destination land signed in, so they are kept verbatim rather than trimmed to a
 * host name.
 *
 * The original's eighth name, 移动门户 (`toolbox.mobile`), is deliberately **not** here. Its portal is
 * reached through WeChat and has no web entry point, so a row that opened it in a browser would be a
 * dead end — better absent than broken. (`Symbol.Portal` stays in the symbol catalogue anyway: it is a
 * plain globe, and the comment beside it records what the bundled font does and does not carry.)
 */
private val ToolboxLinks = listOf(
    ToolboxLink(
        name = "缴费系统",
        icon = Symbol.Payments,
        url = "https://xxcapp.xidian.edu.cn/uc/api/oauth/index?redirect=" +
                "https://ignypt.xidian.edu.cn/revenueH5/login?opcode=MPAY&" +
                "appid=200260318155520600&state=1231231231",
    ),
    ToolboxLink(
        name = "订水系统",
        icon = Symbol.Water,
        url = "https://order.xidian.edu.cn/mobile/thirdoauth/oauth2Xidian/1",
    ),
    ToolboxLink(
        name = "后勤报修",
        icon = Symbol.Repair,
        url = "https://ids.xidian.edu.cn/authserver/login?service=" +
                "https%3A%2F%2Fids.xidian.edu.cn%2Fauthserver%2Foauth2.0%2FcallbackAuthorize" +
                "%3Fclient_id%3D869608421533880320%26" +
                "redirect_uri%3Dhttp%253A%252F%252Frepair.xidian.edu.cn%252Fappsys" +
                "%252FxidianCasLogin%252FoauthLogin%26response_type%3Dcode%26state%3Dhome%26" +
                "client_name%3DCasOAuthClient",
    ),
    ToolboxLink(
        name = "空间预约",
        icon = Symbol.Seat,
        url = "http://libspace.xidian.edu.cn",
    ),
    ToolboxLink(
        name = "网络查询",
        icon = Symbol.Wifi,
        url = "https://zfw.xidian.edu.cn",
    ),
    ToolboxLink(
        name = "物理计算",
        icon = Symbol.Calculator,
        url = "https://experiment-helper.wizzstudio.com/#/",
    ),
    ToolboxLink(
        name = "睿思导航",
        icon = Symbol.Explore,
        url = "https://nav.xdruisi.cn/",
    ),
)

/** Two cards per row, which is what a phone's width fits without squeezing the names. */
private const val COLUMNS = 2

/** The gutter between two cards, and between a card and the page's edge. */
private val ToolboxSpacing = 12.dp

private val CardHeight = 56.dp

private val IconSize = 24.dp
