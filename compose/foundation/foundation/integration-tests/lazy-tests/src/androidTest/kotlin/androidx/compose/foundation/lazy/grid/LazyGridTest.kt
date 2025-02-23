/*
 * Copyright 2021 The Android Open Source Project
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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package androidx.compose.foundation.lazy.grid

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.AutoTestFrameClock
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.list.TestTouchSlop
import androidx.compose.foundation.lazy.list.assertIsNotPlaced
import androidx.compose.foundation.lazy.list.assertIsPlaced
import androidx.compose.foundation.lazy.list.setContentWithTestViewConfiguration
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.testutils.assertPixels
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsMatcher.Companion.keyIsDefined
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.size
import androidx.compose.ui.zIndex
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.collect.Range
import com.google.common.truth.IntegerSubject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class LazyGridTest(private val orientation: Orientation) :
    BaseLazyGridTestWithOrientation(orientation) {
    private val LazyGridTag = "LazyGridTag"

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun initParameters(): Array<Any> =
            arrayOf(
                Orientation.Vertical,
                Orientation.Horizontal,
            )
    }

    @Test
    fun lazyGridShowsOneItem() {
        val itemTestTag = "itemTestTag"

        rule.setContent {
            LazyGrid(cells = 3) { item { Spacer(Modifier.size(10.dp).testTag(itemTestTag)) } }
        }

        rule.onNodeWithTag(itemTestTag).assertIsDisplayed()
    }

    @Test
    fun lazyGridShowsOneLine() {
        val items = (1..5).map { it.toString() }

        rule.setContent {
            LazyGrid(cells = 3, modifier = Modifier.axisSize(300.dp, 100.dp)) {
                items(items) { Spacer(Modifier.mainAxisSize(101.dp).testTag(it)) }
            }
        }

        rule.onNodeWithTag("1").assertIsDisplayed()

        rule.onNodeWithTag("2").assertIsDisplayed()

        rule.onNodeWithTag("3").assertIsDisplayed()

        rule.onNodeWithTag("4").assertDoesNotExist()

        rule.onNodeWithTag("5").assertDoesNotExist()
    }

    @Test
    fun lazyGridShowsSecondLineOnScroll() {
        val items = (1..9).map { it.toString() }

        rule.setContentWithTestViewConfiguration {
            LazyGrid(cells = 3, modifier = Modifier.mainAxisSize(100.dp).testTag(LazyGridTag)) {
                items(items) { Spacer(Modifier.mainAxisSize(101.dp).testTag(it)) }
            }
        }

        rule.onNodeWithTag(LazyGridTag).scrollBy(offset = 50.dp)

        rule.onNodeWithTag("4").assertIsDisplayed()

        rule.onNodeWithTag("5").assertIsDisplayed()

        rule.onNodeWithTag("6").assertIsDisplayed()

        rule.onNodeWithTag("7").assertIsNotDisplayed()

        rule.onNodeWithTag("8").assertIsNotDisplayed()

        rule.onNodeWithTag("9").assertIsNotDisplayed()
    }

    @Test
    fun lazyGridScrollHidesFirstLine() {
        val items = (1..9).map { it.toString() }

        rule.setContentWithTestViewConfiguration {
            LazyGrid(cells = 3, modifier = Modifier.mainAxisSize(200.dp).testTag(LazyGridTag)) {
                items(items) { Spacer(Modifier.mainAxisSize(101.dp).testTag(it)) }
            }
        }

        rule.onNodeWithTag(LazyGridTag).scrollBy(offset = 103.dp)

        rule.onNodeWithTag("1").assertIsNotDisplayed()

        rule.onNodeWithTag("2").assertIsNotDisplayed()

        rule.onNodeWithTag("3").assertIsNotDisplayed()

        rule.onNodeWithTag("4").assertIsDisplayed()

        rule.onNodeWithTag("5").assertIsDisplayed()

        rule.onNodeWithTag("6").assertIsDisplayed()

        rule.onNodeWithTag("7").assertIsDisplayed()

        rule.onNodeWithTag("8").assertIsDisplayed()

        rule.onNodeWithTag("9").assertIsDisplayed()
    }

    @Test
    fun adaptiveLazyGridFillsAllCrossAxisSize() {
        val items = (1..5).map { it.toString() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Adaptive(130.dp),
                modifier = Modifier.axisSize(300.dp, 100.dp)
            ) {
                items(items) { Spacer(Modifier.mainAxisSize(101.dp).testTag(it)) }
            }
        }

        rule.onNodeWithTag("1").assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule.onNodeWithTag("2").assertCrossAxisStartPositionInRootIsEqualTo(150.dp)

        rule.onNodeWithTag("3").assertDoesNotExist()

        rule.onNodeWithTag("4").assertDoesNotExist()

        rule.onNodeWithTag("5").assertDoesNotExist()
    }

    @Test
    fun adaptiveLazyGridAtLeastOneSlot() {
        val items = (1..3).map { it.toString() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Adaptive(301.dp),
                modifier = Modifier.axisSize(300.dp, 100.dp)
            ) {
                items(items) { Spacer(Modifier.mainAxisSize(101.dp).testTag(it)) }
            }
        }

        rule.onNodeWithTag("1").assertIsDisplayed()

        rule.onNodeWithTag("2").assertDoesNotExist()

        rule.onNodeWithTag("3").assertDoesNotExist()
    }

    @Test
    fun adaptiveLazyGridAppliesHorizontalSpacings() {
        val items = (1..3).map { it.toString() }

        val spacing = with(rule.density) { 10.toDp() }
        val itemSize = with(rule.density) { 100.toDp() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Adaptive(itemSize),
                modifier = Modifier.axisSize(itemSize * 3 + spacing * 2, itemSize),
                crossAxisSpacedBy = spacing
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize + spacing)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize * 2 + spacing * 2)
            .assertCrossAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun adaptiveLazyGridAppliesHorizontalSpacingsWithContentPaddings() {
        val items = (1..3).map { it.toString() }

        val spacing = with(rule.density) { 8.toDp() }
        val itemSize = with(rule.density) { 40.toDp() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Adaptive(itemSize),
                modifier = Modifier.axisSize(itemSize * 3 + spacing * 4, itemSize),
                crossAxisSpacedBy = spacing,
                contentPadding = PaddingValues(crossAxis = spacing)
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize + spacing * 2)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize * 2 + spacing * 3)
            .assertCrossAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun adaptiveLazyGridAppliesVerticalSpacings() {
        val items = (1..3).map { it.toString() }

        val spacing = with(rule.density) { 4.toDp() }
        val itemSize = with(rule.density) { 32.toDp() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Adaptive(itemSize),
                modifier = Modifier.axisSize(itemSize, itemSize * 3 + spacing * 2),
                mainAxisSpacedBy = spacing
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize + spacing)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize * 2 + spacing * 2)
            .assertMainAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun adaptiveLazyGridAppliesVerticalSpacingsWithContentPadding() {
        val items = (1..3).map { it.toString() }

        val spacing = with(rule.density) { 16.toDp() }
        val itemSize = with(rule.density) { 72.toDp() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Adaptive(itemSize),
                modifier = Modifier.axisSize(itemSize, itemSize * 3 + spacing * 2),
                mainAxisSpacedBy = spacing,
                contentPadding = PaddingValues(mainAxis = spacing)
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing * 2 + itemSize)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing * 3 + itemSize * 2)
            .assertMainAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun fixedLazyGridAppliesVerticalSpacings() {
        val items = (1..4).map { it.toString() }

        val spacing = with(rule.density) { 24.toDp() }
        val itemSize = with(rule.density) { 80.toDp() }

        rule.setContent {
            LazyGrid(
                cells = 2,
                modifier = Modifier.axisSize(itemSize, itemSize * 2 + spacing),
                mainAxisSpacedBy = spacing,
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing + itemSize)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("4")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing + itemSize)
            .assertMainAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun fixedLazyGridAppliesHorizontalSpacings() {
        val items = (1..4).map { it.toString() }

        val spacing = with(rule.density) { 15.toDp() }
        val itemSize = with(rule.density) { 30.toDp() }

        rule.setContent {
            LazyGrid(
                cells = 2,
                modifier = Modifier.axisSize(itemSize * 2 + spacing, itemSize * 2),
                crossAxisSpacedBy = spacing
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing + itemSize)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("4")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing + itemSize)
            .assertCrossAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun fixedLazyGridAppliesVerticalSpacingsWithContentPadding() {
        val items = (1..4).map { it.toString() }

        val spacing = with(rule.density) { 30.toDp() }
        val itemSize = with(rule.density) { 77.toDp() }

        rule.setContent {
            LazyGrid(
                cells = 2,
                modifier = Modifier.axisSize(itemSize, itemSize * 2 + spacing),
                mainAxisSpacedBy = spacing,
                contentPadding = PaddingValues(mainAxis = spacing)
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing * 2 + itemSize)
            .assertMainAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("4")
            .assertIsDisplayed()
            .assertMainAxisStartPositionInRootIsEqualTo(spacing * 2 + itemSize)
            .assertMainAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun fixedLazyGridAppliesHorizontalSpacingsWithContentPadding() {
        val items = (1..4).map { it.toString() }

        val spacing = with(rule.density) { 22.toDp() }
        val itemSize = with(rule.density) { 44.toDp() }

        rule.setContent {
            LazyGrid(
                cells = 2,
                modifier = Modifier.axisSize(itemSize * 2 + spacing * 3, itemSize * 2),
                crossAxisSpacedBy = spacing,
                contentPadding = PaddingValues(crossAxis = spacing)
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing * 2 + itemSize)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing)
            .assertCrossAxisSizeIsEqualTo(itemSize)

        rule
            .onNodeWithTag("4")
            .assertIsDisplayed()
            .assertCrossAxisStartPositionInRootIsEqualTo(spacing * 2 + itemSize)
            .assertCrossAxisSizeIsEqualTo(itemSize)
    }

    @Test
    fun usedWithArray() {
        val items = arrayOf("1", "2", "3", "4")

        val itemSize = with(rule.density) { 15.toDp() }

        rule.setContent {
            LazyGrid(cells = 2, modifier = Modifier.crossAxisSize(itemSize * 2)) {
                items(items) { Spacer(Modifier.mainAxisSize(itemSize).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule
            .onNodeWithTag("2")
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize)

        rule
            .onNodeWithTag("3")
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize)
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule
            .onNodeWithTag("4")
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize)
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize)
    }

    @Test
    fun usedWithArrayIndexed() {
        val items = arrayOf("1", "2", "3", "4")

        val itemSize = with(rule.density) { 15.toDp() }

        rule.setContent {
            LazyGrid(cells = 2, Modifier.crossAxisSize(itemSize * 2)) {
                itemsIndexed(items) { index, item ->
                    Spacer(Modifier.mainAxisSize(itemSize).testTag("$index*$item"))
                }
            }
        }

        rule
            .onNodeWithTag("0*1")
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule
            .onNodeWithTag("1*2")
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize)

        rule
            .onNodeWithTag("2*3")
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize)
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule
            .onNodeWithTag("3*4")
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize)
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize)
    }

    @Test
    fun changeItemsCountAndScrollImmediately() {
        lateinit var state: LazyGridState
        var count by mutableStateOf(100)
        val composedIndexes = mutableListOf<Int>()
        rule.setContent {
            state = rememberLazyGridState()
            LazyGrid(cells = 1, modifier = Modifier.mainAxisSize(10.dp), state = state) {
                items(count) { index ->
                    composedIndexes.add(index)
                    Box(Modifier.size(20.dp))
                }
            }
        }

        rule.runOnIdle {
            composedIndexes.clear()
            count = 10
            runBlocking(AutoTestFrameClock()) {
                // we try to scroll to the index after 10, but we expect that the component will
                // already be aware there is a new count and not compose items with index > 10
                state.scrollToItem(50)
            }
            composedIndexes.forEach { Truth.assertThat(it).isLessThan(count) }
            Truth.assertThat(state.firstVisibleItemIndex).isEqualTo(9)
        }
    }

    @Test
    fun maxIntElements() {
        val itemSize = with(rule.density) { 15.toDp() }

        rule.setContent {
            LazyGrid(
                cells = 2,
                modifier = Modifier.size(itemSize * 2).testTag(LazyGridTag),
                state = LazyGridState(firstVisibleItemIndex = Int.MAX_VALUE - 3)
            ) {
                items(Int.MAX_VALUE) { Box(Modifier.size(itemSize).testTag("$it")) }
            }
        }

        rule
            .onNodeWithTag("${Int.MAX_VALUE - 3}")
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule
            .onNodeWithTag("${Int.MAX_VALUE - 2}")
            .assertMainAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSize)

        rule
            .onNodeWithTag("${Int.MAX_VALUE - 1}")
            .assertMainAxisStartPositionInRootIsEqualTo(itemSize)
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)

        rule.onNodeWithTag("${Int.MAX_VALUE}").assertDoesNotExist()
        rule.onNodeWithTag("0").assertDoesNotExist()
    }

    @Test
    fun maxIntElements_withKey_startInMiddle() {
        val itemSize = with(rule.density) { 15.toDp() }

        rule.setContent {
            LazyGrid(
                cells = 1,
                modifier = Modifier.size(itemSize),
                state = LazyGridState(firstVisibleItemIndex = Int.MAX_VALUE / 2)
            ) {
                items(Int.MAX_VALUE, key = { it }) { Box(Modifier.size(itemSize).testTag("$it")) }
            }
        }

        rule.onNodeWithTag("${Int.MAX_VALUE / 2}").assertMainAxisStartPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun pointerInputScrollingIsAllowedWhenUserScrollingIsEnabled() {
        val itemSize = with(rule.density) { 30.toDp() }
        rule.setContentWithTestViewConfiguration {
            LazyGrid(
                cells = 1,
                modifier = Modifier.size(itemSize * 3).testTag(LazyGridTag),
                userScrollEnabled = true,
            ) {
                items(5) { Spacer(Modifier.size(itemSize).testTag("$it")) }
            }
        }

        rule.onNodeWithTag(LazyGridTag).apply { scrollBy(offset = itemSize) }

        rule.onNodeWithTag("1").assertMainAxisStartPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun pointerInputScrollingIsDisallowedWhenUserScrollingIsDisabled() {
        val itemSize = with(rule.density) { 30.toDp() }
        rule.setContentWithTestViewConfiguration {
            LazyGrid(
                cells = 1,
                modifier = Modifier.size(itemSize * 3).testTag(LazyGridTag),
                userScrollEnabled = false,
            ) {
                items(5) { Spacer(Modifier.size(itemSize).testTag("$it")) }
            }
        }

        rule.onNodeWithTag(LazyGridTag).scrollBy(offset = itemSize)

        rule.onNodeWithTag("1").assertMainAxisStartPositionInRootIsEqualTo(itemSize)
    }

    @Test
    fun programmaticScrollingIsAllowedWhenUserScrollingIsDisabled() {
        val itemSizePx = 30f
        val itemSize = with(rule.density) { itemSizePx.toDp() }
        lateinit var state: LazyGridState
        rule.setContentWithTestViewConfiguration {
            LazyGrid(
                cells = 1,
                modifier = Modifier.size(itemSize * 3),
                state = rememberLazyGridState().also { state = it },
                userScrollEnabled = false,
            ) {
                items(5) { Spacer(Modifier.size(itemSize).testTag("$it")) }
            }
        }

        rule.runOnIdle { runBlocking { state.scrollBy(itemSizePx) } }

        rule.onNodeWithTag("1").assertMainAxisStartPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun semanticScrollingIsDisallowedWhenUserScrollingIsDisabled() {
        val itemSize = with(rule.density) { 30.toDp() }
        rule.setContentWithTestViewConfiguration {
            LazyGrid(
                cells = 1,
                modifier = Modifier.size(itemSize * 3).testTag(LazyGridTag),
                userScrollEnabled = false,
            ) {
                items(5) { Spacer(Modifier.size(itemSize).testTag("$it")) }
            }
        }

        rule
            .onNodeWithTag(LazyGridTag)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollToIndex))
            // but we still have a read only scroll range property
            .assert(
                keyIsDefined(
                    if (orientation == Orientation.Vertical) {
                        SemanticsProperties.VerticalScrollAxisRange
                    } else {
                        SemanticsProperties.HorizontalScrollAxisRange
                    }
                )
            )
    }

    @Test
    fun rtl() {
        val gridCrossAxisSize = 30
        val gridCrossAxisSizeDp = with(rule.density) { gridCrossAxisSize.toDp() }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LazyGrid(cells = 3, modifier = Modifier.crossAxisSize(gridCrossAxisSizeDp)) {
                    items(3) { Box(Modifier.mainAxisSize(1.dp).testTag("$it")) }
                }
            }
        }

        val tags =
            if (orientation == Orientation.Vertical) {
                listOf("0", "1", "2")
            } else {
                listOf("2", "1", "0")
            }
        rule
            .onNodeWithTag(tags[0])
            .assertCrossAxisStartPositionInRootIsEqualTo(gridCrossAxisSizeDp * 2 / 3)
        rule
            .onNodeWithTag(tags[1])
            .assertCrossAxisStartPositionInRootIsEqualTo(gridCrossAxisSizeDp / 3)
        rule.onNodeWithTag(tags[2]).assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun withMissingItems() {
        val itemMainAxisSize = with(rule.density) { 30.toDp() }
        lateinit var state: LazyGridState
        rule.setContent {
            state = rememberLazyGridState()
            LazyGrid(
                cells = 2,
                modifier = Modifier.mainAxisSize(itemMainAxisSize + 1.dp),
                state = state
            ) {
                items((0..8).map { it.toString() }) {
                    if (it != "3") {
                        Box(Modifier.mainAxisSize(itemMainAxisSize).testTag(it))
                    }
                }
            }
        }

        rule.onNodeWithTag("0").assertIsDisplayed()
        rule.onNodeWithTag("1").assertIsDisplayed()
        rule.onNodeWithTag("2").assertIsDisplayed()

        rule.runOnIdle { runBlocking { state.scrollToItem(3) } }

        rule.onNodeWithTag("0").assertIsNotDisplayed()
        rule.onNodeWithTag("1").assertIsNotDisplayed()
        rule.onNodeWithTag("2").assertIsDisplayed()
        rule.onNodeWithTag("4").assertIsDisplayed()
        rule.onNodeWithTag("5").assertIsDisplayed()
        rule.onNodeWithTag("6").assertDoesNotExist()
        rule.onNodeWithTag("7").assertDoesNotExist()
    }

    @Test
    fun withZeroSizedFirstItem() {
        var scrollConsumedAccumulator = Offset.Zero
        val collectingDataConnection =
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    scrollConsumedAccumulator += consumed
                    return Offset.Zero
                }
            }

        rule.setContent {
            val state = rememberLazyGridState()
            LazyGrid(
                cells = 1,
                state = state,
                modifier =
                    Modifier.testTag("mainList")
                        .nestedScroll(connection = collectingDataConnection),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(all = 10.dp)
            ) {
                item { Spacer(modifier = Modifier.size(size = 0.dp)) }
                items((0..8).map { it.toString() }) {
                    Box(Modifier.testTag(it)) { BasicText(text = it.toString()) }
                }
            }
        }

        rule.onNodeWithTag("mainList").performTouchInput { swipeDown() }

        rule.runOnIdle { assertThat(scrollConsumedAccumulator).isEqualTo(Offset.Zero) }
    }

    @Test
    fun withZeroSizedFirstItem_shouldKeepItemOnSizeChange() {
        val firstItemSize = mutableStateOf(0.dp)

        rule.setContent {
            val state = rememberLazyGridState()
            LazyGrid(
                cells = 1,
                state = state,
                modifier = Modifier.testTag("mainList"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(all = 10.dp)
            ) {
                item {
                    Spacer(
                        modifier =
                            Modifier.testTag("firstItem")
                                .size(size = firstItemSize.value)
                                .background(Color.Black)
                    )
                }
                items((0..8).map { it.toString() }) {
                    Box(Modifier.testTag(it)) { BasicText(text = it.toString()) }
                }
            }
        }

        rule.onNodeWithTag("firstItem").assertIsNotDisplayed()
        firstItemSize.value = 20.dp
        rule.onNodeWithTag("firstItem").assertIsDisplayed()
    }

    @Test
    fun passingNegativeItemsCountIsNotAllowed() {
        var exception: Exception? = null
        rule.setContentWithTestViewConfiguration {
            LazyGrid(cells = 1) {
                try {
                    items(-1) { Box(Modifier) }
                } catch (e: Exception) {
                    exception = e
                }
            }
        }

        rule.runOnIdle {
            Truth.assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun recomposingWithNewComposedModifierObjectIsNotCausingRemeasure() {
        var remeasureCount = 0
        val layoutModifier =
            Modifier.layout { measurable, constraints ->
                remeasureCount++
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        val counter = mutableStateOf(0)

        rule.setContentWithTestViewConfiguration {
            counter.value // just to trigger recomposition
            LazyGrid(
                cells = 1,
                // this will return a new object everytime causing LazyGrid recomposition
                // without causing remeasure
                modifier = Modifier.composed { layoutModifier }
            ) {
                items(1) { Spacer(Modifier.size(10.dp)) }
            }
        }

        rule.runOnIdle {
            Truth.assertThat(remeasureCount).isEqualTo(1)
            counter.value++
        }

        rule.runOnIdle { Truth.assertThat(remeasureCount).isEqualTo(1) }
    }

    @Test
    fun scrollingALotDoesntCauseLazyLayoutRecomposition() {
        var recomposeCount = 0
        lateinit var state: LazyGridState

        rule.setContentWithTestViewConfiguration {
            state = rememberLazyGridState()
            LazyGrid(
                cells = 1,
                modifier =
                    Modifier.composed {
                            recomposeCount++
                            Modifier
                        }
                        .size(100.dp),
                state
            ) {
                items(1000) { Spacer(Modifier.size(100.dp)) }
            }
        }

        rule.runOnIdle {
            Truth.assertThat(recomposeCount).isEqualTo(1)

            runBlocking { state.scrollToItem(100) }
        }

        rule.runOnIdle { Truth.assertThat(recomposeCount).isEqualTo(1) }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun zIndexOnItemAffectsDrawingOrder() {
        rule.setContentWithTestViewConfiguration {
            LazyGrid(cells = 1, modifier = Modifier.size(6.dp).testTag(LazyGridTag)) {
                items(listOf(Color.Blue, Color.Green, Color.Red)) { color ->
                    Box(
                        Modifier.axisSize(6.dp, 2.dp)
                            .zIndex(if (color == Color.Green) 1f else 0f)
                            .drawBehind {
                                drawRect(
                                    color,
                                    topLeft = Offset(-10.dp.toPx(), -10.dp.toPx()),
                                    size = Size(20.dp.toPx(), 20.dp.toPx())
                                )
                            }
                    )
                }
            }
        }

        rule.onNodeWithTag(LazyGridTag).captureToImage().assertPixels { Color.Green }
    }

    @Test
    fun customGridCells() {
        val items = (1..5).map { it.toString() }

        rule.setContent {
            LazyGrid(
                // Two columns in ratio 1:2
                cells =
                    object : GridCells {
                        override fun Density.calculateCrossAxisCellSizes(
                            availableSize: Int,
                            spacing: Int
                        ): List<Int> {
                            val availableCrossAxis = availableSize - spacing
                            val columnSize = availableCrossAxis / 3
                            return listOf(columnSize, columnSize * 2)
                        }
                    },
                modifier = Modifier.axisSize(300.dp, 100.dp)
            ) {
                items(items) { Spacer(Modifier.mainAxisSize(101.dp).testTag(it)) }
            }
        }

        rule
            .onNodeWithTag("1")
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(100.dp)

        rule
            .onNodeWithTag("2")
            .assertCrossAxisStartPositionInRootIsEqualTo(100.dp)
            .assertCrossAxisSizeIsEqualTo(200.dp)

        rule.onNodeWithTag("3").assertDoesNotExist()

        rule.onNodeWithTag("4").assertDoesNotExist()

        rule.onNodeWithTag("5").assertDoesNotExist()
    }

    @Test
    fun onlyOneInitialMeasurePass() {
        val items by mutableStateOf((1..20).toList())
        lateinit var state: LazyGridState
        rule.setContent {
            state = rememberLazyGridState()
            LazyGrid(1, Modifier.requiredSize(100.dp).testTag(LazyGridTag), state = state) {
                items(items) { Spacer(Modifier.requiredSize(20.dp).testTag("$it")) }
            }
        }

        rule.runOnIdle { assertThat(state.numMeasurePasses).isEqualTo(1) }
    }

    @Test
    fun laysOutRtlCorrectlyWithLargerContainer() {
        val mainAxisSize = with(rule.density) { 250.toDp() }
        val crossAxisSize = with(rule.density) { 110.toDp() }
        val itemSize = with(rule.density) { 50.toDp() }

        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LazyGrid(cells = 2, modifier = Modifier.axisSize(crossAxisSize, mainAxisSize)) {
                    items(4) { index ->
                        val label = (index + 1).toString()
                        BasicText(label, Modifier.size(itemSize).testTag(label))
                    }
                }
            }
        }

        rule.onNodeWithTag("1").apply {
            if (vertical) {
                // 2 1
                // 4 3
                assertMainAxisStartPositionInRootIsEqualTo(0.dp)
                assertCrossAxisStartPositionInRootIsEqualTo(crossAxisSize / 2)
            } else {
                // 3 1
                // 4 2
                assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
                assertMainAxisStartPositionInRootIsEqualTo(mainAxisSize - itemSize)
            }
        }
    }

    @Test
    fun scrollDuringMeasure() {
        rule.setContent {
            BoxWithConstraints {
                val state = rememberLazyGridState()
                LazyGrid(cells = 2, state = state, modifier = Modifier.axisSize(40.dp, 100.dp)) {
                    items(20) {
                        val tag = it.toString()
                        BasicText(
                            text = tag,
                            modifier = Modifier.axisSize(20.dp, 20.dp).testTag(tag)
                        )
                    }
                }
                LaunchedEffect(state) { state.scrollToItem(10) }
            }
        }

        rule.onNodeWithTag("10").assertMainAxisStartPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun scrollInLaunchedEffect() {
        rule.setContent {
            val state = rememberLazyGridState()
            LazyGrid(cells = 2, state = state, modifier = Modifier.axisSize(40.dp, 100.dp)) {
                items(20) {
                    val tag = it.toString()
                    BasicText(text = tag, modifier = Modifier.axisSize(20.dp, 20.dp).testTag(tag))
                }
            }
            LaunchedEffect(state) { state.scrollToItem(10) }
        }

        rule.onNodeWithTag("10").assertMainAxisStartPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun changedLinesRemeasuredCorrectly() {
        var flag by mutableStateOf(false)
        rule.setContent {
            LazyGrid(cells = GridCells.Fixed(2), modifier = Modifier.axisSize(60.dp, 100.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.mainAxisSize(32.dp).background(Color.Red))
                }

                if (flag) {
                    item { Box(Modifier.mainAxisSize(32.dp).background(Color.Blue)) }

                    item {
                        Box(Modifier.mainAxisSize(32.dp).background(Color.Yellow).testTag("target"))
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.mainAxisSize(32.dp).background(Color.Blue))
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.mainAxisSize(32.dp).background(Color.Yellow).testTag("target"))
                    }
                }
            }
        }

        flag = true
        rule
            .onNodeWithTag("target")
            .assertCrossAxisSizeIsEqualTo(30.dp)
            .assertMainAxisStartPositionInRootIsEqualTo(32.dp)
            .assertCrossAxisStartPositionInRootIsEqualTo(30.dp)
    }

    @Test
    fun spacingsLargerThanLayoutSize() {
        val items = (1..2).map { it.toString() }

        val spacing = with(rule.density) { 5.toDp() }
        val itemSize = with(rule.density) { 50.toDp() }

        rule.setContent {
            LazyGrid(
                cells = GridCells.Fixed(2),
                modifier = Modifier.axisSize(0.dp, itemSize),
                crossAxisSpacedBy = spacing
            ) {
                items(items) { Spacer(Modifier.size(itemSize).testTag(it)) }
            }
        }

        val bounds1 = rule.onNodeWithTag("1").assertExists().getBoundsInRoot()

        assertThat(bounds1.top).isEqualTo(0.dp)
        assertThat(bounds1.left).isEqualTo(0.dp)
        assertThat(bounds1.size).isEqualTo(DpSize(0.dp, 0.dp))

        val bounds2 = rule.onNodeWithTag("2").assertExists().getBoundsInRoot()

        assertThat(bounds2.top).isEqualTo(0.dp)
        assertThat(bounds2.left).isEqualTo(0.dp)
        assertThat(bounds2.size).isEqualTo(DpSize(0.dp, 0.dp))
    }

    @Test
    fun itemsComposedInOrderDuringAnimatedScroll() {
        // for the Paging use case it is important that during such long scrolls we do not
        // accidentally compose an item from completely other part of the list as it will break
        // the logic defining what page to load. this issue was happening right after the
        // teleporting during the animated scrolling happens (for example we were on item 100
        // and we immediately snap to item 400). the prefetching logic was not detecting such
        // moves and were continuing prefetching item 101 even if it is not needed anymore.
        val state = LazyGridState()
        var previousItem = -1
        // initialize lambda here so it is not recreated when items block is rerun causing
        // extra recompositions
        val itemContent: @Composable LazyGridItemScope.(index: Int) -> Unit = {
            Truth.assertWithMessage("Item $it should be larger than $previousItem")
                .that(it > previousItem)
                .isTrue()
            previousItem = it
            BasicText("$it", Modifier.size(10.dp))
        }
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            LazyGrid(1, Modifier.size(30.dp), state = state) {
                items(500, itemContent = itemContent)
            }
        }

        var animationFinished by mutableStateOf(false)
        rule.runOnIdle {
            scope.launch {
                state.animateScrollToItem(500)
                animationFinished = true
            }
        }
        rule.waitUntil(timeoutMillis = 10000) { animationFinished }
    }

    @Test
    fun fillingFullSize_nextItemIsNotComposed() {
        val state = LazyGridState()
        state.prefetchingEnabled = false
        val itemSizePx = 5f
        val itemSize = with(rule.density) { itemSizePx.toDp() }
        rule.setContentWithTestViewConfiguration {
            LazyGrid(1, Modifier.testTag(LazyGridTag).mainAxisSize(itemSize), state) {
                items(3) { index -> Box(Modifier.size(itemSize).testTag("$index")) }
            }
        }

        repeat(3) { index ->
            rule.onNodeWithTag("$index").assertIsDisplayed()
            rule.onNodeWithTag("${index + 1}").assertDoesNotExist()
            rule.runOnIdle { runBlocking { state.scrollBy(itemSizePx) } }
        }
    }

    @Test
    fun changingMaxSpansCount() {
        val state = LazyGridState()
        state.prefetchingEnabled = false
        val itemSizePx = 100
        val itemSizeDp = with(rule.density) { itemSizePx.toDp() }
        var expanded by mutableStateOf(true)
        rule.setContent {
            Row {
                LazyGrid(
                    GridCells.Adaptive(itemSizeDp),
                    Modifier.testTag(LazyGridTag).layout { measurable, _ ->
                        val crossAxis =
                            if (expanded) {
                                itemSizePx * 3
                            } else {
                                itemSizePx
                            }
                        val mainAxis = itemSizePx * 3 + 1
                        val placeable =
                            measurable.measure(
                                Constraints.fixed(
                                    width = if (vertical) crossAxis else mainAxis,
                                    height = if (vertical) mainAxis else crossAxis
                                )
                            )
                        layout(placeable.width, placeable.height) {
                            placeable.place(IntOffset.Zero)
                        }
                    },
                    state
                ) {
                    items(
                        count = 100,
                        span = {
                            if (it == 0 || it == 5) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        }
                    ) { index ->
                        Box(Modifier.size(itemSizeDp).testTag("$index").debugBorder())
                    }
                }
            }
        }

        rule.waitForIdle()

        expanded = false
        rule.waitForIdle()

        expanded = true
        rule.waitForIdle()
    }

    @Test
    fun fixedSizeCell_forcesFixedSize() {
        val state = LazyGridState()
        val itemSizeDp = with(rule.density) { 100.toDp() }
        rule.setContent {
            LazyGrid(
                cells = GridCells.FixedSize(itemSizeDp * 2),
                modifier = Modifier.axisSize(crossAxis = itemSizeDp * 5, mainAxis = itemSizeDp * 5),
                state = state
            ) {
                items(10) { index -> Box(Modifier.size(itemSizeDp).testTag(index.toString())) }
            }
        }

        rule
            .onNodeWithTag("0")
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(itemSizeDp * 2)
        rule
            .onNodeWithTag("1")
            .assertCrossAxisStartPositionInRootIsEqualTo(itemSizeDp * 2f)
            .assertCrossAxisSizeIsEqualTo(itemSizeDp * 2)
    }

    @Test
    fun fixedSizeCell_smallContainer_matchesContainer() {
        val state = LazyGridState()
        val itemSizeDp = with(rule.density) { 100.toDp() }
        rule.setContent {
            LazyGrid(
                cells = GridCells.FixedSize(itemSizeDp * 2),
                modifier = Modifier.axisSize(crossAxis = itemSizeDp, mainAxis = itemSizeDp * 5),
                state = state
            ) {
                items(10) { index -> Box(Modifier.size(itemSizeDp).testTag(index.toString())) }
            }
        }

        rule
            .onNodeWithTag("0")
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(itemSizeDp)
        rule
            .onNodeWithTag("1")
            .assertCrossAxisStartPositionInRootIsEqualTo(0.dp)
            .assertCrossAxisSizeIsEqualTo(itemSizeDp)
    }

    @Test
    fun customOverscroll() {
        val overscroll = TestOverscrollEffect()

        val items = (1..4).map { it.toString() }
        rule.setContent {
            val state = rememberLazyGridState()
            LazyGrid(
                cells = 1,
                state = state,
                modifier = Modifier.size(200.dp).testTag("grid"),
                overscrollEffect = overscroll
            ) {
                items(items) { Spacer(Modifier.size(101.dp)) }
            }
        }

        // The overscroll modifier should be added / drawn
        rule.runOnIdle { assertThat(overscroll.drawCalled).isTrue() }

        // Swipe backwards to trigger overscroll
        rule.onNodeWithTag("grid").performTouchInput { if (vertical) swipeDown() else swipeRight() }

        rule.runOnIdle {
            // The swipe will result in multiple scroll deltas
            assertThat(overscroll.applyToScrollCalledCount).isGreaterThan(1)
            assertThat(overscroll.applyToFlingCalledCount).isEqualTo(1)
            if (vertical) {
                assertThat(overscroll.scrollOverscrollDelta.y).isGreaterThan(0)
                assertThat(overscroll.flingOverscrollVelocity.y).isGreaterThan(0)
            } else {
                assertThat(overscroll.scrollOverscrollDelta.x).isGreaterThan(0)
                assertThat(overscroll.flingOverscrollVelocity.x).isGreaterThan(0)
            }
        }
    }

    @Test
    fun testLookaheadPositionWithOnlyInBoundChanges() {
        testLookaheadPositionWithPlacementAnimator(
            initialList = listOf(0, 1, 2, 3),
            targetList = listOf(3, 2, 1, 0),
            cells = 1,
            initialExpectedLookaheadPositions =
                if (vertical) {
                    listOf(IntOffset(0, 0), IntOffset(0, 100), IntOffset(0, 200), IntOffset(0, 300))
                } else {
                    listOf(IntOffset(0, 0), IntOffset(100, 0), IntOffset(200, 0), IntOffset(300, 0))
                },
            targetExpectedLookaheadPositions =
                if (vertical) {
                    listOf(IntOffset(0, 300), IntOffset(0, 200), IntOffset(0, 100), IntOffset(0, 0))
                } else {
                    listOf(IntOffset(300, 0), IntOffset(200, 0), IntOffset(100, 0), IntOffset(0, 0))
                },
        )
    }

    @Test
    fun testLookaheadPositionWithCustomStartingIndex() {
        testLookaheadPositionWithPlacementAnimator(
            initialList = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            targetList = listOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
            cells = 2,
            initialExpectedLookaheadPositions =
                if (vertical) {
                    listOf(
                        null,
                        null,
                        IntOffset(0, 0),
                        IntOffset(100, 0),
                        IntOffset(0, 100),
                        IntOffset(100, 100),
                        IntOffset(0, 200),
                        IntOffset(100, 200),
                        // For items outside the view port *before* the visible items, we only have
                        // a contract for their mainAxis position. The crossAxis position for those
                        // items is subject to change.
                        IntOffset(UnspecifiedOffset, 300),
                        IntOffset(UnspecifiedOffset, 300)
                    )
                } else {
                    listOf(
                        null,
                        null,
                        IntOffset(0, 0),
                        IntOffset(0, 100),
                        IntOffset(100, 0),
                        IntOffset(100, 100),
                        IntOffset(200, 0),
                        IntOffset(200, 100),
                        // For items outside the view port *before* the visible items, we only have
                        // a contract for their mainAxis position. The crossAxis position for those
                        // items is subject to change.
                        IntOffset(300, UnspecifiedOffset),
                        IntOffset(300, UnspecifiedOffset)
                    )
                },
            targetExpectedLookaheadPositions =
                if (vertical) {
                    listOf(
                        IntOffset(100, 300),
                        IntOffset(0, 300),
                        IntOffset(100, 200),
                        IntOffset(0, 200),
                        IntOffset(100, 100),
                        IntOffset(0, 100),
                        IntOffset(100, 0),
                        IntOffset(0, 0),
                        IntOffset(0, -100),
                        IntOffset(100, -100)
                    )
                } else {
                    listOf(
                        IntOffset(300, 100),
                        IntOffset(300, 0),
                        IntOffset(200, 100),
                        IntOffset(200, 0),
                        IntOffset(100, 100),
                        IntOffset(100, 0),
                        IntOffset(0, 100),
                        IntOffset(0, 0),
                        IntOffset(-100, 0),
                        IntOffset(-100, 100)
                    )
                },
            startingIndex = 2,
            crossAxisSize = 200
        )
    }

    @Test
    fun testLookaheadPositionWithTwoInBoundTwoOutBound() {
        testLookaheadPositionWithPlacementAnimator(
            initialList = listOf(0, 1, 2, 3, 4, 5),
            targetList = listOf(5, 4, 2, 1, 3, 0),
            initialExpectedLookaheadPositions =
                if (vertical) {
                    listOf(
                        null,
                        null,
                        IntOffset(0, 0),
                        IntOffset(0, 100),
                        IntOffset(0, 200),
                        IntOffset(0, 300)
                    )
                } else {
                    listOf(
                        null,
                        null,
                        IntOffset(0, 0),
                        IntOffset(100, 0),
                        IntOffset(200, 0),
                        IntOffset(300, 0)
                    )
                },
            targetExpectedLookaheadPositions =
                if (vertical) {
                    listOf(
                        IntOffset(0, 300),
                        IntOffset(0, 100),
                        IntOffset(0, 0),
                        IntOffset(0, 200),
                        IntOffset(0, -100),
                        IntOffset(0, -200)
                    )
                } else {
                    listOf(
                        IntOffset(300, 0),
                        IntOffset(100, 0),
                        IntOffset(0, 0),
                        IntOffset(200, 0),
                        IntOffset(-100, 0),
                        IntOffset(-200, 0)
                    )
                },
            startingIndex = 2
        )
    }

    @Test
    fun testLookaheadPositionWithFourInBoundFourOutBound() {
        testLookaheadPositionWithPlacementAnimator(
            initialList = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            targetList = listOf(8, 9, 7, 6, 4, 5, 2, 1, 3, 0),
            initialExpectedLookaheadPositions =
                if (vertical) {
                    listOf(
                        null,
                        null,
                        null,
                        null,
                        IntOffset(0, 0),
                        IntOffset(100, 0),
                        IntOffset(0, 100),
                        IntOffset(100, 100),
                        IntOffset(0, 200),
                        IntOffset(100, 200)
                    )
                } else {
                    listOf(
                        null,
                        null,
                        null,
                        null,
                        IntOffset(0, 0),
                        IntOffset(0, 100),
                        IntOffset(100, 0),
                        IntOffset(100, 100),
                        IntOffset(200, 0),
                        IntOffset(200, 100)
                    )
                },
            targetExpectedLookaheadPositions =
                if (vertical) {
                    listOf(
                        IntOffset(100, 200),
                        IntOffset(100, 100),
                        IntOffset(0, 100),
                        IntOffset(0, 200),
                        IntOffset(0, 0),
                        IntOffset(100, 0),
                        // For items outside the view port *before* the visible items, we only have
                        // a contract for their mainAxis position. The crossAxis position for those
                        // items is subject to change.
                        IntOffset(UnspecifiedOffset, -100),
                        IntOffset(UnspecifiedOffset, -100),
                        IntOffset(UnspecifiedOffset, -200),
                        IntOffset(UnspecifiedOffset, -200)
                    )
                } else {
                    listOf(
                        IntOffset(200, 100),
                        IntOffset(100, 100),
                        IntOffset(100, 0),
                        IntOffset(200, 0),
                        IntOffset(0, 0),
                        IntOffset(0, 100),
                        // For items outside the view port *before* the visible items, we only have
                        // a contract for their mainAxis position. The crossAxis position for those
                        // items is subject to change.
                        IntOffset(-100, UnspecifiedOffset),
                        IntOffset(-100, UnspecifiedOffset),
                        IntOffset(-200, UnspecifiedOffset),
                        IntOffset(-200, UnspecifiedOffset)
                    )
                },
            startingIndex = 4,
            cells = 2,
            crossAxisSize = 200
        )
    }

    private fun testLookaheadPositionWithPlacementAnimator(
        initialList: List<Int>,
        targetList: List<Int>,
        cells: Int = 1,
        initialExpectedLookaheadPositions: List<IntOffset?>,
        targetExpectedLookaheadPositions: List<IntOffset?>,
        startingIndex: Int = 0,
        crossAxisSize: Int? = null
    ) {
        val itemSize = 100
        var list by mutableStateOf(initialList)
        val lookaheadPosition = mutableMapOf<Int, IntOffset>()
        val approachPosition = mutableMapOf<Int, IntOffset>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LazyGridInLookaheadScope(
                    list = list,
                    cells = cells,
                    startingIndex = startingIndex,
                    lookaheadPosition = lookaheadPosition,
                    approachPosition = approachPosition,
                    itemSize = itemSize,
                    crossAxisSize = crossAxisSize
                )
            }
        }
        rule.runOnIdle {
            repeat(list.size) {
                assertOffsetEquals(initialExpectedLookaheadPositions[it], lookaheadPosition[it])
                assertOffsetEquals(initialExpectedLookaheadPositions[it], approachPosition[it])
            }
            lookaheadPosition.clear()
            approachPosition.clear()
            list = targetList
        }
        rule.waitForIdle()
        repeat(20) {
            rule.mainClock.advanceTimeByFrame()
            repeat(list.size) {
                assertOffsetEquals(targetExpectedLookaheadPositions[it], lookaheadPosition[it])
            }
        }
        repeat(list.size) {
            if (
                lookaheadPosition[it]?.let { offset ->
                    (if (vertical) offset.y else offset.x) + itemSize >= 0
                } != false
            ) {
                assertOffsetEquals(lookaheadPosition[it], approachPosition[it])
            }
        }
    }

    private fun assertOffsetEquals(expected: IntOffset?, actual: IntOffset?) {
        if (expected == null || actual == null) return assertEquals(expected, actual)
        if (expected.x == UnspecifiedOffset || actual.x == UnspecifiedOffset) {
            // Only compare y offset
            assertEquals(expected.y, actual.y)
        } else if (expected.y == UnspecifiedOffset || actual.y == UnspecifiedOffset) {
            assertEquals(expected.x, actual.x)
        } else {
            assertEquals(expected, actual)
        }
    }

    @Composable
    private fun LazyGridInLookaheadScope(
        list: List<Int>,
        cells: Int,
        startingIndex: Int,
        lookaheadPosition: MutableMap<Int, IntOffset>,
        approachPosition: MutableMap<Int, IntOffset>,
        itemSize: Int,
        crossAxisSize: Int? = null
    ) {
        LookaheadScope {
            LazyGrid(
                cells = cells,
                if (vertical) {
                    Modifier.requiredHeight(itemSize.dp * (list.size - startingIndex) / cells)
                        .then(
                            if (crossAxisSize != null) Modifier.requiredWidth(crossAxisSize.dp)
                            else Modifier
                        )
                } else {
                    Modifier.requiredWidth(itemSize.dp * (list.size - startingIndex) / cells)
                        .then(
                            if (crossAxisSize != null) Modifier.requiredHeight(crossAxisSize.dp)
                            else Modifier
                        )
                },
                state = rememberLazyGridState(initialFirstVisibleItemIndex = startingIndex),
            ) {
                items(list, key = { it }) { item ->
                    Box(
                        Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = tween<IntOffset>(160)
                            )
                            .trackPositions(
                                lookaheadPosition,
                                approachPosition,
                                this@LookaheadScope,
                                item
                            )
                            .requiredSize(itemSize.dp)
                    )
                }
            }
        }
    }

    private fun Modifier.trackPositions(
        lookaheadPosition: MutableMap<Int, IntOffset>,
        approachPosition: MutableMap<Int, IntOffset>,
        lookaheadScope: LookaheadScope,
        item: Int
    ): Modifier =
        this.layout { measurable, constraints ->
            measurable.measure(constraints).run {
                layout(width, height) {
                    if (isLookingAhead) {
                        lookaheadPosition[item] =
                            with(lookaheadScope) {
                                coordinates!!
                                    .findRootCoordinates()
                                    .localLookaheadPositionOf(coordinates!!)
                                    .round()
                            }
                    } else {
                        approachPosition[item] = coordinates!!.positionInRoot().round()
                    }
                    place(0, 0)
                }
            }
        }

    @Test
    fun animContentSizeWithPlacementAnimator() {
        val itemSize = 100
        val lookaheadPosition = mutableMapOf<Int, IntOffset>()
        val approachPosition = mutableMapOf<Int, IntOffset>()
        var large by mutableStateOf(false)
        var animateSizeChange by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LookaheadScope {
                    LazyGrid(
                        cells = 2,
                        if (vertical) // Define cross axis size
                         Modifier.requiredWidth(200.dp)
                        else Modifier.requiredHeight(200.dp)
                    ) {
                        items(8, key = { it }) {
                            Box(
                                Modifier.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = tween(160, easing = LinearEasing)
                                    )
                                    .trackPositions(
                                        lookaheadPosition,
                                        approachPosition,
                                        this@LookaheadScope,
                                        it
                                    )
                                    .then(
                                        if (animateSizeChange)
                                            Modifier.animateContentSize(tween(160))
                                        else Modifier
                                    )
                                    .requiredSize(if (large) itemSize.dp * 2 else itemSize.dp)
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        repeat(8) {
            if (vertical) {
                assertEquals(it / 2 * itemSize, lookaheadPosition[it]?.y)
                assertEquals(it / 2 * itemSize, approachPosition[it]?.y)
                assertEquals(it % 2 * 100, lookaheadPosition[it]?.x)
                assertEquals(it % 2 * 100, approachPosition[it]?.x)
            } else {
                assertEquals(it / 2 * itemSize, lookaheadPosition[it]?.x)
                assertEquals(it / 2 * itemSize, approachPosition[it]?.x)
                assertEquals(it % 2 * 100, lookaheadPosition[it]?.y)
                assertEquals(it % 2 * 100, approachPosition[it]?.y)
            }
        }

        rule.mainClock.autoAdvance = false
        large = true
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        repeat(20) { frame ->
            val fraction = (frame * 16 / 160f).coerceAtMost(1f)
            repeat(8) {
                if (vertical) {
                    assertEquals(it / 2 * itemSize * 2, lookaheadPosition[it]?.y)
                    assertEquals(
                        (it / 2 * itemSize * (1 + fraction)).roundToInt(),
                        approachPosition[it]?.y
                    )
                } else {
                    assertEquals(it / 2 * itemSize * 2, lookaheadPosition[it]?.x)
                    assertEquals(
                        (it / 2 * itemSize * (1 + fraction)).roundToInt(),
                        approachPosition[it]?.x
                    )
                }
            }
            rule.mainClock.advanceTimeByFrame()
        }

        // Enable animateContentSize
        animateSizeChange = true
        large = false
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        repeat(20) { frame ->
            val fraction = (frame * 16 / 160f).coerceAtMost(1f)
            repeat(4) {
                // Verify that item target offsets are not affected by animateContentSize
                if (vertical) {
                    assertEquals(it / 2 * itemSize, lookaheadPosition[it]?.y)
                    assertEquals(
                        (it / 2 * (2 - fraction) * itemSize).roundToInt(),
                        approachPosition[it]?.y
                    )
                } else {
                    assertEquals(it / 2 * itemSize, lookaheadPosition[it]?.x)
                    assertEquals(
                        (it / 2 * (2 - fraction) * itemSize).roundToInt(),
                        approachPosition[it]?.x
                    )
                }
            }
            rule.mainClock.advanceTimeByFrame()
        }
    }

    @Test
    fun animVisibilityWithPlacementAnimator() {
        val lookaheadPosition = mutableMapOf<Int, IntOffset>()
        val approachPosition = mutableMapOf<Int, IntOffset>()
        var visible by mutableStateOf(false)
        val itemSize = 100
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LookaheadScope {
                    LazyGrid(cells = 1) {
                        items(4, key = { it }) {
                            if (vertical) {
                                Column(
                                    Modifier.animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = tween(160, easing = LinearEasing)
                                        )
                                        .trackPositions(
                                            lookaheadPosition,
                                            approachPosition,
                                            this@LookaheadScope,
                                            it
                                        )
                                ) {
                                    Box(Modifier.requiredSize(itemSize.dp))
                                    AnimatedVisibility(visible = visible) {
                                        Box(Modifier.requiredSize(itemSize.dp))
                                    }
                                }
                            } else {
                                Row(
                                    Modifier.animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = tween(160, easing = LinearEasing)
                                        )
                                        .trackPositions(
                                            lookaheadPosition,
                                            approachPosition,
                                            this@LookaheadScope,
                                            it
                                        )
                                ) {
                                    Box(Modifier.requiredSize(itemSize.dp))
                                    AnimatedVisibility(visible = visible) {
                                        Box(Modifier.requiredSize(itemSize.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        repeat(4) {
            assertEquals(it * itemSize, lookaheadPosition[it]?.mainAxisPosition)
            assertEquals(it * itemSize, approachPosition[it]?.mainAxisPosition)
        }

        rule.mainClock.autoAdvance = false
        visible = true
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        repeat(20) { frame ->
            val fraction = (frame * 16 / 160f).coerceAtMost(1f)
            repeat(4) {
                assertEquals(it * itemSize * 2, lookaheadPosition[it]?.mainAxisPosition)
                assertEquals(
                    (it * itemSize * (1 + fraction)).roundToInt(),
                    approachPosition[it]?.mainAxisPosition
                )
            }
            rule.mainClock.advanceTimeByFrame()
        }
    }

    @Test
    fun resizeLazyGridOnlyDuringApproach() {
        val itemSize = 100
        val lookaheadPositions = mutableMapOf<Int, Offset>()
        val approachPositions = mutableMapOf<Int, Offset>()
        var approachSize by mutableStateOf(itemSize * 2)
        rule.setContent {
            LookaheadScope {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    LazyGrid(
                        cells = 2,
                        Modifier.layout { measurable, _ ->
                            val constraints =
                                if (isLookingAhead) {
                                    Constraints.fixed(4 * itemSize, 4 * itemSize)
                                } else {
                                    Constraints.fixed(approachSize, approachSize)
                                }
                            measurable.measure(constraints).run {
                                layout(width, height) { place(0, 0) }
                            }
                        }
                    ) {
                        items(8) {
                            Box(
                                Modifier.requiredSize(itemSize.dp).layout { measurable, constraints
                                    ->
                                    measurable.measure(constraints).run {
                                        layout(width, height) {
                                            if (isLookingAhead) {
                                                lookaheadPositions[it] =
                                                    coordinates!!
                                                        .findRootCoordinates()
                                                        .localLookaheadPositionOf(coordinates!!)
                                            } else {
                                                approachPositions[it] =
                                                    coordinates!!.positionInRoot()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        rule.runOnIdle {
            repeat(8) {
                assertEquals((it / 2) * itemSize, lookaheadPositions[it]?.mainAxisPosition)
            }
            assertEquals(0, approachPositions[0]?.mainAxisPosition)
            assertEquals(0, approachPositions[1]?.mainAxisPosition)
            assertEquals(itemSize, approachPositions[2]?.mainAxisPosition)
            assertEquals(itemSize, approachPositions[3]?.mainAxisPosition)
            assertEquals(null, approachPositions[4]?.mainAxisPosition)
            assertEquals(null, approachPositions[5]?.mainAxisPosition)
            assertEquals(null, approachPositions[6]?.mainAxisPosition)
            assertEquals(null, approachPositions[7]?.mainAxisPosition)
        }
        approachSize = (2.9f * itemSize).toInt()
        rule.runOnIdle {
            repeat(8) { assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition) }
            assertEquals(0, approachPositions[0]?.mainAxisPosition)
            assertEquals(0, approachPositions[1]?.mainAxisPosition)
            assertEquals(itemSize, approachPositions[2]?.mainAxisPosition)
            assertEquals(itemSize, approachPositions[3]?.mainAxisPosition)
            assertEquals(itemSize * 2, approachPositions[4]?.mainAxisPosition)
            assertEquals(itemSize * 2, approachPositions[5]?.mainAxisPosition)
            assertEquals(null, approachPositions[6]?.mainAxisPosition)
            assertEquals(null, approachPositions[7]?.mainAxisPosition)
        }
        approachSize = (3.4f * itemSize).toInt()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
            }
        }

        // Shrinking approach size
        approachSize = (2.7f * itemSize).toInt()
        approachPositions.clear()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                if (it < 6) {
                    assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
                } else {
                    assertEquals(null, approachPositions[it]?.mainAxisPosition)
                }
            }
        }

        // Shrinking approach size
        approachSize = (1.2f * itemSize).toInt()
        approachPositions.clear()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                if (it < 4) {
                    assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
                } else {
                    assertEquals(null, approachPositions[it]?.mainAxisPosition)
                }
            }
        }
    }

    @Test
    fun lookaheadSizeSmallerThanPostLookahead() {
        val itemSize = 100
        val lookaheadPositions = mutableMapOf<Int, Offset>()
        val approachPositions = mutableMapOf<Int, Offset>()
        val lookaheadSize by mutableStateOf(itemSize * 2)
        var approachSize by mutableStateOf(itemSize * 4)
        rule.setContent {
            LookaheadScope {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    LazyGrid(
                        cells = 2,
                        Modifier.layout { measurable, _ ->
                            val constraints =
                                if (isLookingAhead) {
                                    Constraints.fixed(lookaheadSize, lookaheadSize)
                                } else {
                                    Constraints.fixed(approachSize, approachSize)
                                }
                            measurable.measure(constraints).run {
                                layout(width, height) { place(0, 0) }
                            }
                        }
                    ) {
                        items(8) {
                            Box(
                                Modifier.requiredSize(itemSize.dp).layout { measurable, constraints
                                    ->
                                    measurable.measure(constraints).run {
                                        layout(width, height) {
                                            if (isLookingAhead) {
                                                lookaheadPositions[it] =
                                                    coordinates!!
                                                        .findRootCoordinates()
                                                        .localLookaheadPositionOf(coordinates!!)
                                            } else {
                                                approachPositions[it] =
                                                    coordinates!!.positionInRoot()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        // approachSize was initialized to 4 * ItemSize
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
            }
        }
        approachSize = (2.9f * itemSize).toInt()
        approachPositions.clear()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                if (it < 6) {
                    assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
                } else {
                    assertEquals(null, approachPositions[it]?.mainAxisPosition)
                }
            }
        }
        approachSize = 2 * itemSize
        approachPositions.clear()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                if (it < 4) {
                    assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
                } else {
                    assertEquals(null, approachPositions[it]?.mainAxisPosition)
                }
            }
        }

        // Growing approach size
        approachSize = (2.7f * itemSize).toInt()
        approachPositions.clear()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                if (it < 6) {
                    assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
                } else {
                    assertEquals(null, approachPositions[it]?.mainAxisPosition)
                }
            }
        }

        // Shrinking approach size
        approachSize = (1.2f * itemSize).toInt()
        approachPositions.clear()
        rule.runOnIdle {
            repeat(8) {
                assertEquals(it / 2 * itemSize, lookaheadPositions[it]?.mainAxisPosition)
                if (it < 4) {
                    assertEquals(it / 2 * itemSize, approachPositions[it]?.mainAxisPosition)
                } else {
                    assertEquals(null, approachPositions[it]?.mainAxisPosition)
                }
            }
        }
    }

    @Test
    fun approachItemsComposed() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LookaheadScope {
                    LazyGrid(cells = 2, Modifier.requiredSize(300.dp)) {
                        items(24, key = { it }) {
                            Box(
                                Modifier.testTag("$it")
                                    .then(
                                        if (it == 0) {
                                            Modifier.layout { measurable, constraints ->
                                                val p = measurable.measure(constraints)
                                                val size = if (isLookingAhead) 300 else 30
                                                layout(size, size) { p.place(0, 0) }
                                            }
                                        } else Modifier.size(30.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // Based on lookahead item 0 & 1 would be the only item needed, but approach calculation
        // indicates 10 items will be needed to fill the viewport.
        for (i in 0 until 20) {
            rule.onNodeWithTag("$i").assertIsPlaced()
        }
        for (i in 20 until 24) {
            rule.onNodeWithTag("$i").assertDoesNotExist()
        }
    }

    @Test
    fun approachItemsComposedBasedOnScrollDelta() {
        var lookaheadSize by mutableStateOf(30)
        var approachSize by mutableStateOf(lookaheadSize)
        lateinit var state: LazyGridState
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                LookaheadScope {
                    state = LazyGridState()
                    LazyGrid(cells = 2, Modifier.requiredSize(300.dp), state) {
                        items(24, key = { it }) {
                            Box(
                                Modifier.testTag("$it")
                                    .then(
                                        if (it == 4) {
                                            Modifier.layout { measurable, constraints ->
                                                val p = measurable.measure(constraints)
                                                val size =
                                                    if (isLookingAhead) lookaheadSize
                                                    else approachSize
                                                layout(size, size) { p.place(0, 0) }
                                            }
                                        } else Modifier.size(30.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        for (i in 0 until 24) {
            if (i < 20) {
                rule.onNodeWithTag("$i").assertIsPlaced()
            } else {
                rule.onNodeWithTag("$i").assertDoesNotExist()
            }
        }

        lookaheadSize = 300
        rule.runOnIdle { runBlocking { state.scrollBy(60f) } }
        rule.waitForIdle()

        rule.onNodeWithTag("0").assertIsNotPlaced()
        rule.onNodeWithTag("1").assertIsNotPlaced()
        rule.onNodeWithTag("2").assertIsNotPlaced()
        rule.onNodeWithTag("3").assertIsNotPlaced()
        for (i in 4 until 24) {
            rule.onNodeWithTag("$i").assertIsPlaced()
        }

        approachSize = 300
        rule.waitForIdle()
        for (i in 0 until 24) {
            if (i == 4 || i == 5) {
                rule.onNodeWithTag("$i").assertIsPlaced()
            } else {
                rule.onNodeWithTag("$i").assertIsNotPlaced()
            }
        }
    }

    @Test
    fun testDisposeHappensAfterNoLongerNeededByEitherPass() {

        val disposed = mutableListOf<Boolean>().apply { repeat(20) { this.add(false) } }
        var lookaheadHeight by mutableIntStateOf(1000)
        var approachHeight by mutableIntStateOf(1000)
        rule.setContent {
            LookaheadScope {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    LazyVerticalGrid(
                        GridCells.Fixed(2),
                        Modifier.layout { m, _ ->
                            val c =
                                if (isLookingAhead) Constraints.fixed(400, lookaheadHeight)
                                else Constraints.fixed(400, approachHeight)
                            m.measure(c).run { layout(width, lookaheadHeight) { place(0, 0) } }
                        }
                    ) {
                        items(20) {
                            Box(Modifier.height(100.dp).fillMaxWidth())
                            DisposableEffect(Unit) { onDispose { disposed[it] = true } }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { repeat(20) { assertEquals(false, disposed[it]) } }
        approachHeight = 400
        rule.waitForIdle()
        lookaheadHeight = 400

        rule.runOnIdle {
            repeat(20) {
                if (it < 8) {
                    assertEquals(false, disposed[it])
                } else {
                    assertEquals(true, disposed[it])
                }
            }
        }
        lookaheadHeight = 300

        rule.runOnIdle { repeat(8) { assertEquals(false, disposed[it]) } }
    }

    @Test
    fun testNoOverScroll() {
        val state = LazyGridState()
        var firstItemOffset: Offset? = null
        var lastItemOffset: Offset? = null
        rule.setContent {
            LookaheadScope {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    CompositionLocalProvider(LocalOverscrollFactory provides null) {
                        Box(Modifier.testTag("grid")) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = state,
                                modifier = Modifier.requiredHeight(500.dp).fillMaxWidth()
                            ) {
                                items(30) {
                                    BasicText(
                                        "$it",
                                        Modifier.then(
                                                if (it == 0 || it == 29)
                                                    Modifier.onGloballyPositioned { c ->
                                                        // Checking on each placement there's no
                                                        // overscroll
                                                        if (it == 0) {
                                                            firstItemOffset = c.positionInRoot()
                                                            assertTrue(firstItemOffset!!.y <= 0f)
                                                        } else {
                                                            lastItemOffset = c.positionInRoot()
                                                            assertTrue(lastItemOffset!!.y >= 400f)
                                                        }
                                                    }
                                                else Modifier
                                            )
                                            .height(100.dp)
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scroll beyond bounds in both directions
        repeat(20) {
            rule.runOnIdle { runBlocking { state.scrollBy(200f) } }
            if (it == 19) {
                assertEquals(20, state.firstVisibleItemIndex)
                rule.runOnIdle { runBlocking { state.scrollToItem(14) } }
                rule.onNodeWithTag("grid").performTouchInput { swipeUp(durationMillis = 50) }
            }
            // Checking on each iteration there is no overscroll
            assertTrue(firstItemOffset == null || firstItemOffset!!.y <= 0)
            assertTrue(lastItemOffset == null || lastItemOffset!!.y >= 400)
        }

        repeat(20) {
            rule.runOnIdle { runBlocking { state.scrollBy(-200f) } }
            if (it == 19) {
                assertEquals(0, state.firstVisibleItemIndex)
                rule.runOnIdle { runBlocking { state.scrollToItem(7) } }
                rule.onNodeWithTag("grid").performTouchInput { swipeDown(durationMillis = 50) }
            }
            // Checking on each iteration there is no overscroll
            assertTrue(firstItemOffset == null || firstItemOffset!!.y <= 0)
            assertTrue(lastItemOffset == null || lastItemOffset!!.y >= 400)
        }
    }

    @Test
    fun testSmallScrollWithLookaheadScope() {
        val itemSize = 10
        val itemSizeDp = with(rule.density) { itemSize.toDp() }
        val containerSizeDp = with(rule.density) { 15.toDp() }
        val scrollDelta = 2f
        val scrollDeltaDp = with(rule.density) { scrollDelta.toDp() }
        val state = LazyGridState()
        lateinit var scope: CoroutineScope
        rule.setContent {
            scope = rememberCoroutineScope()
            LookaheadScope {
                LazyGrid(cells = 1, Modifier.mainAxisSize(containerSizeDp), state = state) {
                    repeat(20) { item { Box(Modifier.size(itemSizeDp).testTag("$it")) } }
                }
            }
        }

        rule.runOnIdle { runBlocking { scope.launch { state.scrollBy(scrollDelta) } } }

        rule.onNodeWithTag("0").assertMainAxisStartPositionInRootIsEqualTo(-scrollDeltaDp)
        rule
            .onNodeWithTag("1")
            .assertMainAxisStartPositionInRootIsEqualTo(itemSizeDp - scrollDeltaDp)
    }

    private val Offset.mainAxisPosition: Int
        get() = (if (vertical) this.y else this.x).roundToInt()

    private val IntOffset.mainAxisPosition: Int
        get() = if (vertical) this.y else this.x
}

const val UnspecifiedOffset = 0xFFF_FFFF

internal fun IntegerSubject.isEqualTo(expected: Int, tolerance: Int) {
    isIn(Range.closed(expected - tolerance, expected + tolerance))
}

internal fun SemanticsNodeInteraction.scrollBy(x: Dp = 0.dp, y: Dp = 0.dp, density: Density) =
    performTouchInput {
        with(density) {
            val touchSlop = TestTouchSlop.toInt()
            val xPx = x.roundToPx()
            val yPx = y.roundToPx()
            val offsetX = if (xPx > 0) xPx + touchSlop else if (xPx < 0) xPx - touchSlop else 0
            val offsetY = if (yPx > 0) yPx + touchSlop else if (yPx < 0) yPx - touchSlop else 0
            swipeWithVelocity(
                start = center,
                end = Offset(center.x - offsetX, center.y - offsetY),
                endVelocity = 0f
            )
        }
    }
