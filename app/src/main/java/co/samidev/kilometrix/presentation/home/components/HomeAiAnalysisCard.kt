package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.*

@Composable
fun HomeAiAnalysisCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, Tertiary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🤖", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_ai_analysis_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TertiaryContainer
            ) {
                Text(
                    text = "✦ IA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Tertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.home_ai_analysis_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant
        )
    }
}
