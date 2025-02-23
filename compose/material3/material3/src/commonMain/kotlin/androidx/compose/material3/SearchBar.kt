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

import androidx.annotation.FloatRange
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.SearchBarDefaults.InputFieldHeight
import androidx.compose.material3.internal.BackEventCompat
import androidx.compose.material3.internal.BackEventProgress
import androidx.compose.material3.internal.BackHandler
import androidx.compose.material3.internal.BasicEdgeToEdgeDialog
import androidx.compose.material3.internal.MutableWindowInsets
import androidx.compose.material3.internal.PredictiveBack
import androidx.compose.material3.internal.PredictiveBackHandler
import androidx.compose.material3.internal.PredictiveBackState
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.SwipeEdge
import androidx.compose.material3.internal.getString
import androidx.compose.material3.internal.systemBarsForVisualComponents
import androidx.compose.material3.internal.textFieldBackground
import androidx.compose.material3.tokens.ElevationTokens
import androidx.compose.material3.tokens.FilledTextFieldTokens
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.material3.tokens.MotionTokens
import androidx.compose.material3.tokens.SearchBarTokens
import androidx.compose.material3.tokens.SearchViewTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * <a href="https://m3.material.io/components/search/overview" class="external"
 * target="_blank">Material Design search</a>.
 *
 * A search bar represents a field that allows users to enter a keyword or phrase and get relevant
 * information. It can be used as a way to navigate through an app via search queries.
 *
 * ![Search bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/search-bar.png)
 *
 * A search bar expands when focused to display dynamic suggestions or search results. An expanded
 * [SearchBar] displays its results in a full-screen dialog. If this expansion behavior is
 * undesirable, for example on large tablet screens, consider using [DockedSearchBar] instead.
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField].
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this search bar when collapsed.
 * @param shape the shape of this search bar when it is collapsed. When expanded, the shape will
 *   always be [SearchBarDefaults.fullScreenShape].
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 * @param content the content of this search bar to display search results below the [inputField].
 */
@ExperimentalMaterial3Api
@Composable
internal fun SearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    CollapsedSearchBar(
        // Modifier gets passed to Collapsed because Expanded opens a separate dialog.
        modifier = modifier,
        state = state,
        inputField = inputField,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    )
    ExpandedFullScreenSearchBar(
        state = state,
        inputField = inputField,
        collapsedShape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content
    )
}

/**
 * <a href="https://m3.material.io/components/search/overview" class="external"
 * target="_blank">Material Design search</a>.
 *
 * A search bar represents a field that allows users to enter a keyword or phrase and get relevant
 * information. It can be used as a way to navigate through an app via search queries.
 *
 * A [TopSearchBar] can be used in conjunction with a [Scaffold] for a search bar that remains at
 * the top of the screen.
 *
 * ![Search bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/search-bar.png)
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField].
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this search bar when collapsed.
 * @param shape the shape of this search bar when it is collapsed. When expanded, the shape will
 *   always be [SearchBarDefaults.fullScreenShape].
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 * @param windowInsets the window insets that the search bar will respect when collapsed. When
 *   expanded, the window insets will always be [SearchBarDefaults.fullScreenWindowInsets].
 * @param scrollBehavior a [SearchBarScrollBehavior] which works in conjunction with a scrolled
 *   content to change the search bar appearance as the content scrolls. If null, the search bar
 *   will not automatically react to scrolling.
 * @param content the content of this search bar to display search results below the [inputField].
 */
@ExperimentalMaterial3Api
@Composable
internal fun TopSearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    scrollBehavior: SearchBarScrollBehavior? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SearchBar(
        state = state,
        inputField = inputField,
        modifier =
            modifier
                .then(
                    (scrollBehavior?.let { with(it) { Modifier.searchBarScrollBehavior() } }
                        ?: Modifier)
                )
                .windowInsetsPadding(windowInsets)
                .padding(SearchBarAsTopBarPadding)
                .fillMaxWidth()
                .wrapContentWidth(),
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content,
    )
}

/**
 * <a href="https://m3.material.io/components/search/overview" class="external"
 * target="_blank">Material Design search</a>.
 *
 * A search bar represents a floating search field that allows users to enter a keyword or phrase
 * and get relevant information. It can be used as a way to navigate through an app via search
 * queries.
 *
 * A search bar expands into a search "view" and can be used to display dynamic suggestions or
 * search results.
 *
 * ![Search bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/search-bar.png)
 *
 * A [SearchBar] tries to occupy the entirety of its allowed size in the expanded state. For
 * full-screen behavior as specified by Material guidelines, parent layouts of the [SearchBar] must
 * not pass any [Constraints] that limit its size, and the host activity should set
 * `WindowCompat.setDecorFitsSystemWindows(window, false)`.
 *
 * If this expansion behavior is undesirable, for example on large tablet screens, [DockedSearchBar]
 * can be used instead.
 *
 * An example looks like:
 *
 * @sample androidx.compose.material3.samples.SearchBarSample
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param expanded whether this search bar is expanded and showing search results.
 * @param onExpandedChange the callback to be invoked when this search bar's expanded state is
 *   changed.
 * @param modifier the [Modifier] to be applied to this search bar.
 * @param shape the shape of this search bar when it is not [expanded]. When [expanded], the shape
 *   will always be [SearchBarDefaults.fullScreenShape].
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar
 * @param windowInsets the window insets that this search bar will respect
 * @param content the content of this search bar to display search results below the [inputField].
 */
@ExperimentalMaterial3Api
@Composable
fun SearchBar(
    inputField: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animationProgress = remember { Animatable(initialValue = if (expanded) 1f else 0f) }
    val finalBackProgress = remember { mutableFloatStateOf(Float.NaN) }
    val firstBackEvent = remember { mutableStateOf<BackEventCompat?>(null) }
    val currentBackEvent = remember { mutableStateOf<BackEventCompat?>(null) }

    LaunchedEffect(expanded) {
        val animationInProgress = animationProgress.value > 0 && animationProgress.value < 1
        val animationSpec =
            if (animationInProgress) AnimationPredictiveBackExitFloatSpec
            else if (expanded) AnimationEnterFloatSpec else AnimationExitFloatSpec
        val targetValue = if (expanded) 1f else 0f
        if (animationProgress.value != targetValue) {
            animationProgress.animateTo(targetValue, animationSpec)
        }
        if (!expanded) {
            finalBackProgress.floatValue = Float.NaN
            firstBackEvent.value = null
            currentBackEvent.value = null
        }
    }

    val mutatorMutex = remember { MutatorMutex() }
    PredictiveBackHandler(enabled = expanded) { progress ->
        mutatorMutex.mutate {
            try {
                finalBackProgress.floatValue = Float.NaN
                progress.collect { backEvent ->
                    if (firstBackEvent.value == null) {
                        firstBackEvent.value = backEvent
                    }
                    currentBackEvent.value = backEvent
                    val interpolatedProgress = PredictiveBack.transform(backEvent.progress)
                    animationProgress.snapTo(targetValue = 1 - interpolatedProgress)
                }
                finalBackProgress.floatValue = animationProgress.value
                onExpandedChange(false)
            } catch (e: CancellationException) {
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = AnimationPredictiveBackExitFloatSpec
                )
                finalBackProgress.floatValue = Float.NaN
                firstBackEvent.value = null
                currentBackEvent.value = null
            }
        }
    }

    SearchBarImpl(
        animationProgress = animationProgress,
        finalBackProgress = finalBackProgress,
        firstBackEvent = firstBackEvent,
        currentBackEvent = currentBackEvent,
        modifier = modifier,
        inputField = inputField,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        windowInsets = windowInsets,
        content = content,
    )
}

/**
 * <a href="https://m3.material.io/components/search/overview" class="external"
 * target="_blank">Material Design search</a>.
 *
 * A search bar represents a floating search field that allows users to enter a keyword or phrase
 * and get relevant information. It can be used as a way to navigate through an app via search
 * queries.
 *
 * An search bar expands into a search "view" and can be used to display dynamic suggestions or
 * search results.
 *
 * ![Search bar
 * image](https://developer.android.com/images/reference/androidx/compose/material3/docked-search-bar.png)
 *
 * A [DockedSearchBar] displays search results in a bounded table below the input field. It is an
 * alternative to [SearchBar] when expanding to full-screen size is undesirable on large screens
 * such as tablets.
 *
 * An example looks like:
 *
 * @sample androidx.compose.material3.samples.DockedSearchBarSample
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param expanded whether this search bar is expanded and showing search results.
 * @param onExpandedChange the callback to be invoked when this search bar's expanded state is
 *   changed.
 * @param modifier the [Modifier] to be applied to this search bar.
 * @param shape the shape of this search bar.
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below the search bar.
 * @param content the content of this search bar to display search results below the [inputField].
 */
@ExperimentalMaterial3Api
@Composable
fun DockedSearchBar(
    inputField: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.dockedShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = colors.containerColor,
        contentColor = contentColorFor(colors.containerColor),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier.zIndex(1f).width(SearchBarMinWidth)
    ) {
        Column {
            inputField()

            AnimatedVisibility(
                visible = expanded,
                enter = DockedEnterTransition,
                exit = DockedExitTransition,
            ) {
                val windowContainerHeight = getWindowContainerHeight()
                val maxHeight = windowContainerHeight * DockedExpandedTableMaxHeightScreenRatio
                val minHeight = DockedExpandedTableMinHeight.coerceAtMost(maxHeight)

                Column(Modifier.heightIn(min = minHeight, max = maxHeight)) {
                    HorizontalDivider(color = colors.dividerColor)
                    content()
                }
            }
        }
    }

    BackHandler(enabled = expanded) { onExpandedChange(false) }
}

