package com.example.auditoriafiscal.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalePtBr: Locale = Locale.forLanguageTag("pt-BR")

fun formatarAgora(padrao: String): String = SimpleDateFormat(padrao, LocalePtBr).format(Date())

/** Estados possíveis de um item do checklist. */
object StatusItem {
    const val PENDENTE = "Pendente"
    const val CONFORME = "Conforme"
    const val NAO_CONFORME = "Não Conforme"
}

/** Níveis de severidade de uma não conformidade. */
object Severidade {
    const val BAIXA = "Baixa"
    const val MEDIA = "Média"
    const val ALTA = "Alta"
    val todas = listOf(BAIXA, MEDIA, ALTA)
}

/** Categorias do checklist. */
object Categorias {
    const val FEDERAL = "Tributos Federais"
    const val ESTADUAL = "Tributos Estaduais"
    const val TRABALHISTA = "Trabalhista"
    val todas = listOf(FEDERAL, ESTADUAL, TRABALHISTA)
}

/** Estados possíveis de um apontamento (US04). */
object StatusApontamento {
    const val PENDENTE = "Pendente"
    const val EM_ANDAMENTO = "Em Andamento"
    const val RESOLVIDO = "Resolvido"
    val todos = listOf(PENDENTE, EM_ANDAMENTO, RESOLVIDO)
}

/**
 * Cabeçalho do plano de auditoria (ABNT NBR ISO 19011 - Seção 6.3).
 * Identifica formalmente a entidade auditada e o período fiscal antes do checklist.
 */
@Serializable
data class PlanoAuditoria(
    val protocolo: String = "PT-" + formatarAgora("yyyyMMdd-HHmmss"),
    val razaoSocial: String = "",
    val cnpj: String = "",
    val competenciaFiscal: String = "",
    val responsavelAuditado: String = "",
    val dataAbertura: String = formatarAgora("dd/MM/yyyy")
) {
    val configurado: Boolean get() = razaoSocial.isNotBlank() && cnpj.isNotBlank()
}

/**
 * Registro de não conformidade (ISO 19011 - Seção 6.4.7).
 * Reúne critério normativo violado, constatação, prazo e status do apontamento.
 */
@Serializable
data class NaoConformidade(
    val idItem: Int,
    val nivelSeveridade: String = "",
    val criterioNormativo: String = "",
    val observacao: String = "",
    val prazoResolucao: String = "",
    val status: String = StatusApontamento.PENDENTE
) {
    fun obterStatus(): String = status
}

/**
 * Classe base polimórfica do papel de trabalho (CFC NBC TA 230).
 * Os comportamentos específicos de cada categoria ficam nas classes filhas.
 */
@Serializable
sealed class ItemAuditoria {
    abstract val id: Int
    abstract val codigoPapelTrabalho: String
    abstract val categoria: String
    abstract val descricao: String
    abstract val status: String
    abstract val naoConformidade: NaoConformidade?
    abstract val caminhoFoto: String?
    abstract val timestampFoto: String?

    val temEvidencia: Boolean get() = caminhoFoto != null

    abstract fun obterBaseLegalPadrao(): String
    abstract fun obterOrgaoFiscalizador(): String
    abstract fun sugerirSeveridadePadrao(): String
    abstract fun copiarComStatus(
        novoStatus: String = this.status,
        novaNC: NaoConformidade? = this.naoConformidade,
        novaFoto: String? = this.caminhoFoto,
        novoTimestamp: String? = this.timestampFoto
    ): ItemAuditoria
}

@Serializable
data class ItemTributoFederal(
    override val id: Int,
    override val codigoPapelTrabalho: String,
    override val descricao: String,
    val codigoDarf: String,
    override val status: String = StatusItem.PENDENTE,
    override val naoConformidade: NaoConformidade? = null,
    override val caminhoFoto: String? = null,
    override val timestampFoto: String? = null
) : ItemAuditoria() {

    override val categoria: String = Categorias.FEDERAL

    override fun obterBaseLegalPadrao(): String =
        "Lei nº 9.430/96 e CTN Art. 195 (Cód. Receita DARF: $codigoDarf)"

    override fun obterOrgaoFiscalizador(): String = "Receita Federal do Brasil (RFB / SPED)"

    override fun sugerirSeveridadePadrao(): String = Severidade.ALTA

    override fun copiarComStatus(
        novoStatus: String,
        novaNC: NaoConformidade?,
        novaFoto: String?,
        novoTimestamp: String?
    ): ItemAuditoria = copy(
        status = novoStatus,
        naoConformidade = novaNC,
        caminhoFoto = novaFoto,
        timestampFoto = novoTimestamp
    )
}

@Serializable
data class ItemTributoEstadual(
    override val id: Int,
    override val codigoPapelTrabalho: String,
    override val descricao: String,
    val modeloDocumentoFiscal: String,
    override val status: String = StatusItem.PENDENTE,
    override val naoConformidade: NaoConformidade? = null,
    override val caminhoFoto: String? = null,
    override val timestampFoto: String? = null
) : ItemAuditoria() {

    override val categoria: String = Categorias.ESTADUAL

    override fun obterBaseLegalPadrao(): String =
        "Lei Complementar nº 87/96 (Lei Kandir) - Doc: $modeloDocumentoFiscal"

    override fun obterOrgaoFiscalizador(): String = "Secretaria da Fazenda Estadual (SEFAZ)"

    override fun sugerirSeveridadePadrao(): String = Severidade.MEDIA

    override fun copiarComStatus(
        novoStatus: String,
        novaNC: NaoConformidade?,
        novaFoto: String?,
        novoTimestamp: String?
    ): ItemAuditoria = copy(
        status = novoStatus,
        naoConformidade = novaNC,
        caminhoFoto = novaFoto,
        timestampFoto = novoTimestamp
    )
}

@Serializable
data class ItemTrabalhista(
    override val id: Int,
    override val codigoPapelTrabalho: String,
    override val descricao: String,
    val normaReferencia: String,
    override val status: String = StatusItem.PENDENTE,
    override val naoConformidade: NaoConformidade? = null,
    override val caminhoFoto: String? = null,
    override val timestampFoto: String? = null
) : ItemAuditoria() {

    override val categoria: String = Categorias.TRABALHISTA

    override fun obterBaseLegalPadrao(): String =
        "CLT / eSocial - Diretriz: $normaReferencia"

    override fun obterOrgaoFiscalizador(): String = "Ministério do Trabalho e Emprego (MTE)"

    override fun sugerirSeveridadePadrao(): String = Severidade.ALTA

    override fun copiarComStatus(
        novoStatus: String,
        novaNC: NaoConformidade?,
        novaFoto: String?,
        novoTimestamp: String?
    ): ItemAuditoria = copy(
        status = novoStatus,
        naoConformidade = novaNC,
        caminhoFoto = novaFoto,
        timestampFoto = novoTimestamp
    )
}

/** Auditoria finalizada e arquivada no histórico. */
@Serializable
data class AuditoriaConcluida(
    val dataFechamento: String,
    val plano: PlanoAuditoria,
    val itens: List<ItemAuditoria>
)

/** Corpo enviado ao Worker da Cloudflare na sincronização. */
@Serializable
data class PayloadSincronizacao(
    val plano: PlanoAuditoria,
    val itens: List<ItemAuditoria>,
    val historico: List<AuditoriaConcluida>
)