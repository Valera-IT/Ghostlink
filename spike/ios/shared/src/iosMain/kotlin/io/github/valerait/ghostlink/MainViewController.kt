package io.github.valerait.ghostlink

import androidx.compose.ui.window.ComposeUIViewController
import io.github.valerait.ghostlink.app.App
import platform.UIKit.UIViewController

/** Точка входа для Swift: весь UI — Compose Multiplatform. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/** ghostlink://add/<id> из onOpenURL. */
@Suppress("unused")
fun openUrl(url: String) = io.github.valerait.ghostlink.app.handleUrl(url)
