package com.brendan.controlanything.ui.dashboard.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brendan.controlanything.domain.model.OutputDef
import com.brendan.controlanything.domain.model.TopicValue

private val OFF_COLOR = Color(0xFF616161)

@Composable
fun LedIndicatorWidget(
    definition: OutputDef.LedIndicator,
    value: TopicValue.Bool?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (value?.value == true) Color(definition.color.argb) else OFF_COLOR, CircleShape),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = definition.displayName,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
