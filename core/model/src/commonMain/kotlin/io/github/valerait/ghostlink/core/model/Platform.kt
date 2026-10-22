package io.github.valerait.ghostlink.core.model

/** На чём запущено устройство. Как это хранится в базе, решает слой data. */
enum class Platform {
    Android,
    Ios,
    MacOs,
    Windows,
    Linux,
    Web,
    Unknown,
}
