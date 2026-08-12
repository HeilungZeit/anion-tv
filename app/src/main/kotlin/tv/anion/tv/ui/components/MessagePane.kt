package tv.anion.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.anion.tv.R

@Composable
fun MessagePane(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.anion_logo),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(CircleShape),
        )
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) {
            Button(onClick = onAction) { Text(action) }
        }
    }
}
