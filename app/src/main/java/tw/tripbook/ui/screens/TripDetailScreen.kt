package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import kotlinx.coroutines.launch
import tw.tripbook.TripViewModel
import tw.tripbook.data.ExpenseEntity
import tw.tripbook.domain.*
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Dates
import tw.tripbook.util.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    vm: TripViewModel,
    tripId: Long,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettle: () -> Unit,
    onMembers: () -> Unit,
    onEditTrip: () -> Unit,
    onExpense: (Long) -> Unit,
    onPerson: (Long) -> Unit
) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val exps by vm.expenses(tripId).collectAsStateWithLifecycle(emptyList())
    var balances by remember { mutableStateOf<List<MemberBalance>>(emptyList()) }
    var splitNames by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var pendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(exps, mems) {
        balances = vm.balances(tripId)
        val map = mutableMapOf<Long, List<String>>()
        for (e in exps) {
            val splits = vm.splitsFor(e.id)
            map[e.id] = splits.mapNotNull { s -> mems.firstOrNull { it.id == s.memberId }?.name }
        }
        splitNames = map
    }
    val me = mems.firstOrNull { it.isMe } ?: mems.firstOrNull()
    val currency = trip?.currency ?: "TWD"
    val zone = ZoneId.of("Asia/Taipei")
    val dayFmt = DateTimeFormatter.ofPattern("M/d EEE").withZone(zone)
    val grouped = exps.sortedBy { it.occurredAt }.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(onClick = onEditTrip)) {
                        Text(trip?.name ?: "行程", fontWeight = FontWeight.Black)
                        Text(
                            "${Dates.toDisplay(trip?.startDate.orEmpty())} – ${Dates.toDisplay(trip?.endDate.orEmpty())} · ${trip?.place.orEmpty()}",
                            fontSize = 12.sp,
                            color = InkSoft
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = onEditTrip) { Icon(Icons.Default.Edit, contentDescription = "編輯行程", tint = MintDeep) }
                    TextButton(onClick = onMembers) { Text("旅伴", color = MintDeep, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                containerColor = MintDeep, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("記一筆", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                SoftCard {
                    CashSummaryBlock(
                        remaining = me?.cashCents ?: 0,
                        starting = me?.startingCashCents ?: 0,
                        currency = currency,
                        remainingLabel = "💵 剩餘現金（本人）"
                    )
                }
                Spacer(Modifier.height(12.dp))
                SoftCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("目前誰欠誰", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            "去結算 ›",
                            Modifier
                                .clickable(onClick = onSettle)
                                .clip(RoundedCornerShape(50))
                                .background(MintSoft)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MintDeep,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    balances.forEach { b ->
                        val m = mems.firstOrNull { it.id == b.memberId } ?: return@forEach
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPerson(m.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MemberAvatar(m.name, m.colorIndex, 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (m.isMe) "${m.name}（本人）" else m.name,
                                    fontWeight = FontWeight.Bold, color = Ink, maxLines = 1
                                )
                                Text(
                                    "剩餘 ${Money.format(m.cashCents, currency)}",
                                    color = MintDeep,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    "初始 ${Money.format(m.startingCashCents, currency)}",
                                    color = InkSoft,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                if (b.netCents >= 0) "應收 ${Money.format(b.netCents, currency)}"
                                else "應付 ${Money.format(-b.netCents, currency)}",
                                color = if (b.netCents >= 0) MintDeep else PeachDeep,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            grouped.forEach { (day, list) ->
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dayFmt.format(day.atStartOfDay(zone)), fontWeight = FontWeight.Black)
                        Text(Money.format(list.sumOf { it.amountCents }, currency), color = InkSoft, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(list, key = { it.id }) { e ->
                    val payer = mems.firstOrNull { it.id == e.payerMemberId }?.name ?: "?"
                    val cat = runCatching { ExpenseCategory.valueOf(e.category) }.getOrDefault(ExpenseCategory.OTHER)
                    val method = runCatching { PaymentMethod.valueOf(e.paymentMethod) }.getOrDefault(PaymentMethod.CASH)
                    val names = splitNames[e.id].orEmpty()
                    val share = SplitMath.perPersonShare(e.amountCents, names.size.coerceAtLeast(1))
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = e
                                false
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(PeachDeep)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除", tint = Color.White)
                            }
                        }
                    ) {
                        SoftCard(
                            Modifier
                                .padding(bottom = 8.dp)
                                .clickable { onExpense(e.id) }
                        ) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryChip(cat)
                                        Text("$payer 付", color = InkSoft, fontSize = 12.sp)
                                        PayChip(method)
                                    }
                                    if (names.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(names.joinToString("、"), color = InkSoft, fontSize = 12.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Money.format(e.amountCents, currency), fontWeight = FontWeight.Black)
                                    Text(
                                        "一人 ${Money.format(share, currency)}",
                                        color = InkSoft,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("刪除這筆支出？") },
            text = { Text("「${e.title}」會從分帳與現金中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    val title = e.title
                    pendingDelete = null
                    vm.deleteExpense(e.id) {
                        scope.launch {
                            snack.showSnackbar("已刪除「$title」")
                        }
                    }
                }) { Text("刪除", color = PeachDeep) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}