/**
 * A building block component representing a search bar in the collapsed state.
 *
 * Unless specific customization is needed, consider using a higher level component such as
 * [SearchBar] or [DockedSearchBar] instead.
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField].
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this collapsed search bar.
 * @param shape the shape of this search bar.
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 */
@Suppress("ComposableLambdaParameterNaming", "ComposableLambdaParameterPosition")
@ExperimentalMaterial3Api
@Composable
internal fun CollapsedSearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
) {
    // Disable when collapsed to avoid keyboard flicker when the expanded search bar opens.
    DisableSoftKeyboard {
        Surface(
            modifier = modifier.onGloballyPositioned { state.collapsedCoords = it },
            shape = shape,
            color = colors.containerColor,
            contentColor = contentColorFor(colors.containerColor),
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = inputField,
        )
    }
}

/**
 * A building block component representing a search bar that is currently expanding or in the
 * expanded state. This component is displayed in a new full-screen dialog.
 *
 * Unless specific customization is needed, consider using a higher level component such as
 * [SearchBar] or [DockedSearchBar] instead.
 *
 * @param state the state of the search bar. This state should also be passed to the [inputField].
 * @param inputField the input field of this search bar that allows entering a query, typically a
 *   [SearchBarDefaults.InputField].
 * @param modifier the [Modifier] to be applied to this expanded search bar.
 * @param collapsedShape the shape of the search bar when it is collapsed. When fully expanded, the
 *   shape will always be [SearchBarDefaults.fullScreenShape].
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 *   in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 *   translucent primary color overlay is applied on top of the container. A higher tonal elevation
 *   value will result in a darker color in light theme and lighter color in dark theme. See also:
 *   [Surface].
 * @param shadowElevation the elevation for the shadow below this search bar.
 * @param windowInsets the window insets that this search bar will respect.
 * @param content the content of this search bar to display search results below the [inputField].
 */
@ExperimentalMaterial3Api
@Composable
internal fun ExpandedFullScreenSearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    collapsedShape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: @Composable () -> WindowInsets = { SearchBarDefaults.fullScreenWindowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!state.isExpanded) return

    val coroutineScope = rememberCoroutineScope()

    BasicEdgeToEdgeDialog(
        onDismissRequest = { coroutineScope.launch { state.animateToCollapsed() } }
    ) { predictiveBackState ->
        val softwareKeyboardController = LocalSoftwareKeyboardController.current
        SideEffect { state.softwareKeyboardController = softwareKeyboardController }
        FullScreenSearchBarLayout(
            state = state,
            predictiveBackState = predictiveBackState,
            inputField = inputField,
            modifier = modifier,
            collapsedShape = collapsedShape,
            colors = colors,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            windowInsets = windowInsets(),
            content = content,
        )
    }
}

/**
 * The state of a search bar.
 *
 * @property focusRequester The [FocusRequester] to request focus on the search bar.
 *   [SearchBarDefaults.InputField] applies this automatically. Custom input fields must attach this
 *   focus requester using [Modifier.focusRequester].
 */
@ExperimentalMaterial3Api
@Stable
internal class SearchBarState
private constructor(
    private val animatable: Animatable<Float, AnimationVector1D>,
    private val animationSpecForExpand: AnimationSpec<Float>,
    private val animationSpecForCollapse: AnimationSpec<Float>,
    val focusRequester: FocusRequester,
) {
    /**
     * Construct a [SearchBarState].
     *
     * @param initialExpanded the initial value of whether the search bar is expanded.
     * @param animationSpecForExpand the animation spec used when the search bar expands.
     * @param animationSpecForCollapse the animation spec used when the search bar collapses.
     * @param focusRequester the focus requester to be applied to the search bar's input field.
     */
    constructor(
        initialExpanded: Boolean,
        animationSpecForExpand: AnimationSpec<Float>,
        animationSpecForCollapse: AnimationSpec<Float>,
        focusRequester: FocusRequester = FocusRequester(),
    ) : this(
        animatable = Animatable(if (initialExpanded) 1f else 0f),
        animationSpecForExpand = animationSpecForExpand,
        animationSpecForCollapse = animationSpecForCollapse,
        focusRequester = focusRequester,
    )

    /** The layout coordinates, if available, of the search bar when it is collapsed. */
    var collapsedCoords: LayoutCoordinates? by mutableStateOf(null)

    /**
     * The animation progress of the search bar, where 0 represents the collapsed state and 1
     * represents the expanded state.
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    val progress: Float
        get() = animatable.value.coerceIn(0f, 1f)

    /**
     * Whether this search bar is expanded (showing search results), or in the process of expanding.
     */
    val isExpanded: Boolean by derivedStateOf { progress > 0f }

    internal var softwareKeyboardController: SoftwareKeyboardController? = null

    /** Animate the search bar to its expanded state. */
    suspend fun animateToExpanded() {
        animatable.animateTo(targetValue = 1f, animationSpec = animationSpecForExpand)
        focusRequester.requestFocus()
    }

    /** Animate the search bar to its collapsed state. */
    suspend fun animateToCollapsed() {
        softwareKeyboardController?.hide()
        animatable.animateTo(targetValue = 0f, animationSpec = animationSpecForCollapse)
    }

    /** Snap the search bar progress to the given [fraction]. */
    suspend fun snapTo(fraction: Float) {
        animatable.snapTo(fraction)
    }

    companion object {
        fun Saver(
            animationSpecForExpand: AnimationSpec<Float>,
            animationSpecForCollapse: AnimationSpec<Float>,
            focusRequester: FocusRequester,
        ): Saver<SearchBarState, *> =
            listSaver(
                save = { listOf(it.progress) },
                restore = {
                    SearchBarState(
                        animatable = Animatable(it[0], Float.VectorConverter),
                        animationSpecForExpand = animationSpecForExpand,
                        animationSpecForCollapse = animationSpecForCollapse,
                        focusRequester = focusRequester,
                    )
                },
            )
    }
}

/**
 * Create and remember a [SearchBarState].
 *
 * @param initialExpanded the initial value of whether the search bar is expanded.
 * @param animationSpecForExpand the animation spec used when the search bar expands.
 * @param animationSpecForCollapse the animation spec used when the search bar collapses.
 * @param focusRequester the focus requester to be applied to the search bar's input field.
 */
@ExperimentalMaterial3Api
@Composable
internal fun rememberSearchBarState(
    initialExpanded: Boolean = false,
    animationSpecForExpand: AnimationSpec<Float> = MotionSchemeKeyTokens.SlowSpatial.value(),
    animationSpecForCollapse: AnimationSpec<Float> = MotionSchemeKeyTokens.DefaultSpatial.value(),
    focusRequester: FocusRequester? = null,
): SearchBarState {
    @Suppress("NAME_SHADOWING") val focusRequester = focusRequester ?: remember { FocusRequester() }
    return rememberSaveable(
        initialExpanded,
        animationSpecForExpand,
        animationSpecForCollapse,
        focusRequester,
        saver =
            SearchBarState.Saver(
                animationSpecForExpand = animationSpecForExpand,
                animationSpecForCollapse = animationSpecForCollapse,
                focusRequester = focusRequester,
            )
    ) {
        SearchBarState(
            initialExpanded = initialExpanded,
            animationSpecForExpand = animationSpecForExpand,
            animationSpecForCollapse = animationSpecForCollapse,
            focusRequester = focusRequester,
        )
    }
}

/**
 * A [SearchBarScrollBehavior] defines how a search bar should behave when the content beneath it is
 * scrolled.
 *
 * @see [SearchBarDefaults.enterAlwaysSearchBarScrollBehavior]
 */
@ExperimentalMaterial3Api
@Stable
internal interface SearchBarScrollBehavior {
    /**
     * The search bar's current offset due to scrolling, in pixels. This offset is applied to the
     * fixed size of the search bar to control the displayed size when content is being scrolled.
     *
     * This value is typically negative.
     *
     * Updates to the [scrollOffset] value are coerced between [scrollOffsetLimit] and 0.
     */
    var scrollOffset: Float

    /**
     * The limit that a search bar can be offset due to scrolling, in pixels.
     *
     * This value is typically negative.
     *
     * Use this limit to coerce the [scrollOffset] value when it's updated.
     */
    var scrollOffsetLimit: Float

    /**
     * A [NestedScrollConnection] that should be attached to a [Modifier.nestedScroll] in order to
     * keep track of scroll events.
     */
    val nestedScrollConnection: NestedScrollConnection

    /**
     * The modifier that adds scrolling behavior to the search bar component. [TopSearchBar] applies
     * this automatically.
     */
    fun Modifier.searchBarScrollBehavior(): Modifier
}

