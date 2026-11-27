package com.example.auditoriafiscal.repository

import com.example.auditoriafiscal.model.ItemRegistro
import com.example.auditoriafiscal.model.RegistroAuditoria
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.withTimeoutOrNull

/** Resultado de uma gravação: [confirmadoNoServidor] = false quando ficou na fila offline. */
data class ResultadoCadastro(val id: String, val confirmadoNoServidor: Boolean)

/** Resultado de uma pesquisa: [doCache] = true quando veio do cache local por falta de internet. */
data class ResultadoPesquisa(val registros: List<RegistroAuditoria>, val doCache: Boolean)

/**
 * REPOSITORY - Centraliza o acesso ao Cloud Firestore para a entidade
 * RegistroAuditoria (cadastro e pesquisa por empresa).
 */
class RegistroAuditoriaRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val colecao get() = db.collection(COLECAO)

    /**
     * Grava um documento na coleção.
     * - Auditoria fechada: o id do documento é o protocolo (gravar de novo só atualiza, não duplica).
     * - Cadastro manual: o id é gerado antes do envio.
     * Se a internet cair, o Firestore guarda a gravação na fila e envia depois.
     */
    suspend fun cadastrar(registro: RegistroAuditoria): Result<ResultadoCadastro> = runCatching {
        val documento = if (registro.protocolo.isNotBlank()) colecao.document(registro.protocolo) else colecao.document()
        val dados = hashMapOf<String, Any?>(
            "empresa" to registro.empresa,
            // Campo auxiliar em minúsculas: o Firestore diferencia maiúsculas na busca.
            "empresaBusca" to registro.empresa.lowercase(),
            "cnpj" to registro.cnpj,
            "tipo" to registro.tipo,
            "data" to registro.data,
            "situacao" to registro.situacao,
            "criadoPor" to registro.criadoPor,
            "criadoEm" to FieldValue.serverTimestamp(),
            "protocolo" to registro.protocolo,
            "competencia" to registro.competencia,
            "responsavel" to registro.responsavel,
            "dataFechamento" to registro.dataFechamento,
            "itens" to registro.itens.map { it.paraMapa() }
        )
        // Sem resposta do servidor em 10 s: a gravação continua na fila offline do Firestore.
        val confirmado = withTimeoutOrNull(TEMPO_LIMITE_MS) { documento.set(dados).aguardar() } != null
        ResultadoCadastro(documento.id, confirmado)
    }.recoverCatching { erro -> throw Exception(traduzirErro("gravar o registro", erro)) }

    /**
     * Pesquisa por empresa pelo começo do nome (ex.: "jof" encontra "Jofege Mix").
     * Os dados vêm do Cloud Firestore. Sem internet, o SDK responde com o cache local,
     * e isso é informado em [ResultadoPesquisa.doCache].
     */
    suspend fun pesquisarPorEmpresa(termo: String): Result<ResultadoPesquisa> = runCatching {
        val inicio = termo.trim().lowercase()
        val consulta = colecao
            .orderBy("empresaBusca")
            .startAt(inicio)
            .endAt(inicio + "")
            .get()
            .aguardar()
        ResultadoPesquisa(
            registros = consulta.documents.map { it.paraRegistro() },
            doCache = consulta.metadata.isFromCache
        )
    }.recoverCatching { erro -> throw Exception(traduzirErro("pesquisar", erro)) }

    private fun DocumentSnapshot.paraRegistro() = RegistroAuditoria(
        id = id,
        empresa = getString("empresa").orEmpty(),
        cnpj = getString("cnpj").orEmpty(),
        tipo = getString("tipo").orEmpty(),
        data = getString("data").orEmpty(),
        situacao = getString("situacao").orEmpty(),
        criadoPor = getString("criadoPor").orEmpty(),
        protocolo = getString("protocolo").orEmpty(),
        competencia = getString("competencia").orEmpty(),
        responsavel = getString("responsavel").orEmpty(),
        dataFechamento = getString("dataFechamento").orEmpty(),
        itens = (get("itens") as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .map { it.paraItem() }
    )

    private fun ItemRegistro.paraMapa(): Map<String, Any?> = mapOf(
        "codigo" to codigo,
        "categoria" to categoria,
        "descricao" to descricao,
        "baseLegal" to baseLegal,
        "status" to status,
        "severidade" to severidade,
        "criterio" to criterio,
        "observacao" to observacao,
        "prazo" to prazo,
        "statusApontamento" to statusApontamento,
        "caminhoFoto" to caminhoFoto,
        "timestampFoto" to timestampFoto
    )

    private fun Map<*, *>.paraItem(): ItemRegistro {
        fun texto(chave: String) = this[chave] as? String
        return ItemRegistro(
            codigo = texto("codigo").orEmpty(),
            categoria = texto("categoria").orEmpty(),
            descricao = texto("descricao").orEmpty(),
            baseLegal = texto("baseLegal").orEmpty(),
            status = texto("status").orEmpty(),
            severidade = texto("severidade").orEmpty(),
            criterio = texto("criterio").orEmpty(),
            observacao = texto("observacao").orEmpty(),
            prazo = texto("prazo").orEmpty(),
            statusApontamento = texto("statusApontamento").orEmpty(),
            caminhoFoto = texto("caminhoFoto"),
            timestampFoto = texto("timestampFoto")
        )
    }

    private fun traduzirErro(acao: String, erro: Throwable): String {
        val codigo = (erro as? FirebaseFirestoreException)?.code
        return when (codigo) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Sem permissão para $acao. Verifique as regras do Firestore."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Firestore indisponível. Verifique a conexão com a internet."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Sessão expirada. Faça login novamente."
            else -> "Não foi possível $acao: ${erro.localizedMessage ?: "erro desconhecido"}"
        }
    }

    companion object {
        const val COLECAO = "registros_auditoria"
        private const val TEMPO_LIMITE_MS = 10_000L
    }
}
