package io.github.valerait.ghostlink.ui.mycode

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val QrInk = Color(0xFF0E0F14)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCodeScreen(onBack: () -> Unit, onScan: () -> Unit, viewModel: MyCodeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareText = stringResource(R.string.my_code_share_text, state.link)
    val shareTitle = stringResource(R.string.my_code_share)

    // QR рисуем всегда тёмным по белому: камеры хуже читают инвертированные коды.
    val qr by produceState<ImageBitmap?>(null, state.link) {
        value = withContext(Dispatchers.Default) {
            createQrBitmap(state.link, 720, QrInk.toArgb(), Color.White.toArgb())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_code_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            ) {
                Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    qr?.let { Image(it, contentDescription = stringResource(R.string.my_code_qr_description)) }
                    // Призрак в центре: коррекция ошибок M спокойно переживает такую «дырку».
                    Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(56.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ghost),
                            contentDescription = null,
                            tint = QrInk,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(state.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.my_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_scan_friend))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(shareTitle)
            }
        }
    }
}
