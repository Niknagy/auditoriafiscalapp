package com.example.auditoriafiscal.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.auditoriafiscal.R
import com.example.auditoriafiscal.model.AuditoriaConcluida
import com.example.auditoriafiscal.model.Categorias
import com.example.auditoriafiscal.model.ItemAuditoria
import com.example.auditoriafiscal.model.PlanoAuditoria
import com.example.auditoriafiscal.model.StatusApontamento
import com.example.auditoriafiscal.model.StatusItem
import com.example.auditoriafiscal.viewmodel.AuditoriaUiState
import com.example.auditoriafiscal.viewmodel.AuditoriaViewModel
import kotlinx.serialization.Serializable
import java.io.File

@Serializable object RotaHome
@Serializable object RotaCategorias
@Serializable data class RotaChecklist(val categoria: String)
@Serializable data class RotaResumo(val categoria: String)

// ----------------------------------------------------------------------
// Home
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaHome(navController: NavController, viewModel: AuditoriaViewModel, onSair: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val plano = uiState.plano
    val itens = uiState.itens

    var abaSelecionada by rememberSaveable { mutableStateOf(0) }
    var mostrarConfirmarNova by rememberSaveable { mutableStateOf(false) }
    var mostrarDialogPlano by rememberSaveable { mutableStateOf(false) }
    var abrirChecklistAposPlano by rememberSaveable { mutableStateOf(false) }
    var indiceHistorico by rememberSaveable { mutableStateOf<Int?>(null) }

    val titulosAbas = listOf("Geral", "Pendentes", "Falhas", "Conformes", "Histórico")
    val auditoriaParaVer = indiceHistorico?.let { uiState.historico.getOrNull(it) }

    if (auditoriaParaVer != null) {
        BackHandler { indiceHistorico = null }
        TelaVisualizarHistorico(auditoria = auditoriaParaVer) { indiceHistorico = null }
    } else {
        if (mostrarConfirmarNova) {
            AlertDialog(
                onDismissRequest = { mostrarConfirmarNova = false },
                title = { Text(text = "Nova auditoria") },
                text = { Text(text = "Iniciar uma nova auditoria apagará o rascunho atual, incluindo as fotos. Deseja continuar?") },
                confirmButton = {
                    Button(onClick = {
                        mostrarConfirmarNova = false
                        viewModel.limparAuditoria()
                        abrirChecklistAposPlano = true
                        mostrarDialogPlano = true
                    }) { Text(text = "Confirmar") }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarConfirmarNova = false }) { Text(text = "Cancelar") }
                }
            )
        }

        if (mostrarDialogPlano) {
            DialogPlanoAuditoria(
                planoAtual = plano,
                onDismiss = {
                    mostrarDialogPlano = false
                    abrirChecklistAposPlano = false
                },
                onConfirm = { razao, cnpj, comp, resp ->
                    viewModel.atualizarPlano(razao, cnpj, comp, resp)
                    mostrarDialogPlano = false
                    if (abrirChecklistAposPlano) {
                        abrirChecklistAposPlano = false
                        navController.navigate(RotaCategorias)
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Gestão de Auditorias", fontWeight = FontWeight.Bold) },
                    actions = {
                        // Registros no Firestore (incremento Firebase + MVVM)
                        IconButton(onClick = { navController.navigate(RotaCadastroRegistro(plano.razaoSocial, plano.cnpj)) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Novo registro no Firestore")
                        }
                        IconButton(onClick = { navController.navigate(RotaPesquisaRegistro) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Pesquisar registros")
                        }
                        TextButton(onClick = onSair) {
                            Text(text = "Sair", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (uiState.pendentes == itens.size) {
                            viewModel.limparAuditoria()
                            abrirChecklistAposPlano = true
                            mostrarDialogPlano = true
                        } else {
                            mostrarConfirmarNova = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(text = "Nova auditoria", fontWeight = FontWeight.Bold)
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = abaSelecionada,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    titulosAbas.forEachIndexed { index, titulo ->
                        Tab(
                            selected = abaSelecionada == index,
                            onClick = { abaSelecionada = index },
                            text = {
                                Text(
                                    text = titulo,
                                    fontWeight = if (abaSelecionada == index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                when (abaSelecionada) {
                    0 -> AbaJornadaAuditorUI(
                        uiState = uiState,
                        plano = plano,
                        viewModel = viewModel,
                        navController = navController,
                        onAbrirPlano = {
                            abrirChecklistAposPlano = false
                            mostrarDialogPlano = true
                        }
                    )
                    1 -> ListaSomenteLeitura(itens.filter { it.status == StatusItem.PENDENTE }, "Tudo verificado.")
                    2 -> ListaSomenteLeitura(itens.filter { it.status == StatusItem.NAO_CONFORME }, "Nenhuma falha registrada.")
                    3 -> ListaSomenteLeitura(itens.filter { it.status == StatusItem.CONFORME }, "Nenhum item conforme.")
                    4 -> ListaHistoricoUI(uiState.historico) { indice -> indiceHistorico = indice }
                }
            }
        }
    }
}

@Composable
fun AbaJornadaAuditorUI(
    uiState: AuditoriaUiState,
    plano: PlanoAuditoria,
    viewModel: AuditoriaViewModel,
    navController: NavController,
    onAbrirPlano: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Jornada do auditor (ISO 19011)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Etapa 1 - Planejamento", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                if (plano.configurado) {
                    Text(text = plano.razaoSocial, fontWeight = FontWeight.Bold)
                    Text(text = "CNPJ: ${plano.cnpj}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Competência: ${plano.competenciaFiscal}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(text = "O escopo da auditoria ainda não foi definido.", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "Protocolo: ${plano.protocolo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onAbrirPlano, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (plano.configurado) "Editar escopo" else "Definir escopo")
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Etapa 2 - Execução (checklist)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Situação: ${uiState.statusSessao}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (uiState.total == 0) 0f else uiState.verificados / uiState.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Pendentes: ${uiState.pendentes}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Apontamentos abertos: ${uiState.apontamentosAbertos}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(RotaCategorias) },
                    enabled = plano.configurado,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Abrir checklist")
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Etapa 3 - Nuvem", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.statusFirestore
                        ?: "Ao fechar a auditoria, ela é salva no Firestore e pode ser consultada pela lupa.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = uiState.statusNuvem, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { viewModel.sincronizarComNuvem() },
                    enabled = !uiState.sincronizandoNuvem,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (uiState.sincronizandoNuvem) "Enviando..." else "Enviar para a nuvem (D1/R2)")
                }
            }
        }
    }
}

@Composable
fun ListaHistoricoUI(historico: List<AuditoriaConcluida>, onAbrirAuditoria: (Int) -> Unit) {
    // Mais recentes primeiro; o índice original é preservado para abrir o item correto.
    val ordenado = historico.withIndex().toList().asReversed()

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        if (ordenado.isEmpty()) {
            item { Text(text = "Nenhuma auditoria arquivada.", modifier = Modifier.padding(top = 16.dp)) }
        }
        items(ordenado, key = { it.index }) { (indice, arquivada) ->
            val conformes = arquivada.itens.count { it.status == StatusItem.CONFORME }
            val naoConformes = arquivada.itens.count { it.status == StatusItem.NAO_CONFORME }
            val pendentes = arquivada.itens.count { it.status == StatusItem.PENDENTE }

            ElevatedCard(
                onClick = { onAbrirAuditoria(indice) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Protocolo: ${arquivada.plano.protocolo}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text(text = arquivada.plano.razaoSocial, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = "Concluída em: ${arquivada.dataFechamento}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Itens: ${arquivada.itens.size} | Conformes: $conformes | Não conformes: $naoConformes | Pendentes: $pendentes",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Toque para ver detalhes e fotos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun DialogPlanoAuditoria(
    planoAtual: PlanoAuditoria,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var razao by remember { mutableStateOf(planoAtual.razaoSocial) }
    var cnpjRaw by remember { mutableStateOf(planoAtual.cnpj.filter { it.isLetterOrDigit() }.uppercase().take(14)) }
    var competenciaRaw by remember { mutableStateOf(planoAtual.competenciaFiscal.filter { it.isDigit() }.take(6)) }
    var responsavel by remember { mutableStateOf(planoAtual.responsavelAuditado) }

    val cnpjValido = cnpjEhValido(cnpjRaw)
    val competenciaValida = competenciaEhValida(competenciaRaw)
    val valido = razao.isNotBlank() && cnpjValido && competenciaValida && responsavel.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Plano de auditoria (ISO 19011)") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = razao,
                    onValueChange = { razao = it },
                    label = { Text(text = "Razão social auditada") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                CampoMascarado(
                    valor = cnpjRaw,
                    onValorChange = { cnpjRaw = it.filter { c -> c.isLetterOrDigit() }.uppercase().take(14) },
                    mascara = MASCARA_CNPJ,
                    rotulo = "CNPJ",
                    teclado = TecladoCnpj,
                    erro = cnpjRaw.length == 14 && !cnpjValido,
                    modifier = Modifier.fillMaxWidth()
                )
                if (cnpjRaw.length == 14 && !cnpjValido) {
                    Text(text = "CNPJ inválido.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                CampoMascarado(
                    valor = competenciaRaw,
                    onValorChange = { competenciaRaw = it.filter { c -> c.isDigit() }.take(6) },
                    mascara = MASCARA_COMPETENCIA,
                    rotulo = "Período / competência (MM/AAAA)",
                    erro = competenciaRaw.length == 6 && !competenciaValida,
                    modifier = Modifier.fillMaxWidth()
                )
                if (competenciaRaw.length == 6 && !competenciaValida) {
                    Text(text = "Competência inválida.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = responsavel,
                    onValueChange = { responsavel = it },
                    label = { Text(text = "Responsável local") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        razao.trim(),
                        aplicarMascara(cnpjRaw, MASCARA_CNPJ),
                        aplicarMascara(competenciaRaw, MASCARA_COMPETENCIA),
                        responsavel.trim()
                    )
                },
                enabled = valido
            ) {
                Text(text = "Salvar e auditar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.btn_cancelar)) }
        }
    )
}

@Composable
fun ListaSomenteLeitura(itens: List<ItemAuditoria>, vazio: String, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(16.dp)) {
        if (itens.isEmpty()) {
            item { Text(text = vazio, modifier = Modifier.padding(top = 16.dp)) }
        }
        items(itens, key = { it.id }) { item ->
            CardItemSomenteLeitura(item)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ----------------------------------------------------------------------
// Categorias e checklist
// ----------------------------------------------------------------------

@Composable
fun TelaCategorias(navController: NavController, viewModel: AuditoriaViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        TextButton(onClick = { navController.popBackStack() }) { Text(text = stringResource(R.string.btn_voltar)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.iniciar_auditoria), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = stringResource(R.string.selecione_categoria), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn {
            items(Categorias.todas) { categoria ->
                val daCategoria = uiState.itens.filter { it.categoria == categoria }
                val verificados = daCategoria.count { it.status != StatusItem.PENDENTE }

                Card(
                    onClick = { navController.navigate(RotaChecklist(categoria)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = categoria, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$verificados de ${daCategoria.size} itens verificados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelaChecklistAuditoria(navController: NavController, categoria: String, viewModel: AuditoriaViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val itensExibidos = uiState.itens.filter { it.categoria == categoria }

    var idItemNaoConformidade by rememberSaveable { mutableStateOf<Int?>(null) }
    var idItemFoto by rememberSaveable { mutableStateOf<Int?>(null) }
    var caminhoFotoPendente by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
        val id = idItemFoto
        val caminho = caminhoFotoPendente
        if (id != null && caminho != null) {
            if (sucesso && File(caminho).length() > 0) {
                viewModel.registrarFoto(id, caminho)
            } else {
                viewModel.descartarArquivo(caminho)
            }
        }
        idItemFoto = null
        caminhoFotoPendente = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { navController.popBackStack() }) { Text(text = stringResource(R.string.btn_voltar)) }
            Button(onClick = { navController.navigate(RotaResumo(categoria)) }) { Text(text = stringResource(R.string.btn_finalizar)) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.titulo_checklist, categoria), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = "Empresa: ${uiState.plano.razaoSocial} (${uiState.plano.competenciaFiscal})", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(itensExibidos, key = { it.id }) { item ->
                CardItemAuditoria(
                    item = item,
                    onConformeClick = { viewModel.marcarConforme(item.id) },
                    onEvidenciaClick = {
                        val arquivo = viewModel.novoArquivoFoto(item.id)
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
                            idItemFoto = item.id
                            caminhoFotoPendente = arquivo.absolutePath
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            viewModel.descartarArquivo(arquivo.absolutePath)
                            Toast.makeText(context, "Não foi possível abrir a câmera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onNaoConformeClick = { idItemNaoConformidade = item.id },
                    onRemoverEvidenciaClick = { viewModel.removerFoto(item.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    val itemNaoConformidade = idItemNaoConformidade?.let { id -> uiState.itens.firstOrNull { it.id == id } }
    itemNaoConformidade?.let { item ->
        DialogNaoConformidade(
            item = item,
            onDismiss = { idItemNaoConformidade = null },
            onLerFotoIA = { retornarTexto ->
                item.caminhoFoto?.let { caminho -> viewModel.extrairDadosDaFotoComIA(caminho, retornarTexto) }
            },
            onSave = { severidade, criterio, observacao, prazo ->
                viewModel.registrarNaoConformidade(item.id, severidade, criterio, observacao, prazo)
                idItemNaoConformidade = null
            }
        )
    }
}

// ----------------------------------------------------------------------
// Resumo e fechamento
// ----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaResumoAuditoria(navController: NavController, categoria: String, viewModel: AuditoriaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val plano = uiState.plano
    val itensAuditados = uiState.itens.filter { it.categoria == categoria }
    val pendentes = itensAuditados.count { it.status == StatusItem.PENDENTE }
    val conformes = itensAuditados.count { it.status == StatusItem.CONFORME }
    val naoConformes = itensAuditados.count { it.status == StatusItem.NAO_CONFORME }

    val statusApontamentos = itensAuditados.mapNotNull { it.naoConformidade?.obterStatus() }
    val apPendentes = statusApontamentos.count { it == StatusApontamento.PENDENTE }
    val apAndamento = statusApontamentos.count { it == StatusApontamento.EM_ANDAMENTO }
    val apResolvidos = statusApontamentos.count { it == StatusApontamento.RESOLVIDO }

    var mostrarConfirmarFechamento by rememberSaveable { mutableStateOf(false) }

    fun arquivar() {
        viewModel.fecharEArquivarAuditoria()
        navController.popBackStack<RotaHome>(inclusive = false)
    }

    if (mostrarConfirmarFechamento) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarFechamento = false },
            title = { Text(text = "Itens pendentes") },
            text = {
                Text(
                    text = "Ainda existem ${uiState.pendentes} item(ns) pendente(s) na auditoria (todas as categorias). " +
                            "Deseja arquivar mesmo assim?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    mostrarConfirmarFechamento = false
                    arquivar()
                }) { Text(text = "Arquivar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarFechamento = false }) { Text(text = "Voltar ao checklist") }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text(text = stringResource(R.string.resumo_auditoria), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = "${plano.protocolo} - ${plano.razaoSocial} (CNPJ: ${plano.cnpj})", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.resultados_consolidados), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.qtd_conformes, conformes), color = MaterialTheme.colorScheme.tertiary)
                Text(text = stringResource(R.string.qtd_nao_conformes, naoConformes), color = MaterialTheme.colorScheme.error)
                Text(text = stringResource(R.string.qtd_pendentes, pendentes), color = MaterialTheme.colorScheme.secondary)

                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(R.string.apontamentos_titulo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = stringResource(R.string.qtd_ap_pendentes, apPendentes))
                Text(text = stringResource(R.string.qtd_ap_andamento, apAndamento))
                Text(text = stringResource(R.string.qtd_ap_resolvidos, apResolvidos))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.detalhamento_itens), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(itensAuditados, key = { it.id }) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "[${item.codigoPapelTrabalho}] ${item.descricao}", fontWeight = FontWeight.Bold)
                        val nc = item.naoConformidade
                        if (item.status == StatusItem.NAO_CONFORME && nc != null) {
                            Text(text = stringResource(R.string.status_nao_conforme, nc.nivelSeveridade), color = MaterialTheme.colorScheme.error)
                            Text(text = "Critério legal: ${nc.criterioNormativo}", style = MaterialTheme.typography.bodySmall)
                            Text(text = stringResource(R.string.obs_nao_conformidade, nc.observacao), style = MaterialTheme.typography.bodySmall)
                            Text(text = stringResource(R.string.prazo_nao_conformidade, nc.prazoResolucao), style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusApontamento.todos.forEach { status ->
                                    FilterChip(
                                        selected = nc.obterStatus() == status,
                                        onClick = { viewModel.atualizarStatusApontamento(item.id, status) },
                                        label = { Text(text = status, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        } else if (item.status == StatusItem.CONFORME) {
                            Text(text = stringResource(R.string.status_conforme), color = MaterialTheme.colorScheme.tertiary)
                        } else {
                            Text(text = stringResource(R.string.status_pendente), color = MaterialTheme.colorScheme.secondary)
                        }
                        item.caminhoFoto?.let { MiniaturaEvidencia(it, item.timestampFoto) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (uiState.pendentes > 0) mostrarConfirmarFechamento = true else arquivar()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(55.dp)
        ) {
            Text(text = "Fechar auditoria e arquivar no histórico", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaVisualizarHistorico(auditoria: AuditoriaConcluida, onVoltar: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Relatório: ${auditoria.plano.protocolo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Empresa: ${auditoria.plano.razaoSocial}", fontWeight = FontWeight.Bold)
                    Text(text = "CNPJ: ${auditoria.plano.cnpj}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Competência: ${auditoria.plano.competenciaFiscal}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Responsável: ${auditoria.plano.responsavelAuditado}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Fechado em: ${auditoria.dataFechamento}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(text = "Checklist auditado", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)

            ListaSomenteLeitura(itens = auditoria.itens, vazio = "Nenhum item.", modifier = Modifier.weight(1f))
        }
    }
}
