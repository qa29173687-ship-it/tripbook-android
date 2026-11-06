package tw.tripbook.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.domain.*
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(vm: TripViewModel, tripId: Long, onBack: () -> Unit) {
    val trip by vm.trip(tripId).collectAsStateWithLifecycle(null)
    val mems by vm.members(tripId).collectAsStateWithLifecycle(emptyList())
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var payerId by remember { mutableStateOf(-2L) } // set when mems load
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var receiptPath by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    LaunchedEffect(mems) {
        if (mems.isNotEmpty() && payerId < 0 && payerId != -1L) {
            payerId = mems.firstOrNull { it.isMe }?.id ?: mems.first().id
            selected = mems.map { it.id }.toSet()
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
    val payer = mems.firstOrNull { it.id == payerId }
    val poolPay = method == PaymentMethod.POOL || payerId == -1L
    val cashHint = if (method == PaymentMethod.CASH && payer != null && amount > 0) {
        val remain = payer.cashCents - amount
        "將從 ${payer.name} 身上現金扣除 ${Money.format(amount)}，剩餘約 ${Money.format(remain)}（目前 ${Money.format(payer.cashCents)}）"
    } else if (poolPay && amount > 0) {
        val pool = trip?.poolCashCents ?: 0
        "將從公款扣除 ${Money.format(amount)}，剩餘約 ${Money.format(pool - amount)}（目前 ${Money.format(pool)}）"
    } else null

    val parts = selected.toList().sorted()
    val shares = if (parts.isNotEmpty() && amount > 0) SplitMath.equalSplit(amount, parts) else emptyList()

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("記一筆支出", fontWeight = FontWeight.Black) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SoftCard {
                OutlinedTextField(amountText, { amountText = it.filter { ch -> ch.isDigit() } }, label = { Text("金額") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text("項目／備註") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Text("分類", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { c ->
                    Pill(c.label, category == c) { category = c }
                }
            }
            Text("誰付的", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mems.forEach { m ->
                    val on = payerId == m.id && method != PaymentMethod.POOL
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (on) MintSoft else Color.White)
                            .border(2.dp, if (on) Mint else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { payerId = m.id; if (method == PaymentMethod.POOL) method = PaymentMethod.CASH }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MemberAvatar(m.name, m.colorIndex, 40)
                        Text(m.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                if ((trip?.poolCashCents ?: 0) > 0 || method == PaymentMethod.POOL) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(if (method == PaymentMethod.POOL) MintSoft else Color.White)
                            .border(2.dp, if (method == PaymentMethod.POOL) Mint else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { method = PaymentMethod.POOL; payerId = -1L }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🧳", fontSize = 28.sp)
                        Text("公款", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Text("付款方式", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.TRANSFER).forEach { m ->
                    Pill(m.label, method == m && payerId != -1L, mint = m != PaymentMethod.CASH) {
                        method = m; if (payerId == -1L) payerId = mems.firstOrNull()?.id ?: 0
                    }
                }
            }
            cashHint?.let {
                Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PeachSoft).padding(12.dp), color = PeachDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text("誰要分攤（預設平均）", fontWeight = FontWeight.Bold, color = InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mems.forEach { m ->
                    val on = m.id in selected
                    val share = shares.firstOrNull { it.memberId == m.id }?.amountCents
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(2.dp, if (on) Mint else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable {
                                selected = if (on) selected - m.id else selected + m.id
                            }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MemberAvatar(m.name, m.colorIndex, 36)
                        Text(m.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (on && share != null) Money.format(share) else "未分攤", color = InkSoft, fontSize = 11.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { gallery.launch("image/*") }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (receiptPath != null) "已選收據" else "📷 收據")
                }
                MintButton("儲存這一筆", Modifier.weight(1.3f)) {
                    if (amount <= 0 || parts.isEmpty() || title.isBlank()) return@MintButton
                    val payMethod = if (payerId == -1L) PaymentMethod.POOL else method
                    vm.addExpense(tripId, title, amount, category, payerId, payMethod, parts, note.ifBlank { title }, receiptPath) { onBack() }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
