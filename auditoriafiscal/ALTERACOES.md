# Incremento Firebase + MVVM (branch `firebase-mvvm`)

Evolução do MVP 0.3 do grupo com **Firebase Authentication** e **Cloud Firestore**, em **MVVM**.
Nada do MVP foi removido. A `main` e o `Auditoria_Fiscal_0.3.zip` não foram alterados.

## Fluxo da arquitetura

View → ViewModel → Repository → Firebase Authentication / Cloud Firestore
(nenhuma tela ou Activity importa o Firebase)

| Fluxo | View | ViewModel | Repository |
| --- | --- | --- | --- |
| Login | `ui/TelaLogin.kt` | `LoginViewModel` | `AuthRepository` |
| Cadastro / pesquisa | `ui/TelasRegistro.kt` | `RegistroViewModel` | `RegistroAuditoriaRepository` |
| Fechar auditoria | `ui/TelasAuditoria.kt` | `AuditoriaViewModel` | `RegistroAuditoriaRepository` |

Model: `model/RegistroAuditoria.kt` (entidade `RegistroAuditoria` + `ItemRegistro`).

## O que mudou

- **Login** com e-mail e senha antes de tudo. A biometria do grupo continua como segunda camada.
- **Cadastro** (botão +) e **pesquisa pelo começo do nome da empresa** (lupa) na coleção `registros_auditoria`.
- **Fechar e arquivar** uma auditoria grava o checklist inteiro no Firestore. O id do documento é o protocolo.
- Na pesquisa, tocar no resultado abre o detalhe com os itens auditados.
- As fotos continuam no aparelho. No Firestore vai só o caminho e o horário da foto.
- `ImagemUtils` saiu do `AuditoriaRepository` para a tela não depender do Repository.
- Gradle: Firebase BoM 35.1.0, plugin google-services 4.4.4 e **Kotlin 2.2.10 → 2.3.20**
  (o Firebase 35 foi compilado com Kotlin 2.4 e o 2.2.10 não conseguia ler).

## Para rodar

1. Colocar o `google-services.json` em `app/`. Ele não vai pro Git: peça ao Fabrício.
2. Sync → Run. Para entrar, use um usuário cadastrado em Firebase → Authentication.
