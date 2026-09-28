package com.brendan.controlanything.ui.dashboard.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.brendan.controlanything.domain.model.OutputDef
import com.brendan.controlanything.domain.model.TopicValue

@Composable
fun NumericReadoutWidget(
    definition: OutputDef.NumericReadout,
    value: TopicValue.Number?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value?.value?.let { "%.1f%s".format(it, definition.suffix) } ?: "--",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = definition.displayName,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