@OptIn(ExperimentalMaterial3Api::class)
@Stable
private class EnterAlwaysSearchBarScrollBehavior(
    initialOffset: Float,
    initialOffsetLimit: Float,
    val canScroll: () -> Boolean,
    val reverseLayout: Boolean,
    val snapAnimationSpec: AnimationSpec<Float>,
    val flingAnimationSpec: DecayAnimationSpec<Float>,
) : SearchBarScrollBehavior {
    private var _offset = mutableFloatStateOf(initialOffset)

    override var scrollOffset: Float
        get() = _offset.floatValue
        set(newOffset) {
            _offset.floatValue = newOffset.coerceIn(scrollOffsetLimit, 0f)
        }

    override var scrollOffsetLimit by mutableFloatStateOf(initialOffsetLimit)

    override fun Modifier.searchBarScrollBehavior(): Modifier {
        return this.draggable(
                orientation = Orientation.Vertical,
                state = DraggableState { delta -> scrollOffset += delta },
                onDragStopped = { velocity -> settleSearchBar(velocity) },
                enabled = canScroll(),
            )
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val scrollOffset = scrollOffset.roundToInt()
                val scrolledHeight = (placeable.height + scrollOffset).coerceAtLeast(0)
                layout(placeable.width, scrolledHeight) {
                    placeable.placeWithLayer(0, scrollOffset)
                }
            }
            .onSizeChanged { size -> scrollOffsetLimit = -size.height.toFloat() }
    }

    override val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll()) return Offset.Zero
                val prevScrollOffset = scrollOffset
                scrollOffset += available.y
                // The scrollOffset is coerced between scrollOffsetLimit and 0, so we check if its
                // value was actually changed after available.y is added. If so, the search bar is
                // currently  in between scroll states.
                // Note: when the content is set with reversed layout, we always return Offset.Zero.
                return if (!reverseLayout && prevScrollOffset != scrollOffset) {
                    available.copy(x = 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!canScroll()) return Offset.Zero
                if (reverseLayout && available.y > 0f) {
                    // In a reversed layout, consume scroll if it's a pull down
                    // to reveal the search bar but not if it's a pull up to hide.
                    scrollOffset += available.y
                    return available.copy(x = 0f)
                }
                if (!reverseLayout) scrollOffset += consumed.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!canScroll()) return Velocity.Zero
                return settleSearchBar(available.y)
            }
        }

    private suspend fun settleSearchBar(velocity: Float): Velocity {
        // Check if the search bar scroll is at a scroll limit. If so, no need to settle the search
        // bar, and just return Velocity.Zero.
        // Note that we don't check for 0f due to float precision with the scrollFraction
        // calculation.
        val scrollFraction = if (scrollOffsetLimit != 0f) scrollOffset / scrollOffsetLimit else 0f
        if (scrollFraction < 0.01f || scrollFraction == 1f) {
            return Velocity.Zero
        }
        var remainingVelocity = velocity
        // In case there is an initial velocity that was left after a previous user fling, animate
        // to continue the motion to scroll the search bar.
        if (abs(velocity) > 1f) {
            var lastValue = 0f
            AnimationState(
                    initialValue = 0f,
                    initialVelocity = velocity,
                )
                .animateDecay(flingAnimationSpec) {
                    val delta = value - lastValue
                    val initialScrollOffset = scrollOffset
                    scrollOffset = initialScrollOffset + delta
                    val consumed = abs(initialScrollOffset - scrollOffset)
                    lastValue = value
                    remainingVelocity = this.velocity
                    // avoid rounding errors and stop if anything is unconsumed
                    if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
                }
        }
        if (scrollOffsetLimit < scrollOffset && scrollOffset < 0) {
            AnimationState(initialValue = scrollOffset).animateTo(
                targetValue = if (scrollFraction < 0.5f) 0f else scrollOffsetLimit,
                animationSpec = snapAnimationSpec,
            ) {
                scrollOffset = value
            }
        }

        return Velocity(0f, remainingVelocity)
    }

    companion object {
        fun Saver(
            canScroll: () -> Boolean,
            snapAnimationSpec: AnimationSpec<Float>,
            flingAnimationSpec: DecayAnimationSpec<Float>,
        ): Saver<EnterAlwaysSearchBarScrollBehavior, *> =
            listSaver(
                save = { listOf(it.scrollOffset, it.scrollOffsetLimit, it.reverseLayout) },
                restore = {
                    EnterAlwaysSearchBarScrollBehavior(
                        initialOffset = it[0] as Float,
                        initialOffsetLimit = it[1] as Float,
                        reverseLayout = it[2] as Boolean,
                        canScroll = canScroll,
                        snapAnimationSpec = snapAnimationSpec,
                        flingAnimationSpec = flingAnimationSpec,
                    )
                }
            )
    }
}

/** Defaults used in [SearchBar] and [DockedSearchBar]. */
@ExperimentalMaterial3Api
object SearchBarDefaults {
    /** Default tonal elevation for a search bar. */
    val TonalElevation: Dp = ElevationTokens.Level0

    /** Default shadow elevation for a search bar. */
    val ShadowElevation: Dp = ElevationTokens.Level0

    @Deprecated(
        message = "Renamed to TonalElevation. Not to be confused with ShadowElevation.",
        replaceWith = ReplaceWith("TonalElevation"),
        level = DeprecationLevel.WARNING,
    )
    val Elevation: Dp = TonalElevation

    /** Default height for a search bar's input field, or a search bar in the unexpanded state. */
    val InputFieldHeight: Dp = SearchBarTokens.ContainerHeight

    /** Default shape for a search bar's input field, or a search bar in the unexpanded state. */
    val inputFieldShape: Shape
        @Composable get() = SearchBarTokens.ContainerShape.value

    /** Default shape for a [SearchBar] in the expanded state. */
    val fullScreenShape: Shape
        @Composable get() = SearchViewTokens.FullScreenContainerShape.value

    /** Default shape for a [DockedSearchBar]. */
    val dockedShape: Shape
        @Composable get() = SearchViewTokens.DockedContainerShape.value

