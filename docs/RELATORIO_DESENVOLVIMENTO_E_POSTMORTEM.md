# Relatório Executivo e Técnico de Desenvolvimento — Projeto Inovare TI

**Autor / Desenvolvedor:** Victor Gabriel Hass  
**Período de Execução:** Março de 2026 a Setembro de 2026  
**Escopo:** Ecossistema Integrado de Gestão de TI, Automação de WhatsApp, Conciliação Financeira, Prontuário Médico e Controle Físico de Catracas  
**Status do Projeto:** Concluído / Encerrado por decisão comercial unilateral da clínica contratante (desativação por falta de pagamento)

---

## 1. Sumário Executivo

O presente documento consolida a trajetória completa de engenharia, arquitetura e resolução de incidentes do ecossistema **Inovare TI**. Durante 7 meses de desenvolvimento contínuo, a plataforma evoluiu de um sistema interno de chamados e controle de ativos para uma infraestrutura crítica de missão clínica conectada a 5 ecossistemas externos:
1. **Feegow ERP** (prontuário e pautas médicas)
2. **Take Blip / WhatsApp Meta Active Campaign** (confirmações, lembretes, CSAT e atendimento humano)
3. **GerAcesso** (hardware e controladora física de catracas prediais na rede local)
4. **Conta Azul V2** (conciliação bancária, faturamento médico e emissão de recibos fiscais)
5. **Discord (Bot JDA 5)** (orquestração operacional, slash commands e alertas em tempo real)

Apesar da entrega integral do ecossistema em produção — atingindo **55 migrações Flyway**, **18 módulos hexagonais em Java 21**, PWA mobile offline-first em React 19 e estabilidade operacional comprovada —, a clínica optou unilateralmente por não remunerar o projeto, resultando no seu desligamento planejado e na preservação irrestrita de toda a propriedade intelectual sob posse exclusiva do desenvolvedor.

---

## 2. Linha do Tempo e Principais Desafios de Engenharia Superados

### 2.1 Fase 1: Fundação do Sistema, ITSM, CMDB e Estoque FIFO (Março de 2026)

O projeto iniciou com o objetivo de governar o parque tecnológico da Clínica Inovare.

* **Desafio 1: Vazamento Crítico de Conexões de Banco (HikariCP) e OSIV**
  * *O Problema:* Ao iniciar integrações com chamadas de rede externas, a API sofria esgotamento súbito do pool de conexões do PostgreSQL (`Connection is not available, request timed out after 30000ms`), derrubando o sistema.
  * *Causa Raiz:* O padrão *Open Session In View (OSIV)* mantinha a conexão JDBC aberta durante todo o ciclo de vida da requisição HTTP. Quando uma chamada externa demorava, a conexão do banco ficava presa.
  * *A Solução:* Desativação estrita do OSIV no Spring Boot (`spring.jpa.open-in-view=false`), configuração de `leak-detection-threshold=5000ms` e encapsulamento cirúrgico de consultas com `@Transactional(readOnly = true)`.
* **Desafio 2: Falhas de Carregamento Preguiçoso (`LazyInitializationException`)**
  * *O Problema:* Ao exibir ativos vinculados a usuários e históricos de chamados, a serialização JSON gerava exceções de proxies não inicializados.
  * *A Solução:* Introdução de queries explícitas com `JOIN FETCH` nos repositórios e projeções desacopladas em DTOs imutáveis.
* **Desafio 3: Gestão de Insumos com Baixa Transacional FIFO (First-In, First-Out)**
  * *O Problema:* Necessidade de rastrear o custo real de insumos de TI consumidos em atendimentos técnicos, respeitando lotes de compra antigos.
  * *A Solução:* Algoritmo de exaustão de lotes ordenado por `purchase_date ASC` executado sob `Propagation.MANDATORY`, garantindo que a entrega do item ao colaborador e a dedução contábil do lote ocorram de forma atômica.
