package com.example

import com.example.data.model.SportType
import com.example.data.model.StretchingCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StretchingCatalogTest {

    @Test
    fun everySportHasTailoredStretchesWithInstructions() {
        for (sport in SportType.entries) {
            val exercises = StretchingCatalog.exercisesFor(sport)
            assertTrue("Expected at least 5 stretches for $sport", exercises.size >= 5)
            assertFalse("Summary focus must not be blank for $sport", StretchingCatalog.summaryFocusFor(sport).isBlank())
            for (exercise in exercises) {
                assertFalse("Exercise name blank", exercise.thaiName.isBlank())
                assertFalse("Target muscles blank", exercise.targetMuscles.isBlank())
                assertTrue("Expected step-by-step instructions", exercise.steps.size >= 3)
                assertFalse("Safety tip blank", exercise.safetyTip.isBlank())
            }
        }
    }
}
