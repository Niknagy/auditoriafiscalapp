package com.example.auditoriafiscal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.auditoriafiscal.R
import com.example.auditoriafiscal.model.ImagemUtils
import com.example.auditoriafiscal.model.ItemAuditoria
import com.example.auditoriafiscal.model.LocalePtBr
import com.example.auditoriafiscal.model.Severidade
import com.example.auditoriafiscal.model.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

const val MASCARA_DATA = "##/##/####"
const val MASCARA_CNPJ = "##.###.###/####-##"
const val MASCARA_COMPETENCIA = "##/####"

// ----------------------------------------------------------------------
// Máscaras e validações
// ----------------------------------------------------------------------

/** Aplica a máscara ('#' = caractere do valor); os literais só aparecem quando há caractere seguinte. */
fun aplicarMascara(valor: String, mascara: String): String {
    val saida = StringBuilder()
    var indice = 0
    for (c in mascara) {
        if (indice >= valor.length) break
        if (c == '#') {
            saida.append(valor[indice])
            indice++
        } else {
            saida.append(c)
        }
    }
    return saida.toString()
}

class MascaraVisualTransformation(private val mascara: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val maximo = mascara.count { it == '#' }
        val digitos = text.text.take(maximo)
        val formatado = aplicarMascara(digitos, mascara)

        val mapeamento = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var contados = 0
                for ((indice, c) in mascara.withIndex()) {
                    if (c == '#') {
                        contados++
                        if (contados == offset) return (indice + 1).coerceAtMost(formatado.length)
                    }
                }
                return formatado.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                val limite = offset.coerceIn(0, mascara.length)
                return mascara.take(limite).count { it == '#' }.coerceAtMost(digitos.length)
            }
        }
        return TransformedText(AnnotatedString(formatado), mapeamento)
    }
}

fun prazoEhValido(prazo: String): Boolean {
    if (prazo.length != 10) return false
    return try {
        val formato = SimpleDateFormat("dd/MM/yyyy", LocalePtBr).apply { isLenient = false }
        val data = formato.parse(prazo) ?: return false
        val hoje = formato.parse(formato.format(Date())) ?: return false
        !data.before(hoje)
    } catch (e: Exception) {
        false
    }
}

/** Recebe apenas os dígitos (MMAAAA). */
fun competenciaEhValida(raw: String): Boolean {
    if (raw.length != 6) return false
    val mes = raw.substring(0, 2).toIntOrNull() ?: return false
    val ano = raw.substring(2).toIntOrNull() ?: return false
    return mes in 1..12 && ano in 2000..2100
}

/** Valida CNPJ numérico e alfanumérico (14 caracteres, sem máscara) pelos dígitos verificadores. */
fun cnpjEhValido(cnpj: String): Boolean {
    val c = cnpj.filter { it.isLetterOrDigit() }.uppercase()
    if (c.length != 14 || c.all { it == c[0] }) return false
    if (!c.takeLast(2).all { it.isDigit() }) return false

    fun digito(base: String): Int {
        val pesos = if (base.length == 12) {
            intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        } else {
            intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        }
        val soma = base.indices.sumOf { (base[it].code - 48) * pesos[it] }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }

    val d1 = digito(c.substring(0, 12))
    val d2 = digito(c.substring(0, 12) + d1)
    return (c[12] - '0') == d1 && (c[13] - '0') == d2
}

// ----------------------------------------------------------------------
// Componentes
// ----------------------------------------------------------------------

@Composable
fun MiniaturaEvidencia(caminho: String, timestamp: String?) {
    // Decodificação fora da thread principal.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = caminho) {
        value = withContext(Dispatchers.IO) {
            ImagemUtils.carregarBitmap(caminho, 400)?.asImageBitmap()
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        val imagem = bitmap
        if (imagem != null) {
            Image(
                bitmap = imagem,
                contentDescription = "Foto de evidência",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(100.dp)
            )
        } else {
            Text(text = stringResource(R.string.foto_anexada), style = MaterialTheme.typography.labelSmall)
        }
        if (timestamp != null) {
            Text(
                text = "Capturada em: $timestamp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun CardItemSomenteLeitura(item: ItemAuditoria) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "[${item.codigoPapelTrabalho}] ${item.categoria} - ${item.obterOrgaoFiscalizador()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.descricao, fontWeight = FontWeight.Bold)
            Text(
                text = "Base legal: ${item.obterBaseLegalPadrao()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))

            val nc = item.naoConformidade
            if (item.status == StatusItem.NAO_CONFORME && nc != null) {
                Text(text = stringResource(R.string.status_nao_conforme, nc.nivelSeveridade), color = MaterialTheme.colorScheme.error)
                Text(text = "Critério violado: ${nc.criterioNormativo}", style = MaterialTheme.typography.bodySmall)
                Text(text = stringResource(R.string.status_apontamento, nc.obterStatus()), style = MaterialTheme.typography.bodySmall)
                Text(text = stringResource(R.string.prazo_nao_conformidade, nc.prazoResolucao), style = MaterialTheme.typography.bodySmall)
                Text(text = stringResource(R.string.obs_nao_conformidade, nc.observacao), style = MaterialTheme.typography.bodySmall)
            } else {
                val cor = if (item.status == StatusItem.CONFORME) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                Text(text = "Status: ${item.status}", color = cor)
            }
            item.caminhoFoto?.let { MiniaturaEvidencia(it, item.timestampFoto) }
        }
    }
}

@Composable
fun CardItemAuditoria(
    item: ItemAuditoria,
    onConformeClick: () -> Unit,
    onEvidenciaClick: () -> Unit,
    onNaoConformeClick: () -> Unit,
    onRemoverEvidenciaClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${item.codigoPapelTrabalho} - ${item.obterBaseLegalPadrao()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.descricao, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

            val nc = item.naoConformidade
            if (item.status == StatusItem.NAO_CONFORME && nc != null) {
                Text(text = stringResource(R.string.status_nao_conforme, nc.nivelSeveridade), color = MaterialTheme.colorScheme.error)
                Text(text = "Norma: ${nc.criterioNormativo}", style = MaterialTheme.typography.bodySmall)
                Text(text = stringResource(R.string.prazo_nao_conformidade, nc.prazoResolucao), style = MaterialTheme.typography.bodySmall)
            } else if (item.status == StatusItem.CONFORME) {
                Text(text = stringResource(R.string.status_conforme), color = MaterialTheme.colorScheme.tertiary)
            } else {
                Text(text = stringResource(R.string.status_pendente), color = MaterialTheme.colorScheme.secondary)
            }

            item.caminhoFoto?.let { caminho ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniaturaEvidencia(caminho, item.timestampFoto)
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onRemoverEvidenciaClick) {
                        Text("Excluir foto", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val paddingBotao = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onConformeClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = paddingBotao,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) { Text(stringResource(R.string.btn_ok), maxLines = 1) }
                Button(
                    onClick = onEvidenciaClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = paddingBotao,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text(stringResource(R.string.btn_foto), maxLines = 1) }
                Button(
                    onClick = onNaoConformeClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = paddingBotao,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.btn_falha), maxLines = 1) }
            }
        }
    }
}

