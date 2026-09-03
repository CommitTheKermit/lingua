package com.ao.lingua.dictionary

import androidx.compose.runtime.Composable

@Composable
expect fun rememberDictionaryInstaller(): suspend (ByteArray, String) -> String