    /** Default window insets for a [SearchBar]. */
    val windowInsets: WindowInsets
        @Composable
        get() =
            WindowInsets.systemBarsForVisualComponents.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )

    /**
     * Default window insets used and consumed by a full screen search bar in the expanded state.
     */
    internal val fullScreenWindowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing

    /**
     * Returns a [SearchBarScrollBehavior]. A search bar that is set up with this behavior will
     * immediately scroll upward off-screen when the content is pulled up, and will immediately
     * appear when the content is pulled down.
     *
     * The returned [SearchBarScrollBehavior] is remembered across compositions.
     *
     * @param initialOffset the initial value for [SearchBarScrollBehavior.scrollOffset]. Should be
     *   between [initialOffsetLimit] and 0.
     * @param initialOffsetLimit the initial value for [SearchBarScrollBehavior.scrollOffsetLimit],
     *   which represents the pixel limit that a search bar is allowed to scroll off-screen when the
     *   content is scrolled.
     * @param canScroll a callback used to determine whether scroll events are to be handled by this
     *   [SearchBarScrollBehavior].
     * @param snapAnimationSpec an [AnimationSpec] that defines how the search bar's scroll offset
     *   snaps to either its limit or 0 when a fling or a drag scrolls it into an intermediate
     *   position.
     * @param flingAnimationSpec a [DecayAnimationSpec] that defines how to fling the search bar
     *   when the user flings the search bar itself, or the content beneath it.
     * @param reverseLayout indicates that this behavior is applied to a scrollable content that has
     *   a reversed direction of scrolling and layout.
     */
    @ExperimentalMaterial3Api
    @Composable
    internal fun enterAlwaysSearchBarScrollBehavior(
        initialOffset: Float = 0f,
        initialOffsetLimit: Float = -Float.MAX_VALUE,
        canScroll: () -> Boolean = { true },
        // TODO Load the motionScheme tokens from the component tokens file
        snapAnimationSpec: AnimationSpec<Float> = MotionSchemeKeyTokens.DefaultEffects.value(),
        flingAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
        reverseLayout: Boolean = false,
    ): SearchBarScrollBehavior =
        rememberSaveable(
            snapAnimationSpec,
            flingAnimationSpec,
            canScroll,
            reverseLayout,
            saver =
                EnterAlwaysSearchBarScrollBehavior.Saver(
                    canScroll = canScroll,
                    snapAnimationSpec = snapAnimationSpec,
                    flingAnimationSpec = flingAnimationSpec,
                )
        ) {
            EnterAlwaysSearchBarScrollBehavior(
                initialOffset = initialOffset,
                initialOffsetLimit = initialOffsetLimit,
                canScroll = canScroll,
                reverseLayout = reverseLayout,
                snapAnimationSpec = snapAnimationSpec,
                flingAnimationSpec = flingAnimationSpec,
            )
        }

    /**
     * Creates a [SearchBarColors] that represents the different colors used in parts of the search
     * bar in different states.
     *
     * @param containerColor the container color of the search bar
     * @param dividerColor the color of the divider between the input field and the search results
     * @param inputFieldColors the colors of the input field. This can be accessed using
     *   [SearchBarColors.inputFieldColors] and should be passed to the `inputField` slot of the
     *   search bar.
     */
    @Composable
    fun colors(
        containerColor: Color = SearchBarTokens.ContainerColor.value,
        dividerColor: Color = SearchViewTokens.DividerColor.value,
        inputFieldColors: TextFieldColors =
            inputFieldColors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = containerColor,
            ),
    ): SearchBarColors =
        SearchBarColors(
            containerColor = containerColor,
            dividerColor = dividerColor,
            inputFieldColors = inputFieldColors,
        )

    /**
     * Creates a [TextFieldColors] that represents the different colors used in the search bar input
     * field in different states.
     *
     * Only a subset of the full list of [TextFieldColors] parameters are used in the input field.
     * All other parameters have no effect.
     *
     * @param focusedTextColor the color used for the input text of this input field when focused
     * @param unfocusedTextColor the color used for the input text of this input field when not
     *   focused
     * @param disabledTextColor the color used for the input text of this input field when disabled
     * @param cursorColor the cursor color for this input field
     * @param selectionColors the colors used when the input text of this input field is selected
     * @param focusedLeadingIconColor the leading icon color for this input field when focused
     * @param unfocusedLeadingIconColor the leading icon color for this input field when not focused
     * @param disabledLeadingIconColor the leading icon color for this input field when disabled
     * @param focusedTrailingIconColor the trailing icon color for this input field when focused
     * @param unfocusedTrailingIconColor the trailing icon color for this input field when not
     *   focused
     * @param disabledTrailingIconColor the trailing icon color for this input field when disabled
     * @param focusedPlaceholderColor the placeholder color for this input field when focused
     * @param unfocusedPlaceholderColor the placeholder color for this input field when not focused
     * @param disabledPlaceholderColor the placeholder color for this input field when disabled
     * @param focusedPrefixColor the prefix color for this input field when focused
     * @param unfocusedPrefixColor the prefix color for this input field when not focused
     * @param disabledPrefixColor the prefix color for this input field when disabled
     * @param focusedSuffixColor the suffix color for this input field when focused
     * @param unfocusedSuffixColor the suffix color for this input field when not focused
     * @param disabledSuffixColor the suffix color for this input field when disabled
     * @param focusedContainerColor the container color for this input field when focused
     * @param unfocusedContainerColor the container color for this input field when not focused
     * @param disabledContainerColor the container color for this input field when disabled
     */
    @Composable
    fun inputFieldColors(
        focusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        unfocusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        disabledTextColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        cursorColor: Color = FilledTextFieldTokens.CaretColor.value,
        selectionColors: TextSelectionColors = LocalTextSelectionColors.current,
        focusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        unfocusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        disabledLeadingIconColor: Color =
            FilledTextFieldTokens.DisabledLeadingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledLeadingIconOpacity
            ),
        focusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        unfocusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        disabledTrailingIconColor: Color =
            FilledTextFieldTokens.DisabledTrailingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledTrailingIconOpacity
            ),
        focusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        unfocusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        disabledPlaceholderColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        focusedPrefixColor: Color = FilledTextFieldTokens.InputPrefixColor.value,
        unfocusedPrefixColor: Color = FilledTextFieldTokens.InputPrefixColor.value,
        disabledPrefixColor: Color =
            FilledTextFieldTokens.InputPrefixColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        focusedSuffixColor: Color = FilledTextFieldTokens.InputSuffixColor.value,
        unfocusedSuffixColor: Color = FilledTextFieldTokens.InputSuffixColor.value,
        disabledSuffixColor: Color =
            FilledTextFieldTokens.InputSuffixColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        focusedContainerColor: Color = SearchBarTokens.ContainerColor.value,
        unfocusedContainerColor: Color = SearchBarTokens.ContainerColor.value,
        disabledContainerColor: Color = SearchBarTokens.ContainerColor.value,
    ): TextFieldColors =
        TextFieldDefaults.colors(
            focusedTextColor = focusedTextColor,
            unfocusedTextColor = unfocusedTextColor,
            disabledTextColor = disabledTextColor,
            cursorColor = cursorColor,
            selectionColors = selectionColors,
            focusedLeadingIconColor = focusedLeadingIconColor,
            unfocusedLeadingIconColor = unfocusedLeadingIconColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            focusedTrailingIconColor = focusedTrailingIconColor,
            unfocusedTrailingIconColor = unfocusedTrailingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
            focusedPlaceholderColor = focusedPlaceholderColor,
            unfocusedPlaceholderColor = unfocusedPlaceholderColor,
            disabledPlaceholderColor = disabledPlaceholderColor,
            focusedPrefixColor = focusedPrefixColor,
            unfocusedPrefixColor = unfocusedPrefixColor,
            disabledPrefixColor = disabledPrefixColor,
            focusedSuffixColor = focusedSuffixColor,
            unfocusedSuffixColor = unfocusedSuffixColor,
            disabledSuffixColor = disabledSuffixColor,
            focusedContainerColor = focusedContainerColor,
            unfocusedContainerColor = unfocusedContainerColor,
            disabledContainerColor = disabledContainerColor,
        )

    /**
     * A text field to input a query in a search bar.
     *
     * This overload of [InputField] uses [TextFieldState] to keep track of the text content and
     * position of the cursor or selection, and [SearchBarState] to keep track of the state of the
     * search bar.
     *
     * @param textFieldState [TextFieldState] that holds the internal editing state of the input
     *   field.
     * @param searchBarState the state of the search bar as a whole.
     * @param onSearch the callback to be invoked when the input service triggers the
     *   [ImeAction.Search] action. The current query in the [textFieldState] comes as a parameter
     *   of the callback.
     * @param modifier the [Modifier] to be applied to this input field.
     * @param enabled the enabled state of this input field. When `false`, this component will not
     *   respond to user input, and it will appear visually disabled and disabled to accessibility
     *   services.
     * @param readOnly controls the editable state of the input field. When `true`, the field cannot
     *   be modified. However, a user can focus it and copy text from it.
     * @param textStyle the style to be applied to the input text. Defaults to [LocalTextStyle].
     * @param placeholder the placeholder to be displayed when the input text is empty.
     * @param leadingIcon the leading icon to be displayed at the start of the input field.
     * @param trailingIcon the trailing icon to be displayed at the end of the input field.
     * @param prefix the optional prefix to be displayed before the input text.
     * @param suffix the optional suffix to be displayed after the input text.
     * @param inputTransformation optional [InputTransformation] that will be used to transform
     *   changes to the [TextFieldState] made by the user. The transformation will be applied to
     *   changes made by hardware and software keyboard events, pasting or dropping text,
     *   accessibility services, and tests. The transformation will _not_ be applied when changing
     *   the [textFieldState] programmatically, or when the transformation is changed. If the
     *   transformation is changed on an existing text field, it will be applied to the next user
     *   edit. The transformation will not immediately affect the current [textFieldState].
     * @param outputTransformation optional [OutputTransformation] that transforms how the contents
     *   of the text field are presented.
     * @param scrollState scroll state that manages the horizontal scroll of the input field.
     * @param shape the shape of the input field.
     * @param colors [TextFieldColors] that will be used to resolve the colors used for this input
     *   field in different states. See [SearchBarDefaults.inputFieldColors].
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     *   emitting [Interaction]s for this input field. You can use this to change the search bar's
     *   appearance or preview the search bar in different states. Note that if `null` is provided,
     *   interactions will still happen internally.
     */
    @ExperimentalMaterial3Api
    @Composable
    internal fun InputField(
        textFieldState: TextFieldState,
        searchBarState: SearchBarState,
        onSearch: (String) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        readOnly: Boolean = false,
        textStyle: TextStyle = LocalTextStyle.current,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        prefix: @Composable (() -> Unit)? = null,
        suffix: @Composable (() -> Unit)? = null,
        inputTransformation: InputTransformation? = null,
        outputTransformation: OutputTransformation? = null,
        scrollState: ScrollState = rememberScrollState(),
        shape: Shape = inputFieldShape,
        colors: TextFieldColors = inputFieldColors(),
        interactionSource: MutableInteractionSource? = null,
    ) {
        @Suppress("NAME_SHADOWING")
        val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

        val focused = interactionSource.collectIsFocusedAsState().value
        val focusManager = LocalFocusManager.current

        val searchSemantics = getString(Strings.SearchBarSearch)
        val suggestionsAvailableSemantics = getString(Strings.SuggestionsAvailable)

        val textColor =
            textStyle.color.takeOrElse {
                colors.textColor(enabled, isError = false, focused = focused)
            }
        val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

        val coroutineScope = rememberCoroutineScope()

        BasicTextField(
            state = textFieldState,
            modifier =
                modifier
                    .sizeIn(
                        minWidth = SearchBarMinWidth,
                        maxWidth = SearchBarMaxWidth,
                        minHeight = InputFieldHeight,
                    )
                    .focusRequester(searchBarState.focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            coroutineScope.launch { searchBarState.animateToExpanded() }
                        }
                    }
                    .semantics {
                        contentDescription = searchSemantics
                        if (searchBarState.isExpanded) {
                            stateDescription = suggestionsAvailableSemantics
                        }
                        onClick {
                            searchBarState.focusRequester.requestFocus()
                            true
                        }
                    },
            enabled = enabled,
            readOnly = readOnly,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError = false)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearch(textFieldState.text.toString()) },
            interactionSource = interactionSource,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            scrollState = scrollState,
            decorator =
                TextFieldDefaults.decorator(
                    state = textFieldState,
                    enabled = enabled,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    outputTransformation = outputTransformation,
                    interactionSource = interactionSource,
                    placeholder = placeholder,
                    leadingIcon =
                        leadingIcon?.let { leading ->
                            { Box(Modifier.offset(x = SearchBarIconOffsetX)) { leading() } }
                        },
                    trailingIcon =
                        trailingIcon?.let { trailing ->
                            { Box(Modifier.offset(x = -SearchBarIconOffsetX)) { trailing() } }
                        },
                    prefix = prefix,
                    suffix = suffix,
                    colors = colors,
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                    container = {
                        val containerColor =
                            animateColorAsState(
                                targetValue =
                                    colors.containerColor(
                                        enabled = enabled,
                                        isError = false,
                                        focused = focused
                                    ),
                                animationSpec = MotionSchemeKeyTokens.FastEffects.value(),
                            )
                        Box(Modifier.textFieldBackground(containerColor::value, shape))
                    },
                )
        )

        val shouldClearFocus = !searchBarState.isExpanded && focused
        LaunchedEffect(searchBarState.isExpanded) {
            if (shouldClearFocus) {
                focusManager.clearFocus()
            }
        }
    }

    /**
     * A text field to input a query in a search bar.
     *
     * This overload of [InputField] uses [TextFieldState] to keep track of the text content and
     * position of the cursor or selection, and [expanded] and [onExpandedChange] to keep track of
     * the state of the search bar.
     *
     * @param state [TextFieldState] that holds the internal editing state of the input field.
     * @param onSearch the callback to be invoked when the input service triggers the
     *   [ImeAction.Search] action. The current query in the [state] comes as a parameter of the
     *   callback.
     * @param expanded whether the search bar is expanded and showing search results.
     * @param onExpandedChange the callback to be invoked when the search bar's expanded state is
     *   changed.
     * @param modifier the [Modifier] to be applied to this input field.
     * @param enabled the enabled state of this input field. When `false`, this component will not
     *   respond to user input, and it will appear visually disabled and disabled to accessibility
     *   services.
     * @param readOnly controls the editable state of the input field. When `true`, the field cannot
     *   be modified. However, a user can focus it and copy text from it.
     * @param textStyle the style to be applied to the input text. Defaults to [LocalTextStyle].
     * @param placeholder the placeholder to be displayed when the input text is empty.
     * @param leadingIcon the leading icon to be displayed at the start of the input field.
     * @param trailingIcon the trailing icon to be displayed at the end of the input field.
     * @param prefix the optional prefix to be displayed before the input text.
     * @param suffix the optional suffix to be displayed after the input text.
     * @param inputTransformation optional [InputTransformation] that will be used to transform
     *   changes to the [TextFieldState] made by the user. The transformation will be applied to
     *   changes made by hardware and software keyboard events, pasting or dropping text,
     *   accessibility services, and tests. The transformation will _not_ be applied when changing
     *   the [state] programmatically, or when the transformation is changed. If the transformation
     *   is changed on an existing text field, it will be applied to the next user edit. The
     *   transformation will not immediately affect the current [state].
     * @param outputTransformation optional [OutputTransformation] that transforms how the contents
     *   of the text field are presented.
     * @param scrollState scroll state that manages the horizontal scroll of the input field.
     * @param shape the shape of the input field.
     * @param colors [TextFieldColors] that will be used to resolve the colors used for this input
     *   field in different states. See [SearchBarDefaults.inputFieldColors].
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     *   emitting [Interaction]s for this input field. You can use this to change the search bar's
     *   appearance or preview the search bar in different states. Note that if `null` is provided,
     *   interactions will still happen internally.
     */
    @ExperimentalMaterial3Api
    @Composable
    fun InputField(
        state: TextFieldState,
        onSearch: (String) -> Unit,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        readOnly: Boolean = false,
        textStyle: TextStyle = LocalTextStyle.current,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        prefix: @Composable (() -> Unit)? = null,
        suffix: @Composable (() -> Unit)? = null,
        inputTransformation: InputTransformation? = null,
        outputTransformation: OutputTransformation? = null,
        scrollState: ScrollState = rememberScrollState(),
        shape: Shape = inputFieldShape,
        colors: TextFieldColors = inputFieldColors(),
        interactionSource: MutableInteractionSource? = null,
    ) {
        @Suppress("NAME_SHADOWING")
        val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

        val focused = interactionSource.collectIsFocusedAsState().value
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        val searchSemantics = getString(Strings.SearchBarSearch)
        val suggestionsAvailableSemantics = getString(Strings.SuggestionsAvailable)

        val textColor =
            textStyle.color.takeOrElse {
                colors.textColor(enabled, isError = false, focused = focused)
            }
        val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

        BasicTextField(
            state = state,
            modifier =
                modifier
                    .sizeIn(
                        minWidth = SearchBarMinWidth,
                        maxWidth = SearchBarMaxWidth,
                        minHeight = InputFieldHeight,
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onExpandedChange(true) }
                    .semantics {
                        contentDescription = searchSemantics
                        if (expanded) {
                            stateDescription = suggestionsAvailableSemantics
                        }
                        onClick {
                            focusRequester.requestFocus()
                            true
                        }
                    },
            enabled = enabled,
            readOnly = readOnly,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError = false)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearch(state.text.toString()) },
            interactionSource = interactionSource,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
            scrollState = scrollState,
            decorator =
                TextFieldDefaults.decorator(
                    state = state,
                    enabled = enabled,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    outputTransformation = outputTransformation,
                    interactionSource = interactionSource,
                    placeholder = placeholder,
                    leadingIcon =
                        leadingIcon?.let { leading ->
                            { Box(Modifier.offset(x = SearchBarIconOffsetX)) { leading() } }
                        },
                    trailingIcon =
                        trailingIcon?.let { trailing ->
                            { Box(Modifier.offset(x = -SearchBarIconOffsetX)) { trailing() } }
                        },
                    prefix = prefix,
                    suffix = suffix,
                    colors = colors,
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                    container = {
                        val containerColor =
                            animateColorAsState(
                                targetValue =
                                    colors.containerColor(
                                        enabled = enabled,
                                        isError = false,
                                        focused = focused
                                    ),
                                animationSpec = MotionSchemeKeyTokens.FastEffects.value(),
                            )
                        Box(Modifier.textFieldBackground(containerColor::value, shape))
                    },
                )
        )

        val shouldClearFocus = !expanded && focused
        LaunchedEffect(expanded) {
            if (shouldClearFocus) {
                // Not strictly needed according to the motion spec, but since the animation
                // already has a delay, this works around b/261632544.
                delay(AnimationDelayMillis.toLong())
                focusManager.clearFocus()
            }
        }
    }

    /**
     * A text field to input a query in a search bar.
     *
     * This overload of [InputField] takes a [query] and [onQueryChange] callback to keep track of
     * the text content. Consider using the overload which takes a [TextFieldState] instead.
     *
     * @param query the query text to be shown in the input field.
     * @param onQueryChange the callback to be invoked when the input service updates the query. An
     *   updated text comes as a parameter of the callback.
     * @param onSearch the callback to be invoked when the input service triggers the
     *   [ImeAction.Search] action. The current [query] comes as a parameter of the callback.
     * @param expanded whether the search bar is expanded and showing search results.
     * @param onExpandedChange the callback to be invoked when the search bar's expanded state is
     *   changed.
     * @param modifier the [Modifier] to be applied to this input field.
     * @param enabled the enabled state of this input field. When `false`, this component will not
     *   respond to user input, and it will appear visually disabled and disabled to accessibility
     *   services.
     * @param placeholder the placeholder to be displayed when the [query] is empty.
     * @param leadingIcon the leading icon to be displayed at the start of the input field.
     * @param trailingIcon the trailing icon to be displayed at the end of the input field.
     * @param colors [TextFieldColors] that will be used to resolve the colors used for this input
     *   field in different states. See [SearchBarDefaults.inputFieldColors].
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     *   emitting [Interaction]s for this input field. You can use this to change the search bar's
     *   appearance or preview the search bar in different states. Note that if `null` is provided,
     *   interactions will still happen internally.
     */
    @ExperimentalMaterial3Api
    @Composable
    fun InputField(
        query: String,
        onQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        colors: TextFieldColors = inputFieldColors(),
        interactionSource: MutableInteractionSource? = null,
    ) {
        @Suppress("NAME_SHADOWING")
        val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

        val focused = interactionSource.collectIsFocusedAsState().value
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        val searchSemantics = getString(Strings.SearchBarSearch)
        val suggestionsAvailableSemantics = getString(Strings.SuggestionsAvailable)

        val textColor =
            LocalTextStyle.current.color.takeOrElse {
                colors.textColor(enabled, isError = false, focused = focused)
            }

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                modifier
                    .sizeIn(
                        minWidth = SearchBarMinWidth,
                        maxWidth = SearchBarMaxWidth,
                        minHeight = InputFieldHeight,
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onExpandedChange(true) }
                    .semantics {
                        contentDescription = searchSemantics
                        if (expanded) {
                            stateDescription = suggestionsAvailableSemantics
                        }
                        onClick {
                            focusRequester.requestFocus()
                            true
                        }
                    },
            enabled = enabled,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(colors.cursorColor(isError = false)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            interactionSource = interactionSource,
            decorationBox =
                @Composable { innerTextField ->
                    TextFieldDefaults.DecorationBox(
                        value = query,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interactionSource,
                        placeholder = placeholder,
                        leadingIcon =
                            leadingIcon?.let { leading ->
                                { Box(Modifier.offset(x = SearchBarIconOffsetX)) { leading() } }
                            },
                        trailingIcon =
                            trailingIcon?.let { trailing ->
                                { Box(Modifier.offset(x = -SearchBarIconOffsetX)) { trailing() } }
                            },
                        shape = SearchBarDefaults.inputFieldShape,
                        colors = colors,
                        contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                        container = {
                            val containerColor =
                                animateColorAsState(
                                    targetValue =
                                        colors.containerColor(
                                            enabled = enabled,
                                            isError = false,
                                            focused = focused
                                        ),
                                    animationSpec = MotionSchemeKeyTokens.FastEffects.value(),
                                )
                            Box(
                                Modifier.textFieldBackground(containerColor::value, inputFieldShape)
                            )
                        },
                    )
                }
        )

        val shouldClearFocus = !expanded && focused
        LaunchedEffect(expanded) {
            if (shouldClearFocus) {
                // Not strictly needed according to the motion spec, but since the animation
                // already has a delay, this works around b/261632544.
                delay(AnimationDelayMillis.toLong())
                focusManager.clearFocus()
            }
        }
    }

    @Deprecated(message = "Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    @Composable
    fun colors(
        containerColor: Color = SearchBarTokens.ContainerColor.value,
        dividerColor: Color = SearchViewTokens.DividerColor.value,
    ): SearchBarColors =
        SearchBarColors(
            containerColor = containerColor,
            dividerColor = dividerColor,
            inputFieldColors =
                inputFieldColors(
                    focusedContainerColor = containerColor,
                    unfocusedContainerColor = containerColor,
                    disabledContainerColor = containerColor,
                ),
        )

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    @Composable
    fun inputFieldColors(
        focusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        unfocusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        disabledTextColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        cursorColor: Color = FilledTextFieldTokens.CaretColor.value,
        selectionColors: TextSelectionColors = LocalTextSelectionColors.current,
        focusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        unfocusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        disabledLeadingIconColor: Color =
            FilledTextFieldTokens.DisabledLeadingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledLeadingIconOpacity
            ),
        focusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        unfocusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        disabledTrailingIconColor: Color =
            FilledTextFieldTokens.DisabledTrailingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledTrailingIconOpacity
            ),
        focusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        unfocusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        disabledPlaceholderColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
    ): TextFieldColors =
        inputFieldColors(
            focusedTextColor = focusedTextColor,
            unfocusedTextColor = unfocusedTextColor,
            disabledTextColor = disabledTextColor,
            cursorColor = cursorColor,
            selectionColors = selectionColors,
            focusedLeadingIconColor = focusedLeadingIconColor,
            unfocusedLeadingIconColor = unfocusedLeadingIconColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            focusedTrailingIconColor = focusedTrailingIconColor,
            unfocusedTrailingIconColor = unfocusedTrailingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
            focusedPlaceholderColor = focusedPlaceholderColor,
            unfocusedPlaceholderColor = unfocusedPlaceholderColor,
            disabledPlaceholderColor = disabledPlaceholderColor,
            focusedPrefixColor = FilledTextFieldTokens.InputPrefixColor.value,
            unfocusedPrefixColor = FilledTextFieldTokens.InputPrefixColor.value,
            disabledPrefixColor =
                FilledTextFieldTokens.InputPrefixColor.value.copy(
                    alpha = FilledTextFieldTokens.DisabledInputOpacity
                ),
            focusedSuffixColor = FilledTextFieldTokens.InputSuffixColor.value,
            unfocusedSuffixColor = FilledTextFieldTokens.InputSuffixColor.value,
            disabledSuffixColor =
                FilledTextFieldTokens.InputSuffixColor.value.copy(
                    alpha = FilledTextFieldTokens.DisabledInputOpacity
                ),
            focusedContainerColor = SearchBarTokens.ContainerColor.value,
            unfocusedContainerColor = SearchBarTokens.ContainerColor.value,
            disabledContainerColor = SearchBarTokens.ContainerColor.value,
        )

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    @Composable
    fun inputFieldColors(
        textColor: Color = SearchBarTokens.InputTextColor.value,
        disabledTextColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
        cursorColor: Color = FilledTextFieldTokens.CaretColor.value,
        selectionColors: TextSelectionColors = LocalTextSelectionColors.current,
        focusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        unfocusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        disabledLeadingIconColor: Color =
            FilledTextFieldTokens.DisabledLeadingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledLeadingIconOpacity
            ),
        focusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        unfocusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        disabledTrailingIconColor: Color =
            FilledTextFieldTokens.DisabledTrailingIconColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledTrailingIconOpacity
            ),
        placeholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        disabledPlaceholderColor: Color =
            FilledTextFieldTokens.DisabledInputColor.value.copy(
                alpha = FilledTextFieldTokens.DisabledInputOpacity
            ),
    ) =
        inputFieldColors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            disabledTextColor = disabledTextColor,
            cursorColor = cursorColor,
            selectionColors = selectionColors,
            focusedLeadingIconColor = focusedLeadingIconColor,
            unfocusedLeadingIconColor = unfocusedLeadingIconColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            focusedTrailingIconColor = focusedTrailingIconColor,
            unfocusedTrailingIconColor = unfocusedTrailingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
            focusedPlaceholderColor = placeholderColor,
            unfocusedPlaceholderColor = placeholderColor,
            disabledPlaceholderColor = disabledPlaceholderColor,
        )
}

