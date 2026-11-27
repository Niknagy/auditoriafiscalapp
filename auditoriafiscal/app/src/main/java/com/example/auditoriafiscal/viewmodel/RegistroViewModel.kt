package com.example.auditoriafiscal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auditoriafiscal.model.RegistroAuditoria
import com.example.auditoriafiscal.model.SituacaoRegistro
import com.example.auditoriafiscal.model.TiposRegistro
import com.example.auditoriafiscal.repository.AuthRepository
import com.example.auditoriafiscal.repository.RegistroAuditoriaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Estado da tela de cadastro. */
data class CadastroUiState(
    val salvando: Boolean = false,
    val mensagem: String? = null,
    val sucesso: Boolean = false,
    val errosCampos: Map<String, String> = emptyMap()
)

/** Estado da tela de pesquisa. */
data class PesquisaUiState(
    val pesquisando: Boolean = false,
    val resultados: List<RegistroAuditoria> = emptyList(),
    val termoPesquisado: String? = null,
    val doCache: Boolean = false,
    val erro: String? = null
)

/**
 * VIEWMODEL - Regras das telas de cadastro e pesquisa de registros.
 * Valida os campos obrigatórios e intermedia o acesso ao RegistroAuditoriaRepository.
 */
class RegistroViewModel(
    private val repository: RegistroAuditoriaRepository = RegistroAuditoriaRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _cadastro = MutableStateFlow(CadastroUiState())
    val cadastro: StateFlow<CadastroUiState> = _cadastro.asStateFlow()

    private val _pesquisa = MutableStateFlow(PesquisaUiState())
    val pesquisa: StateFlow<PesquisaUiState> = _pesquisa.asStateFlow()

    /** [cnpj] e [data] chegam já formatados (com máscara) pela View. */
    fun cadastrar(empresa: String, cnpj: String, tipo: String, data: String, situacao: String) {
        if (_cadastro.value.salvando) return

        val cnpjDigitos = cnpj.filter { it.isLetterOrDigit() }
        val erros = buildMap {
            if (empresa.isBlank()) put(CAMPO_EMPRESA, "Informe a empresa")
            if (cnpjDigitos.isNotEmpty() && cnpjDigitos.length != 14) put(CAMPO_CNPJ, "CNPJ incompleto (14 caracteres)")
            if (tipo !in TiposRegistro.todos) put(CAMPO_TIPO, "Escolha o tipo")
            if (!dataValida(data)) put(CAMPO_DATA, "Data inválida (dd/mm/aaaa)")
            if (situacao !in SituacaoRegistro.todas) put(CAMPO_SITUACAO, "Escolha a situação")
        }
        if (erros.isNotEmpty()) {
            _cadastro.value = CadastroUiState(errosCampos = erros, mensagem = "Corrija os campos destacados.")
            return
        }

        val registro = RegistroAuditoria(
            empresa = empresa.trim(),
            cnpj = if (cnpjDigitos.isEmpty()) "" else cnpj.trim(),
            tipo = tipo,
            data = data,
            situacao = situacao,
            criadoPor = authRepository.usuarioAtual().orEmpty()
        )

        viewModelScope.launch {
            _cadastro.value = CadastroUiState(salvando = true)
            repository.cadastrar(registro)
                .onSuccess { resultado ->
                    val mensagem = if (resultado.confirmadoNoServidor) {
                        "Registro salvo no Firestore!"
                    } else {
                        "Sem conexão: o registro ficou na fila e será enviado ao Firestore quando a internet voltar."
                    }
                    _cadastro.value = CadastroUiState(sucesso = true, mensagem = mensagem)
                }
                .onFailure { erro -> _cadastro.value = CadastroUiState(mensagem = erro.message) }
        }
    }

    fun pesquisar(termo: String) {
        val termoLimpo = termo.trim()
        if (termoLimpo.isEmpty()) {
            _pesquisa.value = PesquisaUiState(erro = "Digite o nome (ou o começo do nome) da empresa.")
            return
        }
        viewModelScope.launch {
            _pesquisa.update { it.copy(pesquisando = true, erro = null) }
            repository.pesquisarPorEmpresa(termoLimpo)
                .onSuccess { resultado ->
                    _pesquisa.value = PesquisaUiState(
                        resultados = resultado.registros,
                        termoPesquisado = termoLimpo,
                        doCache = resultado.doCache
                    )
                }
                .onFailure { erro -> _pesquisa.value = PesquisaUiState(termoPesquisado = termoLimpo, erro = erro.message) }
        }
    }

    /** Chamado quando o usuário volta a editar o formulário depois de uma mensagem. */
    fun limparMensagemCadastro() {
        if (_cadastro.value.mensagem != null) _cadastro.update { it.copy(mensagem = null) }
    }

    private fun dataValida(data: String): Boolean = try {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply { isLenient = false }
        data.length == 10 && formato.parse(data) != null
    } catch (e: Exception) {
        false
    }

    companion object {
        const val CAMPO_EMPRESA = "empresa"
        const val CAMPO_CNPJ = "cnpj"
        const val CAMPO_TIPO = "tipo"
        const val CAMPO_DATA = "data"
        const val CAMPO_SITUACAO = "situacao"
    }
}
