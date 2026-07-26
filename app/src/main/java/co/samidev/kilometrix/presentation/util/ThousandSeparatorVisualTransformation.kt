package co.samidev.kilometrix.presentation.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class ThousandSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        
        // Mantener solo dígitos y opcionalmente el punto decimal si lo necesitas,
        // pero para valores como dinero u odómetros en Colombia solemos usar enteros.
        // Si tienes decimales (ej. galones), este formatter podría necesitar ajustes.
        val cleanText = originalText.replace(Regex("[^\\d]"), "")
        if (cleanText.isEmpty()) return TransformedText(AnnotatedString(originalText), OffsetMapping.Identity)

        val number = cleanText.toLongOrNull() ?: 0L
        val symbols = DecimalFormatSymbols(Locale("es", "CO")).apply {
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,###", symbols)
        val formattedText = formatter.format(number)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset >= cleanText.length) return formattedText.length

                val separatorsBefore = (cleanText.length - 1) / 3
                val separatorsAfter = (cleanText.length - offset - 1) / 3
                return offset + (separatorsBefore - separatorsAfter)
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset >= formattedText.length) return cleanText.length
                
                var originalOffset = 0
                for (i in 0 until offset) {
                    if (formattedText[i].isDigit()) {
                        originalOffset++
                    }
                }
                return originalOffset
            }
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}
