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
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.CpfLookupRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.FeegowPreRegistrationLookupResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
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
    private final AppointmentExternalPort appointmentExternalPort;

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
                request.appointmentId(), request.cpf(), domainCompanions, request.credentialId(), request.targetName(), request.userType());

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
            return ResponseEntity.ok(result);
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
            List<CompanionAccessInfo> domainCompanions = new ArrayList<>();
            if (request.companion() != null && request.companion().name() != null && !request.companion().name().isBlank()) {
                domainCompanions.add(new CompanionAccessInfo(
                    request.companion().name(),
                    request.companion().cpf(),
                    request.companion().phone(),
                    null,
                    request.companion().birthDate()
                ));
            }
            if (request.companions() != null) {
                for (var c : request.companions()) {
                    if (c != null && c.name() != null && !c.name().isBlank()) {
                        domainCompanions.add(new CompanionAccessInfo(
                            c.name(),
                            c.cpf(),
                            c.phone(),
                            null,
                            c.birthDate()
                        ));
                    }
                }
            }

            // Se o agendamento real do Feegow foi fornecido, vincula diretamente à consulta real
            if (request.appointmentId() != null && !request.appointmentId().isBlank()
                    && !request.appointmentId().startsWith("INOV-")
                    && !request.appointmentId().startsWith("IMG-")) {
                try {
                    log.info("[AccessControl] Auto-cadastro com agendamento Feegow vinculado: {}", request.appointmentId());
                    var valResult = accessService.processAccessRequest(
                        request.appointmentId(),
                        request.cpf(),
                        domainCompanions
                    );
                    if (valResult != null && valResult.authorized()) {
                        List<AccessCredential> feegowCreds = accessCredentialRepositoryPort.findByAppointmentId(request.appointmentId());
                        if (!feegowCreds.isEmpty()) {
                            String docName = (request.doctorName() != null && !request.doctorName().isBlank())
                                ? request.doctorName().trim()
                                : feegowCreds.get(0).getDoctorName();
                            String apptDateDisplay = resolveDisplayDate(request.appointmentId());
                            List<AccessCredentialResponse> feegowResponseList = feegowCreds.stream()
                                .map(cred -> new AccessCredentialResponse(
                                    cred.getAppointmentId(),
                                    cred.getName(),
                                    cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                                    cred.getLocator(),
                                    cred.getAccessCredential(),
                                    cred.getCpf(),
                                    docName,
                                    apptDateDisplay,
                                    "06:00",
                                    "23:59"
                                ))
                                .toList();
                            return ResponseEntity.ok(feegowResponseList);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[AccessControl] Falha ao processar agendamento Feegow {}, caindo para auto-cadastro padrão: {}", 
                            request.appointmentId(), ex.getMessage());
                }
            }

            List<AccessCredential> credentials = accessService.processSelfRegistration(
                request.name(),
                request.cpf(),
                request.phone(),
                request.birthDate(),
                request.clinic(),
                request.visitDate(),
                request.doctorName(),
                domainCompanions
            );

            if (credentials.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Falha ao gerar credencial"));
            }

            String firstAppId = !credentials.isEmpty() ? credentials.get(0).getAppointmentId() : "";
            boolean isInovare = (request.clinic() != null && request.clinic().toLowerCase().contains("inovare"))
                    || (firstAppId != null && firstAppId.startsWith("INOV-"));

            String resolvedDoctorName = (request.doctorName() != null && !request.doctorName().isBlank())
                ? request.doctorName().trim()
                : (isInovare ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare");

            final String appointmentDateDisplay = resolveDisplayDate(firstAppId);

            List<AccessCredentialResponse> responseList = credentials.stream()
                .map(cred -> new AccessCredentialResponse(
                    cred.getAppointmentId(),
                    cred.getName(),
                    cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                    cred.getLocator(),
                    cred.getAccessCredential(),
                    cred.getCpf(),
                    resolvedDoctorName,
                    appointmentDateDisplay,
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
     * Endpoint de consulta prévia no Feegow ao digitar o CPF no formulário de auto-cadastro.
     * Retorna dados cadastrais e agendamentos futuros para auto-preenchimento instantâneo.
     */
    @PostMapping("/feegow-lookup")
    public ResponseEntity<FeegowPreRegistrationLookupResponse> feegowLookup(@RequestBody @Valid CpfLookupRequest request) {
        log.info("[AccessControl] Requisição de consulta prévia no Feegow para CPF: {}", request.cpf());
        FeegowPreRegistrationLookupResponse response = accessService.lookupFeegowPreRegistration(request.cpf(), request.clinic());
        return ResponseEntity.ok(response);
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
                return ResponseEntity.ok(List.of());
            }

            List<AccessCredentialResponse> responseList = new ArrayList<>();
            Map<String, Optional<FeegowPatientAccessInfo>> feegowCache = new HashMap<>();
            LocalDate today = LocalDate.now(CLINIC_ZONE);
            LocalDate maxAllowed = today.plusDays(7);

            for (AccessCredential cred : credentials) {
                String appointmentId = cred.getAppointmentId();
                String doctorName = cred.getDoctorName();
                String appointmentDateDisplay = resolveDisplayDate(appointmentId);
                String opensAt = "06:00";
                String closesAt = "23:59";

                // Filtro estrito de janela: ignora auto-cadastros passados ou além de 7 dias
                if (appointmentId != null && appointmentId.length() >= 13 && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
                    try {
                        String datePart = appointmentId.substring(5, 13);
                        LocalDate parsedDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                        if (parsedDate.isBefore(today) || parsedDate.isAfter(maxAllowed)) {
                            continue;
                        }
                    } catch (Exception ignored) {}
                }

                // Consulta Feegow para obter médico e data real da consulta
                if (appointmentId != null && !appointmentId.startsWith("INOV-") && !appointmentId.startsWith("IMG-")) {
                    try {
                        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowCache.computeIfAbsent(
                            appointmentId,
                            feegowClientPort::fetchPatientAccessInfo
                        );
                        if (accessInfoOpt.isPresent()) {
                            FeegowPatientAccessInfo info = accessInfoOpt.get();
                            // Filtro estrito de janela: ignora consultas passadas ou além de 7 dias
                            if (info.appointmentDate() != null) {
                                if (info.appointmentDate().isBefore(today) || info.appointmentDate().isAfter(maxAllowed)) {
                                    continue;
                                }
                            }
                            if (info.doctorName() != null && !info.doctorName().isBlank()) {
                                doctorName = info.doctorName();
                                if (cred.getDoctorName() == null || cred.getDoctorName().isBlank()) {
                                    cred.setDoctorName(doctorName);
                                    accessCredentialRepositoryPort.save(cred);
                                }
                            }
                            if (info.appointmentDate() != null) {
                                if (info.appointmentTime() != null) {
                                    appointmentDateDisplay = LocalDateTime.of(info.appointmentDate(), info.appointmentTime())
                                            .format(DATE_TIME_FORMATTER);
                                    opensAt = info.appointmentTime().minusMinutes(120).format(TIME_FORMATTER);
                                    closesAt = info.appointmentTime().plusMinutes(120).format(TIME_FORMATTER);
                                } else {
                                    appointmentDateDisplay = info.appointmentDate().format(DATE_FORMATTER);
                                    opensAt = "08:00";
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[AccessControl] Erro ao buscar detalhes Feegow no lookup por CPF para {}: {}", appointmentId, ex.getMessage());
                    }
                }

                if (doctorName == null || doctorName.isBlank()) {
                    boolean isInovare = (request.clinic() != null && request.clinic().toLowerCase().contains("inovare"))
                            || (appointmentId != null && appointmentId.startsWith("INOV-"));
                    doctorName = isInovare ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare";
                }

                responseList.add(new AccessCredentialResponse(
                    cred.getAppointmentId(),
                    cred.getName(),
                    cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                    cred.getLocator(),
                    cred.getAccessCredential(),
                    cred.getCpf(),
                    doctorName,
                    appointmentDateDisplay,
                    opensAt,
                    closesAt
                ));
            }

            if (responseList.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            responseList.sort((a, b) -> {
                if (a.userType() == UserType.PATIENT && b.userType() != UserType.PATIENT) return -1;
                if (a.userType() != UserType.PATIENT && b.userType() == UserType.PATIENT) return 1;
                return 0;
            });

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

            if (appointmentId != null && !appointmentId.startsWith("IMG-") && !appointmentId.startsWith("INOV-")) {
                try {
                    var accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
                    if (accessInfoOpt.isPresent()) {
                        var accessInfo = accessInfoOpt.get();
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

            // Retorna a lista atualizada de todas as credenciais deste agendamento para o frontend atualizar instantaneamente sem reload
            List<AccessCredential> allCreds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            if (allCreds != null && !allCreds.isEmpty()) {
                String doctorName = "Clínica Inovare";
                String appDateStr = "Hoje";
                if (appointmentId.startsWith("IMG-")) {
                    doctorName = "Clínica Da Imagem - Unidade Inovare";
                } else if (appointmentId.startsWith("INOV-")) {
                    doctorName = "Inovare – Serviços de Saúde";
                }
                final String finalDoctorName = doctorName;
                final String finalAppDateStr = appDateStr;

                List<AccessCredentialResponse> responseList = allCreds.stream()
                    .map(cred -> new AccessCredentialResponse(
                        cred.getAppointmentId(),
                        cred.getName(),
                        cred.getUserType() != null ? cred.getUserType() : UserType.COMPANION,
                        cred.getLocator(),
                        cred.getAccessCredential(),
                        cred.getCpf(),
                        finalDoctorName,
                        finalAppDateStr,
                        "06:00",
                        "23:00"
                    ))
                    .toList();
                return ResponseEntity.ok(responseList);
            }

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

        // Se for rota pública ou auto-cadastro (IMG- ou INOV-), busca diretamente do banco sem desafio Feegow
        if (idAgendamento != null && (idAgendamento.startsWith("IMG-") || idAgendamento.startsWith("INOV-"))) {
            List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(idAgendamento);
            if (creds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
            }
            List<AccessCredentialResponse> resp = creds.stream()
                .map(c -> new AccessCredentialResponse(
                    c.getAppointmentId(),
                    c.getName(),
                    c.getUserType(),
                    c.getLocator(),
                    c.getAccessCredential(),
                    c.getCpf(),
                    idAgendamento.startsWith("INOV-") ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare",
                    "Hoje",
                    "06:00",
                    "23:00"
                ))
                .toList();
            return ResponseEntity.ok(resp);
        }

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

        // AUTO-DETECÇÃO DE SESSÃO ATIVA HOJE: inclui todas as consultas do paciente para hoje
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        if (accessInfo.patientId() != null) {
            try {
                var patientSessions = appointmentSessionRepository.findByPatientId(accessInfo.patientId());
                for (var s : patientSessions) {
                    boolean isToday = (s.getAppointmentAt() != null && s.getAppointmentAt().toLocalDate().equals(today))
                                   || (s.getCreatedAt() != null && s.getCreatedAt().toLocalDate().equals(today));
                    if (isToday) {
                        if (s.getFeegowAppointmentId() != null && !appointmentIds.contains(s.getFeegowAppointmentId())) {
                            appointmentIds.add(s.getFeegowAppointmentId());
                            log.info("[AccessControl] Auto-detectado agendamento de hoje ({}) para o paciente ID: {}. Adicionado ao escopo de credenciamento.", s.getFeegowAppointmentId(), accessInfo.patientId());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[AccessControl] Erro ao auto-detectar sessões de hoje para o paciente: {}", ex.getMessage());
            }

            // AUTO-DETECÇÃO DE CONSULTAS DO DIA NO FEEGOW: inclui todas as consultas de hoje para este mesmo paciente no Feegow
            try {
                List<FeegowAppointment> todayFeegowApps = appointmentExternalPort.searchAppointments(today, 0);
                for (FeegowAppointment fa : todayFeegowApps) {
                    if (accessInfo.patientId().equals(fa.patientId()) && fa.id() != null && !appointmentIds.contains(fa.id())) {
                        appointmentIds.add(fa.id());
                        log.info("[AccessControl] Auto-detectada consulta adicional de hoje ({}) no Feegow para o paciente ID: {}. Adicionada ao escopo de credenciamento.", fa.id(), accessInfo.patientId());
                    }
                }
            } catch (Exception ex) {
                log.warn("[AccessControl] Erro ao buscar consultas adicionais do dia no Feegow: {}", ex.getMessage());
            }
        }

        // AUTO-DETECÇÃO POR CPF: busca credenciais geradas hoje para este CPF
        String patientCpf = accessInfo.cpf() != null ? accessInfo.cpf().replaceAll("\\D", "") : "";
        if (patientCpf.length() == 11) {
            try {
                List<AccessCredential> todayCreds = accessCredentialRepositoryPort.findByCpf(patientCpf);
                for (var c : todayCreds) {
                    if (c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(today)) {
                        if (c.getAppointmentId() != null && !appointmentIds.contains(c.getAppointmentId())) {
                            appointmentIds.add(c.getAppointmentId());
                            log.info("[AccessControl] Auto-detectado agendamento com credencial hoje ({}) por CPF: {}", c.getAppointmentId(), patientCpf);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[AccessControl] Erro ao auto-detectar credenciais de hoje por CPF: {}", ex.getMessage());
            }
        }

        List<AccessCredential> credentials = new ArrayList<>();

        for (String id : appointmentIds) {
            List<AccessCredential> appCreds = accessCredentialRepositoryPort.findByAppointmentId(id);
            boolean hasOnlyContingency = !appCreds.isEmpty() && appCreds.stream()
                    .allMatch(c -> c.getAccessCredential() != null && c.getAccessCredential().startsWith("CRED-"));

            boolean hasPatientWithInvalidCpf = !appCreds.isEmpty() && appCreds.stream()
                    .filter(c -> c.getUserType() == UserType.PATIENT)
                    .anyMatch(c -> c.getCpf() == null || !AccessService.isValidCpf(c.getCpf()));

            if (appCreds.isEmpty() || hasOnlyContingency || hasPatientWithInvalidCpf) {
                log.info("[AccessControl] Credenciais não encontradas, contingenciais (CRED-) ou com CPF inválido para o agendamento ID: {}. Tentando obter credencial real na GerAcesso...", id);
                try {
                    AccessService.AccessValidationResult result = accessService.processAccessRequest(id, null, null);
                    if (result.authorized()) {
                        appCreds = accessCredentialRepositoryPort.findByAppointmentId(id);
                    } else if (result.requiresCpfFallback()) {
                        String patientNameFallback = "Paciente";
                        try {
                            if (id != null && id.matches("\\d+")) {
                                var accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(id);
                                if (accessInfoOpt.isPresent() && accessInfoOpt.get().name() != null) {
                                    patientNameFallback = accessInfoOpt.get().name();
                                }
                            }
                        } catch (Exception ignored) {}

                        AccessCredential ghost = AccessCredential.builder()
                                .id(UUID.randomUUID())
                                .appointmentId(id)
                                .name(patientNameFallback)
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

            // Se for um agendamento diferente do principal, busca as informações específicas de data/hora/médico (apenas para IDs numéricos do Feegow)
            if (!c.getAppointmentId().equalsIgnoreCase(idAgendamento) 
                    && !"CPF_MISSING".equals(c.getAccessCredential()) 
                    && c.getAppointmentId() != null 
                    && c.getAppointmentId().matches("\\d+")) {
                try {
                    FeegowPatientAccessInfo specificInfo = null;
                    if (challengeCache.containsKey(c.getAppointmentId())) {
                        specificInfo = challengeCache.get(c.getAppointmentId());
                    } else {
                        // O paciente já está autenticado pelo agendamento principal da requisição.
                        // Para os demais agendamentos do mesmo paciente, buscamos os dados no Feegow diretamente sem revalidar o Magic Token (que pertence ao agendamento principal).
                        var optInfo = feegowClientPort.fetchPatientAccessInfo(c.getAppointmentId());
                        if (optInfo.isPresent()) {
                            specificInfo = optInfo.get();
                            challengeCache.put(c.getAppointmentId(), specificInfo);
                        }
                    }
                    if (specificInfo != null) {
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
                        if (specificInfo.doctorName() != null && !specificInfo.doctorName().isBlank()) {
                            itemDoctorName = specificInfo.doctorName();
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[AccessControl] Não foi possível obter detalhes específicos para o agendamento do grupo {}: {}", c.getAppointmentId(), ex.getMessage());
                }
            }

            if (c.getAppointmentId() != null && (c.getAppointmentId().startsWith("INOV-") || c.getAppointmentId().startsWith("IMG-"))) {
                if (c.getDoctorName() != null && !c.getDoctorName().isBlank()) {
                    itemDoctorName = c.getDoctorName();
                } else if (itemDoctorName == null || itemDoctorName.isBlank()) {
                    itemDoctorName = c.getAppointmentId().startsWith("INOV-") ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare";
                }
                itemAppointmentDateTime = resolveDisplayDate(c.getAppointmentId());
            }

            // Validação de janela de tempo para liberação de exibição do QR Code no frontend
            LocalDate todayDate = LocalDate.now(CLINIC_ZONE);
            LocalDate itemDate = null;
            
            if (c.getAppointmentId().equalsIgnoreCase(idAgendamento)) {
                itemDate = accessInfo.appointmentDate();
            } else {
                try {
                    FeegowPatientAccessInfo specificInfo = challengeCache.get(c.getAppointmentId());
                    if (specificInfo != null) {
                        itemDate = specificInfo.appointmentDate();
                    }
                } catch (Exception ignored) {}
            }
            
            if (itemDate == null) {
                itemDate = accessInfo.appointmentDate();
            }
            
            boolean isItemToday = itemDate != null && todayDate.equals(itemDate);
            // No dia da consulta ou em auto-cadastros, o QR Code NUNCA deve ser bloqueado na tela!
            boolean isItemReleased = isItemToday;
            if (c.getAppointmentId() != null && (c.getAppointmentId().startsWith("INOV-") || c.getAppointmentId().startsWith("IMG-"))) {
                isItemReleased = true;
            } else if (!isItemToday && itemDate != null) {
                // Se for em data futura (amanha, semana que vem) ou passada, bloqueia exibicao
                isItemReleased = false;
            }

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
                    itemClosesAt,
                    c.getId()
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

    private String resolveDisplayDate(String appointmentId) {
        if (appointmentId != null && appointmentId.length() >= 13 && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
            try {
                String datePart = appointmentId.substring(5, 13);
                LocalDate parsedDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                LocalDate today = LocalDate.now(CLINIC_ZONE);
                if (parsedDate.equals(today)) {
                    return "Hoje";
                } else if (parsedDate.equals(today.plusDays(1))) {
                    return "Amanhã (" + parsedDate.format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
                } else {
                    return parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ignored) {}
        }
        return "Hoje";
    }
}
