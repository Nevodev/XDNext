package com.nevoit.material.core.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.util.fastCoerceAtLeast

fun LazyListScope.paddingItem(state: LazyListState) {
    item(key = LazyPaddingItemKey, contentType = LazyPaddingItem) {
        Box(
            Modifier.layout { _, constraints ->
                val layoutInfo = state.layoutInfo
                val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
                val lastContentItem =
                    layoutInfo.visibleItemsInfo.lastOrNull { it.contentType !== LazyPaddingItem }
                val contentBottom = lastContentItem?.let { it.offset + it.size } ?: 0
                val height = (viewportBottom - contentBottom + 1).fastCoerceAtLeast(0)
                layout(constraints.maxWidth, height) {}
            }
        )
    }
}

fun LazyStaggeredGridScope.paddingItem(
    state: LazyStaggeredGridState,
) {
    item(
        key = LazyPaddingItemKey,
        contentType = LazyPaddingItem,
        span = StaggeredGridItemSpan.FullLine,
    ) {
        Box(
            Modifier.layout { _, constraints ->
                val layoutInfo = state.layoutInfo
                val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
                val contentBottom = layoutInfo.visibleItemsInfo.asSequence()
                    .filter { it.contentType != LazyPaddingItem }
                    .maxOfOrNull { item -> item.offset.y + item.size.height } ?: 0
                val spacing = layoutInfo.mainAxisItemSpacing
                val firstIndex = state.firstVisibleItemIndex
                val firstOffset = state.firstVisibleItemScrollOffset
                val hasNaturalScroll = firstIndex > 0 || firstOffset > MinimumScrollRangePx
                val unscrolledContentBottom = contentBottom + firstOffset
                val height = if (hasNaturalScroll) {
                    0
                } else {
                    viewportBottom - unscrolledContentBottom - spacing + MinimumScrollRangePx
                }.coerceAtLeast(0)
                    .coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(constraints.maxWidth, height) {}
            }
        )
    }
}

private data object LazyPaddingItem

private const val LazyPaddingItemKey = "lazy_padding_item"
private const val MinimumScrollRangePx = 1
