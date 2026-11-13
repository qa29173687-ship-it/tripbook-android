package tw.tripbook.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import tw.tripbook.TripViewModel
import tw.tripbook.cloud.Cloud
import tw.tripbook.domain.SharedView
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*

/** 主帳號：開啟共享、拿邀請碼、看誰加入了。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(vm: TripViewModel, tripId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    var cloudId by remember { mutableStateOf(vm.cloudIdFor(tripId)) }
    var busy by remember { mutableStateOf(false) }
    var google by remember { mutableStateOf(runCatching { Cloud.isGoogle }.getOrDefault(false)) }
    var error by remember { mutableStateOf<String?>(null) }
    val cTrip by remember(cloudId) { cloudId?.let { Cloud.tripFlow(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)
    val joins by remember(cloudId) { cloudId?.let { Cloud.joinsFlow(it) } ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val code = cTrip?.inviteCode.orEmpty()
    val shareText = SharedView.inviteShareText(trip?.name.orEmpty(), code)

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("旅伴共享", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (cloudId == null) {
                SoftCard {
                    Text("開啟後，旅伴輸入邀請碼就能加入，不用登入。", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "・每個人都能寫自己要買的東西（可拍照），也能幫別人找\n" +
                            "・旅伴看得到自己花了多少、剩多少現金、要付誰多少，但不能改\n" +
                            "・支出、分帳、現金只有你能改\n" +
                            "・你這趟的「我要買」會一起搬到共享清單",
                        color = InkSoft, fontSize = 14.sp, lineHeight = 22.sp
                    )
                }
                if (!google) {
                    Text("主帳號要先用 Google 登入，換手機也能接回來。旅伴不用登入。", color = InkSoft, fontSize = 14.sp)
                    MintButton(if (busy) "登入中…" else "用 Google 登入", Modifier.fillMaxWidth()) {
                        if (!busy) scope.launch {
                            busy = true; error = null
                            runCatching { Cloud.signInGoogle(ctx) }
                                .onSuccess { google = true; vm.ownerSync.startAll() }
                                .onFailure { error = "Google 登入沒有完成，請再試一次" }
                            busy = false
                        }
                    }
                } else {
                Text("已用 Google 登入：${Cloud.googleEmail.orEmpty()}", color = InkSoft, fontSize = 13.sp)
                MintButton(if (busy) "開啟中…" else "開啟共享", Modifier.fillMaxWidth()) {
                    if (!busy) scope.launch {
                        busy = true; error = null
                        runCatching { vm.share(tripId) }
                            .onSuccess { cloudId = it }
                            .onFailure { error = "開啟失敗：請確認有網路後再試一次" }
                        busy = false
                    }
                }
                }
            } else {
                SoftCard {
                    Text("邀請碼", color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        if (code.isEmpty()) "讀取中…" else SharedView.displayCode(code),
                        fontSize = 40.sp, fontWeight = FontWeight.Black, color = MintDeep, letterSpacing = 4.sp
                    )
                    if (code.isNotEmpty()) Text(
                        SharedView.joinUrl(code),
                        color = InkSoft, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clip.setText(AnnotatedString(shareText)) }) { Text("複製邀請") }
                        Button(
                            onClick = {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText)
                                }, "傳給旅伴"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintDeep)
                        ) { Text("傳給旅伴") }
                    }
                }
                Text("誰加入了", color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SoftCard {
                    mems.forEach { m ->
                        val joined = m.isMe || joins.any { it.memberId == vm.ownerSync.cloudMemberId(tripId, m.id) }
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(m.name, m.colorIndex, 32)
                            Spacer(Modifier.width(10.dp))
                            Text(m.name + if (m.isMe) "（你・主帳號 👑）" else "", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(
                                if (joined) "已加入" else "還沒加入",
                                color = if (joined) MintDeep else InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    "旅伴買到東西打勾後，你的手機打開旅伴帳就會自動記成一筆支出（付款人是他），你之後可以修改。",
                    color = InkSoft, fontSize = 13.sp, lineHeight = 20.sp
                )
            }
            error?.let { Text(it, color = PeachDeep) }
        }
    }
}
