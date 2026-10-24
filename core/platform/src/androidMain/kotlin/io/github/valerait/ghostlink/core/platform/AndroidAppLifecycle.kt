package io.github.valerait.ghostlink.core.platform

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** На экране хотя бы одна Activity приложения: ProcessLifecycleOwner в состоянии STARTED. */
internal class AndroidAppLifecycle : AppLifecycle {
    private val _isForeground = MutableStateFlow(false)
    override val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        // Наблюдателя можно добавить только с главного потока; там же сразу придёт текущее состояние.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        _isForeground.value = true
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        _isForeground.value = false
                    }
                },
            )
        }
    }
}
