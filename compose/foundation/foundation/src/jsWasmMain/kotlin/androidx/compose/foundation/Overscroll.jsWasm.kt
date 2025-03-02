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

package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalAccessorScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

@Composable
internal actual fun rememberPlatformOverscrollEffect(): OverscrollEffect? =
    JSOverscrollEffect()

internal actual fun CompositionLocalAccessorScope.defaultOverscrollFactory(): OverscrollFactory? =
    null

private class JSOverscrollEffect : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        // JS doesn't yet use drag touches to apply scrolling. But it might be for mobile browsers.
        val overscrollDelta = Offset.Zero // TODO: implement similar to Android
        return overscrollDelta + performScroll(delta)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        // JS doesn't yet use drag touches to apply scrolling. But it might be for mobile browsers.
        // TODO: implement similar to Android
        performFling(velocity)
    }


    //    override fun consumePreScroll(
//        scrollDelta: Offset,
//        source: NestedScrollSource
//    ): Offset = Offset.Zero
//
//    override fun consumePostScroll(
//        initialDragDelta: Offset,
//        overscrollDelta: Offset,
//        source: NestedScrollSource
//    ) {}
//
//    override suspend fun consumePreFling(velocity: Velocity): Velocity = Velocity.Zero
//
//    override suspend fun consumePostFling(velocity: Velocity) {}
//
//    override var isEnabled = false
    override val isInProgress = false
    override val effectModifier = Modifier
}
