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

package androidx.compose.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.sendKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.toInt
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.window.window.toSize
import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.test.assertEquals
import org.junit.Test

class DialogWindowTest {
    @Test
    fun `open and close custom dialog`() = runApplicationTest {
        var window: ComposeDialog? = null

        launchTestApplication {
            var isOpen by remember { mutableStateOf(true) }

            fun createWindow() = ComposeDialog().apply {
                size = Dimension(300, 200)
                isResizable = true
                isUndecorated = true
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) {
                        isOpen = false
                    }
                })
            }

            if (isOpen) {
                DialogWindow(
                    create = ::createWindow,
                    dispose = ComposeDialog::dispose
                ) {
                    window = this.window
                    Box(Modifier.size(32.dp).background(Color.Red))
                }
            }
        }

        awaitIdle()
        assertThat(window?.isShowing).isTrue()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    @Test
    fun `update custom dialog`() = runApplicationTest {
        var window: ComposeDialog? = null

        var isOpen by mutableStateOf(true)
        var title by mutableStateOf("Title1")

        launchTestApplication {
            fun createWindow() = ComposeDialog().apply {
                size = Dimension(300, 200)

                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) {
                        isOpen = false
                    }
                })
            }

            if (isOpen) {
                DialogWindow(
                    create = ::createWindow,
                    dispose = ComposeDialog::dispose,
                    update = { it.title = title }
                ) {
                    window = this.window
                    Box(Modifier.size(32.dp).background(Color.Red))
                }
            }
        }

        awaitIdle()
        assertThat(window?.isShowing).isTrue()
        assertThat(window?.title).isEqualTo(title)

        title = "Title2"
        awaitIdle()
        assertThat(window?.title).isEqualTo(title)

        isOpen = false
    }

    @Test
    fun `open and close dialog`() = runApplicationTest {
        var window: ComposeDialog? = null

        launchTestApplication {
            DialogWindow(onCloseRequest = ::exitApplication) {
                window = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(window?.isShowing).isTrue()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }

    @Test
    fun `disable closing dialog`() = runApplicationTest {
        var isOpen by mutableStateOf(true)
        var isCloseCalled by mutableStateOf(false)
        var window: ComposeDialog? = null

        launchTestApplication {
            if (isOpen) {
                DialogWindow(
                    onCloseRequest = {
                        isCloseCalled = true
                    }
                ) {
                    window = this.window
                    Box(Modifier.size(32.dp).background(Color.Red))
                }
            }
        }

        awaitIdle()

        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        awaitIdle()
        assertThat(isCloseCalled).isTrue()
        assertThat(window?.isShowing).isTrue()

        isOpen = false
        awaitIdle()
        assertThat(window?.isShowing).isFalse()
    }

    @Test
    fun `show splash screen`() = runApplicationTest {
        var window1: ComposeDialog? = null
        var window2: ComposeDialog? = null

        var isOpen by mutableStateOf(true)
        var isLoading by mutableStateOf(true)

        launchTestApplication {
            if (isOpen) {
                if (isLoading) {
                    DialogWindow(onCloseRequest = {}) {
                        window1 = this.window
                        Box(Modifier.size(32.dp).background(Color.Red))
                    }
                } else {
                    DialogWindow(onCloseRequest = {}) {
                        window2 = this.window
                        Box(Modifier.size(32.dp).background(Color.Blue))
                    }
                }
            }
        }

        awaitIdle()
        assertThat(window1?.isShowing).isTrue()
        assertThat(window2).isNull()

        isLoading = false
        awaitIdle()
        assertThat(window1?.isShowing).isFalse()
        assertThat(window2?.isShowing).isTrue()

        isOpen = false
        awaitIdle()
        assertThat(window1?.isShowing).isFalse()
        assertThat(window2?.isShowing).isFalse()
    }

    @Test
    fun `open two dialogs`() = runApplicationTest {
        var window1: ComposeDialog? = null
        var window2: ComposeDialog? = null

        var isOpen by mutableStateOf(true)

        launchTestApplication {
            if (isOpen) {
                DialogWindow(onCloseRequest = {}) {
                    window1 = this.window
                    Box(Modifier.size(32.dp).background(Color.Red))
                }

                DialogWindow(onCloseRequest = {}) {
                    window2 = this.window
                    Box(Modifier.size(32.dp).background(Color.Blue))
                }
            }
        }

        awaitIdle()
        assertThat(window1?.isShowing).isTrue()
        assertThat(window2?.isShowing).isTrue()

        isOpen = false
        awaitIdle()
        assertThat(window1?.isShowing).isFalse()
        assertThat(window2?.isShowing).isFalse()
    }

    @Test
    fun `open nested dialog`() = runApplicationTest(useDelay = true) {
        var window1: ComposeDialog? = null
        var window2: ComposeDialog? = null

        var isOpen by mutableStateOf(true)
        var isNestedOpen by mutableStateOf(true)

        launchTestApplication {
            if (isOpen) {
                DialogWindow(
                    onCloseRequest = {},
                    state = rememberDialogState(
                        size = DpSize(600.dp, 600.dp),
                    )
                ) {
                    window1 = this.window
                    Box(Modifier.size(32.dp).background(Color.Red))

                    if (isNestedOpen) {
                        DialogWindow(
                            onCloseRequest = {},
                            state = rememberDialogState(
                                size = DpSize(300.dp, 300.dp),
                            )
                        ) {
                            window2 = this.window
                            Box(Modifier.size(32.dp).background(Color.Blue))
                        }
                    }
                }
            }
        }

        awaitIdle()
        assertThat(window1?.isShowing).isTrue()
        assertThat(window2?.isShowing).isTrue()

        isNestedOpen = false
        awaitIdle()
        assertThat(window1?.isShowing).isTrue()
        assertThat(window2?.isShowing).isFalse()

        isNestedOpen = true
        awaitIdle()
        assertThat(window1?.isShowing).isTrue()
        assertThat(window2?.isShowing).isTrue()

        isOpen = false
        awaitIdle()
        assertThat(window1?.isShowing).isFalse()
        assertThat(window2?.isShowing).isFalse()
    }

    @Test
    fun `pass composition local to dialogs`() = runApplicationTest {
        var actualValue1: Int? = null
        var actualValue2: Int? = null

        var isOpen by mutableStateOf(true)
        var testValue by mutableStateOf(0)
        val localTestValue = compositionLocalOf { testValue }

        launchTestApplication {
            if (isOpen) {
                CompositionLocalProvider(localTestValue provides testValue) {
                    DialogWindow(
                        onCloseRequest = {},
                        state = rememberDialogState(
                            size = DpSize(600.dp, 600.dp),
                        )
                    ) {
                        actualValue1 = localTestValue.current
                        Box(Modifier.size(32.dp).background(Color.Red))

                        DialogWindow(
                            onCloseRequest = {},
                            state = rememberDialogState(
                                size = DpSize(300.dp, 300.dp),
                            )
                        ) {
                            actualValue2 = localTestValue.current
                            Box(Modifier.size(32.dp).background(Color.Blue))
                        }
                    }
                }
            }
        }

        awaitIdle()
        assertThat(actualValue1).isEqualTo(0)
        assertThat(actualValue2).isEqualTo(0)

        testValue = 42
        awaitIdle()
        assertThat(actualValue1).isEqualTo(42)
        assertThat(actualValue2).isEqualTo(42)

        isOpen = false
    }

    @Test
    fun `DisposableEffect call order`() = runApplicationTest {
        var initCount = 0
        var disposeCount = 0

        var isOpen by mutableStateOf(true)

        launchTestApplication {
            if (isOpen) {
                DialogWindow(onCloseRequest = {}) {
                    DisposableEffect(Unit) {
                        initCount++
                        onDispose {
                            disposeCount++
                        }
                    }
                }
            }
        }

        awaitIdle()
        assertThat(initCount).isEqualTo(1)
        assertThat(disposeCount).isEqualTo(0)

        isOpen = false
        awaitIdle()
        assertThat(initCount).isEqualTo(1)
        assertThat(disposeCount).isEqualTo(1)
    }

    @Test
    fun `catch key handlers`() = runApplicationTest {
        var window: ComposeDialog? = null
        val onKeyEventKeys = mutableSetOf<Key>()
        val onPreviewKeyEventKeys = mutableSetOf<Key>()

        fun clear() {
            onKeyEventKeys.clear()
            onPreviewKeyEventKeys.clear()
        }

        launchTestApplication {
            DialogWindow(
                onCloseRequest = ::exitApplication,
                onPreviewKeyEvent = {
                    onPreviewKeyEventKeys.add(it.key)
                    it.key == Key.Q
                },
                onKeyEvent = {
                    onKeyEventKeys.add(it.key)
                    it.key == Key.W
                }
            ) {
                window = this.window
            }
        }

        awaitIdle()

        window?.sendKeyEvent(KeyEvent.VK_Q)
        awaitIdle()
        assertThat(onPreviewKeyEventKeys).isEqualTo(setOf(Key.Q))
        assertThat(onKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_W)
        awaitIdle()
        assertThat(onPreviewKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onKeyEventKeys).isEqualTo(setOf(Key.W))

        clear()
        window?.sendKeyEvent(KeyEvent.VK_E)
        awaitIdle()
        assertThat(onPreviewKeyEventKeys).isEqualTo(setOf(Key.E))
        assertThat(onKeyEventKeys).isEqualTo(setOf(Key.E))
    }

    @Test
    fun `catch key handlers with focused node`() = runApplicationTest {
        var window: ComposeDialog? = null
        val onWindowKeyEventKeys = mutableSetOf<Key>()
        val onWindowPreviewKeyEventKeys = mutableSetOf<Key>()
        val onNodeKeyEventKeys = mutableSetOf<Key>()
        val onNodePreviewKeyEventKeys = mutableSetOf<Key>()

        fun clear() {
            onWindowKeyEventKeys.clear()
            onWindowPreviewKeyEventKeys.clear()
            onNodeKeyEventKeys.clear()
            onNodePreviewKeyEventKeys.clear()
        }

        launchTestApplication {
            DialogWindow(
                onCloseRequest = ::exitApplication,
                onPreviewKeyEvent = {
                    onWindowPreviewKeyEventKeys.add(it.key)
                    it.key == Key.Q
                },
                onKeyEvent = {
                    onWindowKeyEventKeys.add(it.key)
                    it.key == Key.W
                },
            ) {
                window = this.window

                val focusRequester = remember(::FocusRequester)
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Box(
                    Modifier
                        .focusRequester(focusRequester)
                        .focusTarget()
                        .onPreviewKeyEvent {
                            onNodePreviewKeyEventKeys.add(it.key)
                            it.key == Key.E
                        }
                        .onKeyEvent {
                            onNodeKeyEventKeys.add(it.key)
                            it.key == Key.R
                        }
                )
            }
        }

        awaitIdle()

        window?.sendKeyEvent(KeyEvent.VK_Q)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.Q))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(emptySet<Key>())
        assertThat(onNodeKeyEventKeys).isEqualTo(emptySet<Key>())
        assertThat(onWindowKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_W)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onNodeKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onWindowKeyEventKeys).isEqualTo(setOf(Key.W))

        clear()
        window?.sendKeyEvent(KeyEvent.VK_E)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.E))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.E))
        assertThat(onNodeKeyEventKeys).isEqualTo(emptySet<Key>())
        assertThat(onWindowKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_R)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.R))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.R))
        assertThat(onNodeKeyEventKeys).isEqualTo(setOf(Key.R))
        assertThat(onWindowKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_T)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.T))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.T))
        assertThat(onNodeKeyEventKeys).isEqualTo(setOf(Key.T))
        assertThat(onWindowKeyEventKeys).isEqualTo(setOf(Key.T))
    }

    private fun testDrawingBeforeDialogIsVisible(
        dialogState: DialogState,
        canvasSizeModifier: Modifier,
        expectedCanvasSize: DialogWindowScope.() -> DpSize
    ) = runApplicationTest {
        var isComposed = false
        var isDrawn = false
        var isVisibleOnFirstComposition = false
        var isVisibleOnFirstDraw = false
        var actualCanvasSize: IntSize? = null
        var expectedCanvasSizePx: IntSize? = null

        launchTestApplication {
            DialogWindow(
                onCloseRequest = ::exitApplication,
                state = dialogState
            ) {
                if (!isComposed) {
                    isVisibleOnFirstComposition = window.isVisible
                    isComposed = true
                }

                Canvas(canvasSizeModifier) {
                    if (!isDrawn) {
                        isVisibleOnFirstDraw = window.isVisible
                        isDrawn = true

                        // toInt() because this is how ComposeWindow rounds decimal sizes
                        // (see ComposeBridge.updateSceneSize)
                        actualCanvasSize = size.toInt()
                        expectedCanvasSizePx = expectedCanvasSize().toSize().roundToIntSize()
                    }
                }
            }
        }

        awaitIdle()

        assertThat(isComposed)
        assertThat(isDrawn)
        assertThat(isVisibleOnFirstComposition).isFalse()
        assertThat(isVisibleOnFirstDraw).isFalse()
        assertEquals(expectedCanvasSizePx, actualCanvasSize)
    }

    @Test(timeout = 30000)
    fun `should draw before dialog is visible`() {
        val windowSize = DpSize(400.dp, 300.dp)
        testDrawingBeforeDialogIsVisible(
            dialogState = DialogState(size = windowSize),
            canvasSizeModifier = Modifier.fillMaxSize(),
            expectedCanvasSize = { windowSize - window.insets.toSize() }
        )
    }

    @Test(timeout = 30000)
    fun `should draw before dialog with unspecified size is visible`() {
        val canvasSize = DpSize(400.dp, 300.dp)
        testDrawingBeforeDialogIsVisible(
            dialogState = DialogState(size = DpSize.Unspecified),
            canvasSizeModifier = Modifier.size(canvasSize),
            expectedCanvasSize = { canvasSize }
        )
    }

    @Test
    fun `pass LayoutDirection to DialogWindow`() = runApplicationTest {
        lateinit var localLayoutDirection: LayoutDirection

        var layoutDirection by mutableStateOf(LayoutDirection.Rtl)
        launchTestApplication {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                DialogWindow(onCloseRequest = {}) {
                    localLayoutDirection = LocalLayoutDirection.current
                }
            }
        }
        awaitIdle()

        assertThat(localLayoutDirection).isEqualTo(LayoutDirection.Rtl)

        // Test that changing the local propagates it into the dialog
        layoutDirection = LayoutDirection.Ltr
        awaitIdle()
        assertThat(localLayoutDirection).isEqualTo(LayoutDirection.Ltr)
    }

    @Test
    fun `modal DialogWindow does not block parent window rendering`() {
        runApplicationTest(useDelay = true) {
            var text by mutableStateOf("1")
            var renderedText: String? = null
            lateinit var dialog: ComposeDialog

            launchTestApplication {
                Window(onCloseRequest = {}) {
                    val textMeasurer = rememberTextMeasurer()
                    Canvas(Modifier.size(200.dp)) {
                        renderedText = text
                        drawText(
                            textMeasurer = textMeasurer,
                            text = text
                        )
                    }

                    DialogWindow(onCloseRequest = {}) {
                        dialog = window
                    }
                }
            }

            awaitIdle()
            assertThat(dialog.isModal).isTrue()
            assertThat(renderedText).isEqualTo("1")

            text = "2"
            awaitIdle()
            assertThat(renderedText).isEqualTo("2")
        }
    }

    @Test
    fun `change alwaysOnTop`() = runApplicationTest {
        var dialog: ComposeDialog? = null

        var alwaysOnTop by mutableStateOf(false)

        launchTestApplication {
            DialogWindow(onCloseRequest = ::exitApplication, alwaysOnTop = alwaysOnTop) {
                dialog = this.window
                Box(Modifier.size(32.dp).background(Color.Red))
            }
        }

        awaitIdle()
        assertThat(dialog?.isAlwaysOnTop).isFalse()

        alwaysOnTop = true
        awaitIdle()
        assertThat(dialog?.isAlwaysOnTop).isTrue()

        dialog?.dispatchEvent(WindowEvent(dialog, WindowEvent.WINDOW_CLOSING))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `can save Unspecified dialog size`() = runComposeUiTest {
        val expectedState = DialogState(size = DpSize.Unspecified)
        lateinit var restoredState: DialogState
        var index by mutableIntStateOf(0)
        setContent {
            val saveableStateHolder = rememberSaveableStateHolder()
            saveableStateHolder.SaveableStateProvider(index) {
                val state = rememberDialogState(size = DpSize.Unspecified)
                if (index == 0) {
                    restoredState = state
                }
            }
        }

        index = 1
        waitForIdle()
        index = 0
        waitForIdle()

        assertDialogStateEquals(expectedState, restoredState)
    }
}

private fun assertDialogStateEquals(expected: DialogState, actual: DialogState) {
    assertEquals(expected.size, actual.size, "size differs")
    assertEquals(expected.position, actual.position, "position differs")
}