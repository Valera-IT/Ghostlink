package io.github.valerait.ghostlink.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valerait.ghostlink.R
import io.github.valerait.ghostlink.core.model.ChatId
import io.github.valerait.ghostlink.core.model.GroupInfo
import io.github.valerait.ghostlink.ui.common.Avatar

/** Создание группы (название + друзья) или добавление друзей в существующую группу. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickMembersScreen(
    onBack: () -> Unit,
    onDone: (chatId: ChatId) -> Unit,
    viewModel: PickMembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.doneChatId) {
        state.doneChatId?.let(onDone)
    }

    val canFinish = state.selected.isNotEmpty() && (state.addingToGroup || name.isNotBlank())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.addingToGroup) R.string.group_add_members else R.string.group_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (canFinish) {
                ExtendedFloatingActionButton(
                    onClick = { if (state.addingToGroup) viewModel.addSelected() else viewModel.create(name) },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text(stringResource(if (state.addingToGroup) R.string.group_add else R.string.group_create)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!state.addingToGroup) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(GroupInfo.NAME_MAX_LENGTH) },
                        label = { Text(stringResource(R.string.group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.group_pick_friends, state.slotsLeft),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!state.loading && state.friends.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.group_no_friends),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.friends, key = { it.id.value }) { friend ->
                val checked = friend.id in state.selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = checked || state.slotsLeft > 0) { viewModel.toggle(friend.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Avatar(friend.name, friend.id.value, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { viewModel.toggle(friend.id) },
                        enabled = checked || state.slotsLeft > 0,
                    )
                }
            }
        }
    }
}
