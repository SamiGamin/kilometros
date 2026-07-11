package co.samidev.kilometrix.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import co.samidev.kilometrix.R

// ── Google Fonts provider setup ────────────────────────────────────────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Plus Jakarta Sans — primary humanist typeface
private val plusJakartaSans = GoogleFont("Plus Jakarta Sans")
val PlusJakartaSansFontFamily = FontFamily(
    Font(googleFont = plusJakartaSans, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaSans, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaSans, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = plusJakartaSans, fontProvider = provider, weight = FontWeight.Bold),
)

// JetBrains Mono — monospaced for data/labels
private val jetBrainsMono = GoogleFont("JetBrains Mono")
val JetBrainsMonoFontFamily = FontFamily(
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.SemiBold),
)

// ── Typography scale ───────────────────────────────────────────────────────────
val AppTypography = Typography(
    // display-lg: 32sp / 700 / -0.02em (for hero numbers)
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.64).sp
    ),
    // headline-md: 24sp / 700 / -0.01em
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.24).sp
    ),
    // headline-sm: 20sp / 600
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // title-lg for section headers
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // body-lg: 16sp / 400
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // body-md: 14sp / 400
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    // label-md: JetBrains Mono / 12sp / 500 / 0.05em (for data values)
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    ),
    // label-sm: JetBrains Mono / 10sp / 500 / 0.05em (for small labels)
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
)