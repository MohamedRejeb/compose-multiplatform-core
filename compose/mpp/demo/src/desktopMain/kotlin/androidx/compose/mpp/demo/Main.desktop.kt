/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.mpp.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.singleWindowApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    val windowState = WindowState(
        placement = WindowPlacement.Floating,
        isMinimized = false,
        position = WindowPosition.PlatformDefault,
        size = DpSize(800.dp, 600.dp),
    )

    singleWindowApplication(
        title = "Compose MPP demo",
        state = windowState,
    ) {
        val scope = rememberCoroutineScope()

        val app = remember {
            App(initialScreenName = args.getOrNull(0))
        }
        Column {
            Text(
                text = "Current: ${windowState.placement}"
            )

            Row {
                WindowPlacement.entries.forEach {
                    TextButton(
                        onClick = {
                            windowState.placement = it
                        }
                    ) {
                        Text(it.name)
                    }

                }
            }
            app.Content()
        }
    }
}