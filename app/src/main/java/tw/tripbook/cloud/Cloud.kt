package tw.tripbook.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import tw.tripbook.R
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import tw.tripbook.domain.CExpense
import tw.tripbook.domain.CMember
import tw.tripbook.domain.CPending
import tw.tripbook.util.PhotoStore
import java.io.ByteArrayOutputStream

data class CTrip(
    val id: String, val name: String, val currency: String, val ownerUid: String, val ownerName: String,
    val inviteCode: String, val startDate: String, val endDate: String, val members: List<CMember>
)

data class CHelper(val name: String, val status: String) // FIND = 我幫你找, SEEN = 我看到了

data class CItem(
    val id: String, val ownerUid: String, val memberId: String, val memberName: String,
    val name: String, val qty: Int, val note: String, val photo: String?,
    val done: Boolean, val amountCents: Long, val method: String, val expenseId: String?,
    val helpers: Map<String, CHelper>, val createdAt: Long
)

data class CJoin(val uid: String, val memberId: String, val name: String)

data class CInbox(
    val id: String, val byUid: String, val memberId: String, val memberName: String, val itemId: String,
    val type: String, val title: String, val amountCents: Long, val method: String, val at: Long
)

/** Thin wrapper over Firebase Auth + Firestore for 旅伴共享. */
object Cloud {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val user: FirebaseUser? get() = auth.currentUser

    fun trips() = db.collection("trips")
    fun trip(id: String) = trips().document(id)

    /** No-login: an anonymous identity kept on this phone. */
    suspend fun ensureSignedIn(): FirebaseUser = user ?: auth.signInAnonymously().await().user!!

    /** True when this phone is signed in with Google (主帳號). */
    val isGoogle: Boolean get() = user?.providerData?.any { it.providerId == "google.com" } == true
    val googleEmail: String? get() = user?.providerData?.firstOrNull { it.providerId == "google.com" }?.email

