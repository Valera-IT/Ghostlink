package io.github.valerait.ghostlink.feature.link

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Новое устройство показывает QR и ждёт подтверждения (граф до входа). */
@Serializable data object LinkReceiveRoute

/** code — отсканированная ссылка ghostlink://link/…; разбирает её LinkApproveViewModel. */
@Serializable data class LinkApproveRoute(
    val code: String,
)

fun NavGraphBuilder.linkReceiveScreen(navController: NavController) {
    composable<LinkReceiveRoute> { LinkReceiveScreen(onBack = navController::navigateUp) }
}

fun NavGraphBuilder.linkApproveScreen(navController: NavController) {
    composable<LinkApproveRoute> { LinkApproveScreen(onClose = navController::navigateUp) }
}

fun NavController.navigateToLinkReceive() = navigate(LinkReceiveRoute)

fun NavController.navigateToLinkApprove(code: String) = navigate(LinkApproveRoute(code))

val linkModule =
    module {
        viewModelOf(::LinkReceiveViewModel)
        viewModelOf(::LinkApproveViewModel)
    }
