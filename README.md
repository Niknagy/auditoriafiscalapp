# App de Auditoria Fiscal

## **Sobre o Projeto**

Este aplicativo foi desenvolvido para modernizar e padronizar as verificações fiscais em campo. Atualmente, auditores e equipes de controladoria costumam conduzir auditorias utilizando planilhas, papel e checklists soltos. O App de Auditoria Fiscal resolve esse problema permitindo que o auditor percorra um roteiro padronizado no celular, registre evidências fotográficas e acompanhe não conformidades em tempo real, inclusive em modo offline.

Projeto desenvolvido para a disciplina *ISW036 - Laboratório de Desenvolvimento para Dispositivos Móveis*, sob orientação do Prof. *Dr. Desiré Nguessan*.


## **Funcionalidades do MVP**

Checklist Estruturado: Itens de auditoria organizados por categoria fiscal (Tributos Federais, Trabalhista, etc.).

Painel de Gestão e Segurança: Dashboard com abas de leitura livre (Pendentes, Falhas, Conformes) e "Modo Auditor" protegido por PIN para evitar manipulações indesejadas do documento.

Captura de Evidências: Integração com a câmera nativa (via FileProvider) para fotografar e vincular imagens diretamente ao item verificado na sessão ativa.

Gestão de Não Conformidades: Registro de problemas encontrados com exigência de nível de severidade (Baixa, Média, Alta), detalhamento de motivos e estipulação de prazos.

Resumo e Acompanhamento: Visão consolidada dos totais e fluxo de status dos apontamentos (Pendente -> Em Andamento -> Resolvido).

Funcionamento Offline: Armazenamento local de dados garantindo que o auditor não dependa de internet no local da inspeção.


## **Tecnologias e Arquitetura**

Linguagem: *Kotlin*

Interface de Usuário: *Jetpack Compose (UI Declarativa)*

Arquitetura: *MVVM (Model-View-ViewModel)*

Banco de Dados Local: (desejável) *Room Database (SQLite) para persistência offline.* 
Atualmente usando o Repository Pattern utilizando SharedPreferences e Kotlin Serialization para funcionamento offline do MVP.

Conformidade: *Utilização de dados mockados/anonimizados em respeito à LGPD durante a fase de testes.*


## **Como executar o projeto**

*Clone este repositório:*

git clone https://github.com/SEU-USUARIO/auditoria-fiscal-app.git

Abra o projeto no Android Studio (versão recomendada: Iguana ou superior).

Aguarde a sincronização do Gradle.

Conecte um dispositivo físico via USB/Debugging ou inicie um Emulador Android (API 24+ recomendada).

Clique em Run (Shift + F10) para compilar e instalar o aplicativo.


## **Próximos Passos (Backlog Futuro)**

[ ] Migração da persistência local para Room Database (SQLite).

[ ] Implementar notificações locais para vencimento de prazos.

[ ] Exportação do relatório final de auditoria em formato PDF.

[ ] Sincronização em nuvem e login multiusuário para integração com gestores de compliance.

[ ] Exibição gráfica de acordo com a necessidade do stakeholder.
