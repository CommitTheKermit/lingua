package com.ao.lingua.reader

import androidx.compose.runtime.Composable

data class CsvDocument(val name: String, val content: String)

@Composable
expect fun rememberReaderDatabase(): ReaderDatabase

@Composable
expect fun rememberCsvExporter(): (CsvDocument) -> Unit
