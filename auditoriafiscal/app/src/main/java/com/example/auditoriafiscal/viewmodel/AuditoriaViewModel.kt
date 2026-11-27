package com.example.auditoriafiscal.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auditoriafiscal.model.AuditoriaConcluida
import com.example.auditoriafiscal.model.AuditoriaRepository
import com.example.auditoriafiscal.model.ItemAuditoria
import com.example.auditoriafiscal.model.NaoConformidade
import com.example.auditoriafiscal.model.PlanoAuditoria
import com.example.auditoriafiscal.model.StatusApontamento
import com.example.auditoriafiscal.model.StatusItem
import com.example.auditoriafiscal.model.formatarAgora
import com.example.auditoriafiscal.model.paraRegistro
import com.example.auditoriafiscal.repository.AuthRepository
import com.example.auditoriafiscal.repository.RegistroAuditoriaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AuditoriaUiState(
    val plano: PlanoAuditoria = PlanoAuditoria(),
    val itens: List<ItemAuditoria> = emptyList(),
    val historico: List<AuditoriaConcluida> = emptyList(),
    val sincronizandoNuvem: Boolean = false,
    val statusNuvem: String = "Apenas local (offline-first)",
    val statusFirestore: String? = null
) {
    val total: Int get() = itens.size
    val pendentes: Int get() = itens.count { it.status == StatusItem.PENDENTE }
    val verificados: Int get() = total - pendentes

    val statusSessao: String
        get() = when {
            total == 0 || pendentes == total -> "Não iniciada"
            pendentes == 0 -> "Concluída"
            else -> "Em andamento"
        }

    val apontamentosAbertos: Int
        get() = itens.count {
            val nc = it.naoConformidade
            nc != null && nc.status != StatusApontamento.RESOLVIDO
        }
}

