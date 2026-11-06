package tw.tripbook.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(vm: TripViewModel, tripId: Long, onBack: () -> Unit, onCash: (Long?) -> Unit) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val currency = trip?.currency ?: "TWD"
    var newName by remember { mutableStateOf("") }
    var newCash by remember { mutableStateOf("") }
    var editStartFor by remember { mutableStateOf<Long?>(null) }
    var cashInput by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("旅伴與現金", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    SoftCard {
                        CashSummaryBlock(
                            remaining = trip?.poolCashCents ?: 0,
                            starting = trip?.poolStartingCashCents ?: trip?.poolCashCents ?: 0,
                            currency = currency,
                            remainingLabel = "公款剩餘現金"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("旅伴", fontWeight = FontWeight.Bold, color = InkSoft)
                    Spacer(Modifier.height(8.dp))
                }
                items(mems, key = { it.id }) { m ->
                    SoftCard(Modifier.padding(bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(m.name, m.colorIndex, 48)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (m.isMe) "${m.name}（本人）" else m.name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = Ink,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "剩餘現金 ${Money.format(m.cashCents, currency)}",
                                    color = MintDeep,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                Text(
                                    "初始 ${Money.format(m.startingCashCents, currency)}",
                                    color = InkSoft,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            TextButton(onClick = {
                                editStartFor = m.id
                                cashInput = m.startingCashCents.toString()
                            }) { Text("修改初始", color = PeachDeep, fontWeight = FontWeight.Bold) }
                            if (!m.isMe) {
                                IconButton(onClick = {
                                    vm.deleteMember(m.id) { ok ->
                                        Toast.makeText(
                                            ctx,
                                            if (ok) "已刪除" else "無法刪除（可能還有支出）",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) { Icon(Icons.Default.Close, null, tint = InkSoft) }
                            }
                        }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Cream)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("新增旅伴", fontWeight = FontWeight.Bold, color = InkSoft, fontSize = 13.sp)
                VisibleTextField(newName, { newName = it }, "旅伴名字")
                VisibleMoneyField(newCash, { newCash = it }, "他帶了多少現金（可留空）")
                MintButton("新增", Modifier.fillMaxWidth()) {
                    val n = newName.trim()
                    if (n.isEmpty()) return@MintButton
                    vm.addMember(tripId, n, newCash.toLongOrNull() ?: 0)
                    newName = ""; newCash = ""
                }
                Spacer(Modifier.height(120.dp))
            }
        }
    }

    editStartFor?.let { mid ->
        AlertDialog(
            onDismissRequest = { editStartFor = null },
            title = { Text("修改初始現金") },
            text = { VisibleMoneyField(cashInput, { cashInput = it }, "初始現金") },
            confirmButton = {
                TextButton(onClick = {
                    cashInput.toLongOrNull()?.let { vm.setStartingCash(mid, it) }
                    editStartFor = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editStartFor = null }) { Text("取消") } }
        )
    }
}
