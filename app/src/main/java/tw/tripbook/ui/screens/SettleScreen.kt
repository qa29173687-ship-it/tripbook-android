package tw.tripbook.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.domain.Transfer
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money

@Composable
fun SettlePickScreen(vm: TripViewModel, onOpen: (Long) -> Unit) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Cream).padding(20.dp)) {
        Text("結算", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MintDeep)
        Text("選一個行程看誰該轉給誰", color = InkSoft)
        Spacer(Modifier.height(16.dp))
        trips.forEach { t ->
            SoftCard(Modifier.padding(bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(t.name, fontWeight = FontWeight.Black)
                        Text(if (t.settled) "已結清" else "進行中", color = if (t.settled) MintDeep else PeachDeep, fontSize = 13.sp)
                    }
                    TextButton(onClick = { onOpen(t.id) }) { Text("查看", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleScreen(vm: TripViewModel, tripId: Long, onBack: () -> Unit) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val exps by vm.expenses(tripId).collectAsStateWithLifecycle(emptyList())
    var transfers by remember { mutableStateOf<List<Transfer>>(emptyList()) }
    LaunchedEffect(exps, mems) { transfers = vm.transfers(tripId) }
    val ctx = LocalContext.current
    val total = exps.sumOf { it.amountCents }
    val nameOf = { id: Long -> mems.firstOrNull { it.id == id }?.name ?: "?" }
    val chat = buildString {
        appendLine("【${trip?.name ?: "行程"} 結算】")
        if (transfers.isEmpty()) appendLine("已結清，沒有需要轉的帳。")
        else transfers.forEach { appendLine("${nameOf(it.fromMemberId)} 轉給 ${nameOf(it.toMemberId)} ${Money.format(it.amountCents, trip?.currency ?: "TWD")}") }
        appendLine("—")
        mems.forEach { appendLine("${it.name}初始現金 ${Money.format(it.startingCashCents, trip?.currency ?: "TWD")}") }
        appendLine("公款初始 ${Money.format(trip?.poolStartingCashCents ?: trip?.poolCashCents ?: 0)}（${mems.firstOrNull { it.id == trip?.poolHolderMemberId }?.name ?: "未指定"}保管）可現場發還或折抵。")
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("結算", fontWeight = FontWeight.Black)
                        Text("${trip?.name.orEmpty()} · 最少 ${transfers.size} 筆轉帳", fontSize = 12.sp, color = InkSoft)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                GradientHeader(peach = true) {
                    Text("算清楚了！", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (transfers.isEmpty()) "沒有未結清的帳" else "只要轉 ${transfers.size} 筆就結清", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("總支出 ${Money.format(total)} · ${mems.size} 人", color = Color.White.copy(alpha = 0.95f))
                }
                Spacer(Modifier.height(12.dp))
                SoftCard {
                    Text("初始現金", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mems.forEach { m ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Cream).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(m.name, fontSize = 11.sp, color = InkSoft, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(Money.format(m.startingCashCents), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MintSoft).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("公款初始", fontSize = 11.sp, color = MintDeep, fontWeight = FontWeight.Bold)
                            Text(Money.format(trip?.poolStartingCashCents ?: trip?.poolCashCents ?: 0), fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("建議轉帳", fontWeight = FontWeight.Bold, color = InkSoft)
                Spacer(Modifier.height(8.dp))
            }
            if (transfers.isEmpty()) {
                item { SoftCard { Text("目前沒有需要轉的帳，真棒！", color = MintDeep, fontWeight = FontWeight.Bold) } }
            }
            items(transfers.size) { i ->
                val t = transfers[i]
                val from = mems.first { it.id == t.fromMemberId }
                val to = mems.first { it.id == t.toMemberId }
                SoftCard(Modifier.padding(bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MemberAvatar(from.name, from.colorIndex, 48)
                            Text(from.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Money.format(t.amountCents), color = MintDeep, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("轉給 ›", color = InkSoft, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MemberAvatar(to.name, to.colorIndex, 48)
                            Text(to.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            item {
                SoftCard {
                    Text("複製到聊天室預覽", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(chat, color = InkSoft, fontSize = 13.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(12.dp))
                MintButton("📋 複製到聊天室", Modifier.fillMaxWidth()) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("settle", chat))
                    Toast.makeText(ctx, "已複製", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.markSettled(tripId, !(trip?.settled ?: false)) },
                    Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (trip?.settled == true) "取消已結清標記" else "標記為已結清") }
            }
        }
    }
}
