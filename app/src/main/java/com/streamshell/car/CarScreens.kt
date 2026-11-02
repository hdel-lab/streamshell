package com.streamshell.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate

/**
 * The car screen: StreamShell's own rail and play bar are drawn on the surface ([CarBrowser]). Android Auto requires an
 * action strip on this template, so it keeps one button only — "Parked" while the car gives no speed, else Search —
 * plus the pan button (✥) for dragging the page.
 */
class CarBrowserScreen(
    carContext: CarContext,
    private val browser: CarBrowser,
    private val gate: ParkGate,
    /** "Parked" was tapped: what changed (null = refused). */
    private val onParked: (ParkGate.Change?) -> Unit = {}
) : Screen(carContext) {
    init {
        browser.onSearchRequested = { openSearch() }
    }

    private fun openSearch() {
        screenManager.push(CarSearchScreen(carContext, "Search ${browser.source.title}") { q -> browser.search(q) })
    }

    override fun onGetTemplate(): Template {
        val only = if (gate.needsConfirmation) {
            Action.Builder().setTitle("Parked").setOnClickListener {
                val change = gate.confirm()
                onParked(change)
                if (change != null) {
                    browser.refresh()
                    invalidate()
                }
            }.build()
        } else {
            Action.Builder().setTitle("Search").setOnClickListener { openSearch() }.build()
        }
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(only).build())
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .build()
    }
}

/** YouTube search with the car's keyboard (Android Auto offers it only while parked). */
class CarSearchScreen(carContext: CarContext, private val hint: String, private val onSearch: (String) -> Unit) : Screen(carContext) {
    override fun onGetTemplate(): Template = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
        override fun onSearchSubmitted(searchText: String) {
            onSearch(searchText)
            screenManager.pop()
        }
    })
        .setHeaderAction(Action.BACK)
        .setSearchHint(hint)
        .setShowKeyboardByDefault(true)
        .setItemList(ItemList.Builder().setNoItemsMessage("Type and press search").build())
        .build()
}
