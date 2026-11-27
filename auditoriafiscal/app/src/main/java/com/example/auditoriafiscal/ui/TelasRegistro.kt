package com.example.auditoriafiscal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auditoriafiscal.model.ItemRegistro
import com.example.auditoriafiscal.model.RegistroAuditoria
import com.example.auditoriafiscal.model.StatusItem
import com.example.auditoriafiscal.model.SituacaoRegistro
import com.example.auditoriafiscal.model.TiposRegistro
import com.example.auditoriafiscal.model.formatarAgora
import com.example.auditoriafiscal.viewmodel.RegistroViewModel
import kotlinx.serialization.Serializable
import java.io.File

@Serializable data class RotaCadastroRegistro(val empresa: String = "", val cnpj: String = "")
@Serializable object RotaPesquisaRegistro

// ----------------------------------------------------------------------
// VIEW - Cadastro de Registro de auditoria (Firestore)
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaCadastroRegistro(
    viewModel: RegistroViewModel,
    empresaInicial: String,
    cnpjInicial: String,
    onVoltar: () -> Unit
) {
    val estado by viewModel.cadastro.collectAsState()

    // Os campos começam com os dados do plano de auditoria atual (integração com o MVP).
    // CNPJ e data ficam guardados só com os caracteres; a máscara é aplicada na exibição.
    var empresa by rememberSaveable { mutableStateOf(empresaInicial) }
    var cnpjRaw by rememberSaveable { mutableStateOf(somenteCnpj(cnpjInicial)) }
    var tipo by rememberSaveable { mutableStateOf(TiposRegistro.GERAL) }
    var dataRaw by rememberSaveable { mutableStateOf(hojeSemMascara()) }
    var situacao by rememberSaveable { mutableStateOf(SituacaoRegistro.PENDENTE) }

    // Depois de salvar com sucesso, limpa o formulário para um novo cadastro.
    LaunchedEffect(estado.sucesso) {
        if (estado.sucesso) {
            empresa = ""
            cnpjRaw = ""
            tipo = TiposRegistro.GERAL
            dataRaw = hojeSemMascara()
            situacao = SituacaoRegistro.PENDENTE
        }
    }

    Scaffold(topBar = { BarraTopo("Novo registro de auditoria", onVoltar) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = empresa,
                onValueChange = { empresa = it; viewModel.limparMensagemCadastro() },
                label = { Text("Empresa *") },
                singleLine = true,
                isError = estado.errosCampos.containsKey(RegistroViewModel.CAMPO_EMPRESA),
                supportingText = { estado.errosCampos[RegistroViewModel.CAMPO_EMPRESA]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            CampoMascarado(
                valor = cnpjRaw,
                onValorChange = { cnpjRaw = somenteCnpj(it); viewModel.limparMensagemCadastro() },
                mascara = MASCARA_CNPJ,
                rotulo = "CNPJ (opcional)",
                teclado = TecladoCnpj,
                erro = estado.errosCampos.containsKey(RegistroViewModel.CAMPO_CNPJ),
                modifier = Modifier.fillMaxWidth()
            )
            estado.errosCampos[RegistroViewModel.CAMPO_CNPJ]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text("Tipo *", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TiposRegistro.todos.forEach { opcao ->
                    FilterChip(selected = tipo == opcao, onClick = { tipo = opcao }, label = { Text(opcao) })
                }
            }

            CampoMascarado(
                valor = dataRaw,
                onValorChange = { dataRaw = it.filter { c -> c.isDigit() }.take(8); viewModel.limparMensagemCadastro() },
                mascara = MASCARA_DATA,
                rotulo = "Data * (dd/mm/aaaa)",
                erro = estado.errosCampos.containsKey(RegistroViewModel.CAMPO_DATA),
                modifier = Modifier.fillMaxWidth()
            )
            estado.errosCampos[RegistroViewModel.CAMPO_DATA]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text("Situação *", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SituacaoRegistro.todas.forEach { opcao ->
                    FilterChip(selected = situacao == opcao, onClick = { situacao = opcao }, label = { Text(opcao) })
                }
            }

            estado.mensagem?.let {
                Text(
                    text = it,
                    color = if (estado.sucesso) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = {
                    viewModel.cadastrar(
                        empresa = empresa,
                        cnpj = aplicarMascara(cnpjRaw, MASCARA_CNPJ),
                        tipo = tipo,
                        data = aplicarMascara(dataRaw, MASCARA_DATA),
                        situacao = situacao
                    )
                },
                enabled = !estado.salvando,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (estado.salvando) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Salvar no Firestore")
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// VIEW - Pesquisa de Registros por empresa (Firestore)
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPesquisaRegistro(viewModel: RegistroViewModel, onVoltar: () -> Unit) {
    val estado by viewModel.pesquisa.collectAsState()
    var termo by rememberSaveable { mutableStateOf("") }
    var idSelecionado by rememberSaveable { mutableStateOf<String?>(null) }

    // Detalhe do registro escolhido (os dados já vieram do Firestore na pesquisa).
    val selecionado = idSelecionado?.let { id -> estado.resultados.firstOrNull { it.id == id } }
    if (selecionado != null) {
        BackHandler { idSelecionado = null }
        TelaDetalheRegistro(selecionado) { idSelecionado = null }
        return
    }

    Scaffold(topBar = { BarraTopo("Pesquisar registros", onVoltar) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = termo,
                    onValueChange = { termo = it },
                    label = { Text("Empresa (começo do nome)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = { viewModel.pesquisar(termo) }, enabled = !estado.pesquisando) {
                    Text("Buscar")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            when {
                estado.pesquisando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                estado.erro != null -> Text(estado.erro!!, color = MaterialTheme.colorScheme.error)
                estado.termoPesquisado != null && estado.resultados.isEmpty() ->
                    Text(
                        "Nenhum registro encontrado para \"${estado.termoPesquisado}\".",
                        color = MaterialTheme.colorScheme.outline
                    )
                else -> {
                    if (estado.termoPesquisado != null) {
                        Text("${estado.resultados.size} registro(s) encontrado(s)", fontWeight = FontWeight.SemiBold)
                        if (estado.doCache) {
                            Text(
                                "Sem conexão: exibindo a última cópia salva do Firestore.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(estado.resultados, key = { it.id }) { registro ->
                            CardRegistro(registro) { idSelecionado = registro.id }
                        }
                    }
                }
            }
        }
    }
}

/** Mantém só letras/números do CNPJ (aceita o CNPJ alfanumérico), em maiúsculas. */
private fun somenteCnpj(valor: String) = valor.filter { it.isLetterOrDigit() }.uppercase().take(14)

private fun hojeSemMascara() = formatarAgora("ddMMyyyy")

@Composable
private fun CardRegistro(registro: RegistroAuditoria, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(registro.empresa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (registro.cnpj.isNotBlank()) Text("CNPJ: ${registro.cnpj}")
            Text("Tipo: ${registro.tipo} | Data: ${registro.data}")
            Text("Situação: ${registro.situacao}", fontWeight = FontWeight.SemiBold)
            if (registro.temChecklist) {
                Text(
                    "Protocolo ${registro.protocolo}: ${registro.conformes} conforme(s), " +
                        "${registro.naoConformes} falha(s), ${registro.pendentes} pendente(s)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (registro.temChecklist) "Toque para ver o que foi auditado" else "Cadastro manual (sem checklist)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

// ----------------------------------------------------------------------
// VIEW - Detalhe de um registro: dados da auditoria e itens auditados
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDetalheRegistro(registro: RegistroAuditoria, onVoltar: () -> Unit) {
    Scaffold(topBar = { BarraTopo(registro.protocolo.ifBlank { "Registro de auditoria" }, onVoltar) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(registro.empresa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (registro.cnpj.isNotBlank()) Text("CNPJ: ${registro.cnpj}")
                        if (registro.competencia.isNotBlank()) Text("Competência: ${registro.competencia}")
                        if (registro.responsavel.isNotBlank()) Text("Responsável: ${registro.responsavel}")
                        Text("Tipo: ${registro.tipo}")
                        Text("Data: ${registro.dataFechamento.ifBlank { registro.data }}")
                        Text("Situação: ${registro.situacao}", fontWeight = FontWeight.SemiBold)
                        if (registro.criadoPor.isNotBlank()) {
                            Text(
                                "Registrado por ${registro.criadoPor}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (registro.temChecklist) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Conformes: ${registro.conformes}", color = MaterialTheme.colorScheme.tertiary)
                            Text("Não conformes: ${registro.naoConformes}", color = MaterialTheme.colorScheme.error)
                            Text("Pendentes: ${registro.pendentes}", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (!registro.temChecklist) {
                item {
                    Text(
                        "Este registro foi cadastrado manualmente e não tem checklist.",
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                item { Text("Itens auditados", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(registro.itens) { CardItemRegistro(it) }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CardItemRegistro(item: ItemRegistro) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "[${item.codigo}] ${item.categoria}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(item.descricao, fontWeight = FontWeight.Bold)
            if (item.baseLegal.isNotBlank()) {
                Text(
                    "Base legal: ${item.baseLegal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            val cor = when (item.status) {
                StatusItem.CONFORME -> MaterialTheme.colorScheme.tertiary
                StatusItem.NAO_CONFORME -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Text("Status: ${item.status}", color = cor, fontWeight = FontWeight.SemiBold)
            if (item.status == StatusItem.NAO_CONFORME) {
                Text("Severidade: ${item.severidade}", style = MaterialTheme.typography.bodySmall)
                Text("Critério: ${item.criterio}", style = MaterialTheme.typography.bodySmall)
                Text("Observação: ${item.observacao}", style = MaterialTheme.typography.bodySmall)
                Text("Prazo: ${item.prazo} | Apontamento: ${item.statusApontamento}", style = MaterialTheme.typography.bodySmall)
            }
            val foto = item.caminhoFoto
            if (foto != null) {
                // A foto fica só no aparelho que fez a auditoria.
                if (File(foto).exists()) {
                    MiniaturaEvidencia(foto, item.timestampFoto)
                } else {
                    Text(
                        "Foto de evidência salva no aparelho do auditor" +
                            (item.timestampFoto?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraTopo(titulo: String, onVoltar: () -> Unit) {
    TopAppBar(
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
