package tw.tripbook.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
fun MembersScreen(
    vm: TripViewModel,
    tripId: Long,
    onBack: () -> Unit,
    onPerson: (Long) -> Unit
) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val currency = trip?.currency ?: "TWD"
    var newName by remember { mutableStateOf("") }
    var newCash by remember { mutableStateOf("") }
    var editStartFor by remember { mutableStateOf<Long?>(null) }
    var cashInput by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text("旅伴與現金", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { pad ->
        // Single scrollable list: companions stay visible; add fields are last items and sit above IME via imePadding.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text("旅伴", fontWeight = FontWeight.Bold, color = InkSoft)
                Spacer(Modifier.height(8.dp))
            }
            items(mems, key = { it.id }) { m ->
                SoftCard(Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clickable { onPerson(m.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
            item {
                Spacer(Modifier.height(8.dp))
                SoftCard {
                    Text("新增旅伴", fontWeight = FontWeight.Bold, color = InkSoft, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    VisibleTextField(newName, { newName = it }, "旅伴名字")
                    Spacer(Modifier.height(8.dp))
                    VisibleMoneyField(newCash, { newCash = it }, "他帶了多少現金（可留空）")
                    Spacer(Modifier.height(10.dp))
                    MintButton("新增", Modifier.fillMaxWidth()) {
                        val n = newName.trim()
                        if (n.isEmpty()) return@MintButton
                        vm.addMember(tripId, n, newCash.toLongOrNull() ?: 0)
                        newName = ""; newCash = ""
                    }
                }
                // Modest spacer so last field can clear a tall IME without collapsing the list
                Spacer(Modifier.height(48.dp))
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
