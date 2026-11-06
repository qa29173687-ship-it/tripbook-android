package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.domain.*
import tw.tripbook.ui.components.GradientHeader
import tw.tripbook.ui.components.MintButton
import tw.tripbook.ui.components.Pill
import tw.tripbook.ui.components.SoftCard
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashTimelineScreen(vm: TripViewModel, tripId: Long, memberId: Long?, onBack: () -> Unit) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val member = mems.firstOrNull { it.id == memberId }
    val isPool = memberId == null
    val title = if (isPool) "公款現金流水" else "${member?.name ?: ""}的現金流水"
    val balance = if (isPool) trip?.poolCashCents ?: 0 else member?.cashCents ?: 0
    var entries by remember { mutableStateOf<List<CashTimelineEntry>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(trip?.poolCashCents, mems, memberId) {
        entries = vm.timeline(tripId, memberId)
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = if (isPool) MintDeep else PeachDeep,
                contentColor = Color.White,
                text = { Text("收入／扣錢", fontWeight = FontWeight.Bold) },
                icon = { Text("＋") }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                GradientHeader(peach = !isPool) {
                    Text(if (isPool) "公款目前餘額" else "身上現金", color = Color.White.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text(Money.format(balance, trip?.currency ?: "TWD"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("一目了然 · 儲值／換匯／提款／自己花不會進入分帳", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("現金流水", fontWeight = FontWeight.Bold, color = InkSoft)
                Spacer(Modifier.height(8.dp))
            }
            if (entries.isEmpty()) {
                item { SoftCard { Text("還沒有現金紀錄。可點右下角記一筆收入或扣錢。", color = InkSoft) } }
            }
            items(entries, key = { it.id }) { e ->
                val inDir = e.direction == CashDirection.IN
                SoftCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (inDir) MintSoft else PeachSoft),
                            contentAlignment = Alignment.Center
                        ) { Text(if (inDir) "＋" else "−", color = if (inDir) MintDeep else PeachDeep, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.kind.label + if (e.note.isNotBlank()) " · ${e.note}" else "", fontWeight = FontWeight.Bold)
                            val fmt = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.of("Asia/Taipei"))
                            Text(fmt.format(Instant.ofEpochMilli(e.occurredAt)), color = InkSoft, fontSize = 12.sp)
                            Text("餘額 ${Money.format(e.balanceAfterCents)}", color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            (if (inDir) "+" else "−") + Money.format(e.amountCents),
                            color = if (inDir) MintDeep else PeachDeep,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        CashMovementDialog(
            isPool = isPool,
            onDismiss = { showAdd = false },
            onSave = { dir, kind, amount, note ->
                vm.addCashMovement(tripId, memberId, dir, kind, amount, note) { showAdd = false }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashMovementDialog(
    isPool: Boolean,
    onDismiss: () -> Unit,
    onSave: (CashDirection, CashKind, Long, String) -> Unit
) {
    var direction by remember { mutableStateOf(CashDirection.IN) }
    var kind by remember { mutableStateOf(CashKind.ATM) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val kindsIn = listOf(CashKind.ATM, CashKind.EXCHANGE, CashKind.OTHER)
    val kindsOut = listOf(CashKind.TOP_UP, CashKind.PERSONAL, CashKind.OTHER)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cream
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (isPool) "公款收入／扣錢" else "現金收入／扣錢", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("收入／補錢", direction == CashDirection.IN) {
                    direction = CashDirection.IN; kind = CashKind.ATM
                }
                Pill("支出／扣錢", direction == CashDirection.OUT, mint = false) {
                    direction = CashDirection.OUT; kind = CashKind.TOP_UP
                }
            }
            Text("類型", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (direction == CashDirection.IN) kindsIn else kindsOut).forEach { k ->
                    Pill(k.label, kind == k, mint = direction == CashDirection.IN) { kind = k }
                }
            }
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() } }, label = { Text("金額") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("備註（例如：儲值交通卡）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, Modifier.weight(1f).height(48.dp)) { Text("取消") }
                MintButton("儲存", Modifier.weight(1.2f)) {
                    val a = amount.toLongOrNull() ?: return@MintButton
                    if (a <= 0) return@MintButton
                    onSave(direction, kind, a, note.ifBlank { kind.label })
                }
            }
        }
    }
}