* **Desafio 4: Regra de Parada Crítica (#🚨ParadaCrítica) e Bot Discord**
  * *O Problema:* Incidentes que paravam consultórios ou exames ficavam na fila geral de chamados.
  * *A Solução:* Detecção automática de equipamentos críticos (`assets.is_critical = true`) ou etiquetas patrimoniais, disparando reclassificação para prioridade `URGENT`, encurtamento do SLA útil para **1 hora** e despacho prioritário via bot Discord com embed vermelho e botões de aceite imediato.

---

### 2.2 Fase 2: A Batalha da Integração Financeira com Conta Azul V2 (Março a Abril de 2026)

A integração financeira exigiu conciliar vendas quitadas (`ACQUITTED`) dos médicos no Conta Azul e gerar recibos em PDF.

* **Desafio 5: Quebra na Transição de APIs (Conta Azul v1 para v2) e Inconsistência de Endpoints**
  * *O Problema:* A Conta Azul alterou endpoints e domínios no meio do projeto, quebrando chamadas de vendas e baixas (`404 Not Found` e `401 Unauthorized`).
  * *A Solução:* Migração completa para o domínio oficial `api-v2`, remoção forçada do prefixo residual `/api` nos clientes REST e mapeamento explícito de IDs de parcelas e baixas financeiras.
* **Desafio 6: Concorrência na Renovação de Tokens OAuth2 e Falha de `invalid_grant`**
  * *O Problema:* A Conta Azul adota rotação estrita de `refresh_token`. Quando múltiplos agendamentos ou requisições paralelas tentavam renovar o token expirado ao mesmo tempo, a primeira renovava e as subsequentes usavam o refresh token já invalidado, revogando o acesso da aplicação.
  * *A Solução:* Criação de um job proativo a cada 50 minutos protegido por `ReentrantLock` em memória, assegurando que apenas uma thread renove o token enquanto as demais aguardam a emissão da nova chave.
* **Desafio 7: Natureza Assíncrona e Ausência de Recibos em PDF na API Conta Azul**
  * *O Problema:* Após a baixa de um pagamento, a API da Conta Azul frequentemente demorava vários minutos para disponibilizar o PDF do recibo oficial ou retornava erro `403/404`, travando a esteira de envio de e-mails para os médicos.
  * *A Solução:* Desenvolvimento do `InternalReceiptEmissionService` com **OpenPDF**, criando um gerador autônomo de recibos fiscais padronizados com o layout da clínica, utilizado como fallback instantâneo de contingência.
* **Desafio 8: Rate Limiting Agressivo da Conta Azul e Pacing Transacional**
  * *O Problema:* A API Conta Azul bloqueava o backend com erros `HTTP 429 Too Many Requests` durante consultas massivas de vendas.
  * *A Solução:* Implementação de pacing controlado de **350ms** (`LockSupport.parkNanos`) entre cada venda processada e adoção do `RedisRateLimiter` para blindar endpoints de refresh manual.

---

### 2.3 Fase 3: A Epopeia do Motor de Agendamentos e WhatsApp (Agosto de 2026)

A automação de confirmações via WhatsApp conectou a grade do Feegow ERP à plataforma Take Blip e aos servidores da Meta.

* **Desafio 9: O Erro #132000 da Meta em Templates de WhatsApp Estáticos**
  * *O Problema:* No envio de templates que não possuíam variáveis dinâmicas (ex: avisos de grupo), a API Active Campaign da Take Blip rejeitava o disparo com erro `Validation failed (#132000)`.
  * *Causa Raiz:* A Meta proíbe o envio do campo `messageParams` (mesmo vazio `[]`) em templates estáticos.
  * *A Solução:* O `BlipNotificationService` foi equipado com um detector de aridade de parâmetros, omitindo 100% o campo `messageParams` quando o template não possuir placeholders.
* **Desafio 10: Desconexão de Contexto entre o Roteador Blip e o Blip Desk (Dual-Scope Contact Sync)**
  * *O Problema:* Quando o paciente solicitava falar com a atendente, a conversa transbordava para o Blip Desk, mas a secretária não sabia de qual médico era o agendamento, gerando desordem no balcão.
  * *Causa Raiz:* A plataforma Take Blip mantém escopos de contatos segregados entre o bot do Roteador Principal e o Túnel de Atendimento do Desk. Atualizar um não refletia no outro.
  * *A Solução:* Arquitetura do `BlipContactClientAdapter` para efetuar disparos síncronos LIME `/contacts` nos dois canais simultaneamente (`APP_APPOINTMENT_BLIP_BOT_KEY` e `APP_APPOINTMENT_BLIP_DESK_KEY`), populando `Medico`, `fila`, `taxDocument` e `birthDate`.
* **Desafio 11: Virtual Thread Pinning no Java 21 Loom**
  * *O Problema:* Degradação inexplicável de throughput durante picos de envio de mensagens no WhatsApp.
  * *Causa Raiz:* A biblioteca de conexão continha blocos `synchronized` encapsulando operações de rede socket. No Java 21, `synchronized` bloqueia o carrier thread da JVM (*Virtual Thread Pinning*).
  * *A Solução:* Refatoração completa dos adapters de comunicação para substituir blocos sincronizados legados por mecanismos não-bloqueantes.
* **Desafio 12: Concorrência e Spam em Consultas Familiares / Múltiplos Procedimentos**
  * *O Problema:* Mães levando dois filhos no mesmo dia ou pacientes com consultas e exames sequenciais recebiam múltiplos disparos isolados no WhatsApp, gerando confusão e cliques desordenados.
  * *A Solução:* Criação do `NotificationAccumulatorService`, um agregador atômico que consolida agendamentos com mesmo telefone e data em um único grupo (`notification_groups`), enviando o template consolidado `aviso_agendamento_grupo` e bloqueando race conditions de disparos avulsos.
* **Desafio 13: O "Status Guard" contra Regressão de Confirmação no Feegow ERP**
  * *O Problema:* Pacientes confirmavam a consulta pelo WhatsApp às 08h00. Na ingestão matinal seguinte, a busca geral do Feegow retornava o agendamento antes da atualização refletir na réplica do ERP, correndo o risco de rebaixar a consulta para `PENDING` ou cancelá-la indevidamente.
  * *A Solução:* Implementação do `Status Guard`: consultas marcadas localmente como `CONFIRMED` tornaram-se imutáveis perante a ingestão geral. Uma consulta individual direta na API é realizada antes de qualquer alteração de estado.
* **Desafio 14: Filtro Cirúrgico Fino de Procedimentos**
  * *O Problema:* Mensagens automáticas estavam sendo enviadas para cirurgias complexas que exigiam preparos hospitalares rígidos e não podiam ser confirmadas por robô.
  * *A Solução:* Regex de bloqueio de procedimentos contendo radicais cirúrgicos (`cirurg`, `cirurgias mu`, etc.), preservando cirurgias ambulatoriais leves mediante lista explícita de exceções (retirada de pontos, biópsias, botox, curativos).

---

### 2.4 Fase 4: A Odisseia do Controle de Acesso Físico e Catracas (Setembro de 2026)

A integração mais complexa do projeto: conectar a confirmação médica à liberação de catracas prediais físicas via API GerAcesso na rede local.

* **Desafio 15: O Bug Nativo do Servidor GerAcesso — Parâmetro `tipovisista: 1`**
  * *O Problema:* As catracas físicas rejeitavam silenciosamente a liberação de visitantes. A documentação teórica informava o parâmetro `tipoVisita`, mas o hardware simplesmente não liberava.
  * *Causa Raiz:* Engenharia reversa no tráfego da controladora revelou que o servidor da GerAcesso exigia literalmente a grafia com erro de digitação original do fabricante: `"tipovisista": 1`. Se o código utilizasse o termo correto em português, a liberação falhava.
  * *A Solução:* Adequação estrita do record Java `GerAcessoRequest` com `@JsonProperty("tipovisista")` e garantia canônica de envio de valor `1`.
* **Desafio 16: O Bloqueio Mecânico de Anti-Passback e Hesitação na Catraca**
  * *O Problema:* O paciente aproximava o smartphone do leitor ótico da catraca, o leitor bipava com sucesso, mas o paciente hesitava em empurrar os braços mecânicos a tempo. O temporizador da controladora expirava e, ao tentar ler novamente, a catraca exibia "ACESSO NEGADO (Anti-Passback)".
  * *A Solução:* Criação do `ReactivateAccessUseCase` e do botão *"Atualizar / Reativar QR Code"*:
    * O backend envia uma nova visita ao GerAcesso com `inicio_visita` retroativo em 5 minutos (`now.minusMinutes(5)`) para compensar possíveis descompassos de relógio (*clock skew*) da máquina física.
    * Uma nova credencial numérica é gerada na controladora e atualizada no cartão do paciente sem necessidade de recarregar a página.
* **Desafio 17: Fatores Físicos e Óticos na Leitura de Smartphones**
  * *O Problema:* Pacientes na recepção enfrentavam dificuldades frequentes com o leitor a laser:
    1. Celulares com Modo Escuro forçado (Samsung Internet e Dark Mode de fábrica) invertiam o QR Code para branco sobre fundo preto ou baixo contraste, impossibilitando a leitura pelo sensor ótico.
    2. Pacientes colavam o vidro do celular encostado na câmera da catraca, fora do ponto focal da lente.
    3. A tela do smartphone apagava por inatividade enquanto o paciente aguardava na fila.
    4. O componente `<canvas>` do React congelava em navegadores móveis WebKit/Chrome após atualizações de credenciais.
  * *A Solução:*
    * **Fundo Branco Puro Blindado:** Forçamento de container branco puro com proteção contra inversão CSS.
    * **Guia Visual de Distância:** Animação gráfica instruindo o paciente a posicionar o celular a **15 cm de distância** da lente.
    * **Screen Wake Lock API:** A tela do smartphone é travada acesa com brilho máximo enquanto o modal de QR Code estiver aberto.
    * **Chave Única Reativa no Canvas:** Inserção de `key={cred.credentialCode}` no `<QRCodeCanvas>`, forçando o DOM a remontar o elemento gráfico e prevenindo congelamento de renderização mobile.
* **Desafio 18: Instabilidade de Sinal 4G/Wi-Fi na Portaria e Elevadores (Cache Offline PWA)**
  * *O Problema:* Ao entrar na clínica ou no saguão dos elevadores, a rede celular oscilava ou caía. Se o paciente desse refresh na tela, perdia o QR Code e ficava preso fora do prédio.
  * *A Solução:* Desenvolvimento da camada `saveCredentialsWithOfflineCache` no frontend PWA, gravando as credenciais validadas no `localStorage` com escopo de clínica e expiração diária. O aplicativo funciona 100% offline após o primeiro carregamento.
* **Desafio 19: Cadastro Concorrente de Acompanhantes via Virtual Threads**
  * *O Problema:* Pacientes com múltiplos acompanhantes sofriam travamentos se a API da catraca demorasse a cadastrar o segundo ou terceiro acompanhante.
  * *A Solução:* Processamento paralelo via **Java 21 Virtual Threads**, encapsulado com resiliência *fail-safe*: a falha no cadastro de um acompanhante isolado não impede nem atrasa a liberação do paciente titular.

---

## 3. Matriz de Entregáveis e Inventário Tecnológico

| Módulo | Tecnologia Principal | Principais Capacidades Entregues |
|---|---|---|
| **Controle de Acesso Físico** | Java 21, REST, Virtual Threads, PWA | Emissão de credenciais de catraca, reativação imediata anti-passback, suporte a acompanhantes, totem de autoatendimento, WakeLock API e cache offline. |
| **Agendamentos & Mensageria** | Java 21, LIME, Webhooks, WhatsApp Meta | Ingestão inteligente D+0 a D+3, esteira de nudges, acumulador atômico de grupos, dual-scope sync, status guard anti-regressão e Google Review pós-atendimento. |
| **Conciliação Financeira** | Java 21, OAuth2, Redis, OpenPDF | Sincronização Conta Azul V2, emissão de recibos fiscais com fallback PDF interno, proteção de taxa por Redis e relatório com ciclo financeiro (dia 12). |
| **Suporte & Governança (ITSM)** | PostgreSQL 16, JDA 5 (Discord) | Central de chamados com cálculo de SLA em horário comercial útil, regra de Parada Crítica (1h), bot do Discord interativo e subchamados em árvore. |
| **Ativos & Estoque (CMDB)** | Java 21, JPA/Hibernate, FIFO | Gestão patrimonial de computadores e periféricos, deduções transacionais via algoritmo FIFO e alertas de estoque mínimo no Discord. |
| **Cofre & Auditoria (LGPD)** | AES-256-GCM, TOTP, PostgreSQL | Criptografia simétrica com IV aleatório para senhas e anexos, autenticação 2FA via Google Authenticator e trilha de auditoria imutável (`audit_logs`). |
| **Frontend Web & Mobile** | React 19, TypeScript, Vite, Tailwind | Interface SPA responsiva, design system com identidade visual da clínica e da Inovare Imagem, Error Boundary global e paginação dinâmica. |

---

## 4. Conclusão e Preservação de Direitos

O projeto **Inovare TI** representou um esforço técnico e intelectual colossal, resolvendo desafios de infraestrutura física, hardware proprietário, regras de negócio clínicas complexas e integrações com plataformas globais de comunicação. 

A decisão da clínica de não honrar o compromisso financeiro encerra a operação do sistema nas dependências da contratante, mas **não diminui em absoluto o valor da solução concebida**. O código-fonte, a arquitetura modularizada, o histórico de 55 migrações do banco de dados e todo o conhecimento documentado permanecem sob a **titularidade integral e exclusiva de Victor Gabriel Hass**, configurando uma plataforma proprietária pronta para ser licenciada no modelo SaaS ou utilizada como principal case de excelência em engenharia de software de seu portfólio.
