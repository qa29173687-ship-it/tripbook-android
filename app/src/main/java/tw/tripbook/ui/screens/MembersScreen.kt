package tw.tripbook.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(vm: TripViewModel, tripId: Long, onBack: () -> Unit, onCash: (Long?) -> Unit) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    var newName by remember { mutableStateOf("") }
    var editCashFor by remember { mutableStateOf<Long?>(null) }
    var editPool by remember { mutableStateOf(false) }
    var cashInput by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("旅伴與現金", fontWeight = FontWeight.Black)
                        Text("每人身上現金可分開記", fontSize = 12.sp, color = InkSoft)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
        ) {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    GradientHeader(Modifier.clickable { onCash(null) }) {
                        Text("公款現金（大家一起的）", color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                        Text(Money.format(trip?.poolCashCents ?: 0, trip?.currency ?: "TWD"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        val holder = mems.firstOrNull { it.id == trip?.poolHolderMemberId }?.name ?: "未指定"
                        Text("$holder 保管 · 點一下看流水或調整", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        cashInput = (trip?.poolCashCents ?: 0).toString()
                        editPool = true
                    }) { Text("修改公款金額／保管人", color = MintDeep, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    Text("旅伴", fontWeight = FontWeight.Bold, color = InkSoft)
                    Spacer(Modifier.height(8.dp))
                }
                items(mems, key = { it.id }) { m ->
                    SoftCard(Modifier.padding(bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(m.name, m.colorIndex, 48)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).clickable { onCash(m.id) }) {
                                Text(m.name + if (m.isMe) "（我）" else "", fontWeight = FontWeight.Black, fontSize = 17.sp)
                                Text(if (m.id == trip?.poolHolderMemberId) "保管公款" else "旅伴", color = InkSoft, fontSize = 13.sp)
                                Text("身上現金 ${Money.format(m.cashCents, trip?.currency ?: "TWD")}", color = PeachDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "修改現金",
                                    Modifier
                                        .clickable {
                                            editCashFor = m.id
                                            cashInput = m.cashCents.toString()
                                        }
                                        .clip(RoundedCornerShape(50))
                                        .background(PeachSoft)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = PeachDeep,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!m.isMe) {
                                    IconButton(onClick = {
                                        vm.deleteMember(m.id) { ok ->
                                            Toast.makeText(
                                                ctx,
                                                if (ok) "已刪除 ${m.name}" else "無法刪除（可能還有支出紀錄，或只剩一人）",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "刪除旅伴", tint = InkSoft)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SoftCard {
                        Text(
                            "出國時可先輸入換到的現金。記支出選「現金」會自動扣除；儲值交通卡、自己花的用「現金流水」記，不會進入分帳。",
                            color = InkSoft,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            // Sticky add row above keyboard
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Cream)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("新增旅伴", fontWeight = FontWeight.Bold, color = InkSoft, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                AddMemberRow(
                    name = newName,
                    onNameChange = { newName = it },
                    onAdd = {
                        val n = newName.trim()
                        if (n.isEmpty()) return@AddMemberRow
                        vm.addMember(tripId, n)
                        newName = ""
                    }
                )
            }
        }
    }

    editCashFor?.let { mid ->
        AlertDialog(
            onDismissRequest = { editCashFor = null },
            title = { Text("設定身上現金") },
            text = {
                OutlinedTextField(
                    cashInput,
                    { cashInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("金額") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    cashInput.toLongOrNull()?.let { vm.setMemberCash(mid, it) }
                    editCashFor = null
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editCashFor = null }) { Text("取消") } }
        )
    }
    if (editPool) {
        var holder by remember { mutableStateOf(trip?.poolHolderMemberId) }
        AlertDialog(
            onDismissRequest = { editPool = false },
            title = { Text("公款設定") },
            text = {
                Column(Modifier.imePadding()) {
                    OutlinedTextField(
                        cashInput,
                        { cashInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("公款金額") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("保管人")
                    mems.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { holder = m.id }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = holder == m.id, onClick = { holder = m.id })
                            Text(m.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cashInput.toLongOrNull()?.let { vm.setPool(tripId, it, holder) }
                    editPool = false
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editPool = false }) { Text("取消") } }
        )
    }
}
