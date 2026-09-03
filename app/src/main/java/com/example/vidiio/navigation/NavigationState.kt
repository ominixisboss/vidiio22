package com.example.vidiio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Create a navigation state that persists config changes and process death.
 */
@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>
): NavigationState {

    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }

    val allRoutes = remember(startRoute, topLevelRoutes) { topLevelRoutes + startRoute }
    val backStacks: Map<NavKey, NavBackStack<NavKey>> = allRoutes.associateWith { key ->
        rememberNavBackStack(key)
    }

    val startStack = backStacks[startRoute]
    if (startStack != null && startStack.isEmpty()) {
        startStack.add(startRoute)
    }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

/**
 * State holder for navigation state.
 *
 * @param startRoute - the start route. The user will exit the app through this route.
 * @param topLevelRoute - the current top level route
 * @param backStacks - the back stacks for each top level route
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey by topLevelRoute
    val stacksInUse: List<NavKey>
        get() = listOf(topLevelRoute)

    init {
        val startStack = backStacks[startRoute]
        if (startStack != null && startStack.isEmpty()) {
            startStack.add(startRoute)
        }
    }
}

/**
 * Convert NavigationState into NavEntries.
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {
    val startStack = backStacks[startRoute]
    if (startStack != null && startStack.isEmpty()) {
        startStack.add(startRoute)
    }

    val decoratedEntries = backStacks.mapValues { (key, stack) ->
        if (stack.isEmpty()) {
            stack.add(key)
        }
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }

    val entries = stacksInUse.flatMap { key ->
        decoratedEntries[key] ?: emptyList()
    }

    return if (entries.isEmpty()) {
        listOf(entryProvider(startRoute)).toMutableStateList()
    } else {
        entries.toMutableStateList()
    }
}
