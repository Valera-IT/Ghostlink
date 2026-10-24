package io.github.valerait.ghostlink.core.platform

import org.koin.core.module.Module

/** [SecureStore], [AppLifecycle], [DeepLinks] и [io.github.valerait.ghostlink.core.model.ThisDevice] этой платформы. */
expect val platformModule: Module
