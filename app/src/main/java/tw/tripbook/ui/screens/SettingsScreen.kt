package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tw.tripbook.TripViewModel
import tw.tripbook.ui.components.SoftCard
import tw.tripbook.ui.theme.*

@Composable
fun SettingsScreen(vm: TripViewModel, onOpenTrip: (Long) -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var google by remember { mutableStateOf(runCatching { tw.tripbook.cloud.Cloud.isGoogle }.getOrDefault(false)) }
    var restorable by remember { mutableStateOf<List<tw.tripbook.cloud.CTrip>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(google) { if (google) restorable = runCatching { vm.ownerSync.restorable() }.getOrDefault(emptyList()) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp)) {
        Text("設定", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MintDeep)
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text("關於旅伴帳", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("版本 1.1.2\n出遊分帳，一筆算清楚。\n旅伴共享 · 離線記帳 · 現金流水 · 最少轉帳結算", color = InkSoft, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text("主帳號（Google）", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            if (!google) {
                Text("共享行程時用。換手機後登入同一個 Google，就能把共享的行程還原回來。", color = InkSoft, fontSize = 13.sp)
                TextButton(onClick = {
                    if (!busy) scope.launch {
                        busy = true; msg = null
                        runCatching { tw.tripbook.cloud.Cloud.signInGoogle(ctx) }
                            .onSuccess { google = true; vm.ownerSync.startAll() }
                            .onFailure { msg = "Google 登入沒有完成" }
                        busy = false
                    }
                }) { Text(if (busy) "登入中…" else "用 Google 登入", color = MintDeep, fontWeight = FontWeight.Bold) }
            } else {
                Text("已登入：${tw.tripbook.cloud.Cloud.googleEmail.orEmpty()}", color = InkSoft, fontSize = 13.sp)
                restorable.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("雲端：${c.name}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            if (!busy) scope.launch {
                                busy = true; msg = null
                                runCatching { vm.ownerSync.restore(c) }
                                    .onSuccess { id -> restorable = restorable - c; onOpenTrip(id) }
                                    .onFailure { msg = "還原失敗，請確認網路" }
                                busy = false
                            }
                        }) { Text("還原到這支手機", color = MintDeep, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            msg?.let { Text(it, color = PeachDeep, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(16.dp))
        Text("刪除行程", fontWeight = FontWeight.Bold, color = InkSoft)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(trips) { t ->
                SoftCard(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(t.name, fontWeight = FontWeight.Bold)
                            Text(t.place, color = InkSoft, fontSize = 12.sp)
                        }
                        TextButton(onClick = { confirmDelete = t.id }) { Text("刪除", color = PeachDeep) }
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("確定刪除？") },
            text = { Text("行程、支出、現金流水都會刪掉，無法復原。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTrip(id); confirmDelete = null }) { Text("刪除", color = PeachDeep) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}
