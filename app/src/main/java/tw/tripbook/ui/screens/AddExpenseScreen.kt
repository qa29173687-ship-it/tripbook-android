package tw.tripbook.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tw.tripbook.TripViewModel
import tw.tripbook.domain.*
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Dates
import tw.tripbook.util.Money
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    vm: TripViewModel,
    tripId: Long,
    onBack: () -> Unit,
    expenseId: Long? = null
) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    val currency = trip?.currency ?: "TWD"
    val editing = expenseId != null
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var payerId by remember { mutableStateOf(-2L) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var receiptPath by remember { mutableStateOf<String?>(null) }
    var dateIso by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    LaunchedEffect(trip?.startDate, trip?.endDate, expenseId, mems) {
        if (trip == null || mems.isEmpty()) return@LaunchedEffect
        if (editing && expenseId != null && !loaded) {
            val e = vm.getExpense(expenseId) ?: return@LaunchedEffect
            val splits = vm.splitsFor(expenseId)
            amountText = e.amountCents.toString()
            title = e.title
            category = runCatching { ExpenseCategory.valueOf(e.category) }.getOrDefault(ExpenseCategory.FOOD)
            payerId = e.payerMemberId
            method = runCatching { PaymentMethod.valueOf(e.paymentMethod) }.getOrDefault(PaymentMethod.CASH)
            if (method == PaymentMethod.POOL) method = PaymentMethod.CASH
            selected = splits.map { it.memberId }.toSet()
            receiptPath = e.receiptPath
            dateIso = Dates.clampIsoToRange(
                Dates.millisToIso(e.occurredAt),
                trip!!.startDate,
                trip!!.endDate
            )
            loaded = true
        } else if (!editing && !loaded) {
            payerId = mems.firstOrNull { it.isMe }?.id ?: mems.first().id
            selected = mems.map { it.id }.toSet()
            dateIso = Dates.defaultExpenseIso(trip!!.startDate, trip!!.endDate)
            loaded = true
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
        val out = File(dir, "r_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        }
        receiptPath = out.absolutePath
    }

    val amount = amountText.toLongOrNull() ?: 0L
    val parts = selected.toList().sorted()
    val shares = if (parts.isNotEmpty() && amount > 0) SplitMath.equalSplit(amount, parts) else emptyList()

    fun notify(msg: String) = scope.launch { snack.showSnackbar(msg) }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "編輯支出" else "記一筆支出", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) } },
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
                .verticalScroll(scroll, enabled = true)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SoftCard {
                VisibleMoneyField(amountText, { amountText = it }, "金額")
                Spacer(Modifier.height(8.dp))
                VisibleTextField(title, { title = it }, "項目（可留空）", placeholder = "空白則用分類名稱")
            }
            DatePickerButton(
                label = "支出日期",
                isoDate = dateIso,
                onPicked = { dateIso = it },
                minIso = trip?.startDate,
                maxIso = trip?.endDate
            )
            Text("分類", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { c ->
                    Pill(c.label, category == c) { category = c }
                }
            }
            Text("誰付的（預設本人）", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mems.forEach { m ->
                    val on = payerId == m.id
                    Column(
                        Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) MintSoft else Color.White)
                            .border(2.dp, if (on) Mint else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { payerId = m.id }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MemberAvatar(m.name, m.colorIndex, 36)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (m.isMe) "本人" else m.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text("付款方式", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER).forEach { m ->
                    Pill(m.label, method == m, mint = m != PaymentMethod.CASH) { method = m }
                }
            }
            Text("誰要分攤（預設平均）", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mems.forEach { m ->
                    val on = m.id in selected
                    val share = shares.firstOrNull { it.memberId == m.id }?.amountCents
                    Column(
                        Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(2.dp, if (on) Mint else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable {
                                selected = if (on) selected - m.id else selected + m.id
                            }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MemberAvatar(m.name, m.colorIndex, 36)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (m.isMe) "本人" else m.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (on && share != null) Money.format(share, currency) else " ",
                            color = MintDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.height(18.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { gallery.launch("image/*") },
                    Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (receiptPath != null) "已選收據" else "📷 收據") }
                MintButton(if (editing) "儲存修改" else "儲存這一筆", Modifier.weight(1.3f)) {
                    when {
                        amount <= 0 -> notify("請輸入金額")
                        parts.isEmpty() -> notify("請至少選一位分攤")
                        payerId <= 0 -> notify("請選擇付款人")
                        dateIso.isBlank() -> notify("請選擇日期")
                        else -> {
                            val finalTitle = title.trim().ifBlank { category.label }
                            val occurredAt = Dates.toMillisNoon(dateIso) ?: System.currentTimeMillis()
                            if (editing && expenseId != null) {
                                vm.updateExpense(
                                    expenseId, finalTitle, amount, category, payerId, method,
                                    parts, finalTitle, receiptPath, occurredAt
                                ) { onBack() }
                            } else {
                                vm.addExpense(
                                    tripId, finalTitle, amount, category, payerId, method,
                                    parts, finalTitle, receiptPath, occurredAt
                                ) { onBack() }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(280.dp))
        }
    }
}
