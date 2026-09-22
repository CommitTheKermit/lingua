package com.ao.lingua

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(translationAvailable: Boolean) = ComposeUIViewController { App(translationAvailable) }
