package co.samidev.kilometrix.presentation.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val VERIFY_EMAIL = "verify_email/{email}"
    const val FORGOT_PASSWORD = "forgot_password"
    const val SETUP = "setup"
    const val MAIN = "main"
    const val PICO_Y_PLACA = "pico_y_placa"

    fun verifyEmail(email: String) = "verify_email/$email"
}
