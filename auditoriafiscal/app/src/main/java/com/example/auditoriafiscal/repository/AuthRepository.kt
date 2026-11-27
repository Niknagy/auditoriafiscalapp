package com.example.auditoriafiscal.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/**
 * REPOSITORY - Único lugar do app que conversa com o Firebase Authentication.
 * A View e o ViewModel nunca usam FirebaseAuth diretamente.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /** E-mail do usuário logado, ou null se ninguém estiver autenticado. */
    fun usuarioAtual(): String? = auth.currentUser?.email

    /** Faz login com e-mail e senha. Devolve o e-mail do usuário ou uma mensagem de erro. */
    suspend fun login(email: String, senha: String): Result<String> = runCatching {
        val resultado = auth.signInWithEmailAndPassword(email, senha).aguardar()
        resultado.user?.email ?: email
    }.recoverCatching { erro -> throw Exception(traduzirErro(erro)) }

    fun logout() = auth.signOut()

    private fun traduzirErro(erro: Throwable): String = when (erro) {
        is FirebaseAuthInvalidUserException -> "Usuário não encontrado ou desativado."
        is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha incorretos."
        is FirebaseNetworkException -> "Sem conexão com a internet. Verifique a rede e tente novamente."
        is FirebaseTooManyRequestsException -> "Muitas tentativas. Aguarde alguns minutos e tente de novo."
        else -> "Falha na autenticação: ${erro.localizedMessage ?: "erro desconhecido"}"
    }
}
