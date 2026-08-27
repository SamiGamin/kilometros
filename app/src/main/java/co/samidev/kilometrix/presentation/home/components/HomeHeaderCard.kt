package co.samidev.kilometrix.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samidev.kilometrix.R
import co.samidev.kilometrix.ui.theme.OnSurface
import co.samidev.kilometrix.ui.theme.OnSurfaceVariant
import co.samidev.kilometrix.ui.theme.Primary

@Composable
fun HomeHeaderCard(
    userName: String,
    currentDateText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_greeting, userName),
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface
            )
            Text(
                text = currentDateText,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }

        val initial = userName.firstOrNull()?.toString()?.uppercase() ?: "S"
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
