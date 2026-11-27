package com.example.auditoriafiscal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.auditoriafiscal.ui.RotaCategorias
import com.example.auditoriafiscal.ui.RotaChecklist
import com.example.auditoriafiscal.ui.RotaHome
import com.example.auditoriafiscal.ui.RotaResumo
import com.example.auditoriafiscal.ui.TelaCategorias
import com.example.auditoriafiscal.ui.TelaChecklistAuditoria
import com.example.auditoriafiscal.ui.TelaHome
import com.example.auditoriafiscal.ui.TelaResumoAuditoria
import com.example.auditoriafiscal.ui.RotaCadastroRegistro
import com.example.auditoriafiscal.ui.RotaPesquisaRegistro
import com.example.auditoriafiscal.ui.TelaCadastroRegistro
import com.example.auditoriafiscal.ui.TelaLogin
import com.example.auditoriafiscal.ui.TelaPesquisaRegistro
import com.example.auditoriafiscal.viewmodel.LoginViewModel
import com.example.auditoriafiscal.viewmodel.RegistroViewModel
import androidx.compose.runtime.collectAsState
import com.example.auditoriafiscal.viewmodel.AuditoriaViewModel
import com.example.auditoriafiscal.viewmodel.AuditoriaViewModelFactory

private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF0D3B66),
    onPrimary = Color.White,
    secondary = Color(0xFF5C6B7A),
    onSecondary = Color.White,
    tertiary = Color(0xFF2E7D32),
    onTertiary = Color.White
)

private val EsquemaEscuro = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF00315C),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1C2A36),
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF00390F)
)

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) EsquemaEscuro else EsquemaClaro) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        AppRaiz()
                    }
                }
            }
        }
    }
}

/**
 * Porta de entrada: sem usuário logado no Firebase Authentication, só a TelaLogin aparece.
 * As funcionalidades do app ficam liberadas apenas após o login.
 */
@Composable
fun AppRaiz() {
    val loginViewModel: LoginViewModel = viewModel()
    val estadoLogin by loginViewModel.uiState.collectAsState()

    if (estadoLogin.usuarioLogado == null) {
        TelaLogin(loginViewModel)
    } else {
        AppComSegurancaBiometrica(onSair = loginViewModel::sair)
    }
}

@Composable
fun AppComSegurancaBiometrica(onSair: () -> Unit) {
    // rememberSaveable evita pedir a biometria de novo a cada rotação de tela.
    var autenticado by rememberSaveable { mutableStateOf(false) }
    var protecaoIndisponivel by rememberSaveable { mutableStateOf(false) }
    var mensagemErro by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as AppCompatActivity

    val autenticadores = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun dispararAutenticacao() {
        val status = BiometricManager.from(activity).canAuthenticate(autenticadores)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            // Sem biometria cadastrada e sem bloqueio de tela: não há como autenticar.
            protecaoIndisponivel = true
            return
        }
        protecaoIndisponivel = false
        mensagemErro = null

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                autenticado = true
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    mensagemErro = "Erro na autenticação: $errString"
                }
            }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticação do Auditor Fiscal")
            .setSubtitle("Confirme sua identidade para acessar o sistema")
            .setAllowedAuthenticators(autenticadores)
            .build()

        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) {
        if (!autenticado) dispararAutenticacao()
    }

    if (autenticado) {
        AppNavigation(onSair = onSair)
    } else {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sistema protegido",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (protecaoIndisponivel) {
                    Text(
                        text = "Nenhuma biometria ou bloqueio de tela está configurado neste dispositivo. " +
                                "Configure um método de segurança nas configurações do aparelho para proteger os dados fiscais.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { dispararAutenticacao() },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Verificar novamente") }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { autenticado = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Continuar sem autenticação") }
                } else {
                    Text(
                        text = "A autenticação é necessária para acessar os dados fiscais.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    mensagemErro?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { dispararAutenticacao() },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Autenticar") }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(onSair: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: AuditoriaViewModel = viewModel(factory = AuditoriaViewModelFactory(context))

    NavHost(navController = navController, startDestination = RotaHome) {
        composable<RotaHome> { TelaHome(navController, viewModel, onSair) }
        composable<RotaCategorias> { TelaCategorias(navController, viewModel) }
        composable<RotaChecklist> {
            val rota = it.toRoute<RotaChecklist>()
            TelaChecklistAuditoria(navController, rota.categoria, viewModel)
        }
        composable<RotaResumo> {
            val rota = it.toRoute<RotaResumo>()
            TelaResumoAuditoria(navController, rota.categoria, viewModel)
        }
        composable<RotaCadastroRegistro> {
            val rota = it.toRoute<RotaCadastroRegistro>()
            // ViewModel com escopo da rota: cada abertura da tela começa com o estado limpo.
            val registroViewModel: RegistroViewModel = viewModel()
            TelaCadastroRegistro(registroViewModel, rota.empresa, rota.cnpj) { navController.popBackStack() }
        }
        composable<RotaPesquisaRegistro> {
            val registroViewModel: RegistroViewModel = viewModel()
            TelaPesquisaRegistro(registroViewModel) { navController.popBackStack() }
        }
    }
}
