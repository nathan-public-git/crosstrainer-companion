package com.crosstrainer.companion.data

import com.crosstrainer.companion.model.WorkoutMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface WorkoutMetricsSource {
    val metrics: Flow<WorkoutMetrics>
}

class PendingWorkoutMetricsSource : WorkoutMetricsSource {
    override val metrics: Flow<WorkoutMetrics> = flowOf(WorkoutMetrics())
}
