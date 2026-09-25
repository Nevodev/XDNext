package com.nevoit.material.core.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nevoit.material.core.modifier.thenIf
import com.nevoit.material.theme.MaterialTheme

@Composable
fun PageHeader(
    modifier: Modifier = Modifier,
    title: String,
    subTitle: String? = null,
    height: Dp = 160.dp,
    extraPadding: Boolean = false
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .height(height)
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 16.dp, end = 12.dp)
            .thenIf(extraPadding) {
                padding(horizontal = 12.dp)
            },
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.type.largeTitleEmphasized
        )
        subTitle?.let {
            Text(
                text = it,
                style = MaterialTheme.type.subHeadline,
                color = MaterialTheme.colors.contentVariant
            )
        }
    }
}
