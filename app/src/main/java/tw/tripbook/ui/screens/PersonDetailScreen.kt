package tw.tripbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.data.ExpenseEntity
import tw.tripbook.domain.*
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    vm: TripViewModel,
    tripId: Long,
    memberId: Long,
    onBack: () -> Unit,
    onExpense: (Long) -> Unit
) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val exps by vm.expenses(tripId).collectAsStateWithLifecycle(emptyList())
    val member = mems.firstOrNull { it.id == memberId }
    var balance by remember { mutableStateOf<MemberBalance?>(null) }
    var paidList by remember { mutableStateOf<List<ExpenseEntity>>(emptyList()) }
    var splitNames by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    LaunchedEffect(exps, mems, memberId) {
        val bals = vm.balances(tripId)
        balance = bals.firstOrNull { it.memberId == memberId }
        paidList = exps.filter { it.payerMemberId == memberId }
        val map = mutableMapOf<Long, List<String>>()
        for (e in paidList) {
            val splits = vm.splitsFor(e.id)
            map[e.id] = splits.mapNotNull { s -> mems.firstOrNull { it.id == s.memberId }?.name }
        }
        splitNames = map
    }
    val currency = trip?.currency ?: "TWD"
    val b = balance

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (member?.isMe == true) "${member.name}（本人）" else member?.name ?: "旅伴",
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SoftCard {
                    if (member != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(member.name, member.colorIndex, 56)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (member.isMe) "${member.name}（本人）" else member.name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                )
                                Text("剩餘現金 ${Money.format(member.cashCents, currency)}", color = MintDeep, fontWeight = FontWeight.Bold)
                                Text("初始 ${Money.format(member.startingCashCents, currency)}", color = InkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val net = b?.netCents ?: 0
                        Text(
                            if (net >= 0) "應收 ${Money.format(net, currency)}" else "應付 ${Money.format(-net, currency)}",
                            color = if (net >= 0) MintDeep else PeachDeep,
                            fontWeight = FontWeight.Black
                        )
                        Text("已付 ${Money.format(b?.paidCents ?: 0, currency)}", color = InkSoft, fontWeight = FontWeight.Bold)
                        Text("應分 ${Money.format(b?.shareCents ?: 0, currency)}", color = InkSoft, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("付了這些", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                if (paidList.isEmpty()) {
                    SoftCard { Text("還沒有付款紀錄", color = InkSoft) }
                }
            }
            items(paidList, key = { it.id }) { e ->
                val method = runCatching { PaymentMethod.valueOf(e.paymentMethod) }.getOrDefault(PaymentMethod.CASH)
                val names = splitNames[e.id].orEmpty()
                val share = SplitMath.perPersonShare(e.amountCents, names.size.coerceAtLeast(1))
                SoftCard(Modifier.padding(bottom = 8.dp).clickable { onExpense(e.id) }) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            PayChip(method)
                            if (names.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(names.joinToString("、"), color = InkSoft, fontSize = 12.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(e.amountCents, currency), fontWeight = FontWeight.Black)
                            Text("一人 ${Money.format(share, currency)}", color = InkSoft, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
