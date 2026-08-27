package co.samidev.kilometrix.presentation.main.components

import co.samidev.kilometrix.R

enum class MainTab(val labelRes: Int, val emoji: String) {
    HOME(R.string.nav_home, "🏠"),
    TRANSACTIONS(R.string.nav_transactions, "💵"),
    VEHICLE(R.string.nav_vehicle, "🚗"),
    ANALYTICS(R.string.nav_analytics, "📊"),
    PROFILE(R.string.nav_profile, "👤"),
}
