package com.fitti

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitti.data.ExerciseRepository
import com.fitti.data.FittiDatabase
import com.fitti.data.SettingsRepository
import com.fitti.data.WorkoutSessionRepository
import com.fitti.ui.screens.ActiveWorkoutScreen
import com.fitti.ui.screens.HistoryDetailScreen
import com.fitti.ui.screens.HomeScreen
import com.fitti.ui.screens.SettingsScreen
import com.fitti.ui.theme.FittiTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val database = FittiDatabase.create(applicationContext)
        val exerciseRepo = ExerciseRepository(database.exerciseDao())
        val workoutRepo = WorkoutSessionRepository(database.workoutSessionDao())
        val settingsRepo = SettingsRepository(applicationContext)
        val weightLogDao = database.weightLogDao()
        val aiAnalysisDao = database.aiAnalysisDao()
        val coachingPlanDao = database.coachingPlanDao()
        val nutritionLogDao = database.nutritionLogDao()
        val bodyMeasurementDao = database.bodyMeasurementDao()

        com.fitti.notifications.NotificationScheduler.applyAll(applicationContext, settingsRepo)

        setContent {
            FittiTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            exerciseRepo = exerciseRepo,
                            workoutRepo = workoutRepo,
                            settingsRepo = settingsRepo,
                            weightLogDao = weightLogDao,
                            aiAnalysisDao = aiAnalysisDao,
                            coachingPlanDao = coachingPlanDao,
                            nutritionLogDao = nutritionLogDao,
                            bodyMeasurementDao = bodyMeasurementDao,
                            onStartWorkout = { sessionId ->
                                navController.navigate("workout/$sessionId") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenHistory = { sessionId ->
                                navController.navigate("history/$sessionId")
                            },
                            onOpenSettings = {
                                navController.navigate("settings")
                            },
                            onOpenMeasurements = {
                                navController.navigate("measurements")
                            }
                        )
                    }

                    composable(
                        route = "workout/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                        ActiveWorkoutScreen(
                            sessionId = sessionId,
                            workoutRepo = workoutRepo,
                            exerciseRepo = exerciseRepo,
                            settingsRepo = settingsRepo,
                            weightLogDao = weightLogDao,
                            coachingPlanDao = coachingPlanDao,
                            nutritionLogDao = nutritionLogDao,
                            bodyMeasurementDao = bodyMeasurementDao,
                            application = application,
                            onWorkoutComplete = {
                                navController.popBackStack("home", inclusive = false)
                            }
                        )
                    }

                    composable(
                        route = "history/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                        HistoryDetailScreen(
                            sessionId = sessionId,
                            workoutRepo = workoutRepo,
                            settingsRepo = settingsRepo,
                            weightLogDao = weightLogDao,
                            nutritionLogDao = nutritionLogDao,
                            bodyMeasurementDao = bodyMeasurementDao,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            settingsRepo = settingsRepo,
                            weightLogDao = weightLogDao,
                            exerciseRepo = exerciseRepo,
                            workoutSessionDao = database.workoutSessionDao(),
                            coachingPlanDao = coachingPlanDao,
                            nutritionLogDao = nutritionLogDao,
                            bodyMeasurementDao = bodyMeasurementDao,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("measurements") {
                        com.fitti.ui.screens.MeasurementsScreen(
                            bodyMeasurementDao = bodyMeasurementDao,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
