package io.github.valerait.ghostlink.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.core.designsystem.GhostLogo
import io.github.valerait.ghostlink.core.designsystem.LogoTextStyle
import io.github.valerait.ghostlink.core.ui.DemoModeBanner
import io.github.valerait.ghostlink.core.ui.resources.Res
import io.github.valerait.ghostlink.core.ui.resources.onboarding_feature_no_phone
import io.github.valerait.ghostlink.core.ui.resources.onboarding_feature_qr
import io.github.valerait.ghostlink.core.ui.resources.onboarding_feature_realtime
import io.github.valerait.ghostlink.core.ui.resources.onboarding_have_account
import io.github.valerait.ghostlink.core.ui.resources.onboarding_link_device
import io.github.valerait.ghostlink.core.ui.resources.onboarding_name_hint
import io.github.valerait.ghostlink.core.ui.resources.onboarding_name_label
import io.github.valerait.ghostlink.core.ui.resources.onboarding_recovery_key
import io.github.valerait.ghostlink.core.ui.resources.onboarding_start
import io.github.valerait.ghostlink.core.ui.resources.onboarding_tagline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onLinkDevice: () -> Unit,
    onRecover: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.demoMode) DemoModeBanner()
            Spacer(Modifier.height(48.dp))
            GhostLogo(size = 96.dp)
            Spacer(Modifier.height(16.dp))
            Text("GHOSTLINK", style = LogoTextStyle, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onAction(OnboardingAction.NameChanged(it)) },
                label = { Text(stringResource(Res.string.onboarding_name_label)) },
                supportingText = { Text(stringResource(Res.string.onboarding_name_hint)) },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { viewModel.onAction(OnboardingAction.Submit) }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.onAction(OnboardingAction.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.onboarding_start))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.onboarding_have_account),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLinkDevice, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.onboarding_link_device))
            }
            TextButton(onClick = onRecover, enabled = !state.busy) {
                Text(stringResource(Res.string.onboarding_recovery_key))
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Feature(stringResource(Res.string.onboarding_feature_no_phone))
                Feature(stringResource(Res.string.onboarding_feature_qr))
                Feature(stringResource(Res.string.onboarding_feature_realtime))
            }
        }
    }
}

@Composable
private fun Feature(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
