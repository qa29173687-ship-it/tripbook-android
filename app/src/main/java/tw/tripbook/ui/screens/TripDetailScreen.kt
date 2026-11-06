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
    onCash: (Long?) -> Unit
) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val exps by vm.expenses(tripId).collectAsStateWithLifecycle(emptyList())
    var balances by remember { mutableStateOf<List<MemberBalance>>(emptyList()) }
    LaunchedEffect(exps, mems) { balances = vm.balances(tripId) }
    val me = mems.firstOrNull { it.isMe } ?: mems.firstOrNull()
    val holder = mems.firstOrNull { it.id == trip?.poolHolderMemberId }
    val total = exps.sumOf { it.amountCents }
    val zone = ZoneId.of("Asia/Taipei")
    val dayFmt = DateTimeFormatter.ofPattern("M/d EEE").withZone(zone)
    val grouped = exps.sortedBy { it.occurredAt }.groupBy {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(trip?.name ?: "行程", fontWeight = FontWeight.Black)
                        Text("${Dates.toDisplay(trip?.startDate.orEmpty())} – ${Dates.toDisplay(trip?.endDate.orEmpty())} · ${trip?.place.orEmpty()}", fontSize = 12.sp, color = InkSoft)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { TextButton(onClick = onMembers) { Text("旅伴", color = MintDeep, fontWeight = FontWeight.Bold) } },
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
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                GradientHeader {
                    Text("行程總支出", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                    Text(Money.format(total, trip?.currency ?: "TWD"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("${exps.size} 筆支出 · ${mems.size} 位旅伴", color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftCard(Modifier.weight(1.2f)) {
                        Text("💵 初始現金", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            Money.format(me?.startingCashCents ?: 0, trip?.currency ?: "TWD"),
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Ink,
                            maxLines = 1
                        )
                    }
                    SoftCard(Modifier.weight(1f)) {
                        Text("🧳 公款初始", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            Money.format(trip?.poolStartingCashCents ?: trip?.poolCashCents ?: 0, trip?.currency ?: "TWD"),
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Ink,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SoftCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("目前誰欠誰", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text("去結算 ›", Modifier.clickable(onClick = onSettle).clip(RoundedCornerShape(50)).background(MintSoft).padding(horizontal = 12.dp, vertical = 6.dp), color = MintDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    balances.forEach { b ->
                        val m = mems.firstOrNull { it.id == b.memberId } ?: return@forEach
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(m.name, m.colorIndex, 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                                Text(
                                    "初始現金 ${Money.format(m.startingCashCents, trip?.currency ?: "TWD")}",
                                    color = MintDeep,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Text(
                                if (b.netCents >= 0) "應收 ${Money.format(b.netCents)}" else "應付 ${Money.format(-b.netCents)}",
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
                        Text(Money.format(list.sumOf { it.amountCents }), color = InkSoft, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(list, key = { it.id }) { e ->
                    val payer = if (e.payerMemberId < 0) "公款" else mems.firstOrNull { it.id == e.payerMemberId }?.name ?: "?"
                    val cat = runCatching { ExpenseCategory.valueOf(e.category) }.getOrDefault(ExpenseCategory.OTHER)
                    val method = runCatching { PaymentMethod.valueOf(e.paymentMethod) }.getOrDefault(PaymentMethod.CASH)
                    SoftCard(Modifier.padding(bottom = 8.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CategoryChip(cat)
                                    Text("$payer 付", color = InkSoft, fontSize = 12.sp)
                                    PayChip(method)
                                }
                            }
                            Text(Money.format(e.amountCents, trip?.currency ?: "TWD"), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}
