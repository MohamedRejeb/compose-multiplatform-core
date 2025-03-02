/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:JvmName("NavControllerKt")
@file:JvmMultifileClass

package androidx.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.annotation.NavigationRes
import androidx.annotation.RestrictTo
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.createRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.childHierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.serialization.generateHashCode
import androidx.navigation.serialization.generateRouteWithArgs
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.write
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.removeFirst as removeFirstKt
import kotlin.collections.removeLast as removeLastKt
import kotlin.reflect.KClass
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

public actual open class NavController(
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public val context: Context
) {
    private var activity: Activity? =
        generateSequence(context) {
                if (it is ContextWrapper) {
                    it.baseContext
                } else null
            }
            .firstOrNull { it is Activity } as Activity?

    private var inflater: NavInflater? = null

    private var _graph: NavGraph? = null

    public actual open var graph: NavGraph
        @MainThread
        get() {
            checkNotNull(_graph) { "You must call setGraph() before calling getGraph()" }
            return _graph as NavGraph
        }
        @MainThread
        @CallSuper
        set(graph) {
            setGraph(graph, null)
        }

    private var navigatorStateToRestore: SavedState? = null
    private var backStackToRestore: Array<Parcelable>? = null
    private var deepLinkHandled = false

    private val backQueue: ArrayDeque<NavBackStackEntry> = ArrayDeque()

    private val _currentBackStack: MutableStateFlow<List<NavBackStackEntry>> =
        MutableStateFlow(emptyList())

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual val currentBackStack: StateFlow<List<NavBackStackEntry>> =
        _currentBackStack.asStateFlow()

    private val _visibleEntries: MutableStateFlow<List<NavBackStackEntry>> =
        MutableStateFlow(emptyList())

    public actual val visibleEntries: StateFlow<List<NavBackStackEntry>> =
        _visibleEntries.asStateFlow()

    private val childToParentEntries = mutableMapOf<NavBackStackEntry, NavBackStackEntry>()
    private val parentToChildCount = mutableMapOf<NavBackStackEntry, AtomicInteger>()

    private fun linkChildToParent(child: NavBackStackEntry, parent: NavBackStackEntry) {
        childToParentEntries[child] = parent
        if (parentToChildCount[parent] == null) {
            parentToChildCount[parent] = AtomicInteger(0)
        }
        parentToChildCount[parent]!!.incrementAndGet()
    }

    internal actual fun unlinkChildFromParent(child: NavBackStackEntry): NavBackStackEntry? {
        val parent = childToParentEntries.remove(child) ?: return null
        val count = parentToChildCount[parent]?.decrementAndGet()
        if (count == 0) {
            val navGraphNavigator: Navigator<out NavGraph> =
                _navigatorProvider[parent.destination.navigatorName]
            navigatorState[navGraphNavigator]?.markTransitionComplete(parent)
            parentToChildCount.remove(parent)
        }
        return parent
    }

    private val backStackMap = mutableMapOf<Int, String?>()
    private val backStackStates = mutableMapOf<String, ArrayDeque<NavBackStackEntryState>>()
    private var lifecycleOwner: LifecycleOwner? = null
    private var onBackPressedDispatcher: OnBackPressedDispatcher? = null
    private var viewModel: NavControllerViewModel? = null
    private val onDestinationChangedListeners = CopyOnWriteArrayList<OnDestinationChangedListener>()
    internal actual var hostLifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED
        get() {
            // A LifecycleOwner is not required by NavController.
            // In the cases where one is not provided, always keep the host lifecycle at CREATED
            return if (lifecycleOwner == null) {
                Lifecycle.State.CREATED
            } else {
                field
            }
        }

    private val lifecycleObserver: LifecycleObserver = LifecycleEventObserver { _, event ->
        hostLifecycleState = event.targetState
        if (_graph != null) {
            // Operate on a copy of the queue to avoid issues with reentrant
            // calls if updating the Lifecycle calls navigate() or popBackStack()
            val backStack = backQueue.toMutableList()
            for (entry in backStack) {
                entry.handleLifecycleEvent(event)
            }
        }
    }

    private val onBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                popBackStack()
            }
        }
    private var enableOnBackPressedCallback = true

    public actual fun interface OnDestinationChangedListener {
        public actual fun onDestinationChanged(
            controller: NavController,
            destination: NavDestination,
            arguments: SavedState?
        )
    }

    private var _navigatorProvider = NavigatorProvider()

    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual open var navigatorProvider: NavigatorProvider
        get() = _navigatorProvider
        /**  */
        set(navigatorProvider) {
            check(backQueue.isEmpty()) { "NavigatorProvider must be set before setGraph call" }
            _navigatorProvider = navigatorProvider
        }

    private val navigatorState =
        mutableMapOf<Navigator<out NavDestination>, NavControllerNavigatorState>()
    private var addToBackStackHandler: ((backStackEntry: NavBackStackEntry) -> Unit)? = null
    private var popFromBackStackHandler: ((popUpTo: NavBackStackEntry) -> Unit)? = null
    private val entrySavedState = mutableMapOf<NavBackStackEntry, Boolean>()

    /**
     * Call [Navigator.navigate] while setting up a [handler] that receives callbacks when
     * [NavigatorState.push] is called.
     */
    private fun Navigator<out NavDestination>.navigateInternal(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?,
        handler: (backStackEntry: NavBackStackEntry) -> Unit = {}
    ) {
        addToBackStackHandler = handler
        navigate(entries, navOptions, navigatorExtras)
        addToBackStackHandler = null
    }

    /**
     * Call [Navigator.popBackStack] while setting up a [handler] that receives callbacks when
     * [NavigatorState.pop] is called.
     */
    private fun Navigator<out NavDestination>.popBackStackInternal(
        popUpTo: NavBackStackEntry,
        saveState: Boolean,
        handler: (popUpTo: NavBackStackEntry) -> Unit = {}
    ) {
        popFromBackStackHandler = handler
        popBackStack(popUpTo, saveState)
        popFromBackStackHandler = null
    }

    private inner class NavControllerNavigatorState(val navigator: Navigator<out NavDestination>) :
        NavigatorState() {
        override fun push(backStackEntry: NavBackStackEntry) {
            val destinationNavigator: Navigator<out NavDestination> =
                _navigatorProvider[backStackEntry.destination.navigatorName]
            if (destinationNavigator == navigator) {
                val handler = addToBackStackHandler
                if (handler != null) {
                    handler(backStackEntry)
                    addInternal(backStackEntry)
                } else {
                    // TODO handle the Navigator calling add() outside of a call to navigate()
                    Log.i(
                        TAG,
                        "Ignoring add of destination ${backStackEntry.destination} " +
                            "outside of the call to navigate(). "
                    )
                }
            } else {
                val navigatorBackStack =
                    checkNotNull(navigatorState[destinationNavigator]) {
                        "NavigatorBackStack for ${backStackEntry.destination.navigatorName} should " +
                            "already be created"
                    }
                navigatorBackStack.push(backStackEntry)
            }
        }

        fun addInternal(backStackEntry: NavBackStackEntry) {
            super.push(backStackEntry)
        }

        override fun createBackStackEntry(destination: NavDestination, arguments: SavedState?) =
            NavBackStackEntry.create(context, destination, arguments, hostLifecycleState, viewModel)

        override fun pop(popUpTo: NavBackStackEntry, saveState: Boolean) {
            val destinationNavigator: Navigator<out NavDestination> =
                _navigatorProvider[popUpTo.destination.navigatorName]
            entrySavedState[popUpTo] = saveState
            if (destinationNavigator == navigator) {
                val handler = popFromBackStackHandler
                if (handler != null) {
                    handler(popUpTo)
                    super.pop(popUpTo, saveState)
                } else {
                    popBackStackFromNavigator(popUpTo) { super.pop(popUpTo, saveState) }
                }
            } else {
                navigatorState[destinationNavigator]!!.pop(popUpTo, saveState)
            }
        }

        override fun popWithTransition(popUpTo: NavBackStackEntry, saveState: Boolean) {
            super.popWithTransition(popUpTo, saveState)
        }

        override fun markTransitionComplete(entry: NavBackStackEntry) {
            val savedState = entrySavedState[entry] == true
            super.markTransitionComplete(entry)
            entrySavedState.remove(entry)
            if (!backQueue.contains(entry)) {
                unlinkChildFromParent(entry)
                // If the entry is no longer part of the backStack, we need to manually move
                // it to DESTROYED, and clear its view model
                if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    entry.maxLifecycle = Lifecycle.State.DESTROYED
                }
                if (backQueue.none { it.id == entry.id } && !savedState) {
                    viewModel?.clear(entry.id)
                }
                updateBackStackLifecycle()
                // Nothing in backQueue changed, so unlike other places where
                // we change visibleEntries, we don't need to emit a new
                // currentBackStack
                _visibleEntries.tryEmit(populateVisibleEntries())
            } else if (!this@NavControllerNavigatorState.isNavigating) {
                updateBackStackLifecycle()
                _currentBackStack.tryEmit(backQueue.toMutableList())
                _visibleEntries.tryEmit(populateVisibleEntries())
            }
            // else, updateBackStackLifecycle() will be called after any ongoing navigate() call
            // completes
        }

        override fun prepareForTransition(entry: NavBackStackEntry) {
            super.prepareForTransition(entry)
            if (backQueue.contains(entry)) {
                entry.maxLifecycle = Lifecycle.State.STARTED
            } else {
                throw IllegalStateException("Cannot transition entry that is not in the back stack")
            }
        }
    }

    /**
     * Constructs a new controller for a given [Context]. Controllers should not be used outside of
     * their context and retain a hard reference to the context supplied. If you need a global
     * controller, pass [Context.getApplicationContext].
     *
     * Apps should generally not construct controllers, instead obtain a relevant controller
     * directly from a navigation host via [NavHost.getNavController] or by using one of the utility
     * methods on the [Navigation] class.
     *
     * Note that controllers that are not constructed with an [Activity] context (or a wrapped
     * activity context) will only be able to navigate to
     * [new tasks][android.content.Intent.FLAG_ACTIVITY_NEW_TASK] or
     * [new document tasks][android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT] when navigating to
     * new activities.
     *
     * @param context context for this controller
     */
    init {
        _navigatorProvider.addNavigator(NavGraphNavigator(_navigatorProvider))
        _navigatorProvider.addNavigator(ActivityNavigator(context))
    }

    public actual open fun addOnDestinationChangedListener(listener: OnDestinationChangedListener) {
        onDestinationChangedListeners.add(listener)

        // Inform the new listener of our current state, if any
        if (backQueue.isNotEmpty()) {
            val backStackEntry = backQueue.last()
            listener.onDestinationChanged(
                this,
                backStackEntry.destination,
                backStackEntry.arguments
            )
        }
    }

    public actual open fun removeOnDestinationChangedListener(
        listener: OnDestinationChangedListener
    ) {
        onDestinationChangedListeners.remove(listener)
    }

    @MainThread
    public actual open fun popBackStack(): Boolean {
        return if (backQueue.isEmpty()) {
            // Nothing to pop if the back stack is empty
            false
        } else {
            popBackStack(currentDestination!!.id, true)
        }
    }

    /**
     * Attempts to pop the controller's back stack back to a specific destination.
     *
     * @param destinationId The topmost destination to retain
     * @param inclusive Whether the given destination should also be popped.
     * @return true if the stack was popped at least once and the user has been navigated to another
     *   destination, false otherwise
     */
    @MainThread
    public open fun popBackStack(@IdRes destinationId: Int, inclusive: Boolean): Boolean {
        return popBackStack(destinationId, inclusive, false)
    }

    /**
     * Attempts to pop the controller's back stack back to a specific destination.
     *
     * @param destinationId The topmost destination to retain
     * @param inclusive Whether the given destination should also be popped.
     * @param saveState Whether the back stack and the state of all destinations between the current
     *   destination and the [destinationId] should be saved for later restoration via
     *   [NavOptions.Builder.setRestoreState] or the `restoreState` attribute using the same
     *   [destinationId] (note: this matching ID is true whether [inclusive] is true or false).
     * @return true if the stack was popped at least once and the user has been navigated to another
     *   destination, false otherwise
     */
    @MainThread
    public open fun popBackStack(
        @IdRes destinationId: Int,
        inclusive: Boolean,
        saveState: Boolean
    ): Boolean {
        val popped = popBackStackInternal(destinationId, inclusive, saveState)
        // Only return true if the pop succeeded and we've dispatched
        // the change to a new destination
        return popped && dispatchOnDestinationChanged()
    }

    @MainThread
    @JvmOverloads
    public actual fun popBackStack(route: String, inclusive: Boolean, saveState: Boolean): Boolean {
        val popped = popBackStackInternal(route, inclusive, saveState)
        // Only return true if the pop succeeded and we've dispatched
        // the change to a new destination
        return popped && dispatchOnDestinationChanged()
    }

    @MainThread
    @JvmOverloads
    public actual inline fun <reified T : Any> popBackStack(
        inclusive: Boolean,
        saveState: Boolean
    ): Boolean = popBackStack(T::class, inclusive, saveState)

    @MainThread
    @JvmOverloads
    @OptIn(InternalSerializationApi::class)
    public actual fun <T : Any> popBackStack(
        route: KClass<T>,
        inclusive: Boolean,
        saveState: Boolean
    ): Boolean {
        val id = route.serializer().generateHashCode()
        requireNotNull(graph.findDestinationComprehensive(id, true)) {
            "Destination with route ${route.simpleName} cannot be found in navigation " +
                "graph $graph"
        }
        return popBackStack(id, inclusive, saveState)
    }

    @MainThread
    @JvmOverloads
    public actual fun <T : Any> popBackStack(
        route: T,
        inclusive: Boolean,
        saveState: Boolean
    ): Boolean {
        val popped = popBackStackInternal(route, inclusive, saveState)
        // Only return true if the pop succeeded and we've dispatched
        // the change to a new destination
        return popped && dispatchOnDestinationChanged()
    }

    /**
     * Attempts to pop the controller's back stack back to a specific destination. This does **not**
     * handle calling [dispatchOnDestinationChanged]
     *
     * @param destinationId The topmost destination to retain
     * @param inclusive Whether the given destination should also be popped.
     * @param saveState Whether the back stack and the state of all destinations between the current
     *   destination and the [destinationId] should be saved for later restoration via
     *   [NavOptions.Builder.setRestoreState] or the `restoreState` attribute using the same
     *   [destinationId] (note: this matching ID is true whether [inclusive] is true or false).
     * @return true if the stack was popped at least once, false otherwise
     */
    @MainThread
    private fun popBackStackInternal(
        @IdRes destinationId: Int,
        inclusive: Boolean,
        saveState: Boolean = false
    ): Boolean {
        if (backQueue.isEmpty()) {
            // Nothing to pop if the back stack is empty
            return false
        }
        val popOperations = mutableListOf<Navigator<*>>()
        val iterator = backQueue.reversed().iterator()
        var foundDestination: NavDestination? = null
        while (iterator.hasNext()) {
            val destination = iterator.next().destination
            val navigator = _navigatorProvider.getNavigator<Navigator<*>>(destination.navigatorName)
            if (inclusive || destination.id != destinationId) {
                popOperations.add(navigator)
            }
            if (destination.id == destinationId) {
                foundDestination = destination
                break
            }
        }
        if (foundDestination == null) {
            // We were passed a destinationId that doesn't exist on our back stack.
            // Better to ignore the popBackStack than accidentally popping the entire stack
            val destinationName = NavDestination.getDisplayName(context, destinationId)
            Log.i(
                TAG,
                "Ignoring popBackStack to destination $destinationName as it was not found " +
                    "on the current back stack"
            )
            return false
        }
        return executePopOperations(popOperations, foundDestination, inclusive, saveState)
    }

    private fun <T : Any> popBackStackInternal(
        route: T,
        inclusive: Boolean,
        saveState: Boolean = false
    ): Boolean {
        // route contains arguments so we need to generate and pop with the populated route
        // rather than popping based on route pattern
        val finalRoute = generateRouteFilled(route)
        return popBackStackInternal(finalRoute, inclusive, saveState)
    }

    /**
     * Attempts to pop the controller's back stack back to a specific destination. This does **not**
     * handle calling [dispatchOnDestinationChanged]
     *
     * @param route The topmost destination with this route to retain
     * @param inclusive Whether the given destination should also be popped.
     * @param saveState Whether the back stack and the state of all destinations between the current
     *   destination and the destination with [route] should be saved for later to be restored via
     *   [NavOptions.Builder.setRestoreState] or the `restoreState` attribute using the
     *   [NavDestination.id] of the destination with this route (note: this matching ID is true
     *   whether [inclusive] is true or false).
     * @return true if the stack was popped at least once, false otherwise
     */
    private fun popBackStackInternal(
        route: String,
        inclusive: Boolean,
        saveState: Boolean,
    ): Boolean {
        if (backQueue.isEmpty()) {
            // Nothing to pop if the back stack is empty
            return false
        }

        val popOperations = mutableListOf<Navigator<*>>()
        val foundDestination =
            backQueue
                .lastOrNull { entry ->
                    val hasRoute = entry.destination.hasRoute(route, entry.arguments)
                    if (inclusive || !hasRoute) {
                        val navigator =
                            _navigatorProvider.getNavigator<Navigator<*>>(
                                entry.destination.navigatorName
                            )
                        popOperations.add(navigator)
                    }
                    hasRoute
                }
                ?.destination

        if (foundDestination == null) {
            // We were passed a route that doesn't exist on our back stack.
            // Better to ignore the popBackStack than accidentally popping the entire stack
            Log.i(
                TAG,
                "Ignoring popBackStack to route $route as it was not found " +
                    "on the current back stack"
            )
            return false
        }
        return executePopOperations(popOperations, foundDestination, inclusive, saveState)
    }

    private fun executePopOperations(
        popOperations: List<Navigator<*>>,
        foundDestination: NavDestination,
        inclusive: Boolean,
        saveState: Boolean,
    ): Boolean {
        var popped = false
        val savedState = ArrayDeque<NavBackStackEntryState>()
        for (navigator in popOperations) {
            var receivedPop = false
            navigator.popBackStackInternal(backQueue.last(), saveState) { entry ->
                receivedPop = true
                popped = true
                popEntryFromBackStack(entry, saveState, savedState)
            }
            if (!receivedPop) {
                // The pop did not complete successfully, so stop immediately
                break
            }
        }
        if (saveState) {
            if (!inclusive) {
                // If this isn't an inclusive pop, we need to explicitly map the
                // saved state to the destination you've actually passed to popUpTo
                // as well as its parents (if it is the start destination)
                generateSequence(foundDestination) { destination ->
                        if (destination.parent?.startDestinationId == destination.id) {
                            destination.parent
                        } else {
                            null
                        }
                    }
                    .takeWhile { destination ->
                        // Only add the state if it doesn't already exist
                        !backStackMap.containsKey(destination.id)
                    }
                    .forEach { destination ->
                        backStackMap[destination.id] = savedState.firstOrNull()?.id
                    }
            }
            if (savedState.isNotEmpty()) {
                val firstState = savedState.first()
                // Whether is is inclusive or not, we need to map the
                // saved state to the destination that was popped
                // as well as its parents (if it is the start destination)
                val firstStateDestination = findDestination(firstState.destinationId)
                generateSequence(firstStateDestination) { destination ->
                        if (destination.parent?.startDestinationId == destination.id) {
                            destination.parent
                        } else {
                            null
                        }
                    }
                    .takeWhile { destination ->
                        // Only add the state if it doesn't already exist
                        !backStackMap.containsKey(destination.id)
                    }
                    .forEach { destination -> backStackMap[destination.id] = firstState.id }

                if (backStackMap.values.contains(firstState.id)) {
                    // And finally, store the actual state itself if the entry was added
                    // to backStackMap
                    backStackStates[firstState.id] = savedState
                }
            }
        }
        updateOnBackPressedCallbackEnabled()
        return popped
    }

    /**
     * Trigger a popBackStack() that originated from a Navigator specifically calling
     * [NavigatorState.pop] outside of a call to [popBackStack] (e.g., in response to some user
     * interaction that caused that destination to no longer be needed such as dismissing a dialog
     * destination).
     *
     * This method is responsible for popping all destinations above the given [popUpTo] entry and
     * popping the entry itself and removing it from the back stack before calling the [onComplete]
     * callback. Only after the processing here is done and the [onComplete] callback completes does
     * this method dispatch the destination change event.
     */
    internal actual fun popBackStackFromNavigator(
        popUpTo: NavBackStackEntry,
        onComplete: () -> Unit
    ) {
        val popIndex = backQueue.indexOf(popUpTo)
        if (popIndex < 0) {
            Log.i(TAG, "Ignoring pop of $popUpTo as it was not found on the current back stack")
            return
        }
        if (popIndex + 1 != backQueue.size) {
            // There's other destinations stacked on top of this destination that
            // we need to pop first
            popBackStackInternal(
                backQueue[popIndex + 1].destination.id,
                inclusive = true,
                saveState = false
            )
        }
        // Now record the pop of the actual entry - we don't use popBackStackInternal
        // here since we're being called from the Navigator already
        popEntryFromBackStack(popUpTo)
        onComplete()
        updateOnBackPressedCallbackEnabled()
        dispatchOnDestinationChanged()
    }

    private fun popEntryFromBackStack(
        popUpTo: NavBackStackEntry,
        saveState: Boolean = false,
        savedState: ArrayDeque<NavBackStackEntryState> = ArrayDeque()
    ) {
        val entry = backQueue.last()
        check(entry == popUpTo) {
            "Attempted to pop ${popUpTo.destination}, which is not the top of the back stack " +
                "(${entry.destination})"
        }
        backQueue.removeLastKt()
        val navigator =
            navigatorProvider.getNavigator<Navigator<NavDestination>>(
                entry.destination.navigatorName
            )
        val state = navigatorState[navigator]
        // If we pop an entry with transitions, but not the graph, we will not make a call to
        // popBackStackInternal, so the graph entry will not be marked as transitioning so we
        // need to check if it still has children.
        val transitioning =
            state?.transitionsInProgress?.value?.contains(entry) == true ||
                parentToChildCount.containsKey(entry)
        if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            if (saveState) {
                // Move the state through STOPPED
                entry.maxLifecycle = Lifecycle.State.CREATED
                // Then save the state of the NavBackStackEntry
                savedState.addFirst(NavBackStackEntryState(entry))
            }
            if (!transitioning) {
                entry.maxLifecycle = Lifecycle.State.DESTROYED
                unlinkChildFromParent(entry)
            } else {
                entry.maxLifecycle = Lifecycle.State.CREATED
            }
        }
        if (!saveState && !transitioning) {
            viewModel?.clear(entry.id)
        }
    }

    @MainThread
    public actual fun clearBackStack(route: String): Boolean {
        val cleared = clearBackStackInternal(route)
        // Only return true if the clear succeeded and we've dispatched
        // the change to a new destination
        return cleared && dispatchOnDestinationChanged()
    }

    /**
     * Clears any saved state associated with [destinationId] that was previously saved via
     * [popBackStack] when using a `saveState` value of `true`.
     *
     * @param destinationId The ID of the destination previously used with [popBackStack] with a
     *   `saveState`value of `true`
     * @return true if the saved state of the stack associated with [destinationId] was cleared.
     */
    @MainThread
    public fun clearBackStack(@IdRes destinationId: Int): Boolean {
        val cleared = clearBackStackInternal(destinationId)
        // Only return true if the clear succeeded and we've dispatched
        // the change to a new destination
        return cleared && dispatchOnDestinationChanged()
    }

    @MainThread
    public actual inline fun <reified T : Any> clearBackStack(): Boolean = clearBackStack(T::class)

    @OptIn(InternalSerializationApi::class)
    @MainThread
    public actual fun <T : Any> clearBackStack(route: KClass<T>): Boolean =
        clearBackStack(route.serializer().generateHashCode())

    @OptIn(InternalSerializationApi::class)
    @MainThread
    public actual fun <T : Any> clearBackStack(route: T): Boolean {
        // route contains arguments so we need to generate and clear with the populated route
        // rather than clearing based on route pattern
        val finalRoute = generateRouteFilled(route)
        val cleared = clearBackStackInternal(finalRoute)
        // Only return true if the clear succeeded and we've dispatched
        // the change to a new destination
        return cleared && dispatchOnDestinationChanged()
    }

    @MainThread
    private fun clearBackStackInternal(@IdRes destinationId: Int): Boolean {
        navigatorState.values.forEach { state -> state.isNavigating = true }
        val restored =
            restoreStateInternal(destinationId, null, navOptions { restoreState = true }, null)
        navigatorState.values.forEach { state -> state.isNavigating = false }
        return restored && popBackStackInternal(destinationId, inclusive = true, saveState = false)
    }

    @MainThread
    private fun clearBackStackInternal(route: String): Boolean {
        navigatorState.values.forEach { state -> state.isNavigating = true }
        val restored = restoreStateInternal(route)
        navigatorState.values.forEach { state -> state.isNavigating = false }
        return restored && popBackStackInternal(route, inclusive = true, saveState = false)
    }

    @MainThread
    public actual open fun navigateUp(): Boolean {
        // If there's only one entry, then we may have deep linked into a specific destination
        // on another task.
        if (destinationCountOnBackStack == 1) {
            val extras = activity?.intent?.extras
            if (extras?.getIntArray(KEY_DEEP_LINK_IDS) != null) {
                return tryRelaunchUpToExplicitStack()
            } else {
                return tryRelaunchUpToGeneratedStack()
            }
        } else {
            return popBackStack()
        }
    }

    /**
     * Starts a new Activity directed to the next-upper Destination in the explicit deep link stack
     * used to start this Activity. Returns false if the current destination was already the root of
     * the deep link.
     */
    @Suppress("DEPRECATION")
    private fun tryRelaunchUpToExplicitStack(): Boolean {
        if (!deepLinkHandled) {
            return false
        }

        val intent = activity!!.intent
        val extras = intent.extras

        val deepLinkIds = extras!!.getIntArray(KEY_DEEP_LINK_IDS)!!.toMutableList()
        val deepLinkArgs = extras.getParcelableArrayList<SavedState>(KEY_DEEP_LINK_ARGS)

        // Remove the leaf destination to pop up to one level above it
        var leafDestinationId = deepLinkIds.removeLastKt()
        deepLinkArgs?.removeLastKt()

        // Probably deep linked to a single destination only.
        if (deepLinkIds.isEmpty()) {
            return false
        }

        // Find the destination if the leaf destination was a NavGraph
        with(graph.findDestinationComprehensive(leafDestinationId, false)) {
            if (this is NavGraph) {
                leafDestinationId = this.findStartDestination().id
            }
        }

        // The final element of the deep link couldn't have brought us to the current location.
        if (leafDestinationId != currentDestination?.id) {
            return false
        }

        val navDeepLinkBuilder = createDeepLink()

        // Attach the original global arguments, and also the original calling Intent.
        val arguments = savedState {
            putParcelable(KEY_DEEP_LINK_INTENT, intent)
            extras.getBundle(KEY_DEEP_LINK_EXTRAS)?.let { putAll(it) }
        }
        navDeepLinkBuilder.setArguments(arguments)

        deepLinkIds.forEachIndexed { index, deepLinkId ->
            navDeepLinkBuilder.addDestination(deepLinkId, deepLinkArgs?.get(index))
        }

        navDeepLinkBuilder.createTaskStackBuilder().startActivities()
        activity?.finish()
        return true
    }

    /**
     * Starts a new Activity directed to the parent of the current Destination. Returns false if the
     * current destination was already the root of the deep link.
     */
    private fun tryRelaunchUpToGeneratedStack(): Boolean {
        val currentDestination = currentDestination
        var destId = currentDestination!!.id
        var parent = currentDestination.parent
        while (parent != null) {
            if (parent.startDestinationId != destId) {
                val args = savedState {
                    if (activity != null && activity!!.intent != null) {
                        val data = activity!!.intent.data

                        // We were started via a URI intent.
                        if (data != null) {
                            // Include the original deep link Intent so the Destinations can
                            // synthetically generate additional arguments as necessary.
                            putParcelable(KEY_DEEP_LINK_INTENT, activity!!.intent)
                            val currGraph = backQueue.getTopGraph()
                            val matchingDeepLink =
                                currGraph.matchDeepLinkComprehensive(
                                    navDeepLinkRequest = NavDeepLinkRequest(activity!!.intent),
                                    searchChildren = true,
                                    searchParent = true,
                                    lastVisited = currGraph
                                )
                            if (matchingDeepLink?.matchingArgs != null) {
                                val destinationArgs =
                                    matchingDeepLink.destination.addInDefaultArgs(
                                        matchingDeepLink.matchingArgs
                                    )
                                destinationArgs?.let { putAll(it) }
                            }
                        }
                    }
                }
                val parentIntents =
                    NavDeepLinkBuilder(this)
                        .setDestination(parent.id)
                        .setArguments(args)
                        .createTaskStackBuilder()
                parentIntents.startActivities()
                activity?.finish()
                return true
            }
            destId = parent.id
            parent = parent.parent
        }
        return false
    }

    /** Gets the number of non-NavGraph destinations on the back stack */
    private val destinationCountOnBackStack: Int
        get() = backQueue.count { entry -> entry.destination !is NavGraph }

    private var dispatchReentrantCount = 0
    private val backStackEntriesToDispatch = mutableListOf<NavBackStackEntry>()

    /**
     * Dispatch changes to all OnDestinationChangedListeners.
     *
     * If the back stack is empty, no events get dispatched.
     *
     * @return If changes were dispatched.
     */
    private fun dispatchOnDestinationChanged(): Boolean {
        // We never want to leave NavGraphs on the top of the stack
        while (!backQueue.isEmpty() && backQueue.last().destination is NavGraph) {
            popEntryFromBackStack(backQueue.last())
        }
        val lastBackStackEntry = backQueue.lastOrNull()
        if (lastBackStackEntry != null) {
            backStackEntriesToDispatch += lastBackStackEntry
        }
        // Track that we're updating the back stack lifecycle
        // just in case updateBackStackLifecycle() results in
        // additional calls to navigate() or popBackStack()
        dispatchReentrantCount++
        updateBackStackLifecycle()
        dispatchReentrantCount--

        if (dispatchReentrantCount == 0) {
            // Only the outermost dispatch should dispatch
            val dispatchList = backStackEntriesToDispatch.toMutableList()
            backStackEntriesToDispatch.clear()
            for (backStackEntry in dispatchList) {
                // Now call all registered OnDestinationChangedListener instances
                for (listener in onDestinationChangedListeners) {
                    listener.onDestinationChanged(
                        this,
                        backStackEntry.destination,
                        backStackEntry.arguments
                    )
                }
                _currentBackStackEntryFlow.tryEmit(backStackEntry)
            }
            _currentBackStack.tryEmit(backQueue.toMutableList())
            _visibleEntries.tryEmit(populateVisibleEntries())
        }
        return lastBackStackEntry != null
    }

    internal actual fun updateBackStackLifecycle() {
        // Operate on a copy of the queue to avoid issues with reentrant
        // calls if updating the Lifecycle calls navigate() or popBackStack()
        val backStack = backQueue.toMutableList()
        if (backStack.isEmpty()) {
            // Nothing to update
            return
        }
        // Lifecycle can be split into three layers:
        // 1. Resumed - these are the topmost destination(s) that the user can interact with
        // 2. Started - these destinations are visible, but are underneath resumed destinations
        // 3. Created - these destinations are not visible or on the process of being animated out

        // So first, we need to determine which destinations should be resumed and started
        // This is done by looking at the two special interfaces we have:
        // - FloatingWindow indicates a destination that is above all other destinations, leaving
        //   destinations below it visible, but not interactable. These are always only on the
        //   top of the back stack
        // - SupportingPane indicates a destination that sits alongside the previous destination
        //   and shares the same lifecycle (e.g., both will be resumed, started, or created)

        // This means no matter what, the topmost destination should be able to be resumed,
        // then we add in all of the destinations that also need to be resumed (if the
        // topmost screen is a SupportingPane)
        val topmostDestination = backStack.last().destination
        val nextResumed: MutableList<NavDestination> = mutableListOf(topmostDestination)
        if (topmostDestination is SupportingPane) {
            // A special note for destinations that are marked as both a FloatingWindow and a
            // SupportingPane: a supporting floating window destination can only support other
            // floating windows - if a supporting floating window destination is above
            // a regular destination, the regular destination will *not* be resumed, but instead
            // follow the normal rules between floating windows and regular destinations and only
            // be started.
            val onlyAllowFloatingWindows = topmostDestination is FloatingWindow
            val iterator = backStack.reversed().drop(1).iterator()
            while (iterator.hasNext()) {
                val destination = iterator.next().destination
                if (
                    onlyAllowFloatingWindows &&
                        destination !is FloatingWindow &&
                        destination !is NavGraph
                ) {
                    break
                }
                // Add all visible destinations (e.g., SupportingDestination destinations, their
                // NavGraphs, and the screen directly below all SupportingDestination destinations)
                // to nextResumed
                nextResumed.add(destination)
                // break if we find first visible screen
                if (destination !is SupportingPane && destination !is NavGraph) {
                    break
                }
            }
        }

        // Now that we've marked all of the resumed destinations, we continue to iterate
        // through the back stack to find any destinations that should be started - ones that are
        // below FloatingWindow destinations
        val nextStarted: MutableList<NavDestination> = mutableListOf()
        if (nextResumed.last() is FloatingWindow) {
            // Find all visible destinations in the back stack as they
            // should still be STARTED when the FloatingWindow destination is above it.
            val iterator = backStack.reversed().iterator()
            while (iterator.hasNext()) {
                val destination = iterator.next().destination
                // Add all visible destinations (e.g., FloatingWindow destinations, their
                // NavGraphs, and the screen directly below all FloatingWindow destinations)
                // to nextStarted
                nextStarted.add(destination)
                // break if we find first visible screen
                if (
                    destination !is FloatingWindow &&
                        destination !is SupportingPane &&
                        destination !is NavGraph
                ) {
                    break
                }
            }
        }

        // Now iterate downward through the stack, applying downward Lifecycle
        // transitions and capturing any upward Lifecycle transitions to apply afterwards.
        // This ensures proper nesting where parent navigation graphs are started before
        // their children and stopped only after their children are stopped.
        val upwardStateTransitions = HashMap<NavBackStackEntry, Lifecycle.State>()
        var iterator = backStack.reversed().iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val currentMaxLifecycle = entry.maxLifecycle
            val destination = entry.destination
            if (nextResumed.firstOrNull()?.id == destination.id) {
                // Upward Lifecycle transitions need to be done afterwards so that
                // the parent navigation graph is resumed before their children
                if (currentMaxLifecycle != Lifecycle.State.RESUMED) {
                    val navigator =
                        navigatorProvider.getNavigator<Navigator<*>>(
                            entry.destination.navigatorName
                        )
                    val state = navigatorState[navigator]
                    val transitioning = state?.transitionsInProgress?.value?.contains(entry)
                    if (transitioning != true && parentToChildCount[entry]?.get() != 0) {
                        upwardStateTransitions[entry] = Lifecycle.State.RESUMED
                    } else {
                        upwardStateTransitions[entry] = Lifecycle.State.STARTED
                    }
                }
                if (nextStarted.firstOrNull()?.id == destination.id) nextStarted.removeFirstKt()
                nextResumed.removeFirstKt()
                destination.parent?.let { nextResumed.add(it) }
            } else if (nextStarted.isNotEmpty() && destination.id == nextStarted.first().id) {
                val started = nextStarted.removeFirstKt()
                if (currentMaxLifecycle == Lifecycle.State.RESUMED) {
                    // Downward transitions should be done immediately so children are
                    // paused before their parent navigation graphs
                    entry.maxLifecycle = Lifecycle.State.STARTED
                } else if (currentMaxLifecycle != Lifecycle.State.STARTED) {
                    // Upward Lifecycle transitions need to be done afterwards so that
                    // the parent navigation graph is started before their children
                    upwardStateTransitions[entry] = Lifecycle.State.STARTED
                }
                started.parent?.let {
                    if (!nextStarted.contains(it)) {
                        nextStarted.add(it)
                    }
                }
            } else {
                entry.maxLifecycle = Lifecycle.State.CREATED
            }
        }
        // Apply all upward Lifecycle transitions by iterating through the stack again,
        // this time applying the new lifecycle to the parent navigation graphs first
        iterator = backStack.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val newState = upwardStateTransitions[entry]
            if (newState != null) {
                entry.maxLifecycle = newState
            } else {
                // Ensure the state is up to date
                entry.updateState()
            }
        }
    }

    internal actual fun populateVisibleEntries(): List<NavBackStackEntry> {
        val entries = mutableListOf<NavBackStackEntry>()
        // Add any transitioning entries that are not at least STARTED
        navigatorState.values.forEach { state ->
            entries +=
                state.transitionsInProgress.value.filter { entry ->
                    !entries.contains(entry) &&
                        !entry.maxLifecycle.isAtLeast(Lifecycle.State.STARTED)
                }
        }
        // Add any STARTED entries from the backQueue. This will include the topmost
        // non-FloatingWindow destination plus every FloatingWindow destination above it.
        entries +=
            backQueue.filter { entry ->
                !entries.contains(entry) && entry.maxLifecycle.isAtLeast(Lifecycle.State.STARTED)
            }
        return entries.filter { it.destination !is NavGraph }
    }

    /**
     * The [inflater][NavInflater] for this controller.
     *
     * @return inflater for loading navigation resources
     */
    public open val navInflater: NavInflater by lazy {
        inflater ?: NavInflater(context, _navigatorProvider)
    }

    /**
     * Sets the [navigation graph][NavGraph] to the specified resource. Any current navigation graph
     * data (including back stack) will be replaced.
     *
     * The inflated graph can be retrieved via [graph].
     *
     * @param graphResId resource id of the navigation graph to inflate
     * @see NavController.navInflater
     * @see NavController.setGraph
     * @see NavController.graph
     */
    @MainThread
    @CallSuper
    public open fun setGraph(@NavigationRes graphResId: Int) {
        setGraph(navInflater.inflate(graphResId), null)
    }

    /**
     * Sets the [navigation graph][NavGraph] to the specified resource. Any current navigation graph
     * data (including back stack) will be replaced.
     *
     * The inflated graph can be retrieved via [graph].
     *
     * @param graphResId resource id of the navigation graph to inflate
     * @param startDestinationArgs arguments to send to the start destination of the graph
     * @see NavController.navInflater
     * @see NavController.setGraph
     * @see NavController.graph
     */
    @MainThread
    @CallSuper
    public open fun setGraph(@NavigationRes graphResId: Int, startDestinationArgs: SavedState?) {
        setGraph(navInflater.inflate(graphResId), startDestinationArgs)
    }

    @MainThread
    @CallSuper
    public actual open fun setGraph(graph: NavGraph, startDestinationArgs: SavedState?) {
        check(backQueue.isEmpty() || hostLifecycleState != Lifecycle.State.DESTROYED) {
            "You cannot set a new graph on a NavController with entries on the back stack " +
                "after the NavController has been destroyed. Please ensure that your NavHost " +
                "has the same lifetime as your NavController."
        }
        if (_graph != graph) {
            _graph?.let { previousGraph ->
                // Clear all saved back stacks by iterating through a copy of the saved keys,
                // thus avoiding any concurrent modification exceptions
                val savedBackStackIds = ArrayList(backStackMap.keys)
                savedBackStackIds.forEach { id -> clearBackStackInternal(id) }
                // Pop everything from the old graph off the back stack
                popBackStackInternal(previousGraph.id, true)
            }
            _graph = graph
            onGraphCreated(startDestinationArgs)
        } else {
            // first we update _graph with new instances from graph
            for (i in 0 until graph.nodes.size()) {
                val newDestination = graph.nodes.valueAt(i)
                val key = _graph!!.nodes.keyAt(i)
                _graph!!.nodes.replace(key, newDestination)
            }
            // then we update backstack with the new instances
            backQueue.forEach { entry ->
                // we will trace this hierarchy in new graph to get new destination instance
                val hierarchy = entry.destination.hierarchy.toList().asReversed()
                val newDestination =
                    hierarchy.fold(_graph!!) { newDest: NavDestination, oldDest: NavDestination ->
                        if (oldDest == _graph && newDest == graph) {
                            // if root graph, it is already the node that matches with oldDest
                            newDest
                        } else if (newDest is NavGraph) {
                            // otherwise we walk down the hierarchy to the next child
                            newDest.findNode(oldDest.id)!!
                        } else {
                            // final leaf node found
                            newDest
                        }
                    }
                entry.destination = newDestination
            }
        }
    }

    @MainThread
    private fun onGraphCreated(startDestinationArgs: SavedState?) {
        navigatorStateToRestore?.read {
            if (contains(KEY_NAVIGATOR_STATE_NAMES)) {
                val navigatorNames = getStringList(KEY_NAVIGATOR_STATE_NAMES)
                for (name in navigatorNames) {
                    val navigator = _navigatorProvider.getNavigator<Navigator<*>>(name)
                    if (contains(name)) {
                        val savedState = getSavedState(name)
                        navigator.onRestoreState(savedState)
                    }
                }
            }
        }
        backStackToRestore?.let { backStackToRestore ->
            for (parcelable in backStackToRestore) {
                val state = parcelable as NavBackStackEntryState
                val node = findDestination(state.destinationId)
                if (node == null) {
                    val dest = NavDestination.getDisplayName(context, state.destinationId)
                    throw IllegalStateException(
                        "Restoring the Navigation back stack failed: destination $dest cannot be " +
                            "found from the current destination $currentDestination"
                    )
                }
                val entry = state.instantiate(context, node, hostLifecycleState, viewModel)
                val navigator = _navigatorProvider.getNavigator<Navigator<*>>(node.navigatorName)
                val navigatorBackStack =
                    navigatorState.getOrPut(navigator) { NavControllerNavigatorState(navigator) }
                backQueue.add(entry)
                navigatorBackStack.addInternal(entry)
                val parent = entry.destination.parent
                if (parent != null) {
                    linkChildToParent(entry, getBackStackEntry(parent.id))
                }
            }
            updateOnBackPressedCallbackEnabled()
            this.backStackToRestore = null
        }
        // Mark all Navigators as attached
        _navigatorProvider.navigators.values
            .filterNot { it.isAttached }
            .forEach { navigator ->
                val navigatorBackStack =
                    navigatorState.getOrPut(navigator) { NavControllerNavigatorState(navigator) }
                navigator.onAttach(navigatorBackStack)
            }
        if (_graph != null && backQueue.isEmpty()) {
            val deepLinked =
                !deepLinkHandled && activity != null && handleDeepLink(activity!!.intent)
            if (!deepLinked) {
                // Navigate to the first destination in the graph
                // if we haven't deep linked to a destination
                navigate(_graph!!, startDestinationArgs, null, null)
            }
        } else {
            dispatchOnDestinationChanged()
        }
    }

    /**
     * Checks the given Intent for a Navigation deep link and navigates to the deep link if present.
     * This is called automatically for you the first time you set the graph if you've passed in an
     * [Activity] as the context when constructing this NavController, but should be manually called
     * if your Activity receives new Intents in [Activity.onNewIntent].
     *
     * The types of Intents that are supported include:
     *
     * Intents created by [NavDeepLinkBuilder] or [createDeepLink]. This assumes that the current
     * graph shares the same hierarchy to get to the deep linked destination as when the deep link
     * was constructed. Intents that include a [data Uri][Intent.getData]. This Uri will be checked
     * against the Uri patterns in the [NavDeepLinks][NavDeepLink] added via
     * [NavDestination.addDeepLink].
     *
     * The [navigation graph][graph] should be set before calling this method.
     *
     * @param intent The Intent that may contain a valid deep link
     * @return True if the navigation controller found a valid deep link and navigated to it.
     * @throws IllegalStateException if deep link cannot be accessed from the current destination
     * @see NavDestination.addDeepLink
     */
    @MainThread
    @Suppress("DEPRECATION")
    public open fun handleDeepLink(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        val extras = intent.extras
        var deepLink =
            try {
                extras?.getIntArray(KEY_DEEP_LINK_IDS)
            } catch (e: Exception) {
                Log.e(TAG, "handleDeepLink() could not extract deepLink from $intent", e)
                null
            }
        var deepLinkArgs = extras?.getParcelableArrayList<SavedState>(KEY_DEEP_LINK_ARGS)
        val globalArgs = savedState()
        val deepLinkExtras = extras?.getBundle(KEY_DEEP_LINK_EXTRAS)
        if (deepLinkExtras != null) {
            globalArgs.write { putAll(deepLinkExtras) }
        }
        if (deepLink == null || deepLink.isEmpty()) {
            val currGraph = backQueue.getTopGraph()
            val matchingDeepLink =
                currGraph.matchDeepLinkComprehensive(
                    navDeepLinkRequest = NavDeepLinkRequest(intent),
                    searchChildren = true,
                    searchParent = true,
                    lastVisited = currGraph
                )
            if (matchingDeepLink != null) {
                val destination = matchingDeepLink.destination
                deepLink = destination.buildDeepLinkIds()
                deepLinkArgs = null
                val destinationArgs = destination.addInDefaultArgs(matchingDeepLink.matchingArgs)
                if (destinationArgs != null) {
                    globalArgs.write { putAll(destinationArgs) }
                }
            }
        }
        if (deepLink == null || deepLink.isEmpty()) {
            return false
        }
        val invalidDestinationDisplayName = findInvalidDestinationDisplayNameInDeepLink(deepLink)
        if (invalidDestinationDisplayName != null) {
            Log.i(
                TAG,
                "Could not find destination $invalidDestinationDisplayName in the " +
                    "navigation graph, ignoring the deep link from $intent"
            )
            return false
        }
        globalArgs.write { putParcelable(KEY_DEEP_LINK_INTENT, intent) }
        val args = arrayOfNulls<SavedState>(deepLink.size)
        for (index in args.indices) {
            val arguments = savedState {
                putAll(globalArgs)
                if (deepLinkArgs != null) {
                    val deepLinkArguments = deepLinkArgs[index]
                    if (deepLinkArguments != null) {
                        putAll(deepLinkArguments)
                    }
                }
            }
            args[index] = arguments
        }
        val flags = intent.flags
        if (
            flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 &&
                flags and Intent.FLAG_ACTIVITY_CLEAR_TASK == 0
        ) {
            // Someone called us with NEW_TASK, but we don't know what state our whole
            // task stack is in, so we need to manually restart the whole stack to
            // ensure we're in a predictably good state.
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val taskStackBuilder =
                TaskStackBuilder.create(context).addNextIntentWithParentStack(intent)
            taskStackBuilder.startActivities()
            activity?.let { activity ->
                activity.finish()
                // Disable second animation in case where the Activity is created twice.
                activity.overridePendingTransition(0, 0)
            }
            return true
        }
        return handleDeepLink(deepLink, args, flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @MainThread
    public actual fun handleDeepLink(request: NavDeepLinkRequest): Boolean {
        val currGraph = backQueue.getTopGraph()
        val matchingDeepLink =
            currGraph.matchDeepLinkComprehensive(
                navDeepLinkRequest = request,
                searchChildren = true,
                searchParent = true,
                lastVisited = currGraph
            )
        if (matchingDeepLink != null) {
            val destination = matchingDeepLink.destination
            val deepLink = destination.buildDeepLinkIds()
            val globalArgs = savedState {
                val destinationArgs = destination.addInDefaultArgs(matchingDeepLink.matchingArgs)
                if (destinationArgs != null) {
                    putAll(destinationArgs)
                }
            }
            val args = arrayOfNulls<SavedState>(deepLink.size)
            for (index in args.indices) {
                val arguments = savedState { putAll(globalArgs) }
                args[index] = arguments
            }
            return handleDeepLink(deepLink, args, true)
        }
        return false
    }

    private fun handleDeepLink(
        deepLink: IntArray,
        args: Array<SavedState?>,
        newTask: Boolean
    ): Boolean {
        if (newTask) {
            // Start with a cleared task starting at our root when we're on our own task
            if (!backQueue.isEmpty()) {
                popBackStackInternal(_graph!!.id, true)
            }
            var index = 0
            while (index < deepLink.size) {
                val destinationId = deepLink[index]
                val arguments = args[index++]
                val node = findDestination(destinationId)
                if (node == null) {
                    val dest = NavDestination.getDisplayName(context, destinationId)
                    throw IllegalStateException(
                        "Deep Linking failed: destination $dest cannot be found from the current " +
                            "destination $currentDestination"
                    )
                }
                navigate(
                    node,
                    arguments,
                    navOptions {
                        anim {
                            enter = 0
                            exit = 0
                        }
                        val changingGraphs =
                            node is NavGraph &&
                                node.hierarchy.none { it == currentDestination?.parent }
                        if (changingGraphs && deepLinkSaveState) {
                            // If we are navigating to a 'sibling' graph (one that isn't part
                            // of the current destination's hierarchy), then we need to saveState
                            // to ensure that each graph has its own saved state that users can
                            // return to
                            popUpTo(graph.findStartDestination().id) { saveState = true }
                            // Note we specifically don't call restoreState = true
                            // as our deep link should support multiple instances of the
                            // same graph in a row
                        }
                    },
                    null
                )
            }
            deepLinkHandled = true
            return true
        }
        // Assume we're on another apps' task and only start the final destination
        var graph = _graph
        for (i in deepLink.indices) {
            val destinationId = deepLink[i]
            val arguments = args[i]
            val node = if (i == 0) _graph else graph!!.findNode(destinationId)
            if (node == null) {
                val dest = NavDestination.getDisplayName(context, destinationId)
                throw IllegalStateException(
                    "Deep Linking failed: destination $dest cannot be found in graph $graph"
                )
            }
            if (i != deepLink.size - 1) {
                // We're not at the final NavDestination yet, so keep going through the chain
                if (node is NavGraph) {
                    graph = node
                    // Automatically go down the navigation graph when
                    // the start destination is also a NavGraph
                    while (graph!!.findNode(graph.startDestinationId) is NavGraph) {
                        graph = graph.findNode(graph.startDestinationId) as NavGraph?
                    }
                }
            } else {
                // Navigate to the last NavDestination, clearing any existing destinations
                navigate(
                    node,
                    arguments,
                    NavOptions.Builder()
                        .setPopUpTo(_graph!!.id, true)
                        .setEnterAnim(0)
                        .setExitAnim(0)
                        .build(),
                    null
                )
            }
        }
        deepLinkHandled = true
        return true
    }

    /**
     * Looks through the deep link for invalid destinations, returning the display name of any
     * invalid destinations in the deep link array.
     *
     * @param deepLink array of deep link IDs that are expected to match the graph
     * @return The display name of the first destination not found in the graph or null if all
     *   destinations were found in the graph.
     */
    private fun findInvalidDestinationDisplayNameInDeepLink(deepLink: IntArray): String? {
        var graph = _graph
        for (i in deepLink.indices) {
            val destinationId = deepLink[i]
            val node =
                (if (i == 0) if (_graph!!.id == destinationId) _graph else null
                else graph!!.findNode(destinationId))
                    ?: return NavDestination.getDisplayName(context, destinationId)
            if (i != deepLink.size - 1) {
                // We're not at the final NavDestination yet, so keep going through the chain
                if (node is NavGraph) {
                    graph = node
                    // Automatically go down the navigation graph when
                    // the start destination is also a NavGraph
                    while (graph!!.findNode(graph.startDestinationId) is NavGraph) {
                        graph = graph.findNode(graph.startDestinationId) as NavGraph?
                    }
                }
            }
        }
        // We found every destination in the deepLink array, yay!
        return null
    }

    public actual open val currentDestination: NavDestination?
        get() {
            return currentBackStackEntry?.destination
        }

    /**
     * Recursively searches through parents
     *
     * @param destinationId the [NavDestination.id]
     * @param matchingDest an optional NavDestination that the node should match with. This is
     *   because [destinationId] is only unique to a local graph. Nodes in sibling graphs can have
     *   the same id.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun findDestination(
        @IdRes destinationId: Int,
        matchingDest: NavDestination? = null,
    ): NavDestination? {
        if (_graph == null) {
            return null
        }

        if (_graph!!.id == destinationId) {
            when {
                /**
                 * if the search expected a specific NavDestination (i.e. a duplicated destination
                 * within a specific graph), we need to make sure the result matches it to ensure
                 * this search returns the correct duplicate.
                 */
                matchingDest != null ->
                    if (_graph == matchingDest && matchingDest.parent == null) return _graph
                else -> return _graph
            }
        }

        val currentNode = backQueue.lastOrNull()?.destination ?: _graph!!
        return currentNode.findDestinationComprehensive(destinationId, false, matchingDest)
    }

    /**
     * Recursively searches through parents. If [searchChildren] is true, also recursively searches
     * children.
     *
     * @param destinationId the [NavDestination.id]
     * @param searchChildren recursively searches children when true
     * @param matchingDest an optional NavDestination that the node should match with. This is
     *   because [destinationId] is only unique to a local graph. Nodes in sibling graphs can have
     *   the same id.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun NavDestination.findDestinationComprehensive(
        @IdRes destinationId: Int,
        searchChildren: Boolean,
        matchingDest: NavDestination? = null,
    ): NavDestination? {
        if (id == destinationId) {
            when {
                // check parent in case of duplicated destinations to ensure it finds the correct
                // nested destination
                matchingDest != null ->
                    if (this == matchingDest && this.parent == matchingDest.parent) return this
                else -> return this
            }
        }
        val currentGraph = if (this is NavGraph) this else parent!!
        return currentGraph.findNodeComprehensive(
            destinationId,
            currentGraph,
            searchChildren,
            matchingDest
        )
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual fun findDestination(route: String): NavDestination? {
        if (_graph == null) {
            return null
        }
        // if not matched by routePattern, try matching with route args
        if (_graph!!.route == route || _graph!!.matchRoute(route) != null) {
            return _graph
        }
        return backQueue.getTopGraph().findNode(route)
    }

    /**
     * Returns the last NavGraph on the backstack.
     *
     * If there are no NavGraphs on the stack, returns [_graph]
     */
    private fun ArrayDeque<NavBackStackEntry>.getTopGraph(): NavGraph {
        val currentNode = lastOrNull()?.destination ?: _graph!!
        return if (currentNode is NavGraph) currentNode else currentNode.parent!!
    }

    // Finds destination within _graph including its children and
    // generates a route filled with args based on the serializable object.
    // Throws if destination with `route` is not found
    @OptIn(InternalSerializationApi::class)
    private fun <T : Any> generateRouteFilled(route: T): String {
        val id = route::class.serializer().generateHashCode()
        val destination = graph.findDestinationComprehensive(id, true)
        // throw immediately if destination is not found within the graph
        requireNotNull(destination) {
            "Destination with route ${route::class.simpleName} cannot be found " +
                "in navigation graph $_graph"
        }
        return generateRouteWithArgs(
            route,
            // get argument typeMap
            destination.arguments.mapValues { it.value.type }
        )
    }

    /**
     * Navigate to a destination from the current navigation graph. This supports both navigating
     * via an [action][NavDestination.getAction] and directly navigating to a destination.
     *
     * @param resId an [action][NavDestination.getAction] id or a destination id to navigate to
     * @throws IllegalStateException if there is no current navigation node
     * @throws IllegalArgumentException if the desired destination cannot be found from the current
     *   destination
     */
    @MainThread
    public open fun navigate(@IdRes resId: Int) {
        navigate(resId, null)
    }

    /**
     * Navigate to a destination from the current navigation graph. This supports both navigating
     * via an [action][NavDestination.getAction] and directly navigating to a destination.
     *
     * @param resId an [action][NavDestination.getAction] id or a destination id to navigate to
     * @param args arguments to pass to the destination
     * @throws IllegalStateException if there is no current navigation node
     * @throws IllegalArgumentException if the desired destination cannot be found from the current
     *   destination
     */
    @MainThread
    public open fun navigate(@IdRes resId: Int, args: SavedState?) {
        navigate(resId, args, null)
    }

    /**
     * Navigate to a destination from the current navigation graph. This supports both navigating
     * via an [action][NavDestination.getAction] and directly navigating to a destination.
     *
     * If given [NavOptions] pass in [NavOptions.restoreState] `true`, any args passed here will be
     * overridden by the restored args.
     *
     * @param resId an [action][NavDestination.getAction] id or a destination id to navigate to
     * @param args arguments to pass to the destination
     * @param navOptions special options for this navigation operation
     * @throws IllegalStateException if there is no current navigation node
     * @throws IllegalArgumentException if the desired destination cannot be found from the current
     *   destination
     */
    @MainThread
    public open fun navigate(@IdRes resId: Int, args: SavedState?, navOptions: NavOptions?) {
        navigate(resId, args, navOptions, null)
    }

    /**
     * Navigate to a destination from the current navigation graph. This supports both navigating
     * via an [action][NavDestination.getAction] and directly navigating to a destination.
     *
     * If given [NavOptions] pass in [NavOptions.restoreState] `true`, any args passed here will be
     * overridden by the restored args.
     *
     * @param resId an [action][NavDestination.getAction] id or a destination id to navigate to
     * @param args arguments to pass to the destination
     * @param navOptions special options for this navigation operation
     * @param navigatorExtras extras to pass to the Navigator
     * @throws IllegalStateException if navigation graph has not been set for this NavController
     * @throws IllegalArgumentException if the desired destination cannot be found from the current
     *   destination
     */
    @OptIn(InternalSerializationApi::class)
    @MainThread
    public open fun navigate(
        @IdRes resId: Int,
        args: SavedState?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ) {
        var finalNavOptions = navOptions
        val currentNode =
            (if (backQueue.isEmpty()) _graph else backQueue.last().destination)
                ?: throw IllegalStateException(
                    "No current destination found. Ensure a navigation graph has been set for " +
                        "NavController $this."
                )

        @IdRes var destId = resId
        val navAction = currentNode.getAction(resId)
        var combinedArgs: SavedState? = null
        if (navAction != null) {
            if (finalNavOptions == null) {
                finalNavOptions = navAction.navOptions
            }
            destId = navAction.destinationId
            val navActionArgs = navAction.defaultArguments
            if (navActionArgs != null) {
                combinedArgs = savedState { putAll(navActionArgs) }
            }
        }
        if (args != null) {
            if (combinedArgs == null) {
                combinedArgs = savedState()
            }
            combinedArgs.write { putAll(args) }
        }
        // just pop and return if destId is invalid
        if (
            destId == 0 &&
                finalNavOptions != null &&
                (finalNavOptions.popUpToId != -1 ||
                    finalNavOptions.popUpToRoute != null ||
                    finalNavOptions.popUpToRouteClass != null)
        ) {
            when {
                finalNavOptions.popUpToRoute != null ->
                    popBackStack(
                        finalNavOptions.popUpToRoute!!,
                        finalNavOptions.isPopUpToInclusive()
                    )
                finalNavOptions.popUpToRouteClass != null ->
                    popBackStack(
                        finalNavOptions.popUpToRouteClass!!.serializer().generateHashCode(),
                        finalNavOptions.isPopUpToInclusive()
                    )
                finalNavOptions.popUpToId != -1 ->
                    popBackStack(finalNavOptions.popUpToId, finalNavOptions.isPopUpToInclusive())
            }
            return
        }
        require(destId != 0) {
            "Destination id == 0 can only be used in conjunction with a valid navOptions.popUpTo"
        }
        val node = findDestination(destId)
        if (node == null) {
            val dest = NavDestination.getDisplayName(context, destId)
            require(navAction == null) {
                "Navigation destination $dest referenced from action " +
                    "${NavDestination.getDisplayName(context, resId)} cannot be found from " +
                    "the current destination $currentNode"
            }
            throw IllegalArgumentException(
                "Navigation action/destination $dest cannot be found from the current " +
                    "destination $currentNode"
            )
        }
        navigate(node, combinedArgs, finalNavOptions, navigatorExtras)
    }

    @MainThread
    public actual open fun navigate(deepLink: Uri) {
        navigate(NavDeepLinkRequest(deepLink, null, null))
    }

    @MainThread
    public actual open fun navigate(deepLink: Uri, navOptions: NavOptions?) {
        navigate(NavDeepLinkRequest(deepLink, null, null), navOptions, null)
    }

    @MainThread
    public actual open fun navigate(
        deepLink: Uri,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ) {
        navigate(NavDeepLinkRequest(deepLink, null, null), navOptions, navigatorExtras)
    }

    @MainThread
    public actual open fun navigate(request: NavDeepLinkRequest) {
        navigate(request, null)
    }

    @MainThread
    public actual open fun navigate(request: NavDeepLinkRequest, navOptions: NavOptions?) {
        navigate(request, navOptions, null)
    }

    @MainThread
    public actual open fun navigate(
        request: NavDeepLinkRequest,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ) {
        requireNotNull(_graph) {
            "Cannot navigate to $request. Navigation graph has not been set for " +
                "NavController $this."
        }
        val currGraph = backQueue.getTopGraph()
        val deepLinkMatch =
            currGraph.matchDeepLinkComprehensive(
                navDeepLinkRequest = request,
                searchChildren = true,
                searchParent = true,
                lastVisited = currGraph
            )
        if (deepLinkMatch != null) {
            val destination = deepLinkMatch.destination
            val args = destination.addInDefaultArgs(deepLinkMatch.matchingArgs) ?: savedState()
            val node = deepLinkMatch.destination
            val intent =
                Intent().apply {
                    setDataAndType(request.uri, request.mimeType)
                    action = request.action
                }
            args.write { putParcelable(KEY_DEEP_LINK_INTENT, intent) }
            navigate(node, args, navOptions, navigatorExtras)
        } else {
            throw IllegalArgumentException(
                "Navigation destination that matches request $request cannot be found in the " +
                    "navigation graph $_graph"
            )
        }
    }

    @OptIn(InternalSerializationApi::class)
    @MainThread
    private fun navigate(
        node: NavDestination,
        args: SavedState?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ) {
        navigatorState.values.forEach { state -> state.isNavigating = true }
        var popped = false
        var launchSingleTop = false
        var navigated = false
        if (navOptions != null) {
            when {
                navOptions.popUpToRoute != null ->
                    popped =
                        popBackStackInternal(
                            navOptions.popUpToRoute!!,
                            navOptions.isPopUpToInclusive(),
                            navOptions.shouldPopUpToSaveState()
                        )
                navOptions.popUpToRouteClass != null ->
                    popped =
                        popBackStackInternal(
                            navOptions.popUpToRouteClass!!.serializer().generateHashCode(),
                            navOptions.isPopUpToInclusive(),
                            navOptions.shouldPopUpToSaveState()
                        )
                navOptions.popUpToRouteObject != null ->
                    popped =
                        popBackStackInternal(
                            navOptions.popUpToRouteObject!!,
                            navOptions.isPopUpToInclusive(),
                            navOptions.shouldPopUpToSaveState()
                        )
                navOptions.popUpToId != -1 ->
                    popped =
                        popBackStackInternal(
                            navOptions.popUpToId,
                            navOptions.isPopUpToInclusive(),
                            navOptions.shouldPopUpToSaveState()
                        )
            }
        }
        val finalArgs = node.addInDefaultArgs(args)
        // Now determine what new destinations we need to add to the back stack
        if (navOptions?.shouldRestoreState() == true && backStackMap.containsKey(node.id)) {
            navigated = restoreStateInternal(node.id, finalArgs, navOptions, navigatorExtras)
        } else {
            launchSingleTop =
                navOptions?.shouldLaunchSingleTop() == true && launchSingleTopInternal(node, args)

            if (!launchSingleTop) {
                // Not a single top operation, so we're looking to add the node to the back stack
                val backStackEntry =
                    NavBackStackEntry.create(
                        context,
                        node,
                        finalArgs,
                        hostLifecycleState,
                        viewModel
                    )
                val navigator =
                    _navigatorProvider.getNavigator<Navigator<NavDestination>>(node.navigatorName)
                navigator.navigateInternal(listOf(backStackEntry), navOptions, navigatorExtras) {
                    navigated = true
                    addEntryToBackStack(node, finalArgs, it)
                }
            }
        }
        updateOnBackPressedCallbackEnabled()
        navigatorState.values.forEach { state -> state.isNavigating = false }
        if (popped || navigated || launchSingleTop) {
            dispatchOnDestinationChanged()
        } else {
            updateBackStackLifecycle()
        }
    }

    private fun launchSingleTopInternal(node: NavDestination, args: SavedState?): Boolean {
        val currentBackStackEntry = currentBackStackEntry
        val nodeIndex = backQueue.indexOfLast { it.destination === node }
        // early return when node isn't even in backQueue
        if (nodeIndex == -1) return false
        if (node is NavGraph) {
            // get expected singleTop stack
            val childHierarchyId = node.childHierarchy().map { it.id }.toList()
            // if actual backQueue size does not match expected singleTop stack size, we know its
            // not a single top
            if (backQueue.size - nodeIndex != childHierarchyId.size) return false
            val backQueueId = backQueue.subList(nodeIndex, backQueue.size).map { it.destination.id }
            // then make sure the backstack and singleTop stack is exact match
            if (backQueueId != childHierarchyId) return false
        } else if (node.id != currentBackStackEntry?.destination?.id) {
            return false
        }

        val tempBackQueue: ArrayDeque<NavBackStackEntry> = ArrayDeque()
        // pop from startDestination back to original node and create a new entry for each
        while (backQueue.lastIndex >= nodeIndex) {
            val oldEntry = backQueue.removeLastKt()
            unlinkChildFromParent(oldEntry)
            val newEntry = NavBackStackEntry(oldEntry, oldEntry.destination.addInDefaultArgs(args))
            tempBackQueue.addFirst(newEntry)
        }

        // add each new entry to backQueue starting from original node to startDestination
        tempBackQueue.forEach { newEntry ->
            val parent = newEntry.destination.parent
            if (parent != null) {
                val newParent = getBackStackEntry(parent.id)
                linkChildToParent(newEntry, newParent)
            }
            backQueue.add(newEntry)
        }

        // we replace NavState entries here only after backQueue has been finalized
        tempBackQueue.forEach { newEntry ->
            val navigator =
                _navigatorProvider.getNavigator<Navigator<*>>(newEntry.destination.navigatorName)
            navigator.onLaunchSingleTop(newEntry)
        }

        return true
    }

    private fun restoreStateInternal(
        id: Int,
        args: SavedState?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ): Boolean {
        if (!backStackMap.containsKey(id)) {
            return false
        }
        val backStackId = backStackMap[id]
        // Clear out the state we're going to restore so that it isn't restored a second time
        backStackMap.values.removeAll { it == backStackId }
        val backStackState = backStackStates.remove(backStackId)
        // Now restore the back stack from its saved state
        val entries = instantiateBackStack(backStackState)
        return executeRestoreState(entries, args, navOptions, navigatorExtras)
    }

    private fun restoreStateInternal(route: String): Boolean {
        var id = createRoute(route).hashCode()
        // try to match based on routePattern
        return if (backStackMap.containsKey(id)) {
            restoreStateInternal(id, null, null, null)
        } else {
            // if it didn't match, it means the route contains filled in arguments and we need
            // to find the destination that matches this route's general pattern
            val matchingDestination = findDestination(route)
            check(matchingDestination != null) {
                "Restore State failed: route $route cannot be found from the current " +
                    "destination $currentDestination"
            }

            id = matchingDestination.id
            val backStackId = backStackMap[id]
            // Clear out the state we're going to restore so that it isn't restored a second time
            backStackMap.values.removeAll { it == backStackId }
            val backStackState = backStackStates.remove(backStackId)

            val matchingDeepLink = matchingDestination.matchRoute(route)
            // check if the topmost NavBackStackEntryState contains the arguments in this
            // matchingDeepLink. If not, we didn't find the correct stack.
            val isCorrectStack =
                matchingDeepLink!!.hasMatchingArgs(backStackState?.firstOrNull()?.args)
            if (!isCorrectStack) return false
            val entries = instantiateBackStack(backStackState)
            executeRestoreState(entries, null, null, null)
        }
    }

    private fun executeRestoreState(
        entries: List<NavBackStackEntry>,
        args: SavedState?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ): Boolean {
        // Split up the entries by Navigator so we can restore them as an atomic operation
        val entriesGroupedByNavigator = mutableListOf<MutableList<NavBackStackEntry>>()
        entries
            .filterNot { entry ->
                // Skip navigation graphs - they'll be added by addEntryToBackStack()
                entry.destination is NavGraph
            }
            .forEach { entry ->
                val previousEntryList = entriesGroupedByNavigator.lastOrNull()
                val previousNavigatorName = previousEntryList?.last()?.destination?.navigatorName
                if (previousNavigatorName == entry.destination.navigatorName) {
                    // Group back to back entries associated with the same Navigator together
                    previousEntryList += entry
                } else {
                    // Create a new group for the new Navigator
                    entriesGroupedByNavigator += mutableListOf(entry)
                }
            }
        var navigated = false
        // Now actually navigate to each set of entries
        for (entryList in entriesGroupedByNavigator) {
            val navigator =
                _navigatorProvider.getNavigator<Navigator<NavDestination>>(
                    entryList.first().destination.navigatorName
                )
            var lastNavigatedIndex = 0
            navigator.navigateInternal(entryList, navOptions, navigatorExtras) { entry ->
                navigated = true
                // If this destination is part of the restored back stack,
                // pass all destinations between the last navigated entry and this one
                // to ensure that any navigation graphs are properly restored as well
                val entryIndex = entries.indexOf(entry)
                val restoredEntries =
                    if (entryIndex != -1) {
                        entries.subList(lastNavigatedIndex, entryIndex + 1).also {
                            lastNavigatedIndex = entryIndex + 1
                        }
                    } else {
                        emptyList()
                    }
                addEntryToBackStack(entry.destination, args, entry, restoredEntries)
            }
        }
        return navigated
    }

    private fun instantiateBackStack(
        backStackState: ArrayDeque<NavBackStackEntryState>?
    ): List<NavBackStackEntry> {
        val backStack = mutableListOf<NavBackStackEntry>()
        var currentDestination = backQueue.lastOrNull()?.destination ?: graph
        backStackState?.forEach { state ->
            val node = currentDestination.findDestinationComprehensive(state.destinationId, true)
            checkNotNull(node) {
                val dest = NavDestination.getDisplayName(context, state.destinationId)
                "Restore State failed: destination $dest cannot be found from the current " +
                    "destination $currentDestination"
            }
            backStack += state.instantiate(context, node, hostLifecycleState, viewModel)
            currentDestination = node
        }
        return backStack
    }

    private fun addEntryToBackStack(
        node: NavDestination,
        finalArgs: SavedState?,
        backStackEntry: NavBackStackEntry,
        restoredEntries: List<NavBackStackEntry> = emptyList()
    ) {
        val newDest = backStackEntry.destination
        if (newDest !is FloatingWindow) {
            // We've successfully navigating to the new destination, which means
            // we should pop any FloatingWindow destination off the back stack
            // before updating the back stack with our new destination
            while (
                !backQueue.isEmpty() &&
                    backQueue.last().destination is FloatingWindow &&
                    popBackStackInternal(backQueue.last().destination.id, true)
            ) {
                // Keep popping
            }
        }

        // When you navigate() to a NavGraph, we need to ensure that a new instance
        // is always created vs reusing an existing copy of that destination
        val hierarchy = ArrayDeque<NavBackStackEntry>()
        var destination: NavDestination? = newDest
        if (node is NavGraph) {
            do {
                val parent = destination!!.parent
                if (parent != null) {
                    val entry =
                        restoredEntries.lastOrNull { restoredEntry ->
                            restoredEntry.destination == parent
                        }
                            ?: NavBackStackEntry.create(
                                context,
                                parent,
                                finalArgs,
                                hostLifecycleState,
                                viewModel
                            )
                    hierarchy.addFirst(entry)
                    // Pop any orphaned copy of that navigation graph off the back stack
                    if (backQueue.isNotEmpty() && backQueue.last().destination === parent) {
                        popEntryFromBackStack(backQueue.last())
                    }
                }
                destination = parent
            } while (destination != null && destination !== node)
        }

        // Now collect the set of all intermediate NavGraphs that need to be put onto
        // the back stack. Destinations can have multiple parents, so we check referential
        // equality to ensure that same destinations with a parent that is not this _graph
        // will also have their parents added to the hierarchy.
        destination = if (hierarchy.isEmpty()) newDest else hierarchy.first().destination
        while (
            destination != null && findDestination(destination.id, destination) !== destination
        ) {
            val parent = destination.parent
            if (parent != null) {
                val args = if (finalArgs?.read { isEmpty() } == true) null else finalArgs
                val entry =
                    restoredEntries.lastOrNull { restoredEntry ->
                        restoredEntry.destination == parent
                    }
                        ?: NavBackStackEntry.create(
                            context,
                            parent,
                            parent.addInDefaultArgs(args),
                            hostLifecycleState,
                            viewModel
                        )
                hierarchy.addFirst(entry)
            }
            destination = parent
        }
        val overlappingDestination: NavDestination =
            if (hierarchy.isEmpty()) newDest else hierarchy.first().destination
        // Pop any orphaned navigation graphs that don't connect to the new destinations
        while (
            !backQueue.isEmpty() &&
                backQueue.last().destination is NavGraph &&
                (backQueue.last().destination as NavGraph).nodes[overlappingDestination.id] == null
        ) {
            popEntryFromBackStack(backQueue.last())
        }

        // The _graph should always be on the top of the back stack after you navigate()
        val firstEntry = backQueue.firstOrNull() ?: hierarchy.firstOrNull()
        if (firstEntry?.destination != _graph) {
            val entry =
                restoredEntries.lastOrNull { restoredEntry ->
                    restoredEntry.destination == _graph!!
                }
                    ?: NavBackStackEntry.create(
                        context,
                        _graph!!,
                        _graph!!.addInDefaultArgs(finalArgs),
                        hostLifecycleState,
                        viewModel
                    )
            hierarchy.addFirst(entry)
        }

        // Now add the parent hierarchy to the NavigatorStates and back stack
        hierarchy.forEach { entry ->
            val navigator =
                _navigatorProvider.getNavigator<Navigator<*>>(entry.destination.navigatorName)
            val navigatorBackStack =
                checkNotNull(navigatorState[navigator]) {
                    "NavigatorBackStack for ${node.navigatorName} should already be created"
                }
            navigatorBackStack.addInternal(entry)
        }
        backQueue.addAll(hierarchy)

        // And finally, add the new destination
        backQueue.add(backStackEntry)

        // Link the newly added hierarchy and entry with the parent NavBackStackEntry
        // so that we can track how many destinations are associated with each NavGraph
        (hierarchy + backStackEntry).forEach {
            val parent = it.destination.parent
            if (parent != null) {
                linkChildToParent(it, getBackStackEntry(parent.id))
            }
        }
    }

    /**
     * Navigate via the given [NavDirections]
     *
     * @param directions directions that describe this navigation operation
     */
    @MainThread
    public open fun navigate(directions: NavDirections) {
        navigate(directions.actionId, directions.arguments, null)
    }

    /**
     * Navigate via the given [NavDirections]
     *
     * @param directions directions that describe this navigation operation
     * @param navOptions special options for this navigation operation
     */
    @MainThread
    public open fun navigate(directions: NavDirections, navOptions: NavOptions?) {
        navigate(directions.actionId, directions.arguments, navOptions)
    }

    /**
     * Navigate via the given [NavDirections]
     *
     * @param directions directions that describe this navigation operation
     * @param navigatorExtras extras to pass to the [Navigator]
     */
    @MainThread
    public open fun navigate(directions: NavDirections, navigatorExtras: Navigator.Extras) {
        navigate(directions.actionId, directions.arguments, null, navigatorExtras)
    }

    @MainThread
    public actual fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit) {
        navigate(route, navOptions(builder))
    }

    @MainThread
    @JvmOverloads
    public actual fun navigate(
        route: String,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ) {
        requireNotNull(_graph) {
            "Cannot navigate to $route. Navigation graph has not been set for " +
                "NavController $this."
        }
        val currGraph = backQueue.getTopGraph()
        val deepLinkMatch =
            currGraph.matchRouteComprehensive(
                route,
                searchChildren = true,
                searchParent = true,
                lastVisited = currGraph
            )
        if (deepLinkMatch != null) {
            val destination = deepLinkMatch.destination
            val args = destination.addInDefaultArgs(deepLinkMatch.matchingArgs) ?: savedState()
            val node = deepLinkMatch.destination
            val intent =
                Intent().apply {
                    setDataAndType(createRoute(destination.route).toUri(), null)
                    action = null
                }
            args.write { putParcelable(KEY_DEEP_LINK_INTENT, intent) }
            navigate(node, args, navOptions, navigatorExtras)
        } else {
            throw IllegalArgumentException(
                "Navigation destination that matches route $route cannot be found in the " +
                    "navigation graph $_graph"
            )
        }
    }

    @MainThread
    public actual fun <T : Any> navigate(route: T, builder: NavOptionsBuilder.() -> Unit) {
        navigate(route, navOptions(builder))
    }

    @MainThread
    @JvmOverloads
    public actual fun <T : Any> navigate(
        route: T,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ) {
        navigate(generateRouteFilled(route), navOptions, navigatorExtras)
    }

    /**
     * Create a deep link to a destination within this NavController.
     *
     * @return a [NavDeepLinkBuilder] suitable for constructing a deep link
     */
    public open fun createDeepLink(): NavDeepLinkBuilder {
        return NavDeepLinkBuilder(this)
    }

    @CallSuper
    public actual open fun saveState(): SavedState? {
        var b: SavedState? = null
        val navigatorNames = ArrayList<String>()
        val navigatorState = savedState()
        for ((name, value) in _navigatorProvider.navigators) {
            val savedState = value.onSaveState()
            if (savedState != null) {
                navigatorNames.add(name)
                navigatorState.write { putSavedState(name, savedState) }
            }
        }
        if (navigatorNames.isNotEmpty()) {
            b = savedState {
                navigatorState.write { putStringList(KEY_NAVIGATOR_STATE_NAMES, navigatorNames) }
                putSavedState(KEY_NAVIGATOR_STATE, navigatorState)
            }
        }
        if (backQueue.isNotEmpty()) {
            if (b == null) {
                b = savedState()
            }
            val backStack = arrayOfNulls<Parcelable>(backQueue.size)
            var index = 0
            for (backStackEntry in this.backQueue) {
                backStack[index++] = NavBackStackEntryState(backStackEntry)
            }
            b.write { putParcelableList(KEY_BACK_STACK, backStack.toList().filterNotNull()) }
        }
        if (backStackMap.isNotEmpty()) {
            if (b == null) {
                b = savedState()
            }
            val backStackDestIds = IntArray(backStackMap.size)
            val backStackIds = ArrayList<String?>()
            var index = 0
            for ((destId, id) in backStackMap) {
                backStackDestIds[index++] = destId
                backStackIds += id
            }
            b.write {
                putIntArray(KEY_BACK_STACK_DEST_IDS, backStackDestIds)
                putStringList(KEY_BACK_STACK_IDS, backStackIds.toList().filterNotNull())
            }
        }
        if (backStackStates.isNotEmpty()) {
            if (b == null) {
                b = savedState()
            }
            val backStackStateIds = ArrayList<String>()
            for ((id, backStackStates) in backStackStates) {
                backStackStateIds += id
                val states = arrayOfNulls<Parcelable>(backStackStates.size)
                backStackStates.forEachIndexed { stateIndex, backStackState ->
                    states[stateIndex] = backStackState
                }
                b.write {
                    putParcelableList(
                        KEY_BACK_STACK_STATES_PREFIX + id,
                        states.toList().filterNotNull()
                    )
                }
            }
            b.write { putStringList(KEY_BACK_STACK_STATES_IDS, backStackStateIds) }
        }
        if (deepLinkHandled) {
            if (b == null) {
                b = savedState()
            }
            b.write { putBoolean(KEY_DEEP_LINK_HANDLED, deepLinkHandled) }
        }
        return b
    }

    @CallSuper
    public actual open fun restoreState(navState: SavedState?) {
        if (navState == null) {
            return
        }
        navState.classLoader = context.classLoader
        navState.read {
            navigatorStateToRestore =
                if (contains(KEY_NAVIGATOR_STATE)) {
                    getSavedState(KEY_NAVIGATOR_STATE)
                } else null
            backStackToRestore =
                if (contains(KEY_BACK_STACK)) {
                    getParcelableList<Parcelable>(KEY_BACK_STACK).toTypedArray()
                } else null
            backStackStates.clear()
            if (contains(KEY_BACK_STACK_DEST_IDS) && contains(KEY_BACK_STACK_IDS)) {
                val backStackDestIds = getIntArray(KEY_BACK_STACK_DEST_IDS)
                val backStackIds = getStringArray(KEY_BACK_STACK_IDS)
                backStackDestIds.forEachIndexed { index, id ->
                    backStackMap[id] = backStackIds[index]
                }
            }
            if (contains(KEY_BACK_STACK_STATES_IDS)) {
                val backStackStateIds = getStringArray(KEY_BACK_STACK_STATES_IDS)
                backStackStateIds.forEach { id ->
                    if (contains(KEY_BACK_STACK_STATES_PREFIX + id)) {
                        val backStackState =
                            getParcelableList<Parcelable>(KEY_BACK_STACK_STATES_PREFIX + id)
                        backStackStates[id] =
                            ArrayDeque<NavBackStackEntryState>(backStackState.size).apply {
                                for (parcelable in backStackState) {
                                    add(parcelable as NavBackStackEntryState)
                                }
                            }
                    }
                }
            }
            deepLinkHandled = getBooleanOrElse(KEY_DEEP_LINK_HANDLED) { false }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual open fun setLifecycleOwner(owner: LifecycleOwner) {
        if (owner == lifecycleOwner) {
            return
        }
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = owner
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun setOnBackPressedDispatcher(dispatcher: OnBackPressedDispatcher) {
        if (dispatcher == onBackPressedDispatcher) {
            return
        }
        val lifecycleOwner =
            checkNotNull(lifecycleOwner) {
                "You must call setLifecycleOwner() before calling setOnBackPressedDispatcher()"
            }
        // Remove the callback from any previous dispatcher
        onBackPressedCallback.remove()
        // Then add it to the new dispatcher
        onBackPressedDispatcher = dispatcher
        dispatcher.addCallback(lifecycleOwner, onBackPressedCallback)

        // Make sure that listener for updating the NavBackStackEntry lifecycles comes after
        // the dispatcher
        lifecycleOwner.lifecycle.apply {
            removeObserver(lifecycleObserver)
            addObserver(lifecycleObserver)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun enableOnBackPressed(enabled: Boolean) {
        enableOnBackPressedCallback = enabled
        updateOnBackPressedCallbackEnabled()
    }

    private fun updateOnBackPressedCallbackEnabled() {
        onBackPressedCallback.isEnabled =
            (enableOnBackPressedCallback && destinationCountOnBackStack > 1)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual open fun setViewModelStore(viewModelStore: ViewModelStore) {
        if (viewModel == NavControllerViewModel.getInstance(viewModelStore)) {
            return
        }
        check(backQueue.isEmpty()) { "ViewModelStore should be set before setGraph call" }
        viewModel = NavControllerViewModel.getInstance(viewModelStore)
    }

    /**
     * Gets the [ViewModelStoreOwner] for a NavGraph. This can be passed to
     * [androidx.lifecycle.ViewModelProvider] to retrieve a ViewModel that is scoped to the
     * navigation graph - it will be cleared when the navigation graph is popped off the back stack.
     *
     * @param navGraphId ID of a NavGraph that exists on the back stack
     * @throws IllegalStateException if called before the [NavHost] has called
     *   [NavHostController.setViewModelStore].
     * @throws IllegalArgumentException if the NavGraph is not on the back stack
     */
    public open fun getViewModelStoreOwner(@IdRes navGraphId: Int): ViewModelStoreOwner {
        checkNotNull(viewModel) {
            "You must call setViewModelStore() before calling getViewModelStoreOwner()."
        }
        val lastFromBackStack = getBackStackEntry(navGraphId)
        require(lastFromBackStack.destination is NavGraph) {
            "No NavGraph with ID $navGraphId is on the NavController's back stack"
        }
        return lastFromBackStack
    }

    /**
     * Gets the topmost [NavBackStackEntry] for a destination id.
     *
     * This is always safe to use with [the current destination][currentDestination] or
     * [its parent][NavDestination.parent] or grandparent navigation graphs as these destinations
     * are guaranteed to be on the back stack.
     *
     * @param destinationId ID of a destination that exists on the back stack
     * @throws IllegalArgumentException if the destination is not on the back stack
     */
    public open fun getBackStackEntry(@IdRes destinationId: Int): NavBackStackEntry {
        val lastFromBackStack: NavBackStackEntry? =
            backQueue.lastOrNull { entry -> entry.destination.id == destinationId }
        requireNotNull(lastFromBackStack) {
            "No destination with ID $destinationId is on the NavController's back stack. The " +
                "current destination is $currentDestination"
        }
        return lastFromBackStack
    }

    public actual fun getBackStackEntry(route: String): NavBackStackEntry {
        val lastFromBackStack: NavBackStackEntry? =
            backQueue.lastOrNull { entry -> entry.destination.hasRoute(route, entry.arguments) }
        requireNotNull(lastFromBackStack) {
            "No destination with route $route is on the NavController's back stack. The " +
                "current destination is $currentDestination"
        }
        return lastFromBackStack
    }

    public actual inline fun <reified T : Any> getBackStackEntry(): NavBackStackEntry =
        getBackStackEntry(T::class)

    @OptIn(InternalSerializationApi::class)
    public actual fun <T : Any> getBackStackEntry(route: KClass<T>): NavBackStackEntry {
        val id = route.serializer().generateHashCode()
        requireNotNull(graph.findDestinationComprehensive(id, true)) {
            "Destination with route ${route.simpleName} cannot be found in navigation " +
                "graph $graph"
        }
        val lastFromBackStack =
            currentBackStack.value.lastOrNull { entry -> entry.destination.id == id }
        requireNotNull(lastFromBackStack) {
            "No destination with route ${route.simpleName} is on the NavController's " +
                "back stack. The current destination is $currentDestination"
        }
        return lastFromBackStack
    }

    public actual fun <T : Any> getBackStackEntry(route: T): NavBackStackEntry {
        // route contains arguments so we need to generate the populated route
        // rather than getting entry based on route pattern
        val finalRoute = generateRouteFilled(route)
        return getBackStackEntry(finalRoute)
    }

    public actual open val currentBackStackEntry: NavBackStackEntry?
        get() = backQueue.lastOrNull()

    private val _currentBackStackEntryFlow: MutableSharedFlow<NavBackStackEntry> =
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    public actual val currentBackStackEntryFlow: Flow<NavBackStackEntry> =
        _currentBackStackEntryFlow.asSharedFlow()

    public actual open val previousBackStackEntry: NavBackStackEntry?
        get() {
            val iterator = backQueue.reversed().iterator()
            // throw the topmost destination away.
            if (iterator.hasNext()) {
                iterator.next()
            }
            return iterator.asSequence().firstOrNull { entry -> entry.destination !is NavGraph }
        }

    public actual companion object {
        private const val TAG = "NavController"
        private const val KEY_NAVIGATOR_STATE = "android-support-nav:controller:navigatorState"
        private const val KEY_NAVIGATOR_STATE_NAMES =
            "android-support-nav:controller:navigatorState:names"
        private const val KEY_BACK_STACK = "android-support-nav:controller:backStack"
        private const val KEY_BACK_STACK_DEST_IDS =
            "android-support-nav:controller:backStackDestIds"
        private const val KEY_BACK_STACK_IDS = "android-support-nav:controller:backStackIds"
        private const val KEY_BACK_STACK_STATES_IDS =
            "android-support-nav:controller:backStackStates"
        private const val KEY_BACK_STACK_STATES_PREFIX =
            "android-support-nav:controller:backStackStates:"
        @field:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val KEY_DEEP_LINK_IDS: String = "android-support-nav:controller:deepLinkIds"
        @field:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val KEY_DEEP_LINK_ARGS: String = "android-support-nav:controller:deepLinkArgs"
        @field:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @Suppress("IntentName")
        public const val KEY_DEEP_LINK_EXTRAS: String =
            "android-support-nav:controller:deepLinkExtras"
        @field:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public const val KEY_DEEP_LINK_HANDLED: String =
            "android-support-nav:controller:deepLinkHandled"

        /** The [Intent] that triggered a deep link to the current destination. */
        public const val KEY_DEEP_LINK_INTENT: String =
            "android-support-nav:controller:deepLinkIntent"

        private var deepLinkSaveState = true

        @JvmStatic
        @NavDeepLinkSaveStateControl
        public actual fun enableDeepLinkSaveState(saveState: Boolean) {
            deepLinkSaveState = saveState
        }
    }
}

/**
 * Construct a new [NavGraph]
 *
 * @param id the graph's unique id
 * @param startDestination the route for the start destination
 * @param builder the builder used to construct the graph
 */
@Suppress("Deprecation")
@Deprecated(
    "Use routes to create your NavGraph instead",
    ReplaceWith(
        "createGraph(startDestination = startDestination.toString(), route = id.toString()) " +
            "{ builder.invoke() }"
    )
)
public inline fun NavController.createGraph(
    @IdRes id: Int = 0,
    @IdRes startDestination: Int,
    builder: NavGraphBuilder.() -> Unit
): NavGraph = navigatorProvider.navigation(id, startDestination, builder)
