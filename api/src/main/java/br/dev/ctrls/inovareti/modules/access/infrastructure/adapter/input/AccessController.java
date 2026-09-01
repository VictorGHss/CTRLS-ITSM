package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessService;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessCredentialResponse;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessValidationRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.CompanionRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.config.InovareMotorProperties;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controlador REST para o controle de acesso integrado às catracas físicas.
 * Gerencia validação de credenciais, testes de catraca e fornecimento de QR codes para pacientes e acompanhantes.
 */
@Slf4j
@RestController
@RequestMapping("/v1/access")
@RequiredArgsConstructor
public class AccessController {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final InovareMotorProperties inovareMotorProperties;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final AccessService accessService;
    private final FeegowClientPort feegowClientPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;

    /**
     * Endpoint de teste manual para validação de acesso das catracas.
     * Aceita apenas doctorId permitido nas propriedades de teste para blindar o ambiente de produção.
     *
     * @param doctorId Identificador do médico para teste.
     * @return ResponseEntity com o resultado da operação.
     */
    @PostMapping("/test")
    public ResponseEntity<?> testAccess(@RequestParam("doctorId") Long doctorId) {
        log.info("[AccessControl] Executando validação de acesso de teste para o doctorId: {}", doctorId);

        // Validação estrita: Aceita apenas os IDs de teste manual configurados nas propriedades
        if (doctorId == null || inovareMotorProperties.getTestDoctorIds() == null || !inovareMotorProperties.getTestDoctorIds().contains(doctorId)) {
            log.warn("[AccessControl] Acesso proibido. O doctorId {} não é permitido para testes ou a produção está ativa.", doctorId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", "Acesso negado. O ID fornecido não é elegível para testes."));
        }

        // Cria e persiste uma credencial de teste para validação da infraestrutura JPA
        AccessCredential credential = AccessCredential.builder()
            .id(UUID.randomUUID())
            .appointmentId("TEST-APP-" + doctorId + "-" + System.currentTimeMillis())
            .name("PACIENTE TESTE DOCTORID " + doctorId)
            .cpf("123.456.789-00")
            .userType(UserType.PATIENT)
            .accessCredential("CRED-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .locator("LOC-" + System.currentTimeMillis())
            .createdAt(LocalDateTime.now())
            .build();

        AccessCredential saved = accessCredentialRepositoryPort.save(credential);
        log.info("[AccessControl] Credencial de teste salva com sucesso: ID={}, Nome={}", saved.getId(), saved.getName());

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Acesso de teste permitido e credencial persistida com sucesso.",
            "doctorId", doctorId,
            "credencialId", saved.getId()
        ));
    }

