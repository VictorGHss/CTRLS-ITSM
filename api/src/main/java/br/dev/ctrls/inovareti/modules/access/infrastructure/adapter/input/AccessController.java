package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.access.application.usecase.GenerateCalendarIcsUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.GetCredentialsUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.LookupCredentialsByCpfUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.LookupFeegowPreRegistrationUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.ProcessAccessRequestUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.ReactivateAccessUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.RegisterCompanionUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.SelfRegistrationUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.ValidateAccessChallengeUseCase;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessValidationResult;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessCredentialResponse;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessValidationRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.CompanionRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.CpfLookupRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.FeegowPreRegistrationLookupResponse;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.SelfRegistrationRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.config.InovareMotorProperties;
import br.dev.ctrls.inovareti.modules.access.infrastructure.security.AccessSecurityGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controlador REST para o controle de acesso integrado às catracas físicas.
 * Atua estritamente como adaptador de entrada HTTP, delegando a lógica de negócio
 * para Casos de Uso especializados na camada de aplicação.
 */
@Slf4j
@RestController
@RequestMapping("/v1/access")
@RequiredArgsConstructor
public class AccessController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final InovareMotorProperties inovareMotorProperties;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final FeegowClientPort feegowClientPort;

    private final ProcessAccessRequestUseCase processAccessRequestUseCase;
    private final GetCredentialsUseCase getCredentialsUseCase;
    private final SelfRegistrationUseCase selfRegistrationUseCase;
    private final LookupFeegowPreRegistrationUseCase lookupFeegowPreRegistrationUseCase;
    private final LookupCredentialsByCpfUseCase lookupCredentialsByCpfUseCase;
    private final ReactivateAccessUseCase reactivateAccessUseCase;
    private final RegisterCompanionUseCase registerCompanionUseCase;
    private final ValidateAccessChallengeUseCase validateAccessChallengeUseCase;
    private final GenerateCalendarIcsUseCase generateCalendarIcsUseCase;
    private final AccessSecurityGuard accessSecurityGuard;

    /**
     * Endpoint de teste manual para validação de acesso das catracas.
     */
    @PostMapping("/test")
    public ResponseEntity<?> testAccess(@RequestParam("doctorId") Long doctorId) {
        log.info("[AccessControl] Executando validação de acesso de teste para o doctorId: {}", doctorId);

        if (doctorId == null || inovareMotorProperties.getTestDoctorIds() == null || !inovareMotorProperties.getTestDoctorIds().contains(doctorId)) {
            log.warn("[AccessControl] Acesso proibido. O doctorId {} não é permitido para testes ou a produção está ativa.", doctorId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", "Acesso negado. O ID fornecido não é elegível para testes."));
        }

        AccessCredential credential = AccessCredential.builder()
            .appointmentId("TEST-APP-" + doctorId + "-" + System.currentTimeMillis())
            .name("PACIENTE TESTE DOCTORID " + doctorId)
            .cpf("123.456.789-00")
            .userType(UserType.PATIENT)
            .accessCredential("CRED-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .locator("LOC-" + System.currentTimeMillis())
            .createdAt(LocalDateTime.now())
            .build();

        AccessCredential saved = accessCredentialRepositoryPort.save(credential);
        log.info("[AccessControl] Credencial de teste salva: ID={}, Nome={}", saved.getId(), saved.getName());

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Acesso de teste permitido e credencial persistida com sucesso.",
            "doctorId", doctorId,
            "credencialId", saved.getId()
        ));
    }

    /**
     * Endpoint de validação de acesso utilizado pelo bot do Blip ou portal web.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateAccess(@RequestBody @Valid AccessValidationRequest request) {
        log.info("[AccessControl] Requisição de validação de acesso recebida para o agendamento ID: {}", request.appointmentId());

        List<CompanionAccessInfo> companions = null;
        if (request.companions() != null && !request.companions().isEmpty()) {
            companions = request.companions().stream()
                .map(dto -> new CompanionAccessInfo(
                    dto.name(),
                    dto.cpf(),
                    dto.phone(),
                    dto.email(),
                    dto.birthDate()
                ))
                .toList();
        }

        AccessValidationResult result = processAccessRequestUseCase.execute(
            request.appointmentId(),
            request.cpf(),
            companions,
            request.credentialId(),
            request.targetName(),
            request.userType()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint de auto-cadastro público (Clínica da Imagem ou clínicas externas).
     */
    @PostMapping("/self-registration")
    public ResponseEntity<?> selfRegistration(@RequestBody @Valid SelfRegistrationRequest request) {
        log.info("[AccessControl] Auto-cadastro público recebido: Nome={}, CPF={}, Clínica={}", 
                request.name(), request.cpf(), request.clinic());
        try {
            List<CompanionAccessInfo> domainCompanions = new ArrayList<>();
            if (request.companions() != null && !request.companions().isEmpty()) {
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
            } else if (request.companion() != null && request.companion().name() != null && !request.companion().name().isBlank()) {
                domainCompanions.add(new CompanionAccessInfo(
                    request.companion().name(),
                    request.companion().cpf(),
                    request.companion().phone(),
                    null,
                    request.companion().birthDate()
                ));
            }

            // Deduplica acompanhantes para proteção absoluta contra envio duplo
            Map<String, CompanionAccessInfo> dedupMap = new LinkedHashMap<>();
            for (CompanionAccessInfo comp : domainCompanions) {
                String cleanCpf = CpfValidator.cleanCpf(comp.cpf());
                String key = !cleanCpf.isBlank() ? cleanCpf : comp.name().trim().toLowerCase();
                dedupMap.putIfAbsent(key, comp);
            }
            domainCompanions = new ArrayList<>(dedupMap.values());

            if (request.appointmentId() != null && !request.appointmentId().isBlank()
                    && !request.appointmentId().startsWith("INOV-")
                    && !request.appointmentId().startsWith("IMG-")) {
                try {
                    log.info("[AccessControl] Auto-cadastro com agendamento Feegow vinculado: {}", request.appointmentId());
                    var valResult = processAccessRequestUseCase.execute(
                        request.appointmentId(),
                        request.cpf(),
                        domainCompanions
                    );
                    if (valResult != null && valResult.authorized()) {
                        List<AccessCredential> feegowCreds = accessCredentialRepositoryPort.findByAppointmentId(request.appointmentId());
                        if (!feegowCreds.isEmpty()) {
                            String docName = (request.doctorName() != null && !request.doctorName().isBlank())
                                ? request.doctorName().trim()
                                : feegowCreds.getFirst().getDoctorName();
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
                                    "23:59",
                                    cred.getId()
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

            List<AccessCredential> credentials = selfRegistrationUseCase.processSelfRegistration(
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

            String firstAppId = credentials.getFirst().getAppointmentId();
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
                    "23:59",
                    cred.getId()
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
     */
    @PostMapping("/feegow-lookup")
    public ResponseEntity<?> feegowLookup(@RequestBody @Valid CpfLookupRequest request, HttpServletRequest httpRequest) {
        String clientIp = accessSecurityGuard.extractClientIp(httpRequest);
        if (!accessSecurityGuard.tryAcquireCpfLookup(clientIp)) {
            log.warn("[AccessControl] Rate limit excedido para consulta prévia Feegow por CPF pelo IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Muitas consultas realizadas em pouco tempo. Por favor, aguarde um minuto e tente novamente."));
        }

        log.info("[AccessControl] Requisição de consulta prévia no Feegow para CPF: {} (IP: {})", maskCpf(request.cpf()), clientIp);
        FeegowPreRegistrationLookupResponse response = lookupFeegowPreRegistrationUseCase.execute(request.cpf(), request.clinic());
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de consulta rápida de credenciais ativas pelo CPF.
     */
    @PostMapping("/lookup-by-cpf")
    public ResponseEntity<?> lookupByCpf(@RequestBody @Valid CpfLookupRequest request, HttpServletRequest httpRequest) {
        String clientIp = accessSecurityGuard.extractClientIp(httpRequest);
        if (!accessSecurityGuard.tryAcquireCpfLookup(clientIp)) {
            log.warn("[AccessControl] Rate limit excedido para busca de credenciais por CPF pelo IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Muitas consultas realizadas em pouco tempo. Por favor, aguarde um minuto e tente novamente."));
        }

        log.info("[AccessControl] Busca de credenciais ativas por CPF: {} (IP: {})", maskCpf(request.cpf()), clientIp);
        try {
            List<AccessCredentialResponse> responseList = lookupCredentialsByCpfUseCase.execute(request.cpf(), request.clinic());
            return ResponseEntity.ok(responseList);
        } catch (Exception ex) {
            log.error("[AccessControl] Erro ao buscar por CPF: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao consultar cadastro."));
        }
    }

    /**
     * Endpoint de reativação de acesso para gerar uma nova credencial no GerAcesso.
     */
    @PostMapping("/reactivate/{appointmentId}")
    public ResponseEntity<?> reactivateAccess(@PathVariable("appointmentId") String appointmentId) {
        log.info("[AccessControl] Solicitação de reativação de acesso para agendamento: {}", appointmentId);
        try {
            List<AccessCredential> credentials = reactivateAccessUseCase.reactivateAccess(appointmentId);
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
                .map(cred -> {
                    String docName = cred.getDoctorName();
                    if (docName == null || docName.isBlank()) {
                        docName = finalDoctorName;
                    }
                    return new AccessCredentialResponse(
                        cred.getAppointmentId(),
                        cred.getName(),
                        cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                        cred.getLocator(),
                        cred.getAccessCredential(),
                        cred.getCpf(),
                        docName,
                        finalAppointmentDateTime,
                        finalOpensAt,
                        finalClosesAt,
                        cred.getId()
                    );
                })
                .toList();

            return ResponseEntity.ok(responseList);
        } catch (NotFoundException ex) {
            log.warn("[AccessControl] Agendamento não localizado para reativação: {}", appointmentId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Nenhuma credencial encontrada para reativar. Verifique os dados ou procure a recepção."));
        } catch (Exception ex) {
            log.error("[AccessControl] Erro ao reativar acesso para {}: {}", appointmentId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erro ao reativar acesso. Tente novamente em instantes."));
        }
    }

    /**
     * Endpoint de cadastro de acompanhante pelo portal web do paciente.
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
            AccessCredential credential = registerCompanionUseCase.registerCompanion(appointmentId, companionInfo);

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
     */
    @GetMapping("/credentials/{idAgendamento}")
    public ResponseEntity<List<AccessCredentialResponse>> getCredentials(
            @PathVariable("idAgendamento") String idAgendamento,
            @RequestParam(value = "phoneDigits", required = false) String phoneDigits,
            @RequestParam(value = "t", required = false) String token) {
        List<AccessCredentialResponse> responses = getCredentialsUseCase.execute(idAgendamento, phoneDigits, token);
        return ResponseEntity.ok(responses);
    }

    /**
     * Endpoint de geração/recuperação de Magic Token e URL direta de acesso para chatbots ou integrações.
     * Restrito a administradores autenticados.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/token/{idAgendamento}")
    public ResponseEntity<?> getAccessToken(@PathVariable("idAgendamento") String idAgendamento) {
        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(idAgendamento);
        if (accessInfoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        String token = validateAccessChallengeUseCase.generateAccessToken(idAgendamento, accessInfo.phone());
        String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + idAgendamento + "?t=" + token;
        return ResponseEntity.ok(Map.of(
            "appointmentId", idAgendamento,
            "token", token,
            "accessUrl", accessUrl
        ));
    }

    /**
     * Endpoint para download e adição de evento iCalendar (.ics) nativo no iOS / Apple Calendar e Outlook.
     */
    @GetMapping(value = "/calendar/event.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getCalendarEventIcs(
            @RequestParam(value = "title", defaultValue = "Consulta Médica - Inovare") String title,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "location", defaultValue = "Edifício Inovare, Ponta Grossa - PR") String location,
            @RequestParam(value = "description", defaultValue = "Consulta médica agendada no Edifício Inovare.") String description) {

        String ics = generateCalendarIcsUseCase.execute(title, start, end, location, description);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"consulta-inovare.ics\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=UTF-8")
                .body(ics);
    }

    private String resolveDisplayDate(String appointmentId) {
        if (appointmentId != null && appointmentId.length() >= 13 && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
            try {
                String datePart = appointmentId.substring(5, 13);
                LocalDate parsedDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
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

    private String maskCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return "N/A";
        }
        String clean = cpf.replaceAll("\\D", "");
        if (clean.length() == 11) {
            return "***." + clean.substring(3, 6) + ".***-" + clean.substring(9, 11);
        }
        return "***";
    }
}
