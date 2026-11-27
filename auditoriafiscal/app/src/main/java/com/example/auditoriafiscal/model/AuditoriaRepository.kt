package com.example.auditoriafiscal.model

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuditoriaRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("auditoria_db_v3", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonNuvem = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listaSerializer = ListSerializer(ItemAuditoria.serializer())
    private val historicoSerializer = ListSerializer(AuditoriaConcluida.serializer())

    // ------------------------------------------------------------------
    // Persistência local
    // ------------------------------------------------------------------

    fun salvarPlano(plano: PlanoAuditoria) {
        prefs.edit().putString(CHAVE_PLANO, json.encodeToString(PlanoAuditoria.serializer(), plano)).apply()
    }

    fun carregarPlano(): PlanoAuditoria {
        val salvo = prefs.getString(CHAVE_PLANO, null) ?: return PlanoAuditoria()
        return try {
            json.decodeFromString(PlanoAuditoria.serializer(), salvo)
        } catch (e: Exception) {
            PlanoAuditoria()
        }
    }

    fun salvarHistorico(auditorias: List<AuditoriaConcluida>) {
        prefs.edit().putString(CHAVE_HISTORICO, json.encodeToString(historicoSerializer, auditorias)).apply()
    }

    fun carregarHistorico(): List<AuditoriaConcluida> {
        val salvo = prefs.getString(CHAVE_HISTORICO, null) ?: return emptyList()
        return try {
            json.decodeFromString(historicoSerializer, salvo)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun salvarItens(itens: List<ItemAuditoria>) {
        prefs.edit().putString(CHAVE_ITENS, json.encodeToString(listaSerializer, itens)).apply()
    }

    fun carregarItens(): List<ItemAuditoria> {
        val salvo = prefs.getString(CHAVE_ITENS, null)
        if (salvo != null) {
            try {
                return json.decodeFromString(listaSerializer, salvo)
            } catch (e: Exception) {
                // Schema alterado: recarrega os itens iniciais.
            }
        }
        return itensIniciaisPolimorficos()
    }

    fun novoArquivoFoto(idItem: Int): File {
        val pasta = File(context.filesDir, "evidencias").apply { mkdirs() }
        return File(pasta, "item_${idItem}_${System.currentTimeMillis()}.jpg").apply { createNewFile() }
    }

    fun excluirFoto(caminho: String?) {
        if (caminho != null) File(caminho).delete()
    }

    // ------------------------------------------------------------------
    // Cloudflare (Workers + D1 + R2)
    // ------------------------------------------------------------------

    /**
     * Executa uma requisição HTTP e devolve o código de resposta.
     * Sempre libera a conexão ao final.
     */
    private fun enviar(
        caminho: String,
        metodo: String,
        contentType: String,
        tamanhoCorpo: Long,
        escreverCorpo: (OutputStream) -> Unit
    ): Int {
        val conexao = URL("$URL_WORKER$caminho").openConnection() as HttpURLConnection
        try {
            conexao.requestMethod = metodo
            conexao.connectTimeout = 10_000
            conexao.readTimeout = 15_000
            conexao.doOutput = true
            conexao.setRequestProperty("Content-Type", contentType)
            conexao.setRequestProperty("Authorization", "Bearer $TOKEN_API")
            conexao.setFixedLengthStreamingMode(tamanhoCorpo)
            conexao.outputStream.use(escreverCorpo)
            return conexao.responseCode
        } finally {
            conexao.disconnect()
        }
    }

    /** Envia plano, itens e histórico ao Cloudflare D1 (chamar fora da thread principal). */
    fun sincronizarComCloudflare(
        plano: PlanoAuditoria,
        itens: List<ItemAuditoria>,
        historico: List<AuditoriaConcluida>
    ): Result<String> = runCatching {
        val corpo = jsonNuvem
            .encodeToString(PayloadSincronizacao.serializer(), PayloadSincronizacao(plano, itens, historico))
            .toByteArray(Charsets.UTF_8)
        val codigo = enviar("/api/sincronizar", "POST", "application/json; charset=utf-8", corpo.size.toLong()) {
            it.write(corpo)
        }
        if (codigo !in 200..299) throw IllegalStateException("O servidor respondeu HTTP $codigo")
        "Dados sincronizados (HTTP $codigo)"
    }

    /** Envia a foto de evidência ao bucket R2 (chamar fora da thread principal). */
    fun enviarFotoParaCloudflareR2(protocolo: String, idItem: Int, caminhoFoto: String): Result<String> = runCatching {
        val arquivo = File(caminhoFoto)
        if (!arquivo.exists()) throw IllegalStateException("Arquivo de foto não encontrado")

        val protocoloUrl = URLEncoder.encode(protocolo, "UTF-8")
        val codigo = enviar("/api/evidencia/$idItem?protocolo=$protocoloUrl", "PUT", "image/jpeg", arquivo.length()) { saida ->
            arquivo.inputStream().use { entrada -> entrada.copyTo(saida) }
        }
        if (codigo !in 200..299) throw IllegalStateException("Falha no upload da evidência: HTTP $codigo")
        "Evidência salva no Cloudflare R2"
    }

    // ------------------------------------------------------------------
    // OCR on-device (Google ML Kit)
    // ------------------------------------------------------------------

    /** Lê o texto da foto e devolve um resumo dos campos fiscais encontrados. */
    suspend fun analisarFotoComIA(caminhoFoto: String): String {
        val bitmap = withContext(Dispatchers.IO) { ImagemUtils.carregarBitmap(caminhoFoto, 1600) }
            ?: return "Não foi possível carregar a imagem para leitura."
        return try {
            interpretarTextoFiscal(reconhecerTexto(bitmap))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Falha na leitura OCR: ${e.localizedMessage ?: "erro desconhecido"}"
        }
    }

    private suspend fun reconhecerTexto(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        reconhecedor.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            .addOnCompleteListener { reconhecedor.close() }
    }

    private fun interpretarTextoFiscal(textoBruto: String): String {
        if (textoBruto.isBlank()) return "Foto validada, mas nenhum texto legível foi detectado."

        val cnpj = REGEX_CNPJ.find(textoBruto)?.value
        val data = REGEX_DATA.find(textoBruto)?.value
        val valor = REGEX_VALOR.find(textoBruto)?.value
        val codDarf = REGEX_DARF.find(textoBruto)?.value
        val chaveNfe = REGEX_CHAVE.find(textoBruto)?.value
            ?.filter { it.isDigit() }
            ?.takeIf { it.length == 44 }

        val tipoDoc = when {
            textoBruto.contains("DARF", ignoreCase = true) -> "Guia DARF"
            textoBruto.contains("DANFE", ignoreCase = true) || chaveNfe != null -> "Nota Fiscal (NF-e)"
            textoBruto.contains("FGTS", ignoreCase = true) ||
                    textoBruto.contains("eSocial", ignoreCase = true) -> "Guia Trabalhista"
            else -> "Documento Fiscal"
        }

        val campos = mutableListOf("Tipo: $tipoDoc")
        cnpj?.let { campos.add("CNPJ: $it") }
        codDarf?.let { campos.add("Cód. Receita: $it") }
        data?.let { campos.add("Vencimento/Data: $it") }
        valor?.let { campos.add("Valor: $it") }
        chaveNfe?.let { campos.add("Chave NF-e: ${it.take(8)}...${it.takeLast(4)}") }

        return if (campos.size > 1) {
            "[OCR] " + campos.joinToString(" | ")
        } else {
            val trecho = textoBruto.replace("\n", " ").trim().take(120)
            "[OCR] Resumo: $trecho..."
        }
    }

    // ------------------------------------------------------------------
    // Dados iniciais do checklist
    // ------------------------------------------------------------------

    fun itensIniciaisPolimorficos(): List<ItemAuditoria> = listOf(
        ItemTributoFederal(
            id = 1,
            codigoPapelTrabalho = "RFB-01",
            descricao = "Verificar guias de DARF pagas e conciliadas com DCTFWeb",
            codigoDarf = "0561"
        ),
        ItemTributoFederal(
            id = 2,
            codigoPapelTrabalho = "RFB-02",
            descricao = "Conferir apuração de IRPJ e CSLL no período",
            codigoDarf = "2089"
        ),
        ItemTributoFederal(
            id = 3,
            codigoPapelTrabalho = "RFB-03",
            descricao = "Validar recolhimento de PIS/COFINS (EFD-Contribuições)",
            codigoDarf = "6912"
        ),
        ItemTributoEstadual(
            id = 4,
            codigoPapelTrabalho = "SEFAZ-01",
            descricao = "Verificar escrituração de notas fiscais de ICMS",
            modeloDocumentoFiscal = "NF-e Modelo 55"
        ),
        ItemTributoEstadual(
            id = 5,
            codigoPapelTrabalho = "SEFAZ-02",
            descricao = "Conferir recolhimento de guias de ICMS-ST / DIFAL",
            modeloDocumentoFiscal = "GNRE Estadual"
        ),
        ItemTrabalhista(
            id = 6,
            codigoPapelTrabalho = "MTE-01",
            descricao = "Analisar folhas de pagamento e fechamento do eSocial",
            normaReferencia = "CLT Art. 464 / eSocial S-1200"
        ),
        ItemTrabalhista(
            id = 7,
            codigoPapelTrabalho = "MTE-02",
            descricao = "Verificar regularidade dos depósitos do FGTS Digital",
            normaReferencia = "Lei nº 8.036/90 (Art. 15)"
        )
    )

    companion object {
        private const val CHAVE_ITENS = "itens_polimorficos_v3"
        private const val CHAVE_PLANO = "plano_auditoria_v3"
        private const val CHAVE_HISTORICO = "historico_v3"

        // Atenção: qualquer valor embutido no APK pode ser extraído. Em produção, use
        // autenticação por usuário no backend e mantenha o token fora do repositório
        // (local.properties + BuildConfig).
        private const val URL_WORKER = "https://auditoria-api.falcaof22.workers.dev"
        private const val TOKEN_API = "SENHA_SECRETA_FATEC_2026"

        private val REGEX_CNPJ = Regex("""\b[0-9A-Z]{2}\.[0-9A-Z]{3}\.[0-9A-Z]{3}/[0-9A-Z]{4}-[0-9]{2}\b""")
        private val REGEX_DATA = Regex("""\b\d{2}/\d{2}/\d{4}\b""")
        private val REGEX_VALOR = Regex("""R\$\s?(?:\d{1,3}(?:\.\d{3})+|\d+),\d{2}""")
        private val REGEX_DARF = Regex("""(?<!\d)(?:0561|2089|6912|5952|1708)(?!\d)""")
        private val REGEX_CHAVE = Regex("""(?:\d{4}\s?){11}""")
    }
}