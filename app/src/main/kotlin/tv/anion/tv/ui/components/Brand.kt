package tv.anion.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.anion.tv.R

@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.anion_logo),
                contentDescription = "anion",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
            Text(
                "anion",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    "TV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

/** Компактный пункт верхней TV-навигации с хорошо читаемым D-pad фокусом. */
@Composable
fun HeaderAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = pill, focusedShape = pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.045f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
            pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .height(3.dp)
                .fillMaxWidth(0.035f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            title,
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
