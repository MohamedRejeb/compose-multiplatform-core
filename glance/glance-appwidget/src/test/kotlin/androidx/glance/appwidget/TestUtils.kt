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

package androidx.glance.appwidget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.os.Parcel
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.children
import androidx.glance.Applier
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.session.GlobalSnapshotManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertIs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal suspend fun runTestingComposition(
    content: @Composable @GlanceComposable () -> Unit,
): RemoteViewsRoot =
    runCompositionUntil(
        stopWhen = { state: Recomposer.State, root: RemoteViewsRoot ->
            state == Recomposer.State.Idle && !root.shouldIgnoreResult()
        },
        content
    )

internal suspend fun runCompositionUntil(
    stopWhen: (Recomposer.State, RemoteViewsRoot) -> Boolean,
    content: @Composable () -> Unit
): RemoteViewsRoot = coroutineScope {
    GlobalSnapshotManager.ensureStarted()
    val root = RemoteViewsRoot(10)
    val applier = Applier(root)
    val recomposer = Recomposer(currentCoroutineContext())
    val composition = Composition(applier, recomposer)
    composition.setContent { content() }

    launch(TestFrameClock()) { recomposer.runRecomposeAndApplyChanges() }

    recomposer.currentState.first { stopWhen(it, root) }
    recomposer.cancel()
    recomposer.join()

    root
}

/** Test clock that sends all frames immediately. */
class TestFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R) =
        onFrame(System.currentTimeMillis())
}

/** Create the view out of a RemoteViews. */
internal fun Context.applyRemoteViews(rv: RemoteViews): View {
    val p = Parcel.obtain()
    return try {
        rv.writeToParcel(p, 0)
        p.setDataPosition(0)
        val parceled = RemoteViews(p)
        val parent = FrameLayout(this)
        parent.layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        val view = parceled.apply(this, parent)
        assertIs<FrameLayout>(view)
        assertThat(view.childCount).isEqualTo(1)
        view.getChildAt(0)
    } finally {
        p.recycle()
    }
}

internal suspend fun Context.runAndTranslate(
    appWidgetId: Int = 0,
    content: @Composable () -> Unit
): RemoteViews {
    val originalRoot = runTestingComposition(content)

    // Copy makes a deep copy of the emittable tree, so will exercise the copy methods
    // of all of the emmitables the test checks too.
    val root = originalRoot.copy() as RemoteViewsRoot
    normalizeCompositionTree(root)
    return translateComposition(
        this,
        appWidgetId,
        root,
        LayoutConfiguration.create(this, appWidgetId),
        rootViewIndex = 0,
        layoutSize = DpSize.Zero,
    )
}

internal suspend fun Context.runAndTranslateInRtl(
    appWidgetId: Int = 0,
    content: @Composable () -> Unit
): RemoteViews {
    val rtlLocale =
        Locale.getAvailableLocales().first {
            TextUtils.getLayoutDirectionFromLocale(it) == View.LAYOUT_DIRECTION_RTL
        }
    val rtlContext =
        createConfigurationContext(
            Configuration(resources.configuration).also { it.setLayoutDirection(rtlLocale) }
        )
    return rtlContext.runAndTranslate(appWidgetId, content = content)
}

internal fun appWidgetProviderInfo(
    builder: AppWidgetProviderInfo.() -> Unit
): AppWidgetProviderInfo = AppWidgetProviderInfo().apply(builder)

internal fun TextUnit.toPixels(displayMetrics: DisplayMetrics) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, displayMetrics).toInt()

inline fun <reified T : View> View.findView(noinline pred: (T) -> Boolean) =
    findView(pred, T::class.java)

inline fun <reified T : View> View.findViewByType() = findView({ true }, T::class.java)

fun <T : View> View.findView(predicate: (T) -> Boolean, klass: Class<T>): T? {
    try {
        val castView = klass.cast(this)!!
        if (predicate(castView)) {
            return castView
        }
    } catch (e: ClassCastException) {
        // Nothing to do
    }
    if (this !is ViewGroup) {
        return null
    }
    return children.mapNotNull { it.findView(predicate, klass) }.firstOrNull()
}

internal class TestWidget(
    override val sizeMode: SizeMode = SizeMode.Single,
    val ui: @Composable () -> Unit,
) : GlanceAppWidget(errorUiLayout = 0) {
    override var errorUiLayout: Int = 0

    val provideGlanceCalled = AtomicBoolean(false)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideGlanceCalled.set(true)
        provideContent(ui)
    }

    inline fun withErrorLayout(layout: Int, block: () -> Unit) {
        val previousErrorLayout = errorUiLayout
        errorUiLayout = layout
        try {
            block()
        } finally {
            errorUiLayout = previousErrorLayout
        }
    }
}

/** Count the number of children that are not gone. */
internal val ViewGroup.nonGoneChildCount: Int
    get() = children.count { it.visibility != View.GONE }

/** Iterate over children that are not gone. */
internal val ViewGroup.nonGoneChildren: Sequence<View>
    get() = children.filter { it.visibility != View.GONE }

fun configurationContext(modifier: Configuration.() -> Unit): Context {
    val configuration = Configuration()
    modifier(configuration)
    return ApplicationProvider.getApplicationContext<Context>()
        .createConfigurationContext(configuration)
}