class AuditoriaViewModel(
    private val repository: AuditoriaRepository,
    private val registroRepository: RegistroAuditoriaRepository = RegistroAuditoriaRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuditoriaUiState(
            plano = repository.carregarPlano(),
            itens = repository.carregarItens(),
            historico = repository.carregarHistorico()
        )
    )
    val uiState: StateFlow<AuditoriaUiState> = _uiState.asStateFlow()

    fun atualizarPlano(razaoSocial: String, cnpj: String, competencia: String, responsavel: String) {
        val planoAtualizado = _uiState.value.plano.copy(
            razaoSocial = razaoSocial,
            cnpj = cnpj,
            competenciaFiscal = competencia,
            responsavelAuditado = responsavel
        )
        repository.salvarPlano(planoAtualizado)
        _uiState.update { it.copy(plano = planoAtualizado) }
    }

    fun marcarConforme(idItem: Int) {
        val item = _uiState.value.itens.firstOrNull { it.id == idItem } ?: return
        substituirItem(item.copiarComStatus(novoStatus = StatusItem.CONFORME, novaNC = null))
    }

    fun registrarNaoConformidade(
        idItem: Int,
        severidade: String,
        criterioNormativo: String,
        observacao: String,
        prazo: String
    ) {
        val item = _uiState.value.itens.firstOrNull { it.id == idItem } ?: return
        val novaNC = NaoConformidade(
            idItem = idItem,
            nivelSeveridade = severidade,
            criterioNormativo = criterioNormativo,
            observacao = observacao,
            prazoResolucao = prazo,
            status = item.naoConformidade?.status ?: StatusApontamento.PENDENTE
        )
        substituirItem(item.copiarComStatus(novoStatus = StatusItem.NAO_CONFORME, novaNC = novaNC))
    }

    fun atualizarStatusApontamento(idItem: Int, novoStatus: String) {
        val item = _uiState.value.itens.firstOrNull { it.id == idItem } ?: return
        val nc = item.naoConformidade ?: return
        substituirItem(item.copiarComStatus(novaNC = nc.copy(status = novoStatus)))
    }

    fun novoArquivoFoto(idItem: Int): File = repository.novoArquivoFoto(idItem)

    fun descartarArquivo(caminho: String) = repository.excluirFoto(caminho)

    fun registrarFoto(idItem: Int, caminho: String) {
        val item = _uiState.value.itens.firstOrNull { it.id == idItem }
        if (item == null) {
            repository.excluirFoto(caminho)
            return
        }
        if (item.caminhoFoto != null && item.caminhoFoto != caminho) {
            repository.excluirFoto(item.caminhoFoto)
        }
        val agora = formatarAgora("dd/MM/yyyy HH:mm")
        substituirItem(item.copiarComStatus(novaFoto = caminho, novoTimestamp = agora))
    }

    fun removerFoto(idItem: Int) {
        val item = _uiState.value.itens.firstOrNull { it.id == idItem } ?: return
        repository.excluirFoto(item.caminhoFoto)
        substituirItem(item.copiarComStatus(novaFoto = null, novoTimestamp = null))
    }

    /** Executa o OCR fora da thread principal e entrega o texto na thread principal. */
    fun extrairDadosDaFotoComIA(caminhoFoto: String, onTextoExtraido: (String) -> Unit) {
        viewModelScope.launch {
            onTextoExtraido(repository.analisarFotoComIA(caminhoFoto))
        }
    }

    fun sincronizarComNuvem() {
        val estado = _uiState.value
        if (estado.sincronizandoNuvem) return

        viewModelScope.launch {
            _uiState.update { it.copy(sincronizandoNuvem = true, statusNuvem = "Sincronizando com a nuvem...") }

            val mensagem = withContext(Dispatchers.IO) {
                val resultado = repository.sincronizarComCloudflare(estado.plano, estado.itens, estado.historico)
                if (resultado.isFailure) {
                    val causa = resultado.exceptionOrNull()?.message ?: "sem conexão"
                    "Falha na sincronização ($causa). Dados mantidos no aparelho."
                } else {
                    val fotos = estado.itens.mapNotNull { item -> item.caminhoFoto?.let { item.id to it } }
                    val falhas = fotos.count { (id, caminho) ->
                        repository.enviarFotoParaCloudflareR2(estado.plano.protocolo, id, caminho).isFailure
                    }
                    val hora = formatarAgora("dd/MM/yyyy HH:mm")
                    if (falhas == 0) {
                        "Sincronizado em $hora (${fotos.size} foto(s) enviada(s))"
                    } else {
                        "Dados sincronizados em $hora, mas $falhas de ${fotos.size} foto(s) não foram enviadas"
                    }
                }
            }

            _uiState.update { it.copy(sincronizandoNuvem = false, statusNuvem = mensagem) }
        }
    }

    /** Descarta o rascunho atual (inclusive as fotos) e inicia uma nova sessão. */
    fun limparAuditoria() {
        reiniciarSessao(descartarFotos = true)
    }

    /** Arquiva a auditoria atual no histórico, preservando as fotos, e inicia uma nova sessão. */
    fun fecharEArquivarAuditoria() {
        val estado = _uiState.value
        val concluida = AuditoriaConcluida(
            dataFechamento = formatarAgora("dd/MM/yyyy HH:mm"),
            plano = estado.plano,
            itens = estado.itens
        )
        val novoHistorico = estado.historico + concluida
        repository.salvarHistorico(novoHistorico)
        _uiState.update { it.copy(historico = novoHistorico) }
        reiniciarSessao(descartarFotos = false)
        salvarNoFirestore(concluida)
    }

    /**
     * Envia a auditoria fechada (plano + itens auditados) para o Cloud Firestore.
     * As fotos continuam no aparelho; vai só o caminho local de cada uma.
     */
    private fun salvarNoFirestore(concluida: AuditoriaConcluida) {
        val registro = concluida.paraRegistro(authRepository.usuarioAtual().orEmpty())
        viewModelScope.launch {
            _uiState.update { it.copy(statusFirestore = "Salvando auditoria ${registro.protocolo} no Firestore...") }
            val mensagem = registroRepository.cadastrar(registro).fold(
                onSuccess = { resultado ->
                    if (resultado.confirmadoNoServidor) {
                        "Auditoria ${registro.protocolo} salva no Firestore."
                    } else {
                        "Sem conexão: a auditoria ${registro.protocolo} será enviada ao Firestore quando a internet voltar."
                    }
                },
                onFailure = { erro -> "Falha ao salvar a auditoria no Firestore: ${erro.message}" }
            )
            _uiState.update { it.copy(statusFirestore = mensagem) }
        }
    }

    private fun reiniciarSessao(descartarFotos: Boolean) {
        if (descartarFotos) {
            _uiState.value.itens.forEach { repository.excluirFoto(it.caminhoFoto) }
        }
        val novoPlano = PlanoAuditoria()
        val itensIniciais = repository.itensIniciaisPolimorficos()
        repository.salvarPlano(novoPlano)
        repository.salvarItens(itensIniciais)
        // copy preserva o histórico e o status da nuvem.
        _uiState.update { it.copy(plano = novoPlano, itens = itensIniciais) }
    }

    private fun substituirItem(itemAtualizado: ItemAuditoria) {
        val novaLista = _uiState.value.itens.map { if (it.id == itemAtualizado.id) itemAtualizado else it }
        repository.salvarItens(novaLista)
        _uiState.update { it.copy(itens = novaLista) }
    }
}

class AuditoriaViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AuditoriaViewModel(AuditoriaRepository(context.applicationContext)) as T
    }
}