@Composable
fun DialogNaoConformidade(
    item: ItemAuditoria,
    onDismiss: () -> Unit,
    onLerFotoIA: ((String) -> Unit) -> Unit = {},
    onSave: (severidade: String, criterio: String, observacao: String, prazo: String) -> Unit
) {
    val existente = item.naoConformidade

    var severidade by remember(item.id) {
        mutableStateOf(existente?.nivelSeveridade?.takeIf { it.isNotBlank() } ?: item.sugerirSeveridadePadrao())
    }
    var criterioNormativo by remember(item.id) {
        mutableStateOf(existente?.criterioNormativo?.takeIf { it.isNotBlank() } ?: item.obterBaseLegalPadrao())
    }
    var observacao by remember(item.id) { mutableStateOf(existente?.observacao ?: "") }
    var prazoRaw by remember(item.id) { mutableStateOf((existente?.prazoResolucao ?: "").filter { it.isDigit() }.take(8)) }
    var lendoComIA by remember { mutableStateOf(false) }

    val prazoValido = prazoRaw.length == 8 && prazoEhValido(aplicarMascara(prazoRaw, MASCARA_DATA))
    val podeSalvar = severidade.isNotEmpty() && criterioNormativo.isNotBlank() && observacao.isNotBlank() && prazoValido

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.titulo_nao_conformidade)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${item.codigoPapelTrabalho} - ${item.descricao}",
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (item.caminhoFoto != null) {
                    FilledTonalButton(
                        onClick = {
                            lendoComIA = true
                            onLerFotoIA { textoExtraido ->
                                observacao = if (observacao.isBlank()) {
                                    textoExtraido
                                } else {
                                    observacao.trimEnd() + "\n" + textoExtraido
                                }
                                lendoComIA = false
                            }
                        },
                        enabled = !lendoComIA,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (lendoComIA) "Lendo documento fiscal..." else "Ler foto com OCR (ML Kit)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "Dica: registre uma foto antes de abrir a falha para preencher a observação por OCR.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = criterioNormativo,
                    onValueChange = { criterioNormativo = it },
                    label = { Text("Critério normativo / lei (ISO 19011)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = observacao,
                    onValueChange = { observacao = it },
                    label = { Text(stringResource(R.string.label_observacao)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = prazoRaw,
                    onValueChange = { valor ->
                        prazoRaw = valor.filter { it.isDigit() }.take(8)
                    },
                    label = { Text(stringResource(R.string.label_prazo)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = MascaraVisualTransformation(MASCARA_DATA),
                    singleLine = true,
                    isError = prazoRaw.length == 8 && !prazoValido,
                    modifier = Modifier.fillMaxWidth()
                )
                if (prazoRaw.length == 8 && !prazoValido) {
                    Text(
                        stringResource(R.string.prazo_invalido),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.selecione_severidade))
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Severidade.todas.forEach { nivel ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(
                                selected = severidade == nivel,
                                onClick = { severidade = nivel },
                                role = Role.RadioButton
                            )
                        ) {
                            RadioButton(selected = severidade == nivel, onClick = null)
                            Text(nivel, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        severidade,
                        criterioNormativo.trim(),
                        observacao.trim(),
                        aplicarMascara(prazoRaw, MASCARA_DATA)
                    )
                },
                enabled = podeSalvar
            ) {
                Text(stringResource(R.string.btn_salvar))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancelar)) } }
    )
}

@Composable
fun CampoMascarado(
    valor: String,
    onValorChange: (String) -> Unit,
    mascara: String,
    rotulo: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    erro: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(rotulo) },
        keyboardOptions = teclado,
        visualTransformation = MascaraVisualTransformation(mascara),
        singleLine = true,
        isError = erro,
        modifier = modifier
    )
}

/** Opções de teclado para o CNPJ (alfanumérico, em maiúsculas). */
val TecladoCnpj = KeyboardOptions(
    capitalization = KeyboardCapitalization.Characters,
    keyboardType = KeyboardType.Ascii
)