/**
 * Represents the colors used by a search bar in different states.
 *
 * See [SearchBarDefaults.colors] for the default implementation that follows Material
 * specifications.
 */
@ExperimentalMaterial3Api
@Immutable
class SearchBarColors(
    val containerColor: Color,
    val dividerColor: Color,
    val inputFieldColors: TextFieldColors,
) {
    @Deprecated(
        message = "Use overload that takes `inputFieldColors",
        replaceWith = ReplaceWith("SearchBarColors(containerColor, dividerColor, inputFieldColors)")
    )
    constructor(
        containerColor: Color,
        dividerColor: Color,
    ) : this(containerColor, dividerColor, UnspecifiedTextFieldColors)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchBarColors) return false

        if (containerColor != other.containerColor) return false
        if (dividerColor != other.dividerColor) return false
        if (inputFieldColors != other.inputFieldColors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + dividerColor.hashCode()
        result = 31 * result + inputFieldColors.hashCode()
        return result
    }
}

@Suppress("DEPRECATION")
@Deprecated(
    message = "Use overload which takes inputField as a parameter",
    replaceWith =
        ReplaceWith(
            "SearchBar(\n" +
                "    inputField = {\n" +
                "        SearchBarDefaults.InputField(\n" +
                "            query = query,\n" +
                "            onQueryChange = onQueryChange,\n" +
                "            onSearch = onSearch,\n" +
                "            expanded = active,\n" +
                "            onExpandedChange = onActiveChange,\n" +
                "            enabled = enabled,\n" +
                "            placeholder = placeholder,\n" +
                "            leadingIcon = leadingIcon,\n" +
                "            trailingIcon = trailingIcon,\n" +
                "            colors = colors.inputFieldColors,\n" +
                "            interactionSource = interactionSource,\n" +
                "        )\n" +
                "    },\n" +
                "    expanded = active,\n" +
                "    onExpandedChange = onActiveChange,\n" +
                "    modifier = modifier,\n" +
                "    shape = shape,\n" +
                "    colors = colors,\n" +
                "    tonalElevation = tonalElevation,\n" +
                "    shadowElevation = shadowElevation,\n" +
                "    windowInsets = windowInsets,\n" +
                "    content = content,\n" +
                ")"
        ),
)
@ExperimentalMaterial3Api
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) =
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                expanded = active,
                onExpandedChange = onActiveChange,
                enabled = enabled,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                colors = colors.inputFieldColors,
                interactionSource = interactionSource,
            )
        },
        expanded = active,
        onExpandedChange = onActiveChange,
        modifier = modifier,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        windowInsets = windowInsets,
        content = content,
    )

