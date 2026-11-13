package tw.tripbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.tripbook.TripViewModel
import tw.tripbook.cloud.CTrip
import tw.tripbook.cloud.Cloud
import tw.tripbook.cloud.JoinedTrip
import tw.tripbook.domain.SharedView
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*

/** 旅伴：輸入邀請碼 → 選「我是誰」→ 進入共享旅程。免登入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(vm: TripViewModel, onBack: () -> Unit, onJoined: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<CTrip?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("加入旅伴的旅程", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val t = found
            if (t == null) {
                Text("輸入主帳號給你的邀請碼，不用登入。", color = InkSoft)
                VisibleTextField(code, { code = it.uppercase() }, "邀請碼", placeholder = "例如 8K3Q-TR")
                MintButton(if (busy) "查詢中…" else "下一步", Modifier.fillMaxWidth()) {
                    val c = SharedView.normalizeCode(code)
                    if (c.length != 6 || busy) { error = "邀請碼是 6 個英文或數字"; return@MintButton }
                    scope.launch {
                        busy = true; error = null
                        runCatching { Cloud.ensureSignedIn(); Cloud.lookupInvite(c) }
                            .onSuccess { if (it == null) error = "找不到這個邀請碼，請再確認一次" else found = it }
                            .onFailure { error = "連不上網路，請再試一次" }
                        busy = false
                    }
                }
            } else {
                SoftCard {
                    Text(t.name, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text("${t.ownerName} 邀請你一起記帳", color = InkSoft)
                }
                Text("你是哪一位？", fontWeight = FontWeight.Bold)
                t.members.filter { !it.isOwner }.forEach { m ->
                    SoftCard(Modifier.clickable(enabled = !busy) {
                        scope.launch {
                            busy = true; error = null
                            runCatching { Cloud.join(t, m.id, m.name) }
                                .onSuccess {
                                    vm.shareStore.addJoined(JoinedTrip(t.id, m.id, m.name, t.name))
                                    vm.refreshJoined()
                                    onJoined(t.id)
                                }
                                .onFailure { error = "加入失敗，請再試一次" }
                            busy = false
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(m.name, 0, 36)
                            Spacer(Modifier.width(12.dp))
                            Text("我是${m.name}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
                if (t.members.none { !it.isOwner }) Text("主帳號還沒加旅伴，請他先在「旅伴」頁把你加進去。", color = PeachDeep)
                TextButton(onClick = { found = null }) { Text("換一個邀請碼") }
            }
            error?.let { Text(it, color = PeachDeep) }
        }
    }
}