    /**
     * Endpoint de validação de acesso utilizado pelo bot do Blip ou portal web.
     * Consulta prontuários no Feegow, agrupa agendamentos diários, cadastra paciente/acompanhantes no GerAcesso
     * e gera as credenciais unificadas.
     *
     * @param request Payload contendo dados do agendamento, CPF e acompanhantes.
     * @return ResponseEntity com o resultado da validação de acesso.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateAccess(@RequestBody @Valid AccessValidationRequest request) {
        log.info("[AccessControl] Solicitação de validação de acesso: agendamento={}, cpf={}", 
                request.appointmentId(), request.cpf());

        List<CompanionAccessInfo> domainCompanions = null;
        if (request.companions() != null) {
            domainCompanions = request.companions().stream()
                    .map(c -> new CompanionAccessInfo(c.name(), c.cpf(), c.phone(), c.email(), c.birthDate()))
                    .toList();
        }

        AccessService.AccessValidationResult result = accessService.processAccessRequest(
                request.appointmentId(), request.cpf(), domainCompanions);

        // Se o CPF estiver ausente, retornamos a flag de fallback para o bot solicitar ao usuário
        if (result.requiresCpfFallback()) {
            log.warn("[AccessControl] CPF ausente para agendamento {}. Retornando requerimento de fallback de CPF.", request.appointmentId());
            return ResponseEntity.ok(Map.of(
                "authorized", false,
                "requiresCpfFallback", true,
                "message", result.message()
            ));
        }

        if (!result.authorized()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint de auto-cadastro público (Clínica da Imagem ou clínicas externas).
     * Libera o acesso no GerAcesso e retorna credenciais com QR code.
     */
    @PostMapping("/self-registration")
    public ResponseEntity<?> selfRegistration(@RequestBody @Valid br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.SelfRegistrationRequest request) {
        log.info("[AccessControl] Auto-cadastro público recebido: Nome={}, CPF={}, Clínica={}", 
                request.name(), request.cpf(), request.clinic());
        try {
            CompanionAccessInfo domainCompanion = null;
            if (request.companion() != null && request.companion().name() != null && !request.companion().name().isBlank()) {
                domainCompanion = new CompanionAccessInfo(
                    request.companion().name(),
                    request.companion().cpf(),
                    request.companion().phone(),
                    null,
                    request.companion().birthDate()
                );
            }

            List<AccessCredential> credentials = accessService.processSelfRegistration(
                request.name(),
                request.cpf(),
                request.phone(),
                request.birthDate(),
                request.clinic(),
                domainCompanion
            );

            if (credentials.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Falha ao gerar credencial"));
            }

            List<AccessCredentialResponse> responseList = credentials.stream()
                .map(cred -> new AccessCredentialResponse(
                    cred.getAppointmentId(),
                    cred.getName(),
                    cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                    cred.getLocator(),
                    cred.getAccessCredential(),
                    cred.getCpf(),
                    "Clínica Da Imagem - Unidade Inovare",
                    "Hoje",
                    "06:00",
                    "23:59"
                ))
                .toList();

            return ResponseEntity.ok(responseList);
        } catch (IllegalArgumentException ex) {
            log.warn("[AccessControl] Validação falhou no auto-cadastro: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("[AccessControl] Erro inesperado no auto-cadastro: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao processar cadastro. Tente novamente em instantes."));
        }
    }

    /**
     * Endpoint de consulta rápida de credenciais ativas pelo CPF.
     */
    @PostMapping("/lookup-by-cpf")
    public ResponseEntity<?> lookupByCpf(@RequestBody @Valid br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.CpfLookupRequest request) {
        log.info("[AccessControl] Busca de credenciais ativas por CPF: {}", request.cpf());
        try {
            List<AccessCredential> credentials = accessService.lookupCredentialsByCpf(request.cpf(), request.clinic());
            if (credentials.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Nenhum cadastro ativo encontrado para este CPF hoje."));
            }

            List<AccessCredentialResponse> responseList = credentials.stream()
                .map(cred -> new AccessCredentialResponse(
                    cred.getAppointmentId(),
                    cred.getName(),
                    cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                    cred.getLocator(),
                    cred.getAccessCredential(),
                    cred.getCpf(),
                    "Clínica Da Imagem - Unidade Inovare",
                    "Hoje",
                    "06:00",
                    "23:59"
                ))
                .toList();

            return ResponseEntity.ok(responseList);
        } catch (Exception ex) {
            log.error("[AccessControl] Erro ao buscar por CPF: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao consultar cadastro."));
        }
    }

