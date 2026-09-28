package com.brendan.controlanything.ui.dashboard

import com.brendan.controlanything.domain.grid.PlacedWidget
import com.brendan.controlanything.domain.model.DashboardOrientation
import com.brendan.controlanything.domain.model.DeviceInfo
import com.brendan.controlanything.domain.model.TopicValue

data class DashboardUiState(
    val deviceInfo: DeviceInfo? = null,
    val columnCount: Int = DEFAULT_COLUMN_COUNT,
    val positions: List<PlacedWidget> = emptyList(),
    val outputValues: Map<String, TopicValue> = emptyMap(),
    val controlValues: Map<String, TopicValue> = emptyMap(),
    val orientation: DashboardOrientation = DashboardOrientation.PORTRAIT,
)
