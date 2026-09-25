package com.nevoit.xdnext.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.component.CircularProgressIndicator
import com.nevoit.material.core.component.Text
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.data.timetable.TimetableSource

/**
 * The two-line notice above the grid: who is being refreshed, and who is being served from a cache.
 *
 * Ported from `ClassTableInlineBanner`. It shows nothing at all when there is nothing to say, which is
 * the normal case — a timetable that was just fetched, on a device that is online, draws no banner.
 *
 * The spinner is on the *loading* line rather than on the cached one, and only when something is
 * loading: a cached timetable is a finished state, not a pending one, even though it is a state worth
 * naming. The original's separators are reproduced per line rather than shared — 正在更新 joins with
 * "；" and so does 当前使用缓存.
 *
 * The source list is a list because the original's grid drew four sources at once. Only the timetable
 * exists so far; see [TimetableSource].
 */
@Composable
internal fun TimetableBanner(
    loading: List<TimetableSource>,
    cache: List<TimetableSource>,
    modifier: Modifier = Modifier,
) {
    if (loading.isEmpty() && cache.isEmpty()) return

    val colors = MaterialTheme.colors
    val loadingText = loading.takeIf { it.isNotEmpty() }
        ?.let { TimetableStrings.bannerLoading(it.joinToString("；") { source -> source.label }) }
    val cacheText = cache.takeIf { it.isNotEmpty() }
        ?.let { TimetableStrings.bannerCache(it.joinToString("；") { source -> source.label }) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            loadingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.type.subHeadlineEmphasized,
                    color = colors.onSegmentedControlBackground,
                )
            }
            cacheText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.type.subHeadline,
                    color = colors.onSegmentedControlBackground,
                )
            }
        }

        if (loadingText != null) {
            Spacer(Modifier.size(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.onSegmentedControlBackground,
                strokeWidth = 2.5.dp,
            )
        }
    }
}
