package com.example.auditoriafiscal.model

/**
 * MODEL - Entidade "Registro de auditoria" gravada no Cloud Firestore
 * (coleção "registros_auditoria").
 *
 * Pode nascer de dois jeitos:
 *  - cadastro manual (tela "Novo registro"): só os dados básicos, sem itens;
 *  - fechamento de uma auditoria do checklist: dados do plano + todos os itens auditados.
 *
 * Só representa os dados: não sabe nada de tela nem de Firebase.
 */
data class RegistroAuditoria(
    val id: String = "",
    val empresa: String = "",
    val cnpj: String = "",
    val tipo: String = "",
    val data: String = "",          // dd/MM/yyyy
    val situacao: String = "",
    val criadoPor: String = "",     // e-mail do usuário logado que cadastrou
    // Preenchidos quando o registro vem de uma auditoria fechada:
    val protocolo: String = "",
    val competencia: String = "",
    val responsavel: String = "",
    val dataFechamento: String = "",
    val itens: List<ItemRegistro> = emptyList()
) {
    val temChecklist: Boolean get() = itens.isNotEmpty()
    val conformes: Int get() = itens.count { it.status == StatusItem.CONFORME }
    val naoConformes: Int get() = itens.count { it.status == StatusItem.NAO_CONFORME }
    val pendentes: Int get() = itens.count { it.status == StatusItem.PENDENTE }
}

/**
 * Cópia "achatada" de um item do checklist para guardar no Firestore.
 * A foto fica no aparelho: aqui vai só o caminho local e o horário da captura.
 */
data class ItemRegistro(
    val codigo: String = "",
    val categoria: String = "",
    val descricao: String = "",
    val baseLegal: String = "",
    val status: String = "",
    val severidade: String = "",
    val criterio: String = "",
    val observacao: String = "",
    val prazo: String = "",
    val statusApontamento: String = "",
    val caminhoFoto: String? = null,
    val timestampFoto: String? = null
)

/** Tipos de auditoria (reaproveita as categorias do checklist do MVP). */
object TiposRegistro {
    const val GERAL = "Geral"
    val todos = listOf(GERAL) + Categorias.todas
}

/** Situações possíveis de um registro. */
object SituacaoRegistro {
    const val PENDENTE = "Pendente"
    const val EM_ANDAMENTO = "Em andamento"
    const val CONCLUIDA = "Concluída"
    val todas = listOf(PENDENTE, EM_ANDAMENTO, CONCLUIDA)
}

/** Converte uma auditoria fechada (checklist do MVP) no registro que vai para o Firestore. */
fun AuditoriaConcluida.paraRegistro(usuario: String): RegistroAuditoria {
    val itensRegistro = itens.map { item ->
        val nc = item.naoConformidade
        ItemRegistro(
            codigo = item.codigoPapelTrabalho,
            categoria = item.categoria,
            descricao = item.descricao,
            baseLegal = item.obterBaseLegalPadrao(),
            status = item.status,
            severidade = nc?.nivelSeveridade.orEmpty(),
            criterio = nc?.criterioNormativo.orEmpty(),
            observacao = nc?.observacao.orEmpty(),
            prazo = nc?.prazoResolucao.orEmpty(),
            statusApontamento = nc?.status.orEmpty(),
            caminhoFoto = item.caminhoFoto,
            timestampFoto = item.timestampFoto
        )
    }
    val semPendencias = itensRegistro.none { it.status == StatusItem.PENDENTE }
    return RegistroAuditoria(
        empresa = plano.razaoSocial,
        cnpj = plano.cnpj,
        tipo = TiposRegistro.GERAL,
        data = dataFechamento.take(10),     // "dd/MM/yyyy HH:mm" -> "dd/MM/yyyy"
        situacao = if (semPendencias) SituacaoRegistro.CONCLUIDA else SituacaoRegistro.EM_ANDAMENTO,
        criadoPor = usuario,
        protocolo = plano.protocolo,
        competencia = plano.competenciaFiscal,
        responsavel = plano.responsavelAuditado,
        dataFechamento = dataFechamento,
        itens = itensRegistro
    )
}
