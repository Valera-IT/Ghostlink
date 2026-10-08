package io.github.valerait.ghostlink.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.valerait.ghostlink.R

@Composable
fun GhostLogo(modifier: Modifier = Modifier, size: Dp = 72.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(
        painter = painterResource(R.drawable.ic_ghost),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
