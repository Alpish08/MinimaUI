package com.minimaui.app.model

/** The pages the custom wave can show behind, in the order of the Customize screen's pages. */
enum class WavePage(val title: String) { AllGames("All Games"), Apps("Apps"), Favorites("Favorites") }

/** How the custom wave shows behind one page: [blur] and [brightness] in % (a brightness of 0 doesn't draw it there at all). */
data class WavePageLook(val blur: Int, val brightness: Int) {
    val shown: Boolean get() = brightness > 0
}
