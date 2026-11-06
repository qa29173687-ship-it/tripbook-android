package tw.tripbook.ui.screens

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.data.ExpenseEntity
import tw.tripbook.data.ExpenseSplitEntity
import tw.tripbook.domain.*
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Dates
import tw.tripbook.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    vm: TripViewModel,
    tripId: Long,
    expenseId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    var expense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var splits by remember { mutableStateOf<List<ExpenseSplitEntity>>(emptyList()) }
    LaunchedEffect(expenseId) {
        expense = vm.getExpense(expenseId)
        splits = vm.splitsFor(expenseId)
    }
    // refresh when expenses flow changes
    val exps by vm.expenses(tripId).collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(exps) {
        expense = vm.getExpense(expenseId)
        splits = vm.splitsFor(expenseId)
    }
    val e = expense
    val currency = trip?.currency ?: "TWD"
    val share = SplitMath.perPersonShare(e?.amountCents ?: 0, splits.size.coerceAtLeast(1))

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("支出明細", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = onEdit) { Text("編輯", color = MintDeep, fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("找不到這筆支出", color = InkSoft)
            }
            return@Scaffold
        }
        val method = runCatching { PaymentMethod.valueOf(e.paymentMethod) }.getOrDefault(PaymentMethod.CASH)
        val payer = mems.firstOrNull { it.id == e.payerMemberId }
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoftCard {
                Text(e.title, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Spacer(Modifier.height(6.dp))
                Text(Money.format(e.amountCents, currency), fontWeight = FontWeight.Black, fontSize = 28.sp, color = MintDeep)
                Text("一人 ${Money.format(share, currency)}", color = InkSoft, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PayChip(method)
                }
                Spacer(Modifier.height(8.dp))
                Text("日期 ${Dates.toDisplay(Dates.millisToIso(e.occurredAt))}", color = InkSoft, fontSize = 13.sp)
                Text(
                    "付款 ${(if (payer?.isMe == true) "${payer.name}（本人）" else payer?.name) ?: "?"}",
                    color = InkSoft,
                    fontSize = 13.sp
                )
            }
            SoftCard {
                Text("誰分了", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                splits.forEach { s ->
                    val m = mems.firstOrNull { it.id == s.memberId }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m != null) MemberAvatar(m.name, m.colorIndex, 36)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (m?.isMe == true) "${m.name}（本人）" else m?.name ?: "?",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(Money.format(s.shareCents, currency), fontWeight = FontWeight.Black, color = MintDeep)
                    }
                }
            }
            MintButton("編輯這一筆", Modifier.fillMaxWidth(), onClick = onEdit)
        }
    }
}
