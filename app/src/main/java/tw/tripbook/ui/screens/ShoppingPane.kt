package tw.tripbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.tripbook.TripViewModel
import tw.tripbook.data.ExpenseEntity
import tw.tripbook.data.MemberEntity
import tw.tripbook.data.ShoppingItemEntity
import tw.tripbook.domain.PaymentMethod
import tw.tripbook.ui.components.*
import tw.tripbook.ui.theme.*
import tw.tripbook.util.Money

/** 我要買：自己出國前想買的東西，買到時打勾記帳（現金會扣剩餘現金）。 */
@Composable
fun ShoppingPane(
    vm: TripViewModel,
    tripId: Long,
    me: MemberEntity?,
    exps: List<ExpenseEntity>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val items by vm.shopping(tripId).collectAsStateWithLifecycle(emptyList())
    var newName by remember { mutableStateOf("") }
    var buying by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var unticking by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var editing by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    val doneCount = items.count { it.done }
    val expById = exps.associateBy { it.id }

    Column(modifier.imePadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CashSummaryBlock(
                                remaining = me?.cashCents ?: 0,
                                starting = me?.startingCashCents ?: 0,
                                currency = currency,
                                remainingLabel = "💵 我的剩餘現金"
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("已買", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("$doneCount / ${items.size}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = MintDeep)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "出發前先把想買的東西列在這裡，買到時打勾，選現金或信用卡就會記進支出。",
                        color = InkSoft, fontSize = 14.sp, modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(items, key = { it.id }) { it ->
                val e = it.expenseId?.let { id -> expById[id] }
                ShoppingRow(
                    item = it,
                    paid = e,
                    currency = currency,
                    onCheck = { if (it.done) unticking = it else buying = it },
                    onEdit = { editing = it }
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Cream)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisibleTextField(
                newName, { newName = it }, "新增要買的東西",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        vm.addShoppingItem(tripId, newName, 1, "")
                        newName = ""
                    }
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MintDeep)
            ) { Icon(Icons.Default.Add, contentDescription = "新增", tint = Color.White) }
        }
    }

    buying?.let { item ->
        BoughtDialog(
            item = item,
            remaining = me?.cashCents ?: 0,
            currency = currency,
            onDismiss = { buying = null },
            onConfirm = { amount, method ->
                buying = null
                vm.markBought(item.id, amount, method)
            }
        )
    }
    unticking?.let { item ->
        AlertDialog(
            onDismissRequest = { unticking = null },
            title = { Text("取消「${item.name}」的打勾？") },
            text = { Text("會一併刪除這筆支出，付現的金額會加回剩餘現金。") },
            confirmButton = {
                TextButton(onClick = { unticking = null; vm.unmarkBought(item.id) }) { Text("取消打勾", color = PeachDeep) }
            },
            dismissButton = { TextButton(onClick = { unticking = null }) { Text("保留") } }
        )
    }
    editing?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { name, qty, note -> editing = null; vm.updateShoppingItem(item.id, name, qty, note) },
            onDelete = { editing = null; vm.deleteShoppingItem(item.id) }
        )
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItemEntity,
    paid: ExpenseEntity?,
    currency: String,
    onCheck: () -> Unit,
    onEdit: () -> Unit
) {
    SoftCard(Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.done) MintDeep else Color.White)
                    .border(2.dp, if (item.done) MintDeep else Color(0xFFB7C9C4), RoundedCornerShape(10.dp))
                    .clickable(onClick = onCheck),
                contentAlignment = Alignment.Center
            ) {
                if (item.done) Icon(Icons.Default.Check, contentDescription = "已買", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.qty > 1) "${item.name} ×${item.qty}" else item.name,
                    fontWeight = FontWeight.Black, fontSize = 16.sp,
                    color = if (item.done) InkSoft else Ink,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null
                )
                if (item.note.isNotBlank()) Text(item.note, color = InkSoft, fontSize = 12.sp)
                if (item.done && paid != null) {
                    val m = runCatching { PaymentMethod.valueOf(paid.paymentMethod) }.getOrDefault(PaymentMethod.CASH)
                    Text(
                        if (m == PaymentMethod.CASH) "現金 ${Money.format(paid.amountCents, currency)} · 已扣剩餘現金"
                        else "信用卡 ${Money.format(paid.amountCents, currency)} · 不扣現金",
                        color = MintDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BoughtDialog(
    item: ShoppingItemEntity,
    remaining: Long,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, PaymentMethod) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    val amount = amountText.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("買到了！${if (item.qty > 1) "${item.name} ×${item.qty}" else item.name}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VisibleMoneyField(amountText, { amountText = it }, "實付金額")
                Text("付款方式", color = InkSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("現金", method == PaymentMethod.CASH, mint = false) { method = PaymentMethod.CASH }
                    Pill("信用卡", method == PaymentMethod.CARD, mint = false) { method = PaymentMethod.CARD }
                }
                if (method == PaymentMethod.CASH) {
                    Text(
                        "剩餘現金 ${Money.format(remaining, currency)} → 扣完剩 ${Money.format(remaining - amount, currency)}",
                        color = MintDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                } else {
                    Text("信用卡只記進支出，不扣現金", color = InkSoft, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amount > 0, onClick = { onConfirm(amount, method) }) {
                Text("打勾並記帳", color = if (amount > 0) MintDeep else InkSoft, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EditItemDialog(
    item: ShoppingItemEntity,
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var qty by remember { mutableStateOf(item.qty.toString()) }
    var note by remember { mutableStateOf(item.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯要買的東西", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VisibleTextField(name, { name = it }, "東西")
                VisibleMoneyField(qty, { qty = it }, "數量")
                VisibleTextField(note, { note = it }, "備註（選填）", placeholder = "例如：送同事")
                TextButton(onClick = onDelete) { Text("刪除這項", color = PeachDeep) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, qty.toIntOrNull() ?: 1, note) }) {
                Text("儲存", color = MintDeep, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
