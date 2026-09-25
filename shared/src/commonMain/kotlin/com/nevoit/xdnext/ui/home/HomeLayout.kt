package com.nevoit.xdnext.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.Surface
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.layout.ListScope
import com.nevoit.material.core.layout.ListStack
import com.nevoit.material.core.modifier.thenIf
import com.nevoit.material.theme.MaterialShapes
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.material.theme.local.LocalContentColor
import com.nevoit.material.theme.local.ProvideContentColor
import com.nevoit.material.theme.toShape
import com.nevoit.xdnext.ui.shared.rememberCardValueFontFamily
import com.nevoit.xdnext.ui.shared.rememberValueWithUnit
import com.nevoit.xdnext.ui.symbols.SymbolIcon

@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    content: ListScope.() -> Unit,
) {
    ListStack(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        content()
        item {
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(FloatingBarSpace)
            )
        }
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.specs.cardShape)
            .thenIf(onClick != null) { clickable(onClick = onClick!!) },
        color = MaterialTheme.colors.cardBackground,
        contentColor = MaterialTheme.colors.content,
    ) {
        Box(content = content)
    }
}

/**
 * A reading-sized card: an icon over three lines on the left, and the whole right half left to the
 * caller.
 *
 * The two halves are equal on purpose. The reading itself is a number and a label and needs no more than
 * half a card, which is why the original's own widgets put a chart or a list beside it — [trailing] is
 * that space, and the campus page's timetable banner is what fills it today.
 */
@Composable
fun InfoCard(
    icon: String,
    title: String,
    value: String,
    unit: String? = null,
    detail: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colors
    val valueWithUnit = rememberValueWithUnit(
        value = value,
        unit = unit,
        style = MaterialTheme.type.title2,
        unitColor = colors.content,
    )

    Card(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxHeight().weight(1f).padding(12.dp)) {
                IconBox(
                    icon = icon,
                    shape = MaterialShapes.Cookie12Sided.toShape(),
                    background = if (warning) colors.errorContainer else colors.primaryContainer,
                    contentColor = if (warning) colors.onErrorContainer else colors.onPrimaryContainer,
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.type.footnote,
                        color = colors.contentVariant
                    )
                    Text(
                        text = valueWithUnit,
                        style = MaterialTheme.type.title2,
                        fontFamily = rememberCardValueFontFamily(),
                    )
                    Text(
                        detail,
                        style = MaterialTheme.type.footnote,
                        color = colors.contentVariant
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxHeight().weight(1f),
                content = trailing,
            )
        }
    }
}

@Composable
fun WidgetTile(
    icon: String,
    iconContainerShape: Shape = CircleShape,
    title: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    detail: String? = null,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colors
    val valueWithUnit = rememberValueWithUnit(
        value = value,
        unit = unit,
        style = MaterialTheme.type.title2,
        unitColor = colors.content.copy(alpha = 0.25f),
    )

    Card(modifier = modifier, onClick = onClick) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            IconBox(
                icon = icon,
                shape = iconContainerShape,
                background = if (warning) colors.errorContainer else colors.primaryContainer,
                contentColor = if (warning) colors.onErrorContainer else colors.onPrimaryContainer,
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.type.footnote,
                    color = colors.contentVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = valueWithUnit,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.type.footnote,
                        color = colors.contentVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One single-cell shortcut: an accent icon over a short label. */
@Composable
fun StatTile(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.specs.cardShape)
            .thenIf(onClick != null) { clickable(onClick = onClick!!) },
        color = MaterialTheme.colors.cardBackground,
        contentColor = MaterialTheme.colors.content,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colors.segmentedControlIndicator
            ) {
                SymbolIcon(name = icon, contentDescription = null)
            }
            Text(
                text = label,
                style = MaterialTheme.type.footnote,
                color = MaterialTheme.colors.contentVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    leadingIcon: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            IconBox(
                icon = it,
                background = MaterialTheme.colors.segmentedControlBackground,
                contentColor = MaterialTheme.colors.onSegmentedControlBackground,
                size = 36.dp,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.type.body)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.type.footnote,
                    color = MaterialTheme.colors.contentVariant
                )
            }
        }
        trailing()
    }
}

@Composable
fun IconBox(
    icon: String,
    shape: Shape = CircleShape,
    background: Color,
    contentColor: Color,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        ProvideContentColor(contentColor) {
            SymbolIcon(name = icon, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}
