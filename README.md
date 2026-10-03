# App de Auditoria Fiscal

## **Sobre o Projeto**

Este aplicativo foi desenvolvido para modernizar e padronizar as verificações fiscais em campo. Atualmente, auditores e equipes de controladoria costumam conduzir auditorias utilizando planilhas, papel e checklists soltos. O App de Auditoria Fiscal resolve esse problema permitindo que o auditor percorra um roteiro padronizado no celular, registre evidências fotográficas e acompanhe não conformidades em tempo real, inclusive em modo offline.

Projeto desenvolvido para a disciplina *ISW036 - Laboratório de Desenvolvimento para Dispositivos Móveis*, sob orientação do Prof. *Dr. Desiré Nguessan*.


## **Funcionalidades do MVP**

Checklist Estruturado: *Itens de auditoria organizados por categoria fiscal (Tributos Federais, Trabalhista, etc.).*

Captura de Evidências: *Integração com a câmera nativa para fotografar e vincular evidências diretamente ao item verificado.*

Gestão de Não Conformidades: *Registro de problemas encontrados com exigência de nível de severidade (Baixa, Média, Alta), observações e prazos de resolução.*

Resumo da Auditoria: *Visão consolidada dos apontamentos pendentes, em andamento e resolvidos ao final da inspeção.*

Funcionamento Offline: *Armazenamento local de dados garantindo que o auditor não dependa de internet no local da inspeção.*


## **Tecnologias e Arquitetura**

Linguagem: *Kotlin*

Interface de Usuário: *Jetpack Compose (UI Declarativa)*

Arquitetura: *MVVM (Model-View-ViewModel)*

Banco de Dados Local: *Room Database (SQLite) para persistência offline.*

Conformidade: *Utilização de dados mockados/anonimizados em respeito à LGPD durante a fase de testes.*


## **Como executar o projeto**

*Clone este repositório:*

git clone https://github.com/SEU-USUARIO/auditoria-fiscal-app.git

Abra o projeto no Android Studio (versão recomendada: Iguana ou superior).

Aguarde a sincronização do Gradle.

Conecte um dispositivo físico via USB/Debugging ou inicie um Emulador Android (API 24+ recomendada).

Clique em Run (Shift + F10) para compilar e instalar o aplicativo.


## **Próximos Passos (Backlog Futuro)**

[ ] Implementar notificações locais para vencimento de prazos.

[ ] Exportação do relatório final de auditoria em formato PDF.

[ ] Sincronização em nuvem e login multiusuário para integração com gestores de compliance.
