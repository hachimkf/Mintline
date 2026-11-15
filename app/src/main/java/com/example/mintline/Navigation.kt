package com.example.mintline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.mintline.ui.main.MainScreen
import com.example.mintline.ui.main.MainScreenViewModel

@Composable
fun MainNavigation(viewModel: MainScreenViewModel) {
    MainScreen(modifier = Modifier.fillMaxSize(), viewModel = viewModel)
}
