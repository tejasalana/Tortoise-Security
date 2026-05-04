package com.skylinestudio.security.repo

import com.skylinestudio.security.data.Command
import com.skylinestudio.security.data.DeviceStatus
import com.skylinestudio.security.data.User
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseRepo {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // ── Suspend Auth ─────────────────────────────────────────────────────────

    suspend fun signUpWithEmail(email: String, password: String): String =
        suspendCancellableCoroutine { cont ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { cont.resume(it.user!!.uid) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun signInWithEmail(email: String, password: String): String =
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { cont.resume(it.user!!.uid) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun signInWithGoogle(idToken: String): String =
        suspendCancellableCoroutine { cont ->
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { cont.resume(it.user!!.uid) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    // ── Suspend Firestore ─────────────────────────────────────────────────────

    suspend fun verifyParentId(parentId: String): Boolean =
        suspendCancellableCoroutine { cont ->
            db.collection("users")
                .document(parentId)
                .get()
                .addOnSuccessListener { doc ->
                    cont.resume(doc.exists() && doc.getString("role") == "PARENT")
                }
                .addOnFailureListener { cont.resume(false) }
        }

    suspend fun getUser(uid: String): User? =
        suspendCancellableCoroutine { cont ->
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    cont.resume(doc.toObject(User::class.java))
                }
                .addOnFailureListener { cont.resume(null) }
        }

    suspend fun saveUser(user: User): Unit =
        suspendCancellableCoroutine { cont ->
            db.collection("users")
                .document(user.uid)
                .set(user)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun uploadDeviceStatus(childId: String, status: DeviceStatus): Unit =
        suspendCancellableCoroutine { cont ->
            db.collection("sync").document(childId)
                .collection("status").document("current")
                .set(status)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun sendCommand(
        childId: String,
        action: String,
        params: Map<String, Any> = emptyMap()
    ): Unit =
        suspendCancellableCoroutine { cont ->
            val command = Command(action = action, params = params)
            db.collection("sync").document(childId)
                .collection("commands")
                .add(command)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    // ── Real-time Flows ───────────────────────────────────────────────────────

    fun commandsFlow(childId: String): Flow<Command> = callbackFlow {
        val listener = db.collection("sync").document(childId)
            .collection("commands")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                snapshot?.documents?.firstOrNull()
                    ?.toObject(Command::class.java)
                    ?.let { trySend(it) }
            }
        awaitClose { listener.remove() }
    }

    fun statusFlow(childId: String): Flow<DeviceStatus?> = callbackFlow {
        val listener = db.collection("sync").document(childId)
            .collection("status").document("current")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                trySend(snapshot?.toObject(DeviceStatus::class.java))
            }
        awaitClose { listener.remove() }
    }

    fun getLinkedChildren(parentId: String): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("parentId", parentId)
            .whereEqualTo("role", "CHILD")
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObjects(User::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // ── Legacy callback API ──────────────────────────────────────────────────

    fun verifyParentId(parentId: String, onResult: (Boolean) -> Unit) {
        db.collection("users")
            .document(parentId)
            .get()
            .addOnSuccessListener { doc ->
                onResult(doc.exists() && doc.getString("role") == "PARENT")
            }
            .addOnFailureListener { onResult(false) }
    }

    fun saveUserToFirestore(user: User, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(user.copy(uid = uid))
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun createUserProfile(user: User, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(user.copy(uid = uid))
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun startCommandListener(childId: String, onCommandReceived: (Command) -> Unit) {
        db.collection("sync").document(childId)
            .collection("commands")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                snapshot?.documents?.firstOrNull()?.toObject(Command::class.java)
                    ?.let(onCommandReceived)
            }
    }
}
