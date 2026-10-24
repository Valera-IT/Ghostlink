package io.github.valerait.ghostlink.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

/**
 * Одноразовые события ViewModel (переход на другой экран и т. п.). В отличие от UiState, событие
 * не должно повториться после поворота экрана, поэтому ViewModel отдаёт их из Channel, а экран забирает здесь.
 */
@Composable
fun <E> CollectEffects(
    effects: Flow<E>,
    onEffect: (E) -> Unit,
) {
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) {
        effects.collect { currentOnEffect(it) }
    }
}