    /** 主帳號 Google sign-in. If this phone already has a no-login identity it is upgraded (same uid). */
    suspend fun signInGoogle(activity: Context): FirebaseUser {
        val webId = activity.getString(R.string.default_web_client_id)
        val req = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webId).build())
            .build()
        val c = CredentialManager.create(activity).getCredential(activity, req).credential
        require(c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "不是 Google 帳號" }
        val cred = GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(c.data).idToken, null)
        val cur = user
        if (cur != null && cur.isAnonymous) {
            try { return cur.linkWithCredential(cred).await().user!! } catch (_: FirebaseAuthUserCollisionException) { }
        }
        return auth.signInWithCredential(cred).await().user!!
    }

    fun signOut() = auth.signOut()

    // ---------- parsing ----------
    fun parseTrip(d: DocumentSnapshot): CTrip? {
        if (!d.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val ms = (d.get("members") as? List<Map<String, Any?>>).orEmpty().map {
            CMember(
                it["id"].toString(), it["name"]?.toString().orEmpty(),
                (it["start"] as? Number)?.toLong() ?: 0, (it["cash"] as? Number)?.toLong() ?: 0,
                it["owner"] == true
            )
        }
        return CTrip(
            d.id, d.getString("name").orEmpty(), d.getString("currency") ?: "TWD",
            d.getString("ownerUid").orEmpty(), d.getString("ownerName").orEmpty(),
            d.getString("inviteCode").orEmpty(), d.getString("startDate").orEmpty(), d.getString("endDate").orEmpty(), ms
        )
    }

    fun parseExpense(d: DocumentSnapshot): CExpense {
        @Suppress("UNCHECKED_CAST")
        val sh = (d.get("shares") as? Map<String, Any?>).orEmpty().mapValues { (it.value as? Number)?.toLong() ?: 0L }
        return CExpense(
            d.id, d.getString("title").orEmpty(), d.getLong("amount") ?: 0, d.getString("payerId").orEmpty(),
            d.getString("method") ?: "CARD", d.getLong("at") ?: 0, sh
        )
    }

    fun parseItem(d: DocumentSnapshot): CItem {
        @Suppress("UNCHECKED_CAST")
        val hs = (d.get("helpers") as? Map<String, Map<String, Any?>>).orEmpty()
            .mapValues { CHelper(it.value["name"]?.toString().orEmpty(), it.value["status"]?.toString() ?: "FIND") }
        return CItem(
            d.id, d.getString("ownerUid").orEmpty(), d.getString("memberId").orEmpty(), d.getString("memberName").orEmpty(),
            d.getString("name").orEmpty(), (d.getLong("qty") ?: 1).toInt(), d.getString("note").orEmpty(),
            d.getString("photo")?.takeIf { it.isNotEmpty() }, d.getBoolean("done") == true,
            d.getLong("amount") ?: 0, d.getString("method") ?: "CASH", d.getString("expenseId"),
            hs, d.getLong("createdAt") ?: 0
        )
    }

    fun parseInbox(d: DocumentSnapshot) = CInbox(
        d.id, d.getString("byUid").orEmpty(), d.getString("memberId").orEmpty(), d.getString("memberName").orEmpty(),
        d.getString("itemId").orEmpty(), d.getString("type") ?: "buy", d.getString("title").orEmpty(),
        d.getLong("amount") ?: 0, d.getString("method") ?: "CASH", d.getLong("at") ?: 0
    )

    fun pendingOf(list: List<CInbox>) = list.filter { it.type == "buy" }
        .map { CPending(it.memberId, it.title, it.amountCents, it.method, it.at) }

    // ---------- live flows ----------
    fun tripFlow(id: String): Flow<CTrip?> = callbackFlow {
        val reg = trip(id).addSnapshotListener { s, _ -> trySend(s?.let { parseTrip(it) }) }
        awaitClose { reg.remove() }
    }

    fun <T> queryFlow(q: Query, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { s, _ -> if (s != null) trySend(s.documents.map(map)) }
        awaitClose { reg.remove() }
    }

    fun itemsFlow(id: String) = queryFlow(trip(id).collection("shopping").orderBy("createdAt"), ::parseItem)
    fun expensesFlow(id: String) = queryFlow(trip(id).collection("expenses"), ::parseExpense)
    fun inboxFlow(id: String) = queryFlow(trip(id).collection("inbox"), ::parseInbox)
    fun joinsFlow(id: String) = queryFlow(trip(id).collection("joins")) {
        CJoin(it.id, it.getString("memberId").orEmpty(), it.getString("name").orEmpty())
    }

    // ---------- shopping writes (everyone) ----------
    suspend fun addItem(tripId: String, memberId: String, memberName: String, name: String, photo: String?) {
        val now = System.currentTimeMillis()
        trip(tripId).collection("shopping").add(
            mapOf(
                "ownerUid" to user!!.uid, "memberId" to memberId, "memberName" to memberName,
                "name" to name, "qty" to 1, "note" to "", "photo" to (photo ?: ""),
                "done" to false, "amount" to 0L, "method" to "CASH",
                "helpers" to emptyMap<String, Any>(), "createdAt" to now, "updatedAt" to now
            )
        ).await()
    }

    suspend fun updateItem(tripId: String, itemId: String, fields: Map<String, Any?>) {
        trip(tripId).collection("shopping").document(itemId)
            .update(fields + ("updatedAt" to System.currentTimeMillis())).await()
    }

    suspend fun deleteItem(tripId: String, itemId: String) {
        trip(tripId).collection("shopping").document(itemId).delete().await()
    }

    /** Toggle 我幫你找 / 我看到了 on someone else's item. */
    suspend fun setHelp(tripId: String, itemId: String, name: String, status: String?) {
        val uid = user!!.uid
        trip(tripId).collection("shopping").document(itemId).update(
            mapOf(
                "helpers.$uid" to (if (status == null) FieldValue.delete() else mapOf("name" to name, "status" to status)),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /** Companion ticks their own item: mark it and drop a request for the owner's phone to record the expense. */
    suspend fun companionBuy(tripId: String, item: CItem, memberName: String, title: String, amount: Long, method: String) {
        updateItem(tripId, item.id, mapOf("done" to true, "amount" to amount, "method" to method))
        trip(tripId).collection("inbox").add(
            mapOf(
                "byUid" to user!!.uid, "memberId" to item.memberId, "memberName" to memberName, "itemId" to item.id,
                "type" to "buy", "title" to title, "amount" to amount, "method" to method, "at" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun companionUnbuy(tripId: String, item: CItem, memberName: String) {
        updateItem(tripId, item.id, mapOf("done" to false, "amount" to 0L))
        trip(tripId).collection("inbox").add(
            mapOf(
                "byUid" to user!!.uid, "memberId" to item.memberId, "memberName" to memberName, "itemId" to item.id,
                "type" to "unbuy", "title" to item.name, "amount" to 0L, "method" to item.method, "at" to System.currentTimeMillis()
            )
        ).await()
    }

    // ---------- join ----------
    suspend fun lookupInvite(code: String): CTrip? {
        val inv = db.collection("invites").document(code).get().await()
        val tid = inv.getString("tripId") ?: return null
        return parseTrip(trip(tid).get().await())
    }

    suspend fun join(t: CTrip, memberId: String, name: String) {
        trip(t.id).collection("joins").document(user!!.uid).set(
            mapOf("memberId" to memberId, "name" to name, "code" to t.inviteCode, "at" to System.currentTimeMillis())
        ).await()
    }

    // ---------- photos ----------
    /** Shrinks a local photo to ~720px JPEG and returns base64 (fits in a Firestore document). */
    fun photoToBase64(path: String): String? = runCatching {
        var bmp = PhotoStore.load(path, 720) ?: return null
        val scale = 720f / maxOf(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    fun decodePhoto(b64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