    /**
     * Endpoint de reativação de acesso para gerar uma nova credencial no GerAcesso
     * caso o paciente/acompanhante tenha saído do prédio e precise entrar novamente.
     */
    @PostMapping("/reactivate/{appointmentId}")
    public ResponseEntity<?> reactivateAccess(@PathVariable("appointmentId") String appointmentId) {
        log.info("[AccessControl] Solicitação de reativação de acesso para agendamento: {}", appointmentId);
        try {
            List<AccessCredential> credentials = accessService.reactivateAccess(appointmentId);
            if (credentials.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Nenhuma credencial encontrada para reativar."));
            }

            String doctorName = "Clínica Inovare";
            String appointmentDateTime = "Hoje";
            String opensAt = "07:00";
            String closesAt = "23:00";

            if (appointmentId != null && !appointmentId.startsWith("IMG-")) {
                try {
                    var accessInfo = accessService.validateAccessChallenge(appointmentId, null, null);
                    if (accessInfo != null) {
                        if (accessInfo.doctorName() != null && !accessInfo.doctorName().isBlank()) {
                            doctorName = accessInfo.doctorName();
                        }
                        if (accessInfo.appointmentDate() != null) {
                            if (accessInfo.appointmentTime() != null) {
                                appointmentDateTime = LocalDateTime.of(accessInfo.appointmentDate(), accessInfo.appointmentTime())
                                        .format(DATE_TIME_FORMATTER);
                                LocalTime openingTime = accessInfo.appointmentTime().minusMinutes(120);
                                opensAt = openingTime.format(TIME_FORMATTER);
                            } else {
                                appointmentDateTime = accessInfo.appointmentDate().format(DATE_FORMATTER);
                                opensAt = "08:00";
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[AccessControl] Não foi possível resolver dados do Feegow na reativação: {}", ex.getMessage());
                }
            } else if (appointmentId != null && appointmentId.startsWith("IMG-")) {
                doctorName = "Clínica Da Imagem - Unidade Inovare";
                opensAt = "06:00";
                closesAt = "23:59";
            }

            final String finalDoctorName = doctorName;
            final String finalAppointmentDateTime = appointmentDateTime;
            final String finalOpensAt = opensAt;
            final String finalClosesAt = closesAt;

            List<AccessCredentialResponse> responseList = credentials.stream()
                .map(cred -> new AccessCredentialResponse(
                    cred.getAppointmentId(),
                    cred.getName(),
                    cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                    cred.getLocator(),
                    cred.getAccessCredential(),
                    cred.getCpf(),
                    finalDoctorName,
                    finalAppointmentDateTime,
                    finalOpensAt,
                    finalClosesAt
                ))
                .toList();

            return ResponseEntity.ok(responseList);
        } catch (Exception ex) {
            log.error("[AccessControl] Erro ao reativar acesso para {}: {}", appointmentId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao reativar acesso. Tente novamente em instantes."));
        }
    }

    /**
     * Endpoint de cadastro de acompanhante pelo portal web do paciente.
     *
     * @param appointmentId Identificador do agendamento principal.
     * @param request Dados cadastrais do acompanhante (nome, CPF, nascimento).
     * @return ResponseEntity contendo a credencial gerada para o acompanhante.
     */
    @PostMapping("/companions/{appointmentId}")
    public ResponseEntity<?> registerCompanion(
            @PathVariable("appointmentId") String appointmentId,
            @RequestBody @Valid CompanionRequest request) {
        log.info("[AccessControl] Cadastro de acompanhante pelo portal para o agendamento ID {}: {}", appointmentId, request.name());
        try {
            CompanionAccessInfo companionInfo = new CompanionAccessInfo(
                request.name(),
                request.cpf(),
                request.phone(),
                request.email(),
                request.birthDate()
            );
            AccessCredential credential = accessService.registerCompanion(appointmentId, companionInfo);
            return ResponseEntity.ok(credential);
        } catch (Exception ex) {
            log.error("[AccessControl] Erro ao cadastrar acompanhante para agendamento {}: {}", appointmentId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", ex.getMessage() != null ? ex.getMessage() : "Erro ao cadastrar acompanhante."
            ));
        }
    }

    /**
     * Endpoint de consulta de credenciais físicas para renderização no portal web.
     * Retorna a lista de credenciais geradas para o agendamento informado após validação do desafio telefônico.
     *
     * @param idAgendamento Identificador do agendamento vindo da rota.
     * @param phoneDigits 4 últimos dígitos do telefone para desafio de segurança.
     * @return ResponseEntity contendo a lista de credenciais formatadas.
     */
    @GetMapping("/credentials/{idAgendamento}")
    public ResponseEntity<List<AccessCredentialResponse>> getCredentials(
            @PathVariable("idAgendamento") String idAgendamento,
            @RequestParam(value = "phoneDigits", required = false) String phoneDigits,
            @RequestParam(value = "t", required = false) String token) {
        log.info("[AccessControl] Consulta de credenciais para o agendamento ID: {} (token={}, phoneDigits={})", 
                idAgendamento, token != null && !token.isBlank() ? "presente" : "ausente", phoneDigits);

        // Executa a validação do desafio (por token criptográfico ou 4 dígitos do telefone)
        FeegowPatientAccessInfo accessInfo = accessService.validateAccessChallenge(idAgendamento, phoneDigits, token);

        // Resolve todos os IDs de agendamento que pertencem ao mesmo grupo
        List<String> appointmentIds = new ArrayList<>();
        appointmentIds.add(idAgendamento);

        try {
            var mainSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(idAgendamento);
            if (mainSessionOpt.isPresent() && mainSessionOpt.get().getCurrentGroupId() != null) {
                var groupSessions = appointmentSessionRepository.findByCurrentGroupId(mainSessionOpt.get().getCurrentGroupId());
                for (var s : groupSessions) {
                    if (s.getFeegowAppointmentId() != null && !s.getFeegowAppointmentId().equalsIgnoreCase(idAgendamento)) {
                        appointmentIds.add(s.getFeegowAppointmentId());
                    }
                }
                log.info("[AccessControl] Encontrado grupo com {} agendamentos: {}", appointmentIds.size(), appointmentIds);
            }
        } catch (Exception ex) {
            log.warn("[AccessControl] Erro ao buscar grupo de sessões para o agendamento {}: {}", idAgendamento, ex.getMessage());
        }

        // AUTO-DETECÇÃO DE SESSÃO ATIVA HOJE: inclui sessão atual do paciente caso o link seja histórico
        if (accessInfo.patientId() != null) {
            try {
                LocalDate today = LocalDate.now(CLINIC_ZONE);
                var patientSessions = appointmentSessionRepository.findByPatientId(accessInfo.patientId());
                for (var s : patientSessions) {
                    if (s.getCreatedAt() != null && s.getCreatedAt().toLocalDate().equals(today)) {
                        if (s.getFeegowAppointmentId() != null && !appointmentIds.contains(s.getFeegowAppointmentId())) {
                            appointmentIds.add(s.getFeegowAppointmentId());
                            log.info("[AccessControl] Auto-detectado agendamento de hoje ({}) para o paciente ID: {}. Adicionado ao escopo de credenciamento.", s.getFeegowAppointmentId(), accessInfo.patientId());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[AccessControl] Erro ao auto-detectar sessões de hoje para o paciente: {}", ex.getMessage());
            }
        }

        List<AccessCredential> credentials = new ArrayList<>();

        for (String id : appointmentIds) {
            List<AccessCredential> appCreds = accessCredentialRepositoryPort.findByAppointmentId(id);
            boolean hasOnlyContingency = !appCreds.isEmpty() && appCreds.stream()
                    .allMatch(c -> c.getAccessCredential() != null && c.getAccessCredential().startsWith("CRED-"));

            if (appCreds.isEmpty() || hasOnlyContingency) {
                log.info("[AccessControl] Credenciais não encontradas ou contingenciais (CRED-) para o agendamento ID: {}. Tentando obter credencial real na GerAcesso...", id);
                try {
                    AccessService.AccessValidationResult result = accessService.processAccessRequest(id, null, null);
                    if (result.authorized()) {
                        appCreds = accessCredentialRepositoryPort.findByAppointmentId(id);
                    } else if (result.requiresCpfFallback()) {
                        var specificInfo = accessService.validateAccessChallenge(id, phoneDigits, token);
                        AccessCredential ghost = AccessCredential.builder()
                                .id(UUID.randomUUID())
                                .appointmentId(id)
                                .name(specificInfo.name())
                                .cpf("")
                                .userType(UserType.PATIENT)
                                .accessCredential("CPF_MISSING")
                                .locator("CPF_MISSING")
                                .createdAt(LocalDateTime.now())
                                .build();
                        appCreds = List.of(ghost);
                    }
                } catch (Exception ex) {
                    log.error("[AccessControl] Falha ao processar acesso em tempo real para o agendamento ID {}: {}", id, ex.getMessage());
                }
            }
            credentials.addAll(appCreds);
        }

        // FILTRO DE HOJE: se o paciente possui credenciais geradas hoje sob o mesmo CPF, exibe as de hoje
        try {
            LocalDate todayDate = LocalDate.now(CLINIC_ZONE);
            boolean hasTodayCredentials = credentials.stream()
                    .anyMatch(c -> c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(todayDate));
            if (hasTodayCredentials) {
                credentials = credentials.stream()
                        .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(todayDate))
                        .collect(Collectors.toList());
                log.info("[AccessControl] Filtro de hoje aplicado. Retornando apenas as credenciais geradas hoje.");
            }
        } catch (Exception ex) {
            log.warn("[AccessControl] Erro ao aplicar filtro de hoje nas credenciais: {}", ex.getMessage());
        }

        // Ordena para que o paciente principal venha em primeiro lugar, seguido de acompanhantes
        credentials.sort((c1, c2) -> {
            boolean isC1MainPatient = c1.getUserType() == UserType.PATIENT && c1.getAppointmentId().equalsIgnoreCase(idAgendamento);
            boolean isC2MainPatient = c2.getUserType() == UserType.PATIENT && c2.getAppointmentId().equalsIgnoreCase(idAgendamento);
            if (isC1MainPatient && !isC2MainPatient) return -1;
            if (!isC1MainPatient && isC2MainPatient) return 1;

            if (c1.getUserType() == UserType.PATIENT && c2.getUserType() != UserType.PATIENT) return -1;
            if (c1.getUserType() != UserType.PATIENT && c2.getUserType() == UserType.PATIENT) return 1;

            return 0;
        });

        // Formata data e hora do agendamento principal
        String appointmentDateTime = "";
        String opensAt = "";
        String closesAt = "23:00";
        if (accessInfo.appointmentDate() != null) {
            if (accessInfo.appointmentTime() != null) {
                appointmentDateTime = LocalDateTime.of(accessInfo.appointmentDate(), accessInfo.appointmentTime())
                        .format(DATE_TIME_FORMATTER);
                LocalTime openingTime = accessInfo.appointmentTime().minusMinutes(120);
                opensAt = openingTime.format(TIME_FORMATTER);
            } else {
                appointmentDateTime = accessInfo.appointmentDate().format(DATE_FORMATTER);
                opensAt = "08:00";
            }
        }

        String doctorName = accessInfo.doctorName() != null ? accessInfo.doctorName() : "";
        final String finalAppointmentDateTime = appointmentDateTime;
        final String finalDoctorName = doctorName;
        final String finalOpensAt = opensAt;
        final String finalClosesAt = closesAt;

        Map<String, FeegowPatientAccessInfo> challengeCache = new HashMap<>();
        List<AccessCredentialResponse> response = new ArrayList<>();
        for (AccessCredential c : credentials) {
            String itemAppointmentDateTime = finalAppointmentDateTime;
            String itemDoctorName = finalDoctorName;
            String itemOpensAt = finalOpensAt;
            String itemClosesAt = finalClosesAt;

            // Se for um agendamento diferente do principal, busca as informações específicas de data/hora/médico
            if (!c.getAppointmentId().equalsIgnoreCase(idAgendamento) && !c.getAccessCredential().equals("CPF_MISSING")) {
                try {
                    FeegowPatientAccessInfo specificInfo;
                    if (challengeCache.containsKey(c.getAppointmentId())) {
                        specificInfo = challengeCache.get(c.getAppointmentId());
                    } else {
                        specificInfo = accessService.validateAccessChallenge(c.getAppointmentId(), phoneDigits, token);
                        challengeCache.put(c.getAppointmentId(), specificInfo);
                    }
                    if (specificInfo.appointmentDate() != null) {
                        if (specificInfo.appointmentTime() != null) {
                            itemAppointmentDateTime = LocalDateTime.of(specificInfo.appointmentDate(), specificInfo.appointmentTime())
                                    .format(DATE_TIME_FORMATTER);
                            LocalTime openingTime = specificInfo.appointmentTime().minusMinutes(120);
                            itemOpensAt = openingTime.format(TIME_FORMATTER);
                        } else {
                            itemAppointmentDateTime = specificInfo.appointmentDate().format(DATE_FORMATTER);
                            itemOpensAt = "08:00";
                        }
                    }
                    if (specificInfo.doctorName() != null) {
                        itemDoctorName = specificInfo.doctorName();
                    }
                } catch (Exception ex) {
                    log.warn("[AccessControl] Não foi possível obter detalhes específicos para o agendamento do grupo {}: {}", c.getAppointmentId(), ex.getMessage());
                }
            }

            // Validação de janela de tempo para liberação de exibição do QR Code no frontend
            LocalDate todayDate = LocalDate.now(CLINIC_ZONE);
            LocalTime nowTime = LocalTime.now(CLINIC_ZONE);
            
            LocalDate itemDate = null;
            LocalTime itemTime = null;
            
            if (c.getAppointmentId().equalsIgnoreCase(idAgendamento)) {
                itemDate = accessInfo.appointmentDate();
                itemTime = accessInfo.appointmentTime();
            } else {
                try {
                    FeegowPatientAccessInfo specificInfo = challengeCache.get(c.getAppointmentId());
                    if (specificInfo != null) {
                        itemDate = specificInfo.appointmentDate();
                        itemTime = specificInfo.appointmentTime();
                    }
                } catch (Exception ignored) {}
            }
            
            if (itemDate == null) {
                itemDate = accessInfo.appointmentDate();
                itemTime = accessInfo.appointmentTime();
            }
            
            boolean isItemToday = itemDate != null && todayDate.equals(itemDate);
            boolean isItemTimeOpen = false;
            if (isItemToday) {
                if (itemTime != null) {
                    LocalTime openingTime = itemTime.minusMinutes(120);
                    LocalTime closingTime = LocalTime.of(23, 0);
                    isItemTimeOpen = !nowTime.isBefore(openingTime) && !nowTime.isAfter(closingTime);
                } else {
                    isItemTimeOpen = true;
                }
            }
            
            boolean isItemReleased = isItemToday && isItemTimeOpen;
            String credentialCodeToReturn = c.getAccessCredential();
            if (!"CPF_MISSING".equals(credentialCodeToReturn)) {
                credentialCodeToReturn = isItemReleased ? c.getAccessCredential() : "BLOCKED_OUTSIDE_WINDOW";
            }

            response.add(new AccessCredentialResponse(
                    c.getAppointmentId(),
                    c.getName(),
                    c.getUserType(),
                    c.getLocator(),
                    credentialCodeToReturn,
                    c.getCpf(),
                    itemDoctorName,
                    itemAppointmentDateTime,
                    itemOpensAt,
                    itemClosesAt
            ));
        }

        log.info("[AccessControl] Retornando {} credencial(ais) para o agendamento ID: {}", response.size(), idAgendamento);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de geração/recuperação de Magic Token e URL direta de acesso para chatbots ou integrações.
     */
    @GetMapping("/token/{idAgendamento}")
    public ResponseEntity<?> getAccessToken(@PathVariable("idAgendamento") String idAgendamento) {
        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(idAgendamento);
        if (accessInfoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        String token = accessService.generateAccessToken(idAgendamento, accessInfo.phone());
        String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + idAgendamento + "?t=" + token;
        return ResponseEntity.ok(Map.of(
            "appointmentId", idAgendamento,
            "token", token,
            "accessUrl", accessUrl
        ));
    }
}
