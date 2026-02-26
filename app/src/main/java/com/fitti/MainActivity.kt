package com.fitti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitti.data.ExerciseRepository
import com.fitti.data.FittiDatabase
import com.fitti.domain.Exercise
import com.fitti.ui.MainViewModel
import com.fitti.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ExerciseRepository(FittiDatabase.create(applicationContext).exerciseDao())

        setContent {
            MaterialTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModelFactory(repository))
                val state by vm.uiState.collectAsState()
                MainScreen(state.exercises)
            }
        }
    }
}

@Composable
private fun MainScreen(exercises: List<Exercise>) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Initialer Geräte-Stand",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Marke: Nautilus • Datum: 22.02.2026",
                style = MaterialTheme.typography.bodyMedium
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(exercises) { exercise ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = exercise.code, style = MaterialTheme.typography.titleMedium)
                            Text(text = "Gewicht: ${exercise.currentWeight}")
                        }
                    }
                }
            }
        }
    }
}
