package net.ustyle.app.scheduleimager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import net.ustyle.app.scheduleimager.auth.AuthManager
import net.ustyle.app.scheduleimager.data.ScheduleEvent
import net.ustyle.app.scheduleimager.data.ScheduleRefresher
import net.ustyle.app.scheduleimager.data.ScheduleSnapshot
import net.ustyle.app.scheduleimager.data.ScheduleStore
import net.ustyle.app.scheduleimager.widget.ScheduleWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ScheduleScreen(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleScreen(activity: ComponentActivity) {
    val context = activity.applicationContext
    val auth = remember { AuthManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<ScheduleSnapshot?>(null) }
    var signedIn by remember { mutableStateOf(false) }
    var accountLabel by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun sync() {
        isBusy = true
        try {
            snapshot = ScheduleRefresher.refresh(context)
            signedIn = auth.isSignedIn()
            accountLabel = auth.currentAccount()?.username
            ScheduleWidget().updateAll(context)
        } finally {
            isBusy = false
        }
    }

    LaunchedEffect(Unit) {
        snapshot = ScheduleStore.load(context)
        signedIn = auth.isSignedIn()
        accountLabel = auth.currentAccount()?.username
        if (signedIn) sync()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { scope.launch { sync() } }, enabled = !isBusy) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("更新")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!auth.isConfigured) {
                Text(
                    "res/raw/msal_config.json に Azure AD の Client ID を設定してください。詳しい手順は README.md を参照してください。",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (signedIn) {
                accountLabel?.let {
                    Text("サインイン中: $it", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        scope.launch {
                            auth.signOut()
                            signedIn = false
                            accountLabel = null
                            val cleared = ScheduleSnapshot(emptyList(), System.currentTimeMillis(), false)
                            ScheduleStore.save(context, cleared)
                            snapshot = cleared
                            ScheduleWidget().updateAll(context)
                        }
                    },
                ) { Text("サインアウト") }
            } else {
                Button(
                    enabled = auth.isConfigured && !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            try {
                                auth.signIn(activity)
                                errorMessage = null
                                sync()
                            } catch (e: Exception) {
                                errorMessage = "サインインに失敗しました: ${e.message}"
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                ) { Text("Outlookでサインイン") }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("今後の予定", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val events = snapshot?.events.orEmpty()
            if (events.isEmpty()) {
                Text("予定はありません", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(events, key = { it.id }) { event ->
                        EventRow(event)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: ScheduleEvent) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.JAPANESE).withZone(ZoneId.systemDefault())
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(event.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (event.isAllDay) "終日" else formatter.format(Instant.ofEpochMilli(event.start)),
            style = MaterialTheme.typography.bodySmall,
        )
        event.location?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
