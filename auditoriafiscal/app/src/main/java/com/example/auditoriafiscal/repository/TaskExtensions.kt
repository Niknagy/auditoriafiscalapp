package com.example.auditoriafiscal.repository

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converte uma Task do Firebase em função suspend, para os repositories
 * poderem usar coroutines em vez de callbacks.
 */
suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { resultado -> if (cont.isActive) cont.resume(resultado) }
    addOnFailureListener { erro -> if (cont.isActive) cont.resumeWithException(erro) }
    addOnCanceledListener { cont.cancel() }
}
