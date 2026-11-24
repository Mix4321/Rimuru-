package com.example

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.WorkoutDraftStore
import com.example.data.local.AppSettings
import com.example.data.local.MoveRepository
import com.example.data.local.MoveRoomDatabase
import com.example.data.model.SportType
import com.example.data.model.TrackingMode
import com.example.service.WorkoutLiveNotificationBus
import com.example.service.WorkoutServiceSnapshot
import com.example.service.WorkoutTrackingService
import com.example.ui.viewmodel.WorkoutViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutAutoPauseAndServiceTest {

    private lateinit var app: Application
    private lateinit var db: MoveRoomDatabase
    private lateinit var viewModel: WorkoutViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MoveRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = WorkoutViewModel(
            repository = MoveRepository(db.moveDao()),
            draftStore = WorkoutDraftStore(app)
        )
    }

    @After
    fun tearDown() {
        db.close()
        WorkoutLiveNotificationBus.isServiceActive.value = false
        WorkoutLiveNotificationBus.snapshotFlow.value = null
    }

    @Test
    fun `autoPause setting syncs into WorkoutUiState and supports manual toggle`() {
        viewModel.updateAppSettings(AppSettings(autoPauseEnabled = false))
        viewModel.startFreshSession(SportType.RUNNING, TrackingMode.SENSOR_GPS)
        assertFalse(viewModel.uiState.value.autoPauseEnabled)

        viewModel.toggleAutoPauseEnabled()
        assertTrue(viewModel.uiState.value.autoPauseEnabled)

        // Manual override for current session is preserved across AppSettings updates
        viewModel.updateAppSettings(AppSettings(autoPauseEnabled = false))
        assertTrue(viewModel.uiState.value.autoPauseEnabled)
    }

    @Test
    fun `togglePauseFromNotification toggles running state cleanly`() {
        viewModel.startFreshSession(SportType.RUNNING, TrackingMode.SENSOR_GPS)
        assertFalse(viewModel.uiState.value.isRunning)

        viewModel.togglePauseFromNotification()
        assertTrue(viewModel.uiState.value.isRunning)
        assertFalse(viewModel.uiState.value.isAutoPaused)

        viewModel.togglePauseFromNotification()
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun `WorkoutTrackingService refuses foreground start without location permission and emits toggle action`() {
        runBlocking {
            assertFalse(WorkoutTrackingService.hasLocationPermission(app))

            WorkoutTrackingService.syncState(
                app,
                WorkoutServiceSnapshot(
                    sessionId = "s1",
                    sportThaiName = "วิ่ง",
                    trackingMode = TrackingMode.SENSOR_GPS,
                    elapsedSeconds = 12,
                    distanceMeters = 120,
                    caloriesBurned = 15,
                    isRunning = true,
                    isAutoPaused = false,
                    isAutoPauseEnabled = true
                )
            )
            assertFalse(WorkoutLiveNotificationBus.isServiceActive.value)

            Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            assertTrue(WorkoutTrackingService.hasLocationPermission(app))

            val serviceController = Robolectric.buildService(WorkoutTrackingService::class.java)
                .create()
            val service = serviceController.get()
            service.onStartCommand(
                Intent(app, WorkoutTrackingService::class.java).apply {
                    action = WorkoutTrackingService.ACTION_SYNC
                },
                0,
                1
            )
            assertTrue(WorkoutLiveNotificationBus.isServiceActive.value)

            val toggleDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(1500) {
                    WorkoutLiveNotificationBus.togglePauseEvents.first()
                }
            }
            service.onStartCommand(
                Intent(app, WorkoutTrackingService::class.java).apply {
                    action = WorkoutTrackingService.ACTION_TOGGLE_PAUSE
                },
                0,
                2
            )
            toggleDeferred.await()

            service.onStartCommand(
                Intent(app, WorkoutTrackingService::class.java).apply {
                    action = WorkoutTrackingService.ACTION_STOP_TRACKING
                },
                0,
                3
            )
            assertFalse(WorkoutLiveNotificationBus.isServiceActive.value)
            assertEquals(null, service.onBind(null))
            serviceController.destroy()
        }
    }
}
