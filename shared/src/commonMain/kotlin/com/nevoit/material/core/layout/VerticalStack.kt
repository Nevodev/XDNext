package com.nevoit.material.core.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.interaction.rememberFlingBehavior

@Composable
fun VerticalStack(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = rememberFlingBehavior(),
        userScrollEnabled = userScrollEnabled
    ) {
        content()
        paddingItem(state)
    }
}

@Composable
fun LazyListState.isScrolledPast(threshold: Dp): State<Boolean> {
    val density = LocalDensity.current

    val thresholdPx = remember(threshold, density) {
        with(density) { threshold.toPx() }
    }

    return remember(this, thresholdPx) {
        derivedStateOf {
            firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > thresholdPx
        }
    }
}