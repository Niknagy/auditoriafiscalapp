package com.example.auditoriafiscal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auditoriafiscal.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado da tela de login observado pela View. */
data class LoginUiState(
    val usuarioLogado: String? = null,
    val carregando: Boolean = false,
    val erro: String? = null
)

/**
 * VIEWMODEL - Valida os campos, chama o AuthRepository e expõe o estado
 * (carregando / erro / usuário logado) para a TelaLogin.
 */
class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(usuarioLogado = authRepository.usuarioAtual()))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun entrar(email: String, senha: String) {
        val emailLimpo = email.trim()
        val erroValidacao = when {
            emailLimpo.isBlank() || senha.isBlank() -> "Informe o e-mail e a senha."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(emailLimpo).matches() -> "E-mail em formato inválido."
            senha.length < 6 -> "A senha deve ter pelo menos 6 caracteres."
            else -> null
        }
        if (erroValidacao != null) {
            _uiState.update { it.copy(erro = erroValidacao) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(carregando = true, erro = null) }
            authRepository.login(emailLimpo, senha)
                .onSuccess { usuario -> _uiState.update { LoginUiState(usuarioLogado = usuario) } }
                .onFailure { erro -> _uiState.update { it.copy(carregando = false, erro = erro.message) } }
        }
    }

    fun sair() {
        authRepository.logout()
        _uiState.value = LoginUiState()
    }

    fun limparErro() = _uiState.update { it.copy(erro = null) }
}
