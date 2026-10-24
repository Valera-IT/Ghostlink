package io.github.valerait.ghostlink.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ghostlink.core.designsystem.generated.resources.Res
import ghostlink.core.designsystem.generated.resources.ic_ghost
import org.jetbrains.compose.resources.painterResource

@Composable
fun GhostLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_ghost),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
