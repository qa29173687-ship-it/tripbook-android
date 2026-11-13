package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.cloud.Cloud
import tw.tripbook.domain.SharedView
import tw.tripbook.domain.ShoppingText
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 旅伴（副帳號）看到的共享旅程：我要買（可寫自己的）＋ 我的花費（只能看）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTripScreen(vm: TripViewModel, cloudId: String, onBack: () -> Unit) {
    val joinedList by vm.joined.collectAsStateWithLifecycle()
    val j = joinedList.firstOrNull { it.cloudId == cloudId }
    val trip by remember(cloudId) { Cloud.tripFlow(cloudId) }.collectAsStateWithLifecycle(null)
    val exps by remember(cloudId) { Cloud.expensesFlow(cloudId) }.collectAsStateWithLifecycle(emptyList())
    val inbox by remember(cloudId) { Cloud.inboxFlow(cloudId) }.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { runCatching { Cloud.ensureSignedIn() } }
    if (j == null) { LaunchedEffect(Unit) { onBack() }; return }
    val members = trip?.members.orEmpty()
    val currency = trip?.currency ?: "TWD"
    val sum = SharedView.summary(j.memberId, members, exps, Cloud.pendingOf(inbox))
    val ownerName = trip?.ownerName?.ifBlank { null } ?: "主帳號"
    val nameOf = { id: String -> members.firstOrNull { it.id == id }?.name ?: "?" }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(trip?.name ?: j.tripName, fontWeight = FontWeight.Black)
                        Text("${j.memberName}的手機 · ${ownerName}邀請", fontSize = 12.sp, color = InkSoft)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    Text(
                        "👁 只能看支出",
                        Modifier.padding(end = 12.dp).clip(RoundedCornerShape(50)).background(MintSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MintDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = Cream, contentColor = MintDeep) {
                Tab(tab == 0, { tab = 0 }, text = { Text("我要買", fontWeight = FontWeight.Bold) })
                Tab(tab == 1, { tab = 1 }, text = { Text("我的花費", fontWeight = FontWeight.Bold) })
            }
            if (tab == 0) {
                SharedShoppingPane(
                    cloudId = cloudId, myMemberId = j.memberId, myName = j.memberName, members = members,
                    myCash = sum.remainingCash, myStartCash = sum.startCash, currency = currency,
                    onBuy = { item, amount, method ->
                        Cloud.companionBuy(cloudId, item, j.memberName, ShoppingText.expenseTitle(item.name, item.qty), amount, method.name)
                    },
                    onUnbuy = { item -> Cloud.companionUnbuy(cloudId, item, j.memberName) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val fmt = DateTimeFormatter.ofPattern("M/d").withZone(ZoneId.of("Asia/Taipei"))
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MintDeep).padding(18.dp)
                        ) {
                            Text("我的剩餘現金（帶了 ${Money.format(sum.startCash, currency)}）", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(Money.format(sum.remainingCash, currency), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(6.dp))
                            Text("我總共花了 ${Money.format(sum.spent, currency)}", color = Color.White, fontWeight = FontWeight.Bold)
                            val pay = sum.transfers.filter { it.fromId == j.memberId }
                            val get = sum.transfers.filter { it.toId == j.memberId }
                            if (pay.isEmpty() && get.isEmpty()) Text("目前不用轉帳", color = Color.White, fontSize = 13.sp)
                            pay.forEach { Text("要付 ${nameOf(it.toId)} ${Money.format(it.amountCents, currency)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            get.forEach { Text("${nameOf(it.fromId)} 要給你 ${Money.format(it.amountCents, currency)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (sum.pending.isNotEmpty()) item {
                        Text("等${ownerName}的手機記帳（打開旅伴帳就會自動記）", color = PeachDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    items(sum.pending) { p ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PeachSoft).padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.Bold)
                                Text("我付 · ${if (p.method == "CASH") "現金" else "信用卡"} · 只算我", color = InkSoft, fontSize = 12.sp)
                            }
                            Text(Money.format(p.amountCents, currency), fontWeight = FontWeight.Black)
                        }
                    }
                    item { Text("跟我有關的支出", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    if (sum.related.isEmpty()) item { Text("還沒有跟你有關的支出。", color = InkSoft, fontSize = 14.sp) }
                    items(sum.related, key = { it.id }) { e ->
                        val mine = e.shares[j.memberId] ?: 0L
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, fontWeight = FontWeight.Bold)
                                val who = if (e.payerId == j.memberId) "我付" else "${nameOf(e.payerId)} 付"
                                val method = when (e.method) { "CASH" -> "現金"; "CARD" -> "刷卡"; "TRANSFER" -> "轉帳"; else -> "" }
                                Text(
                                    "${fmt.format(Instant.ofEpochMilli(e.at))} · $who · $method · " +
                                        if (e.shares.size == 1 && mine > 0) "只算我" else "我分 ${Money.format(mine, currency)}",
                                    color = InkSoft, fontSize = 12.sp
                                )
                            }
                            Text(Money.format(e.amountCents, currency), fontWeight = FontWeight.Black)
                        }
                    }
                    item {
                        Text("🔒 支出和分帳由${ownerName}（主帳號）管理，有問題請跟他說。", color = InkSoft, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