@Suppress("DEPRECATION")
@Deprecated(
    message = "Use overload which takes inputField as a parameter",
    replaceWith =
        ReplaceWith(
            "DockedSearchBar(\n" +
                "    inputField = {\n" +
                "        SearchBarDefaults.InputField(\n" +
                "            query = query,\n" +
                "            onQueryChange = onQueryChange,\n" +
                "            onSearch = onSearch,\n" +
                "            expanded = active,\n" +
                "            onExpandedChange = onActiveChange,\n" +
                "            enabled = enabled,\n" +
                "            placeholder = placeholder,\n" +
                "            leadingIcon = leadingIcon,\n" +
                "            trailingIcon = trailingIcon,\n" +
                "            colors = colors.inputFieldColors,\n" +
                "            interactionSource = interactionSource,\n" +
                "        )\n" +
                "    },\n" +
                "    expanded = active,\n" +
                "    onExpandedChange = onActiveChange,\n" +
                "    modifier = modifier,\n" +
                "    shape = shape,\n" +
                "    colors = colors,\n" +
                "    tonalElevation = tonalElevation,\n" +
                "    shadowElevation = shadowElevation,\n" +
                "    content = content,\n" +
                ")"
        ),
)
@ExperimentalMaterial3Api
@Composable
fun DockedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.dockedShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) =
    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                expanded = active,
                onExpandedChange = onActiveChange,
                enabled = enabled,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                colors = colors.inputFieldColors,
                interactionSource = interactionSource,
            )
        },
        expanded = active,
        onExpandedChange = onActiveChange,
        modifier = modifier,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBarImpl(
    animationProgress: Animatable<Float, AnimationVector1D>,
    finalBackProgress: MutableFloatState,
    firstBackEvent: MutableState<BackEventCompat?>,
    currentBackEvent: MutableState<BackEventCompat?>,
    modifier: Modifier = Modifier,
    inputField: @Composable () -> Unit,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current

    val defaultInputFieldShape = SearchBarDefaults.inputFieldShape
    val defaultFullScreenShape = SearchBarDefaults.fullScreenShape
    val useFullScreenShape by remember {
        derivedStateOf(structuralEqualityPolicy()) { animationProgress.value == 1f }
    }
    val animatedShape =
        remember(useFullScreenShape, shape) {
            when {
                shape == defaultInputFieldShape ->
                    // The shape can only be animated if it's the default spec value
                    GenericShape { size, _ ->
                        val radius =
                            with(density) {
                                (SearchBarCornerRadius * (1 - animationProgress.value)).toPx()
                            }
                        addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
                    }
                useFullScreenShape -> defaultFullScreenShape
                else -> shape
            }
        }
    val surface =
        @Composable {
            Surface(
                shape = animatedShape,
                color = colors.containerColor,
                contentColor = contentColorFor(colors.containerColor),
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                content = {},
            )
        }

    val showContent by remember {
        derivedStateOf(structuralEqualityPolicy()) { animationProgress.value > 0 }
    }
    val wrappedContent: (@Composable () -> Unit)? =
        if (showContent) {
            {
                Column(Modifier.graphicsLayer { alpha = animationProgress.value }) {
                    HorizontalDivider(color = colors.dividerColor)
                    content()
                }
            }
        } else null

    SearchBarLayout(
        animationProgress = animationProgress,
        finalBackProgress = finalBackProgress,
        firstBackEvent = firstBackEvent,
        currentBackEvent = currentBackEvent,
        modifier = modifier,
        windowInsets = windowInsets,
        inputField = inputField,
        surface = surface,
        content = wrappedContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarLayout(
    animationProgress: Animatable<Float, AnimationVector1D>,
    finalBackProgress: MutableFloatState,
    firstBackEvent: MutableState<BackEventCompat?>,
    currentBackEvent: MutableState<BackEventCompat?>,
    modifier: Modifier,
    windowInsets: WindowInsets,
    inputField: @Composable () -> Unit,
    surface: @Composable () -> Unit,
    content: (@Composable () -> Unit)?,
) {
    // `Modifier.windowInsetsPadding` does not support animation,
    // so the insets are converted to paddings in the Layout's MeasureScope
    // and the animation calculations are done manually.
    val unconsumedInsets = remember { MutableWindowInsets() }
    Layout(
        modifier =
            modifier
                .zIndex(1f)
                .onConsumedWindowInsetsChanged { consumedInsets ->
                    unconsumedInsets.insets = windowInsets.exclude(consumedInsets)
                }
                .consumeWindowInsets(windowInsets),
        content = {
            Box(Modifier.layoutId(LayoutIdSurface), propagateMinConstraints = true) { surface() }
            Box(Modifier.layoutId(LayoutIdInputField), propagateMinConstraints = true) {
                inputField()
            }
            content?.let { content ->
                Box(Modifier.layoutId(LayoutIdSearchContent), propagateMinConstraints = true) {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        @Suppress("NAME_SHADOWING") val animationProgress = animationProgress.value

        val inputFieldMeasurable = measurables.fastFirst { it.layoutId == LayoutIdInputField }
        val surfaceMeasurable = measurables.fastFirst { it.layoutId == LayoutIdSurface }
        val contentMeasurable = measurables.fastFirstOrNull { it.layoutId == LayoutIdSearchContent }

        val topPadding = unconsumedInsets.getTop(this) + SearchBarVerticalPadding.roundToPx()
        val bottomPadding = SearchBarVerticalPadding.roundToPx()

        val defaultStartWidth =
            constraints.constrainWidth(
                inputFieldMeasurable.maxIntrinsicWidth(constraints.maxHeight)
            )
        val defaultStartHeight =
            constraints.constrainHeight(
                inputFieldMeasurable.minIntrinsicHeight(constraints.maxWidth)
            )

        val predictiveBackStartWidth =
            (constraints.maxWidth * SearchBarPredictiveBackMinScale).roundToInt()
        val predictiveBackStartHeight =
            (constraints.maxHeight * SearchBarPredictiveBackMinScale).roundToInt()
        val predictiveBackMultiplier =
            calculatePredictiveBackMultiplier(
                currentBackEvent.value,
                animationProgress,
                finalBackProgress.floatValue
            )

        val startWidth = lerp(defaultStartWidth, predictiveBackStartWidth, predictiveBackMultiplier)
        val startHeight =
            lerp(
                topPadding + defaultStartHeight,
                predictiveBackStartHeight,
                predictiveBackMultiplier
            )

        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight

        val minWidth = lerp(startWidth, maxWidth, animationProgress)
        val height = lerp(startHeight, maxHeight, animationProgress)

        // Note: animatedTopPadding decreases w.r.t. animationProgress
        val animatedTopPadding = lerp(topPadding, 0, animationProgress)
        val animatedBottomPadding = lerp(0, bottomPadding, animationProgress)

        val inputFieldPlaceable =
            inputFieldMeasurable.measure(
                Constraints(
                    minWidth = minWidth,
                    maxWidth = maxWidth,
                    minHeight = defaultStartHeight,
                    maxHeight = defaultStartHeight,
                )
            )
        val width = inputFieldPlaceable.width

        // As the animation proceeds, the surface loses its padding
        // and expands to cover the entire container.
        val surfacePlaceable =
            surfaceMeasurable.measure(Constraints.fixed(width, height - animatedTopPadding))
        val contentPlaceable =
            contentMeasurable?.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight =
                        if (constraints.hasBoundedHeight) {
                            (constraints.maxHeight -
                                    (topPadding + defaultStartHeight + bottomPadding))
                                .coerceAtLeast(0)
                        } else {
                            constraints.maxHeight
                        }
                )
            )

        layout(width, height) {
            val minOffsetMargin = SearchBarPredictiveBackMinMargin.roundToPx()
            val predictiveBackOffsetX =
                calculatePredictiveBackOffsetX(
                    constraints = constraints,
                    minMargin = minOffsetMargin,
                    currentBackEvent = currentBackEvent.value,
                    layoutDirection = layoutDirection,
                    progress = animationProgress,
                    predictiveBackMultiplier = predictiveBackMultiplier,
                )
            val predictiveBackOffsetY =
                calculatePredictiveBackOffsetY(
                    constraints = constraints,
                    minMargin = minOffsetMargin,
                    currentBackEvent = currentBackEvent.value,
                    firstBackEvent = firstBackEvent.value,
                    height = height,
                    maxOffsetY = SearchBarPredictiveBackMaxOffsetY.roundToPx(),
                    predictiveBackMultiplier = predictiveBackMultiplier,
                )

            surfacePlaceable.placeRelative(
                predictiveBackOffsetX,
                predictiveBackOffsetY + animatedTopPadding,
            )
            inputFieldPlaceable.placeRelative(
                predictiveBackOffsetX,
                predictiveBackOffsetY + topPadding,
            )
            contentPlaceable?.placeRelative(
                predictiveBackOffsetX,
                predictiveBackOffsetY +
                    topPadding +
                    inputFieldPlaceable.height +
                    animatedBottomPadding,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenSearchBarLayout(
    state: SearchBarState,
    predictiveBackState: PredictiveBackState,
    inputField: @Composable () -> Unit,
    modifier: Modifier,
    collapsedShape: Shape,
    colors: SearchBarColors,
    tonalElevation: Dp,
    shadowElevation: Dp,
    windowInsets: WindowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backEvent by remember { derivedStateOf { predictiveBackState.value } }
    val firstInProgressValue =
        remember { mutableStateOf<BackEventProgress.InProgress?>(null) }
            .apply {
                when (val event = backEvent) {
                    is BackEventProgress.InProgress -> if (value == null) value = event
                    BackEventProgress.NotRunning -> value = null
                    BackEventProgress.Completed -> Unit
                }
            }
    val lastInProgressValue =
        remember { mutableStateOf<BackEventProgress.InProgress?>(null) }
            .apply {
                when (val event = backEvent) {
                    is BackEventProgress.InProgress -> value = event
                    BackEventProgress.NotRunning -> value = null
                    BackEventProgress.Completed -> Unit
                }
            }

    val density = LocalDensity.current
    val fullScreenShape = SearchBarDefaults.fullScreenShape
    val animatedShape =
        remember(density, fullScreenShape) {
            GenericShape { size, layoutDirection ->
                if (collapsedShape === CircleShape && fullScreenShape === RectangleShape) {
                    // The shape can only be animated if it's the default spec value
                    val radius =
                        with(density) {
                            val fraction =
                                max(1 - state.progress, lastInProgressValue.value.transform())
                            (SearchBarCornerRadius * fraction).toPx()
                        }
                    if (radius < 1e-3) {
                        addRect(size.toRect())
                    } else {
                        addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
                    }
                } else {
                    val shape = if (state.progress < 0.5f) collapsedShape else fullScreenShape
                    addOutline(shape.createOutline(size, layoutDirection, density))
                }
            }
        }

    // Top window insets need to be animated, but `Modifier.windowInsetsPadding` does not support
    // animation. The top insets are separated out so the animation calculations can be done
    // manually in the Layout's MeasureScope.
    val unconsumedInsets = remember { MutableWindowInsets() }
    val nonTopInsets =
        unconsumedInsets.insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Layout(
        modifier =
            modifier
                .onConsumedWindowInsetsChanged { consumedInsets ->
                    unconsumedInsets.insets = windowInsets.exclude(consumedInsets)
                }
                .consumeWindowInsets(windowInsets),
        content = {
            Box(
                modifier =
                    Modifier.layoutId(LayoutIdInputField)
                        .padding(nonTopInsets.only(WindowInsetsSides.Horizontal).asPaddingValues()),
                propagateMinConstraints = true,
            ) {
                inputField()
            }

            Surface(
                modifier = Modifier.layoutId(LayoutIdSurface),
                shape = animatedShape,
                color = colors.containerColor,
                contentColor = contentColorFor(colors.containerColor),
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                content = {},
            )

            Column(
                Modifier.layoutId(LayoutIdSearchContent).padding(nonTopInsets.asPaddingValues())
            ) {
                HorizontalDivider(color = colors.dividerColor)
                content()
            }
        },
    ) { measurables, constraints ->
        val predictiveBackProgress = lastInProgressValue.value.transform()

        val predictiveBackEndWidth =
            (constraints.maxWidth * SearchBarPredictiveBackMinScale)
                .roundToInt()
                .coerceAtLeast(state.collapsedBounds.width)
        val predictiveBackEndHeight =
            (constraints.maxHeight * SearchBarPredictiveBackMinScale)
                .roundToInt()
                .coerceAtLeast(state.collapsedBounds.height)
        val endWidth = lerp(constraints.maxWidth, predictiveBackEndWidth, predictiveBackProgress)
        val endHeight = lerp(constraints.maxHeight, predictiveBackEndHeight, predictiveBackProgress)
        val width =
            constraints.constrainWidth(lerp(state.collapsedBounds.width, endWidth, state.progress))
        val height =
            constraints.constrainHeight(
                lerp(state.collapsedBounds.height, endHeight, state.progress)
            )

        val surfaceMeasurable = measurables.fastFirst { it.layoutId == LayoutIdSurface }
        val surfacePlaceable = surfaceMeasurable.measure(Constraints.fixed(width, height))

        val inputFieldMeasurable = measurables.fastFirst { it.layoutId == LayoutIdInputField }
        val inputFieldPlaceable =
            inputFieldMeasurable.measure(Constraints.fixed(width, state.collapsedBounds.height))

        val topPadding = unconsumedInsets.getTop(this@Layout) + SearchBarVerticalPadding.roundToPx()
        val bottomPadding = SearchBarVerticalPadding.roundToPx()
        val animatedTopPadding =
            lerp(0, topPadding, min(state.progress, 1 - predictiveBackProgress))
        val animatedBottomPadding = lerp(0, bottomPadding, state.progress)

        val paddedInputFieldHeight =
            inputFieldPlaceable.height + animatedTopPadding + animatedBottomPadding
        val contentMeasurable = measurables.fastFirst { it.layoutId == LayoutIdSearchContent }
        val contentPlaceable =
            contentMeasurable.measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = (height - paddedInputFieldHeight).coerceAtLeast(0),
                )
            )

        layout(constraints.maxWidth, constraints.maxHeight) {
            fun BackEventProgress.InProgress.endOffsetX(): Int =
                (if (swipeEdge == SwipeEdge.Left) {
                        constraints.maxWidth -
                            SearchBarPredictiveBackMinMargin.roundToPx() -
                            predictiveBackEndWidth
                    } else {
                        SearchBarPredictiveBackMinMargin.roundToPx()
                    })
                    .coerceAtLeast(state.collapsedBounds.right - predictiveBackEndWidth)
                    .coerceAtMost(state.collapsedBounds.left)

            fun BackEventProgress.InProgress.endOffsetY(): Int {
                val absoluteDeltaY = this.touchY - (firstInProgressValue.value?.touchY ?: return 0)
                val relativeDeltaY = abs(absoluteDeltaY) / constraints.maxHeight

                val availableVerticalSpace =
                    ((constraints.maxHeight - predictiveBackEndHeight) / 2 -
                            SearchBarPredictiveBackMinMargin.roundToPx())
                        .coerceAtLeast(0)
                val totalOffsetY =
                    min(
                        availableVerticalSpace,
                        SearchBarPredictiveBackMaxOffsetY.roundToPx(),
                    )
                val interpolatedOffsetY = lerp(0, totalOffsetY, relativeDeltaY)
                return (interpolatedOffsetY * sign(absoluteDeltaY).toInt() + topPadding)
                    .coerceAtMost(state.collapsedBounds.top)
            }

            val endOffsetX =
                lerp(
                    0,
                    lastInProgressValue.value?.endOffsetX() ?: 0,
                    predictiveBackProgress,
                )
            val endOffsetY =
                lerp(
                    0,
                    lastInProgressValue.value?.endOffsetY() ?: 0,
                    predictiveBackProgress,
                )
            val offsetX = lerp(state.collapsedBounds.left, endOffsetX, state.progress)
            val offsetY = lerp(state.collapsedBounds.top, endOffsetY, state.progress)

            surfacePlaceable.place(x = offsetX, y = offsetY)
            inputFieldPlaceable.place(x = offsetX, y = offsetY + animatedTopPadding)
            contentPlaceable.placeWithLayer(
                x = offsetX,
                y =
                    offsetY +
                        animatedTopPadding +
                        inputFieldPlaceable.height +
                        animatedBottomPadding,
                layerBlock = { alpha = state.progress },
            )
        }
    }
}

private fun BackEventProgress.InProgress?.transform(): Float =
    if (this == null) 0f else PredictiveBack.transform(this.progress)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DisableSoftKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { _, _ -> awaitCancellation() },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private val SearchBarState.collapsedBounds: IntRect
    get() =
        collapsedCoords?.let { IntRect(offset = it.positionOnScreen().round(), size = it.size) }
            ?: IntRect.Zero

private fun calculatePredictiveBackMultiplier(
    currentBackEvent: BackEventCompat?,
    progress: Float,
    finalBackProgress: Float
) =
    when {
        currentBackEvent == null -> 0f // Not in predictive back at all.
        finalBackProgress.isNaN() -> 1f // User is currently swiping predictive back.
        finalBackProgress <= 0 -> 0f // Safety check for divide by zero.
        else -> progress / finalBackProgress // User has released predictive back swipe.
    }

private fun calculatePredictiveBackOffsetX(
    constraints: Constraints,
    minMargin: Int,
    currentBackEvent: BackEventCompat?,
    layoutDirection: LayoutDirection,
    progress: Float,
    predictiveBackMultiplier: Float
): Int {
    if (currentBackEvent == null || predictiveBackMultiplier == 0f) {
        return 0
    }
    val directionMultiplier = if (currentBackEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
    val rtlMultiplier = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    val maxOffsetX = (constraints.maxWidth * SearchBarPredictiveBackMaxOffsetXRatio) - minMargin
    val interpolatedOffsetX = maxOffsetX * (1 - progress)
    return (interpolatedOffsetX * predictiveBackMultiplier * directionMultiplier * rtlMultiplier)
        .roundToInt()
}

private fun calculatePredictiveBackOffsetY(
    constraints: Constraints,
    minMargin: Int,
    currentBackEvent: BackEventCompat?,
    firstBackEvent: BackEventCompat?,
    height: Int,
    maxOffsetY: Int,
    predictiveBackMultiplier: Float
): Int {
    if (firstBackEvent == null || currentBackEvent == null || predictiveBackMultiplier == 0f) {
        return 0
    }
    val availableVerticalSpace = max(0, (constraints.maxHeight - height) / 2 - minMargin)
    val adjustedMaxOffsetY = min(availableVerticalSpace, maxOffsetY)
    val yDelta = currentBackEvent.touchY - firstBackEvent.touchY
    val yProgress = abs(yDelta) / constraints.maxHeight
    val directionMultiplier = sign(yDelta)
    val interpolatedOffsetY = lerp(0, adjustedMaxOffsetY, yProgress)
    return (interpolatedOffsetY * predictiveBackMultiplier * directionMultiplier).roundToInt()
}

private val UnspecifiedTextFieldColors: TextFieldColors =
    TextFieldColors(
        focusedTextColor = Color.Unspecified,
        unfocusedTextColor = Color.Unspecified,
        disabledTextColor = Color.Unspecified,
        errorTextColor = Color.Unspecified,
        focusedContainerColor = Color.Unspecified,
        unfocusedContainerColor = Color.Unspecified,
        disabledContainerColor = Color.Unspecified,
        errorContainerColor = Color.Unspecified,
        cursorColor = Color.Unspecified,
        errorCursorColor = Color.Unspecified,
        textSelectionColors = TextSelectionColors(Color.Unspecified, Color.Unspecified),
        focusedIndicatorColor = Color.Unspecified,
        unfocusedIndicatorColor = Color.Unspecified,
        disabledIndicatorColor = Color.Unspecified,
        errorIndicatorColor = Color.Unspecified,
        focusedLeadingIconColor = Color.Unspecified,
        unfocusedLeadingIconColor = Color.Unspecified,
        disabledLeadingIconColor = Color.Unspecified,
        errorLeadingIconColor = Color.Unspecified,
        focusedTrailingIconColor = Color.Unspecified,
        unfocusedTrailingIconColor = Color.Unspecified,
        disabledTrailingIconColor = Color.Unspecified,
        errorTrailingIconColor = Color.Unspecified,
        focusedLabelColor = Color.Unspecified,
        unfocusedLabelColor = Color.Unspecified,
        disabledLabelColor = Color.Unspecified,
        errorLabelColor = Color.Unspecified,
        focusedPlaceholderColor = Color.Unspecified,
        unfocusedPlaceholderColor = Color.Unspecified,
        disabledPlaceholderColor = Color.Unspecified,
        errorPlaceholderColor = Color.Unspecified,
        focusedSupportingTextColor = Color.Unspecified,
        unfocusedSupportingTextColor = Color.Unspecified,
        disabledSupportingTextColor = Color.Unspecified,
        errorSupportingTextColor = Color.Unspecified,
        focusedPrefixColor = Color.Unspecified,
        unfocusedPrefixColor = Color.Unspecified,
        disabledPrefixColor = Color.Unspecified,
        errorPrefixColor = Color.Unspecified,
        focusedSuffixColor = Color.Unspecified,
        unfocusedSuffixColor = Color.Unspecified,
        disabledSuffixColor = Color.Unspecified,
        errorSuffixColor = Color.Unspecified,
    )

// TODO: Replace to `WindowInfo.containerSize` once available
@Composable internal expect fun getWindowContainerHeight(): Dp

private const val LayoutIdInputField = "InputField"
private const val LayoutIdSurface = "Surface"
private const val LayoutIdSearchContent = "Content"

// Measurement specs
private val SearchBarAsTopBarPadding = 8.dp
@OptIn(ExperimentalMaterial3Api::class) private val SearchBarCornerRadius: Dp = InputFieldHeight / 2
internal val DockedExpandedTableMinHeight: Dp = 240.dp
private const val DockedExpandedTableMaxHeightScreenRatio: Float = 2f / 3f
internal val SearchBarMinWidth: Dp = 360.dp
private val SearchBarMaxWidth: Dp = 720.dp
internal val SearchBarVerticalPadding: Dp = 8.dp
// Search bar has 16dp padding between icons and start/end, while by default text field has 12dp.
private val SearchBarIconOffsetX: Dp = 4.dp
private const val SearchBarPredictiveBackMinScale: Float = 9f / 10f
private val SearchBarPredictiveBackMinMargin: Dp = 8.dp
private const val SearchBarPredictiveBackMaxOffsetXRatio: Float = 1f / 20f
private val SearchBarPredictiveBackMaxOffsetY: Dp = 24.dp

// Animation specs
private const val AnimationEnterDurationMillis: Int = MotionTokens.DurationLong4.toInt()
private const val AnimationExitDurationMillis: Int = MotionTokens.DurationMedium3.toInt()
private const val AnimationDelayMillis: Int = MotionTokens.DurationShort2.toInt()
private val AnimationEnterEasing = MotionTokens.EasingEmphasizedDecelerateCubicBezier
private val AnimationExitEasing = CubicBezierEasing(0.0f, 1.0f, 0.0f, 1.0f)
private val AnimationEnterFloatSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = AnimationEnterDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationEnterEasing,
    )
private val AnimationExitFloatSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = AnimationExitDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationExitEasing,
    )
private val AnimationPredictiveBackExitFloatSpec: FiniteAnimationSpec<Float> =
    tween(
        durationMillis = AnimationExitDurationMillis,
        easing = AnimationExitEasing,
    )
private val AnimationEnterSizeSpec: FiniteAnimationSpec<IntSize> =
    tween(
        durationMillis = AnimationEnterDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationEnterEasing,
    )
private val AnimationExitSizeSpec: FiniteAnimationSpec<IntSize> =
    tween(
        durationMillis = AnimationExitDurationMillis,
        delayMillis = AnimationDelayMillis,
        easing = AnimationExitEasing,
    )
private val DockedEnterTransition: EnterTransition =
    fadeIn(AnimationEnterFloatSpec) + expandVertically(AnimationEnterSizeSpec)
private val DockedExitTransition: ExitTransition =
    fadeOut(AnimationExitFloatSpec) + shrinkVertically(AnimationExitSizeSpec)
