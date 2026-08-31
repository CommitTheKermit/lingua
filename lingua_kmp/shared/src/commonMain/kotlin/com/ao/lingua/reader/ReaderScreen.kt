package com.ao.lingua.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ReaderScreen(
    state: ReaderState,
    onOpenFile: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        val horizontalPadding = if (maxWidth >= 600.dp) 32.dp else 20.dp

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
        ) {
            Text(
                text = "Lingua",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenFile) {
                Text("Open .txt file")
            }

            if (state.sentences.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Choose a .txt file to start reading.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.currentSentence,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                Text(
                    text = state.positionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = "Reading position ${state.positionLabel}" },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onPrevious,
                        enabled = state.canGoPrevious,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Previous")
                    }
                    Button(
                        onClick = onNext,
                        enabled = state.canGoNext,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

@Preview(name = "Phone")
@Composable
private fun ReaderScreenPhonePreview() {
    MaterialTheme {
        Box(Modifier.width(360.dp).height(800.dp)) {
            ReaderScreen(
                state = ReaderState("sample.txt", listOf("Reading one sentence at a time keeps the page calm.")),
                onOpenFile = {},
                onPrevious = {},
                onNext = {},
            )
        }
    }
}

@Preview(name = "Tablet")
@Composable
private fun ReaderScreenTabletPreview() {
    MaterialTheme {
        Box(Modifier.width(840.dp).height(900.dp)) {
            ReaderScreen(
                state = ReaderState("sample.txt", listOf("The same shared UI stays focused on a wider screen.")),
                onOpenFile = {},
                onPrevious = {},
                onNext = {},
            )
        }
    }
}
