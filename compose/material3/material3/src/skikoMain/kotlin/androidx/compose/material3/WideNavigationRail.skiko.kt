/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Immutable
@ExperimentalMaterial3ExpressiveApi
actual class ModalWideNavigationRailProperties
actual constructor(
    actual val shouldDismissOnBackPress: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModalBottomSheetProperties) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shouldDismissOnBackPress.hashCode()
        return result
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal actual fun createDefaultModalWideNavigationRailProperties() =
    ModalWideNavigationRailProperties()

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalMaterial3ExpressiveApi
@Composable
internal actual fun ModalWideNavigationRailDialog(
    onDismissRequest: () -> Unit,
    properties: ModalWideNavigationRailProperties,
    onPredictiveBack: (Float) -> Unit,
    onPredictiveBackCancelled: () -> Unit,
    predictiveBackState: RailPredictiveBackState,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.shouldDismissOnBackPress,
            usePlatformDefaultWidth = false,
            usePlatformInsets = false,
            scrimColor = Color.Transparent,
        ),
        content = content
    )
}
