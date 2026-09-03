package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.InvalidChallengeException;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.FeegowPreRegistrationLookupResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Serviço de domínio AccessService.
 * Gerencia a inteligência de agrupamento de agendamentos por CPF/telefone, cálculo de janelas de tempo,
 * cadastro de liberação física de acesso na GerAcesso API e orquestração assíncrona de acompanhantes.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessService {

    public record DoctorAccessData(String matricula, String cpf) {}



    /**
     * Fuso horário local da Clínica Inovare (Ponta Grossa-PR).
     * Definido explicitamente aqui para blindar o serviço contra divergências de timezone
     * em servidores de nuvem (UTC por padrão), garantindo que LocalDate.now(), as janelas
     * de abertura (-2h) e o fechamento fixo (21:00) sempre reflitam a hora local da clínica.
     */
    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * Formatador de data e hora imutável e thread-safe para integração com a GerAcesso API.
     * Definido como constante estática para evitar instanciação repetida em alto volume de chamadas.
     * Formato exigido pela GerAcesso: 'dd/MM/yyyy HH:mm'.
     */
    private static final DateTimeFormatter GERACESSO_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FeegowClientPort feegowClientPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final PatientExternalPort patientExternalPort;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final GerAcessoClientPort gerAcessoClientPort;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;

    private final java.util.concurrent.ConcurrentMap<String, FeegowPatient> patientCache = new java.util.concurrent.ConcurrentHashMap<>();

    private FeegowPatient getPatientInfoWithCache(String patientId) {
        if (patientId == null) {
            return null;
        }
        FeegowPatient cached = patientCache.get(patientId);
        if (cached != null) {
            return cached;
        }
        try {
            FeegowPatient patient = patientExternalPort.patientInfo(patientId);
            if (patient != null) {
                patientCache.put(patientId, patient);
            }
            return patient;
        } catch (Exception ex) {
            log.warn("[AccessService] Erro ao buscar prontuário do paciente ID: {} para cache. Causa: {}", patientId, ex.getMessage());
            return null;
        }
    }

    /**
     * Processa a requisição de acesso para um determinado agendamento. Realiza agrupamento de consultas,
     * cálculo de janela de abertura de catracas, cadastro do Paciente Titular na GerAcesso, persistência
     * local e orquestração paralela (Virtual Threads) e resiliente dos acompanhantes.
     *
     * @param appointmentId Identificador do agendamento vindo do Blip ou frontend.
     * @param requestCpf CPF opcional passado pela requisição.
     * @param companions Lista opcional de acompanhantes contidos no payload.
     * @return AccessValidationResult contendo os dados de liberação e flags de contingência.
     */
    public AccessValidationResult processAccessRequest(String appointmentId, String requestCpf, List<CompanionAccessInfo> companions) {
        return processAccessRequest(appointmentId, requestCpf, companions, null, null, null);
    }

    public AccessValidationResult processAccessRequest(
            String appointmentId,
            String requestCpf,
            List<CompanionAccessInfo> companions,
            UUID credentialId,
            String targetName,
            String userType) {
        log.info("[AccessService] Processando solicitação de acesso físico. Agendamento: {}, credentialId={}, targetName={}, userType={}",
                appointmentId, credentialId, targetName, userType);

        // Caso específico: Atualização de CPF de ACOMPANHANTE
        boolean isCompanionTarget = "COMPANION".equalsIgnoreCase(userType);
        if (!isCompanionTarget && credentialId != null) {
            List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            isCompanionTarget = creds != null && creds.stream().anyMatch(c -> c.getId() != null && c.getId().equals(credentialId) && c.getUserType() == UserType.COMPANION);
        }

        if (isCompanionTarget && requestCpf != null && !requestCpf.isBlank()) {
            log.info("[AccessService] Redirecionando para atualização dedicada de CPF de ACOMPANHANTE: agendamento={}, targetName={}", appointmentId, targetName);
            return processCompanionCpfUpdate(appointmentId, requestCpf, credentialId, targetName);
        }

        // 0. Verificação de Existência (Idempotência) antes de criar/salvar novas credenciais.
        // Se já existem credenciais REAIS (não contingenciais CRED-) para este agendamento e nenhum novo CPF foi informado,
        // retornamos imediatamente para evitar chamadas redundantes.
        List<AccessCredential> existingList = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        boolean hasOnlyContingency = existingList != null && !existingList.isEmpty() && existingList.stream()
                .allMatch(c -> c.getAccessCredential() != null && c.getAccessCredential().startsWith("CRED-"));

        if (existingList != null && !existingList.isEmpty() && !hasOnlyContingency 
                && (requestCpf == null || requestCpf.isBlank()) && (companions == null || companions.isEmpty())) {
            log.info("[AccessService] Credenciais já existentes encontradas no banco para o agendamento ID: {}. Ignorando processamento redundante.", appointmentId);
            Optional<AccessCredential> patientCredOpt = existingList.stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst();
            String token = generateAccessToken(appointmentId, null);
            String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
            if (patientCredOpt.isPresent()) {
                AccessCredential patientCred = patientCredOpt.get();
                return new AccessValidationResult(true, patientCred.getName(), patientCred.getAccessCredential(), false, "Credencial resolvida com sucesso (recuperada do banco).", token, accessUrl);
            } else {
                AccessCredential firstCred = existingList.getFirst();
                return new AccessValidationResult(true, firstCred.getName(), firstCred.getAccessCredential(), false, "Credencial resolvida com sucesso (recuperada do banco).", token, accessUrl);
            }
        }

        // Auto-cadastro público (ex: INOV-... ou IMG-...): atualiza o CPF diretamente
        if (appointmentId != null && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
            return processSelfRegistrationCpfUpdate(appointmentId, requestCpf);
        }

        // 1. Busca os dados cadastrais do agendamento no Feegow.
        // Resiliência: caso o ERP Feegow oscile (5xx, timeout, IOException), retornamos o Fallback
        // Seguro imediatamente (requiresCpfFallback = true) em vez de propagar a exceção ao Blip.
        Optional<FeegowPatientAccessInfo> accessInfoOpt;
        try {
            accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        } catch (Exception ex) {
            // Passando 'ex' como terceiro argumento do SLF4J para preservar o stacktrace completo nos logs de produção
            log.warn("[AccessService] ERP Feegow indisponível ao buscar agendamento {}. Ativando Fallback Seguro.",
                    appointmentId, ex);
            String token = generateAccessToken(appointmentId, null);
            String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
            return new AccessValidationResult(false, null, null, true, "ERP Feegow temporária e indisponível. Solicite o CPF ao paciente.", token, accessUrl);
        }
        if (accessInfoOpt.isEmpty()) {
            log.warn("[AccessService] Não foi possível obter dados do agendamento {} na API Feegow.", appointmentId);
            return new AccessValidationResult(false, null, null, false, "Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();

        // Busca prontuário para recuperar o telefone e suportar agrupamentos familiares
        FeegowPatient mainPatient = getPatientInfoWithCache(accessInfo.patientId());
        String patientName = mainPatient != null ? mainPatient.name() : null;
        String patientBirthdate = mainPatient != null ? mainPatient.birthdate() : null;

        // 2. Resolve o CPF (priorizando o CPF do prontuário local/Feegow caso seja válido, com fallback para o da requisição)
        String rawFeegowCpf = accessInfo.cpf();
        if (rawFeegowCpf == null || rawFeegowCpf.isBlank()) {
            if (mainPatient != null && mainPatient.cpf() != null && !mainPatient.cpf().isBlank()) {
                rawFeegowCpf = mainPatient.cpf();
            }
        }

        String cleanFeegowCpf = rawFeegowCpf != null ? rawFeegowCpf.replaceAll("\\D", "") : "";
        String cleanRequestCpf = "";
        if (requestCpf != null && !requestCpf.isBlank() && !requestCpf.contains("{{") && !requestCpf.equalsIgnoreCase("null")) {
            cleanRequestCpf = requestCpf.replaceAll("\\D", "");
        }

        String resolvedCpf = null;
        if (isValidCpf(cleanRequestCpf)) {
            // Se o paciente acabou de submeter um CPF válido (via formulário web ou chat), usa-o com prioridade
            resolvedCpf = cleanRequestCpf;
            try {
                String resolvedName = (patientName != null && !patientName.isBlank()) ? patientName : accessInfo.name();
                log.info("[AccessService] Sincronizando CPF válido informado ({}) de volta com a Feegow para o paciente ID: {}", resolvedCpf, accessInfo.patientId());
                patientExternalPort.updatePatientCpf(accessInfo.patientId(), resolvedCpf, resolvedName, patientBirthdate);
            } catch (Exception e) {
                log.error("[AccessService] Falha ao sincronizar CPF com a Feegow: {}", e.getMessage());
            }
        } else if (isValidCpf(cleanFeegowCpf)) {
            resolvedCpf = cleanFeegowCpf;
        }

        String targetPhone = mainPatient != null && mainPatient.phone() != null ? mainPatient.phone() : accessInfo.phone();

        // 3. Contingência de CPF Nulo, incompleto ou matematicamente inválido (Receita Federal):
        // Se o CPF for nulo ou inválido, solicitamos a confirmação do CPF correto para liberação física na catraca.
        if (resolvedCpf == null || resolvedCpf.isBlank() || !isValidCpf(resolvedCpf)) {
            log.warn("[AccessService] Prontuário Feegow sem CPF válido pela Receita Federal (valor Feegow: '{}', valor request: '{}') para o paciente ID: {}. Ativando flag 'requiresCpfFallback'.",
                    cleanFeegowCpf, cleanRequestCpf, accessInfo.patientId());
            String token = generateAccessToken(appointmentId, targetPhone);
            String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
            return new AccessValidationResult(false, null, null, true, "Por favor, confirme seu CPF para liberação da catraca.", token, accessUrl);
        }

        // Usa o timezone local explícito da clínica para garantir que LocalDate.now() reflita
        // a data/hora local mesmo em servidores de nuvem configurados em UTC.
        LocalDate resolvedAppointmentDate = accessInfo.appointmentDate();
        if (resolvedAppointmentDate == null) {
            resolvedAppointmentDate = LocalDate.now(CLINIC_ZONE);
        }
        if (resolvedAppointmentDate.isBefore(LocalDate.now(CLINIC_ZONE))) {
            log.info("[AccessService] Data do agendamento ({}) em atraso. Ajustando data de visita para hoje ({}) para compatibilidade com a catraca.", resolvedAppointmentDate, LocalDate.now(CLINIC_ZONE));
            resolvedAppointmentDate = LocalDate.now(CLINIC_ZONE);
        }
        final LocalDate appointmentDate = resolvedAppointmentDate;

        // 4. Busca todos os agendamentos marcados daquela data para agrupamento (status 0 = todos os status ativos)
        log.info("[AccessService] Buscando pauta do dia {} no Feegow para agrupamento de consultas...", appointmentDate);
        List<FeegowAppointment> dailyAppointments = appointmentExternalPort.searchAppointments(appointmentDate, 0);

        List<FeegowAppointment> matchingAppointments = new ArrayList<>();
        String finalCpf = resolvedCpf;

        // Lógica de Agrupamento: Agrupamos agendamentos do mesmo paciente (mesmo patientId) para a data.
        for (FeegowAppointment app : dailyAppointments) {
            if (app.patientId().equals(accessInfo.patientId())) {
                matchingAppointments.add(app);
            }
        }

        // Também incorpora sessões locais do paciente agendadas para a data (evitando dependência estrita do Feegow)
        if (accessInfo.patientId() != null) {
            try {
                var sessions = appointmentSessionRepository.findByPatientId(accessInfo.patientId());
                for (var s : sessions) {
                    if (s.getAppointmentAt() != null && s.getAppointmentAt().toLocalDate().equals(appointmentDate)) {
                        boolean alreadyPresent = matchingAppointments.stream().anyMatch(m -> m.id().equals(s.getFeegowAppointmentId()));
                        if (!alreadyPresent && s.getFeegowAppointmentId() != null) {
                            matchingAppointments.add(new FeegowAppointment(
                                s.getFeegowAppointmentId(),
                                accessInfo.patientId(),
                                s.getDoctorProfissionalId() != null ? s.getDoctorProfissionalId() : "",
                                "",
                                "",
                                s.getAppointmentAt(),
                                "1",
                                "",
                                "",
                                false
                            ));
                            log.info("[AccessService] Sessão adicional ({}) incorporada ao agrupamento de hoje para paciente {}", 
                                    s.getFeegowAppointmentId(), accessInfo.patientId());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[AccessService] Erro ao incorporar sessões locais no agrupamento: {}", ex.getMessage());
            }
        }

        // Garante que o agendamento atual esteja na lista mesmo em caso de falha de agrupamento
        if (matchingAppointments.isEmpty()) {
            matchingAppointments.add(new FeegowAppointment(
                accessInfo.appointmentId(),
                accessInfo.patientId(),
                accessInfo.doctorId(),
                accessInfo.doctorName(),
                "",
                LocalDateTime.of(appointmentDate, accessInfo.appointmentTime() != null ? accessInfo.appointmentTime() : LocalTime.of(12, 0)),
                "1",
                "",
                "",
                false
            ));
        }

        // 5. Identifica o menor horário de consulta (a primeira do dia)
        LocalTime earliestTime = matchingAppointments.stream()
            .map(app -> app.startAt().toLocalTime())
            .min(java.util.Comparator.naturalOrder())
            .orElse(accessInfo.appointmentTime() != null ? accessInfo.appointmentTime() : LocalTime.of(12, 0));

        // 6. Janela de Abertura na Catraca GerAcesso:
        // No dia da consulta, a catraca física é liberada a partir das 06:00 (ou início da janela) até as 23:00,
        // garantindo que pacientes que chegam com antecedência ou têm múltiplas consultas não sejam barrados.
        LocalTime physicalOpeningTime = LocalTime.of(6, 0);
        LocalTime closingTime = LocalTime.of(23, 0);

        log.info("[ACCESS-WINDOW] Janela GerAcesso calculada para a data {}. Acesso físico liberado das {} às {}. Menor consulta: {}.",
            appointmentDate, physicalOpeningTime, closingTime, earliestTime);

        // 7. Verificação de Credencial existente especificamente para este agendamento.
        // Importante: Não reutilizamos credencial de outro agendamento do mesmo paciente em horários distintos,
        // pois a catraca da GerAcesso encerra e dá baixa na visita quando o paciente sai do prédio (anti-passback).
        // Cada agendamento precisa de sua própria credencial com o respectivo médico visitado.
        List<AccessCredential> existingForThisApp = accessCredentialRepositoryPort.findByAppointmentId(accessInfo.appointmentId());
        Optional<AccessCredential> activeCredOpt = existingForThisApp.stream()
            .filter(c -> c.getUserType() == UserType.PATIENT
                      && c.getAccessCredential() != null
                      && !c.getAccessCredential().startsWith("CRED-"))
            .findFirst();

        String token;
        String locator;

        if (activeCredOpt.isPresent()) {
            // Reutiliza o token e localizador real existente para este agendamento
            AccessCredential existing = activeCredOpt.get();
            token = existing.getAccessCredential();
            locator = existing.getLocator();
            log.info("[AccessService] Reutilizando credencial ativa existente para o agendamento {}: {}", accessInfo.appointmentId(), token);
        } else {
            // Constrói payload de requisição para registrar o Paciente Titular na GerAcesso
            String startVisit = LocalDateTime.of(appointmentDate, physicalOpeningTime).format(GERACESSO_DATE_FORMATTER);
            String endVisit = LocalDateTime.of(appointmentDate, closingTime).format(GERACESSO_DATE_FORMATTER);

            // Resolve os dados do médico para injeção de visitado
            String docId = accessInfo.doctorId();
            String matricula = "";
            String doctorCpf = "";
            if (docId != null && !docId.isBlank()) {
                DoctorAccessData docData = resolveDoctorAccessData(docId);
                matricula = docData.matricula();
                doctorCpf = docData.cpf();
            }

            GerAcessoRequest titularRequest = GerAcessoRequest.builder()
                .cpf(finalCpf)
                .status(1)
                .name(accessInfo.name())
                .phone(targetPhone != null ? targetPhone : "")
                .email("")
                .visitType(1) // 1 = PACIENTE / TITULAR
                .startVisit(startVisit)
                .endVisit(endVisit)
                .visitedRegistration(matricula)
                .visitedCpf(doctorCpf)
                .build();

            // Tenta cadastro na GerAcesso com CPF de 11 dígitos
            boolean isCpfValid = finalCpf != null && finalCpf.length() == 11;
            Optional<GerAcessoResponse> responseOpt = Optional.empty();

            if (isCpfValid) {
                log.info("[AccessService] Enviando cadastro do paciente titular {} para a GerAcesso local (Médico: {})...", accessInfo.name(), accessInfo.doctorName());
                responseOpt = gerAcessoClientPort.registerAccess(titularRequest);
            } else {
                log.warn("[GERACESSO-CPF] CPF do paciente ID {} incompleto (CPF: {}). Ativando credencial local contingencial.",
                        accessInfo.patientId(), finalCpf);
            }

            if (responseOpt.isPresent() && responseOpt.get().credential() != null && !responseOpt.get().credential().isBlank()) {
                token = responseOpt.get().credential().trim();
                locator = responseOpt.get().locator() != null ? responseOpt.get().locator().trim() : "";
                log.info("[AccessService] Cadastro concluído na GerAcesso. Credencial (QR Code)={}, Locator={}", token, locator);
            } else {
                // Fallback: se falhar a conexão local ou CPF for inválido, gera credencial interna para contingência e recepção manual
                token = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                locator = "LOC-" + System.currentTimeMillis();
                log.warn("[AccessService] Utilizando credencial local contingencial.");
            }
        }

        // 8. Salva o registro no banco mapeando para o ID deste agendamento
        List<AccessCredential> savedList = accessCredentialRepositoryPort.findByAppointmentId(accessInfo.appointmentId());
        String cleanPatientPhone = targetPhone != null ? targetPhone.replaceAll("\\D", "") : null;
        if (cleanPatientPhone != null && cleanPatientPhone.isBlank()) cleanPatientPhone = null;

        if (savedList.isEmpty()) {
            AccessCredential credential = AccessCredential.builder()
                .appointmentId(accessInfo.appointmentId())
                .name(accessInfo.name())
                .cpf(finalCpf)
                .phone(cleanPatientPhone)
                .doctorName(accessInfo.doctorName())
                .userType(UserType.PATIENT)
                .accessCredential(token)
                .locator(locator)
                .createdAt(LocalDateTime.now())
                .build();

            accessCredentialRepositoryPort.save(credential);
            log.info("[AccessService] Credencial associada e salva para o agendamento ID: {}", accessInfo.appointmentId());
        } else {
            // Se já existia e era uma credencial contingencial (CRED-), atualiza para a credencial real do GerAcesso
            AccessCredential existingCred = savedList.get(0);
            if (cleanPatientPhone != null && !cleanPatientPhone.isBlank()) {
                existingCred.setPhone(cleanPatientPhone);
            }
            if (accessInfo.doctorName() != null && !accessInfo.doctorName().isBlank()) {
                existingCred.setDoctorName(accessInfo.doctorName());
            }
            if (finalCpf != null && !finalCpf.isBlank() && isValidCpf(finalCpf)) {
                existingCred.setCpf(finalCpf);
            }
            if (existingCred.getAccessCredential() != null 
                    && existingCred.getAccessCredential().startsWith("CRED-") 
                    && !token.startsWith("CRED-")) {
                existingCred.setAccessCredential(token);
                existingCred.setLocator(locator);
                existingCred.setCreatedAt(LocalDateTime.now());
                accessCredentialRepositoryPort.save(existingCred);
                log.info("[AccessService] Atualizando credencial contingencial para credencial GerAcesso real para o agendamento ID: {}", accessInfo.appointmentId());
            } else {
                accessCredentialRepositoryPort.save(existingCred);
            }
        }

        // 9. Orquestração de Acompanhantes: loop paralelo via Java 21 Virtual Threads
        if (companions != null && !companions.isEmpty()) {
            log.info("[AccessService] Iniciando cadastro paralelo de {} acompanhante(s) via Virtual Threads...", companions.size());
            final String finalToken = token;
            final String finalLocator = locator;
            final List<AccessCredential> existingAppCreds = accessCredentialRepositoryPort.findByAppointmentId(accessInfo.appointmentId());

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Void>> futures = companions.stream()
                    .map(companion -> executor.submit(() -> {
                        // Isolamento e resiliência (Fail-Safe) por tarefa
                        try {
                            boolean alreadyRegisteredWithRealCred = existingAppCreds.stream()
                                .anyMatch(c -> c.getName().equalsIgnoreCase(companion.name().trim()) 
                                            && c.getUserType() == UserType.COMPANION
                                            && !c.getAccessCredential().startsWith("CRED-"));
                            if (alreadyRegisteredWithRealCred) {
                                log.info("[AccessService] Acompanhante '{}' já possui credencial GerAcesso real cadastrada para o agendamento {}. Ignorando duplicata.", companion.name(), accessInfo.appointmentId());
                            } else {
                                registerCompanionAccess(companion, appointmentDate, physicalOpeningTime, closingTime, finalToken, finalLocator, accessInfo.appointmentId(), accessInfo.doctorId(), accessInfo.doctorName());
                            }
                        } catch (Exception ex) {
                            log.error("[AccessService] Erro fatal no processamento assíncrono do acompanhante '{}': {}", 
                                    companion.name(), ex.getMessage(), ex);
                        }
                        return (Void) null;
                    }))
                    .toList();

                // Aguarda a execução de todas as tarefas de cadastro
                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        log.error("[AccessService] Falha ao recuperar resultado da Virtual Thread do acompanhante", e);
                    }
                }
            }
            log.info("[AccessService] Processamento assíncrono de acompanhantes finalizado.");
        }

        String magicToken = generateAccessToken(appointmentId, targetPhone);
        String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + magicToken;
        return new AccessValidationResult(true, accessInfo.name(), token, false, "Credencial resolvida com sucesso.", magicToken, accessUrl);
    }

    /**
     * Cadastra um acompanhante isoladamente para um determinado agendamento.
     * Herda os mesmos dados do agendamento (médico visitado, data e janela de horários) e efetua o cadastro unificado na GerAcesso.
     *
     * @param appointmentId Identificador do agendamento titular.
     * @param companion Informações do acompanhante.
     * @return AccessCredential gerado para o acompanhante.
     */
    public AccessCredential registerCompanion(String appointmentId, CompanionAccessInfo companion) {
        log.info("[AccessService] Processando cadastro individual de acompanhante '{}' para agendamento {}", companion.name(), appointmentId);

        if (appointmentId != null && (appointmentId.startsWith("IMG-") || appointmentId.startsWith("INOV-"))) {
            LocalDate date = LocalDate.now(CLINIC_ZONE);
            LocalTime openingTime = LocalTime.of(6, 0);
            LocalTime closingTime = LocalTime.of(23, 59);
            String docName = accessCredentialRepositoryPort.findByAppointmentId(appointmentId).stream()
                    .filter(c -> c != null && c.getDoctorName() != null && !c.getDoctorName().isBlank())
                    .map(c -> c.getDoctorName())
                    .findFirst()
                    .orElse(null);
            return registerCompanionAccess(companion, date, openingTime, closingTime, null, null, appointmentId, null, docName);
        }

        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[AccessService] Agendamento {} não encontrado para cadastrar acompanhante.", appointmentId);
            throw new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Agendamento não encontrado.");
        }
        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();

        LocalDate resolvedDate = accessInfo.appointmentDate();
        if (resolvedDate == null) {
            resolvedDate = LocalDate.now(CLINIC_ZONE);
        }
        if (resolvedDate.isBefore(LocalDate.now(CLINIC_ZONE))) {
            resolvedDate = LocalDate.now(CLINIC_ZONE);
        }
        LocalDate date = resolvedDate;

        LocalTime openingTime = LocalTime.of(6, 0);
        LocalTime closingTime = LocalTime.of(23, 0);

        return registerCompanionAccess(companion, date, openingTime, closingTime, null, null, appointmentId, accessInfo.doctorId(), accessInfo.doctorName());
    }

    /**
     * Efetua o cadastro individual e isolado (fail-safe) do acompanhante na GerAcesso e no banco local.
     */
    private AccessCredential registerCompanionAccess(
            CompanionAccessInfo companion,
            LocalDate date,
            LocalTime openingTime,
            LocalTime closingTime,
            String patientToken,
            String patientLocator,
            String appointmentId,
            String docId,
            String doctorName) {

        String companionCpf = companion.cpf() != null ? companion.cpf().replaceAll("\\D", "") : "";
        // Reutiliza a constante estática imutável GERACESSO_DATE_FORMATTER
        String startVisit = LocalDateTime.of(date, openingTime).format(GERACESSO_DATE_FORMATTER);
        String endVisit = LocalDateTime.of(date, closingTime).format(GERACESSO_DATE_FORMATTER);

        // Resolve os dados do médico para injeção de visitado
        String matricula = "";
        String doctorCpf = "";
        if (docId != null && !docId.isBlank()) {
            DoctorAccessData docData = resolveDoctorAccessData(docId);
            matricula = docData.matricula();
            doctorCpf = docData.cpf();
        }

        GerAcessoRequest request = GerAcessoRequest.builder()
            .cpf(companionCpf)
            .status(1)
            .name(companion.name())
            .phone(companion.phone() != null ? companion.phone() : "")
            .email(companion.email() != null ? companion.email() : "")
            .visitType(1) // 1 = PACIENTE / TITULAR / ACOMPANHANTE
            .startVisit(startVisit)
            .endVisit(endVisit)
            .visitedRegistration(matricula)
            .visitedCpf(doctorCpf)
            .build();

        boolean isCompanionCpfValid = companionCpf.length() == 11 && isValidCpf(companionCpf);
        Optional<GerAcessoResponse> responseOpt = Optional.empty();

        if (!isCompanionCpfValid) {
            log.warn("[GERACESSO-CPF] CPF do acompanhante '{}' é inválido ou com formato incompleto (CPF: {}). Tentando cadastro na GerAcesso física.",
                    companion.name(), companionCpf);
            if (!companionCpf.isEmpty()) {
                responseOpt = gerAcessoClientPort.registerAccess(request);
            }
        } else {
            log.info("[AccessService] Cadastrando acompanhante {} na GerAcesso...", companion.name());
            responseOpt = gerAcessoClientPort.registerAccess(request);
        }

        String companionToken;
        String companionLocator;

        if (responseOpt.isPresent() && responseOpt.get().credential() != null && !responseOpt.get().credential().isBlank()) {
            companionToken = responseOpt.get().credential().trim();
            companionLocator = responseOpt.get().locator() != null ? responseOpt.get().locator().trim() : "";
            log.info("[AccessService] Acompanhante {} cadastrado com sucesso na GerAcesso. Credencial (QR Code): {}, Localizador: {}", companion.name(), companionToken, companionLocator);
        } else {
            companionToken = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            companionLocator = "LOC-" + System.currentTimeMillis();
            log.warn("[AccessService] Utilizando credencial local contingencial para acompanhante {}.", companion.name());
        }

        // Verifica se já existe um registro salvo no banco para este acompanhante no agendamento
        Optional<AccessCredential> existingCredOpt = accessCredentialRepositoryPort
            .findByAppointmentId(appointmentId).stream()
            .filter(c -> c.getName().equalsIgnoreCase(companion.name().trim()) && c.getUserType() == UserType.COMPANION)
            .findFirst();

        String cleanCompPhone = companion.phone() != null ? companion.phone().replaceAll("\\D", "") : null;
        if (cleanCompPhone != null && cleanCompPhone.isBlank()) cleanCompPhone = null;

        AccessCredential credential;
        if (existingCredOpt.isPresent()) {
            credential = existingCredOpt.get();
            credential.setCpf(companionCpf.isEmpty() ? null : companionCpf);
            if (cleanCompPhone != null) {
                credential.setPhone(cleanCompPhone);
            }
            if (doctorName != null && !doctorName.isBlank()) {
                credential.setDoctorName(doctorName.trim());
            }
            credential.setAccessCredential(companionToken);
            credential.setLocator(companionLocator);
            credential.setCreatedAt(LocalDateTime.now());
            log.info("[AccessService] Atualizando credencial existente do acompanhante {} no banco local com credencial GerAcesso: {}", companion.name(), companionToken);
        } else {
            credential = AccessCredential.builder()
                .appointmentId(appointmentId)
                .name(companion.name())
                .cpf(companionCpf.isEmpty() ? null : companionCpf)
                .phone(cleanCompPhone)
                .doctorName(doctorName != null ? doctorName.trim() : null)
                .userType(UserType.COMPANION)
                .accessCredential(companionToken)
                .locator(companionLocator)
                .createdAt(LocalDateTime.now())
                .build();
        }

        AccessCredential saved = accessCredentialRepositoryPort.save(credential);
        log.info("[AccessService] Credencial do acompanhante {} salva no banco local com sucesso. (ID={})", companion.name(), saved.getId());
        return saved;
    }

    /**
     * Gera um token determinístico e criptograficamente seguro (HMAC-SHA256) para o agendamento e telefone do paciente.
     * Permite autenticação sem senhas (Magic Link) conforme os princípios de segurança e LGPD.
     */
    public String generateAccessToken(String appointmentId, String phone) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return "";
        }
        try {
            String cleanPhone = phone != null ? phone.replaceAll("\\D", "") : "";
            if (cleanPhone.startsWith("55") && (cleanPhone.length() == 12 || cleanPhone.length() == 13)) {
                cleanPhone = cleanPhone.substring(2);
            }
            String raw = appointmentId + ":" + cleanPhone;
            String secret = "inovare_magic_access_token_secret_key_2026";
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("[AccessService] Erro ao gerar token de acesso HMAC", ex);
            return "";
        }
    }

    /**
     * Valida o desafio de acesso seguro utilizando Magic Token (HMAC) ou os 4 dígitos do telefone.
     */
    public FeegowPatientAccessInfo validateAccessChallenge(String appointmentId, String phoneDigits, String token) {
        log.info("[AccessService] Validando credenciais para o agendamento ID: {} (token={})", 
                appointmentId, token != null && !token.isBlank() ? "presente" : "ausente");

        // Tratamento para auto-check-in da Clínica da Imagem ou Inovare
        if (appointmentId != null && (appointmentId.startsWith("IMG-") || appointmentId.startsWith("INOV-"))) {
            List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            if (creds == null || creds.isEmpty()) {
                throw new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Credencial de acesso não encontrada.");
            }
            AccessCredential patient = creds.stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst()
                .orElse(creds.get(0));

            LocalDate appDate = LocalDate.now(CLINIC_ZONE);
            if (appointmentId.length() >= 13) {
                try {
                    String datePart = appointmentId.substring(5, 13);
                    appDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                } catch (Exception ignored) {}
            }

            String docName = (patient.getDoctorName() != null && !patient.getDoctorName().isBlank())
                    ? patient.getDoctorName()
                    : (appointmentId.startsWith("INOV-") ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare");

            return new FeegowPatientAccessInfo(
                patient.getAppointmentId(),
                patient.getLocator(),
                patient.getName(),
                patient.getCpf(),
                appDate,
                LocalTime.of(8, 0),
                null,
                docName,
                ""
            );
        }

        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[AccessService] Agendamento {} não encontrado para validação do desafio.", appointmentId);
            throw new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        FeegowPatient mainPatient = getPatientInfoWithCache(accessInfo.patientId());
        String phone = mainPatient != null && mainPatient.phone() != null && !mainPatient.phone().isBlank()
                ? mainPatient.phone()
                : accessInfo.phone();

        // 1. Validação prioritária por Magic Token criptográfico
        if (token != null && !token.isBlank()) {
            String trimmedToken = token.trim();
            String expectedToken1 = generateAccessToken(appointmentId, phone);
            String expectedToken2 = generateAccessToken(appointmentId, accessInfo.phone());
            String expectedToken3 = generateAccessToken(appointmentId, "");

            if (expectedToken1.equalsIgnoreCase(trimmedToken) 
                    || expectedToken2.equalsIgnoreCase(trimmedToken) 
                    || expectedToken3.equalsIgnoreCase(trimmedToken)) {
                log.info("[AccessService] Magic Token criptográfico validado com sucesso para o agendamento {}", appointmentId);
                return accessInfo;
            }
            log.warn("[AccessService] Token de acesso inválido para agendamento {}. Esperado: {}, Recebido: {}", 
                    appointmentId, expectedToken1, token);
        }

        // 2. Validação por 4 dígitos do telefone (fallback tradicional)
        if (phoneDigits != null && !phoneDigits.isBlank()) {
            return validatePhoneChallenge(appointmentId, phoneDigits);
        }

        throw new InvalidChallengeException("Identificação necessária. Por favor, utilize o link recebido no WhatsApp.");
    }

    /**
     * Valida o desafio dos 4 últimos dígitos do telefone do paciente cadastrado no prontuário.
     * Caso os dígitos não batam, lança InvalidChallengeException.
     * Comentários em PT-BR pelas Regras de Ouro.
     *
     * @param appointmentId Identificador do agendamento.
     * @param phoneDigits Os 4 dígitos enviados para validação.
     */
    public FeegowPatientAccessInfo validatePhoneChallenge(String appointmentId, String phoneDigits) {
        log.info("[AccessService] Validando desafio de telefone para o agendamento ID: {}", appointmentId);
        
        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[AccessService] Agendamento {} não encontrado para validação do desafio.", appointmentId);
            throw new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        FeegowPatient mainPatient = getPatientInfoWithCache(accessInfo.patientId());
        
        String phone = mainPatient != null && mainPatient.phone() != null && !mainPatient.phone().isBlank() 
                ? mainPatient.phone() 
                : accessInfo.phone();
        if (phone == null || phone.isBlank()) {
            log.warn("[AccessService] Paciente ID {} não possui telefone cadastrado no prontuário.", accessInfo.patientId());
            throw new InvalidChallengeException("Não há telefone cadastrado no prontuário do paciente.");
        }

        // Robustez: Mantém apenas números puros do telefone cadastrado
        String cleanPhone = phone.replaceAll("\\D", "");
        if (cleanPhone.length() < 4) {
            log.warn("[AccessService] Telefone cadastrado '{}' (limpo: '{}') possui menos de 4 dígitos.", phone, cleanPhone);
            throw new InvalidChallengeException("Telefone inválido cadastrado no prontuário.");
        }

        // Extrai os 4 últimos dígitos do telefone limpo
        String lastFourDigits = cleanPhone.substring(cleanPhone.length() - 4);
        
        // Limpa os dígitos enviados pelo usuário para comparação
        String cleanInputDigits = phoneDigits != null ? phoneDigits.replaceAll("\\D", "") : "";

        if (!lastFourDigits.equals(cleanInputDigits)) {
            log.warn("[AccessService] Falha no desafio de segurança para agendamento {}. Esperado: {}, Recebido: {}", 
                    appointmentId, lastFourDigits, cleanInputDigits);
            throw new InvalidChallengeException("Os dígitos informados estão incorretos. Por favor, tente novamente.");
        }
        
        log.info("[AccessService] Desafio de segurança validado com sucesso para agendamento {}", appointmentId);
        return accessInfo;
    }

    private DoctorAccessData resolveDoctorAccessData(String docId) {
        if (docId == null || docId.isBlank()) {
            return new DoctorAccessData("", "");
        }
        try {
            Long docProfId = Long.parseLong(docId.trim());
            return doctorConfigurationRepository.findById(docProfId)
                .map(config -> {
                    String mat = config.getGerAcessoMatricula() != null ? config.getGerAcessoMatricula().trim() : "";
                    String cpf = config.getGerAcessoCpf() != null ? config.getGerAcessoCpf().trim() : "";
                    log.info("[CATRACA-MÉDICO] Injetando dados do visitado para o profissional ID: {} (Matricula: {}, CPF: {})", docId, mat, cpf);
                    return new DoctorAccessData(mat, cpf);
                })
                .orElseGet(() -> {
                    log.warn("[CATRACA-MÉDICO] Profissional ID: {} não possui metadados GerAcesso configurados na tabela doctor_configurations.", docId);
                    return new DoctorAccessData("", "");
                });
        } catch (Exception ex) {
            log.warn("[CATRACA-MÉDICO] Falha ao buscar credenciais do visitado para o profissional ID: {} - {}", docId, ex.getMessage());
            return new DoctorAccessData("", "");
        }
    }

    /**
     * Valida se um CPF é matematicamente válido utilizando os dígitos verificadores do Módulo 11 (algoritmo da Receita Federal).
     */
    public static boolean isValidCpf(String rawCpf) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return false;
        }
        String cpf = rawCpf.replaceAll("\\D", "");
        if (cpf.length() != 11) {
            return false;
        }
        if (cpf.matches("^(\\d)\\1{10}$")) {
            return false;
        }

        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += (cpf.charAt(i) - '0') * (10 - i);
            }
            int remainder = sum % 11;
            int firstCheck = remainder < 2 ? 0 : (11 - remainder);
            if ((cpf.charAt(9) - '0') != firstCheck) {
                return false;
            }

            sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += (cpf.charAt(i) - '0') * (11 - i);
            }
            remainder = sum % 11;
            int secondCheck = remainder < 2 ? 0 : (11 - remainder);
            return (cpf.charAt(10) - '0') == secondCheck;
        } catch (Exception e) {
            return false;
        }
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic, CompanionAccessInfo companion) {
        return processSelfRegistration(name, rawCpf, phone, birthDate, clinic, null, null, companion != null ? List.of(companion) : List.of());
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic, List<CompanionAccessInfo> companions) {
        return processSelfRegistration(name, rawCpf, phone, birthDate, clinic, null, null, companions);
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic,
            String visitDateStr, String doctorName, List<CompanionAccessInfo> companions) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do paciente é obrigatório.");
        }
        if (rawCpf == null || rawCpf.isBlank()) {
            throw new IllegalArgumentException("CPF do paciente é obrigatório.");
        }

        String cleanCpf = rawCpf.replaceAll("\\D", "");
        if (cleanCpf.length() != 11) {
            throw new IllegalArgumentException("CPF inválido. Deve conter 11 dígitos.");
        }

        LocalDate today = LocalDate.now(CLINIC_ZONE);
        LocalDate visitDate = today;
        if (visitDateStr != null && !visitDateStr.isBlank()) {
            try {
                String cleanDate = visitDateStr.trim();
                if (cleanDate.contains("-")) {
                    visitDate = LocalDate.parse(cleanDate);
                } else if (cleanDate.contains("/")) {
                    visitDate = LocalDate.parse(cleanDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ex) {
                log.warn("[AccessService] Erro ao fazer parse da data da visita '{}': {}. Usando data de hoje.", visitDateStr, ex.getMessage());
            }
        }
        if (visitDate.isBefore(today)) {
            visitDate = today;
        }

        String dateIdSuffix = visitDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = (clinic != null && clinic.toLowerCase().contains("inovare")) ? "INOV-" : "IMG-";
        String appointmentId = prefix + dateIdSuffix + "-" + cleanCpf;

        // 1. Verifica se já existe credencial para este agendamento/data
        List<AccessCredential> existing = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (existing != null && !existing.isEmpty()) {
            log.info("[AccessService] Credencial já existente para auto-cadastro ({}). CPF: {}, ID: {}", clinic, cleanCpf, appointmentId);
            if (companions != null && !companions.isEmpty()) {
                for (CompanionAccessInfo comp : companions) {
                    if (comp != null && comp.name() != null && !comp.name().isBlank()) {
                        String compCpf = comp.cpf() != null ? comp.cpf().replaceAll("\\D", "") : "";
                        boolean compExists = existing.stream().anyMatch(c -> 
                            (c.getName() != null && c.getName().equalsIgnoreCase(comp.name().trim())) ||
                            (!compCpf.isEmpty() && c.getCpf() != null && c.getCpf().replaceAll("\\D", "").equals(compCpf))
                        );
                        if (!compExists) {
                            registerCompanionAccess(
                                comp,
                                visitDate,
                                LocalTime.of(6, 0),
                                LocalTime.of(23, 59),
                                existing.get(0).getAccessCredential(),
                                existing.get(0).getLocator(),
                                appointmentId,
                                null,
                                doctorName
                            );
                        }
                    }
                }
                return accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            }
            return existing;
        }

        // Janela de acesso para a data escolhida (06:00 até 23:59)
        LocalDateTime startWindow = LocalDateTime.of(visitDate, LocalTime.of(6, 0));
        LocalDateTime endWindow = LocalDateTime.of(visitDate, LocalTime.of(23, 59));
        String startDateFormatted = startWindow.format(GERACESSO_DATE_FORMATTER);
        String endDateFormatted = endWindow.format(GERACESSO_DATE_FORMATTER);

        // 2. Registra na GerAcesso
        GerAcessoRequest gerAcessoRequest = GerAcessoRequest.builder()
                .name(name.trim().toUpperCase())
                .cpf(cleanCpf)
                .startVisit(startDateFormatted)
                .endVisit(endDateFormatted)
                .phone(phone != null ? phone.replaceAll("\\D", "") : "")
                .visitType(1)
                .visitedRegistration("")
                .visitedCpf("")
                .build();

        String credentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String locatorValue = cleanCpf;

        try {
            Optional<GerAcessoResponse> gerResponseOpt = gerAcessoClientPort.registerAccess(gerAcessoRequest);
            if (gerResponseOpt.isPresent()) {
                if (gerResponseOpt.get().credential() != null && !gerResponseOpt.get().credential().isBlank()) {
                    credentialValue = gerResponseOpt.get().credential();
                }
                if (gerResponseOpt.get().locator() != null && !gerResponseOpt.get().locator().isBlank()) {
                    locatorValue = gerResponseOpt.get().locator();
                }
                log.info("[AccessService] Paciente {} cadastrado no GerAcesso com sucesso para {}. CPF={}, Credential={}", clinic, visitDate, cleanCpf, credentialValue);
            }
        } catch (Exception ex) {
            log.warn("[AccessService] Falha na integração GerAcesso para paciente {} (usando contingência): {}", clinic, ex.getMessage());
        }

        // 2. Persiste a credencial do paciente titular no banco
        Optional<AccessCredential> existingPatientCredOpt = accessCredentialRepositoryPort
                .findByAppointmentId(appointmentId).stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst();

        String cleanSelfPhone = phone != null ? phone.replaceAll("\\D", "") : null;
        if (cleanSelfPhone != null && cleanSelfPhone.isBlank()) cleanSelfPhone = null;

        AccessCredential patientCred;
        if (existingPatientCredOpt.isPresent()) {
            patientCred = existingPatientCredOpt.get();
            patientCred.setName(name.trim().toUpperCase());
            patientCred.setCpf(cleanCpf);
            if (cleanSelfPhone != null) {
                patientCred.setPhone(cleanSelfPhone);
            }
            if (doctorName != null && !doctorName.isBlank()) {
                patientCred.setDoctorName(doctorName.trim());
            }
            patientCred.setAccessCredential(credentialValue);
            patientCred.setLocator(locatorValue);
            patientCred.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
        } else {
            patientCred = AccessCredential.builder()
                    .appointmentId(appointmentId)
                    .name(name.trim().toUpperCase())
                    .cpf(cleanCpf)
                    .phone(cleanSelfPhone)
                    .doctorName(doctorName != null ? doctorName.trim() : null)
                    .userType(UserType.PATIENT)
                    .accessCredential(credentialValue)
                    .locator(locatorValue)
                    .createdAt(LocalDateTime.now(CLINIC_ZONE))
                    .build();
        }
        accessCredentialRepositoryPort.save(patientCred);

        List<AccessCredential> resultList = new ArrayList<>();
        resultList.add(patientCred);

        // 3. Processa os acompanhantes
        if (companions != null && !companions.isEmpty()) {
            for (CompanionAccessInfo comp : companions) {
                if (comp != null && comp.name() != null && !comp.name().isBlank()) {
                    try {
                        registerCompanionAccess(
                            comp,
                            visitDate,
                            LocalTime.of(6, 0),
                            LocalTime.of(23, 59),
                            credentialValue,
                            locatorValue,
                            appointmentId,
                            null,
                            doctorName
                        );
                    } catch (Exception e) {
                        log.warn("[AccessService] Erro ao cadastrar acompanhante '{}' no auto-cadastro {}: {}", comp.name(), clinic, e.getMessage());
                    }
                }
            }
            List<AccessCredential> updatedList = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            if (updatedList != null && !updatedList.isEmpty()) {
                return updatedList;
            }
        }

        return resultList;
    }

    /**
     * Atualiza o CPF especificamente de um acompanhante e tenta sua liberação física direta na GerAcesso.
     */
    private AccessValidationResult processCompanionCpfUpdate(String appointmentId, String newCpf, UUID credentialId, String targetName) {
        log.info("[AccessService] Atualizando CPF de acompanhante para agendamento {} (credentialId={}, targetName={})", appointmentId, credentialId, targetName);
        if (newCpf == null || newCpf.isBlank()) {
            return new AccessValidationResult(false, null, null, true, "Por favor, informe um CPF válido para o acompanhante.");
        }
        String cleanCpf = newCpf.replaceAll("\\D", "");
        if (!isValidCpf(cleanCpf)) {
            return new AccessValidationResult(false, null, null, false, "CPF inválido perante a Receita Federal. Por favor, confira os números digitados.");
        }

        List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (creds == null || creds.isEmpty()) {
            return new AccessValidationResult(false, null, null, false, "Cadastro não encontrado.");
        }

        Optional<AccessCredential> compOpt = Optional.empty();
        if (credentialId != null) {
            compOpt = creds.stream().filter(c -> c.getId() != null && c.getId().equals(credentialId)).findFirst();
        }
        if (compOpt.isEmpty() && targetName != null && !targetName.isBlank()) {
            compOpt = creds.stream().filter(c -> c.getUserType() == UserType.COMPANION && c.getName() != null && c.getName().equalsIgnoreCase(targetName.trim())).findFirst();
        }
        if (compOpt.isEmpty()) {
            compOpt = creds.stream().filter(c -> c.getUserType() == UserType.COMPANION).findFirst();
        }

        if (compOpt.isEmpty()) {
            return new AccessValidationResult(false, null, null, false, "Acompanhante não encontrado para este agendamento.");
        }

        AccessCredential companion = compOpt.get();
        companion.setCpf(cleanCpf);

        // Resolve data e horários
        LocalDate visitDate = LocalDate.now(CLINIC_ZONE);
        String doctorId = null;

        if (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-")) {
            if (appointmentId.length() >= 13) {
                try {
                    String datePart = appointmentId.substring(5, 13);
                    visitDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                } catch (Exception ignored) {}
            }
        } else {
            try {
                var accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
                if (accessInfoOpt.isPresent()) {
                    FeegowPatientAccessInfo info = accessInfoOpt.get();
                    if (info.appointmentDate() != null) {
                        visitDate = info.appointmentDate();
                    }
                    doctorId = info.doctorId();
                    if (info.doctorName() != null && !info.doctorName().isBlank()) {
                        companion.setDoctorName(info.doctorName().trim());
                    }
                }
            } catch (Exception ex) {
                log.warn("[AccessService] Falha ao consultar Feegow ao atualizar acompanhante: {}", ex.getMessage());
            }
        }

        LocalTime openingTime = LocalTime.of(6, 0);
        LocalTime closingTime = LocalTime.of(23, 59);
        String startVisit = LocalDateTime.of(visitDate, openingTime).format(GERACESSO_DATE_FORMATTER);
        String endVisit = LocalDateTime.of(visitDate, closingTime).format(GERACESSO_DATE_FORMATTER);

        String matricula = "";
        String doctorCpf = "";
        if (doctorId != null && !doctorId.isBlank()) {
            DoctorAccessData docData = resolveDoctorAccessData(doctorId);
            matricula = docData.matricula();
            doctorCpf = docData.cpf();
        }

        GerAcessoRequest gerRequest = GerAcessoRequest.builder()
                .cpf(cleanCpf)
                .status(1)
                .name(companion.getName().toUpperCase())
                .phone(companion.getPhone() != null ? companion.getPhone().replaceAll("\\D", "") : "")
                .email("")
                .visitType(1)
                .startVisit(startVisit)
                .endVisit(endVisit)
                .visitedRegistration(matricula)
                .visitedCpf(doctorCpf)
                .build();

        try {
            Optional<GerAcessoResponse> gerResponseOpt = gerAcessoClientPort.registerAccess(gerRequest);
            if (gerResponseOpt.isPresent()) {
                GerAcessoResponse resp = gerResponseOpt.get();
                if (resp.credential() != null && !resp.credential().isBlank()) {
                    companion.setAccessCredential(resp.credential().trim());
                }
                if (resp.locator() != null && !resp.locator().isBlank()) {
                    companion.setLocator(resp.locator().trim());
                }
                companion.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
                accessCredentialRepositoryPort.save(companion);
                log.info("[AccessService] Acompanhante {} liberado na GerAcesso com QR Code: {}, Locator: {}", 
                        companion.getName(), companion.getAccessCredential(), companion.getLocator());
                String token = generateAccessToken(appointmentId, companion.getPhone());
                String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
                return new AccessValidationResult(true, companion.getName(), companion.getAccessCredential(), false, "Acesso do acompanhante liberado com sucesso!", token, accessUrl);
            } else {
                accessCredentialRepositoryPort.save(companion);
                log.warn("[AccessService] GerAcesso retornou vazio para acompanhante {}. Credencial mantida contingencial.", companion.getName());
                return new AccessValidationResult(false, companion.getName(), companion.getAccessCredential(), false, "Não foi possível liberar o acompanhante na catraca física neste momento. Dirija-se à recepção.");
            }
        } catch (Exception ex) {
            accessCredentialRepositoryPort.save(companion);
            log.error("[AccessService] Erro ao cadastrar acompanhante na GerAcesso: {}", ex.getMessage(), ex);
            return new AccessValidationResult(false, companion.getName(), companion.getAccessCredential(), false, "Erro de comunicação com as catracas. Dirija-se à recepção.");
        }
    }

    /**
     * Atualiza o CPF de um auto-cadastro público e tenta liberação direta na GerAcesso.
     */
    private AccessValidationResult processSelfRegistrationCpfUpdate(String appointmentId, String newCpf) {
        log.info("[AccessService] Atualizando CPF de auto-cadastro para agendamento {}", appointmentId);
        if (newCpf == null || newCpf.isBlank()) {
            return new AccessValidationResult(false, null, null, true, "Por favor, informe um CPF válido.");
        }
        String cleanCpf = newCpf.replaceAll("\\D", "");
        if (!isValidCpf(cleanCpf)) {
            return new AccessValidationResult(false, null, null, false, "CPF inválido perante a Receita Federal. Por favor, confira os números digitados.");
        }

        List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (creds == null || creds.isEmpty()) {
            return new AccessValidationResult(false, null, null, false, "Cadastro não encontrado.");
        }

        Optional<AccessCredential> patientCredOpt = creds.stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst();

        if (patientCredOpt.isEmpty()) {
            return new AccessValidationResult(false, null, null, false, "Paciente titular não encontrado.");
        }

        AccessCredential patient = patientCredOpt.get();
        String oldCpf = patient.getCpf() != null ? patient.getCpf().replaceAll("\\D", "") : "";
        patient.setCpf(cleanCpf);

        // Resolve data da visita a partir do appointmentId (ex: INOV-20260904-...)
        LocalDate visitDate = LocalDate.now(CLINIC_ZONE);
        if (appointmentId.length() >= 13) {
            try {
                String datePart = appointmentId.substring(5, 13);
                visitDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } catch (Exception ignored) {}
        }

        LocalDateTime startWindow = LocalDateTime.of(visitDate, LocalTime.of(6, 0));
        LocalDateTime endWindow = LocalDateTime.of(visitDate, LocalTime.of(23, 59));
        String startDateFormatted = startWindow.format(GERACESSO_DATE_FORMATTER);
        String endDateFormatted = endWindow.format(GERACESSO_DATE_FORMATTER);

        GerAcessoRequest gerAcessoRequest = GerAcessoRequest.builder()
                .name(patient.getName().trim().toUpperCase())
                .cpf(cleanCpf)
                .startVisit(startDateFormatted)
                .endVisit(endDateFormatted)
                .phone(patient.getPhone() != null ? patient.getPhone().replaceAll("\\D", "") : "")
                .visitType(1)
                .visitedRegistration("")
                .visitedCpf("")
                .build();

        try {
            Optional<GerAcessoResponse> gerResponseOpt = gerAcessoClientPort.registerAccess(gerAcessoRequest);
            if (gerResponseOpt.isPresent()) {
                GerAcessoResponse resp = gerResponseOpt.get();
                if (resp.credential() != null && !resp.credential().isBlank()) {
                    patient.setAccessCredential(resp.credential());
                }
                if (resp.locator() != null && !resp.locator().isBlank()) {
                    patient.setLocator(resp.locator());
                }
                log.info("[AccessService] CPF de auto-cadastro atualizado com sucesso no GerAcesso. Novo QR Code: {}", patient.getAccessCredential());
            }
        } catch (Exception ex) {
            log.warn("[AccessService] Falha na integração GerAcesso ao atualizar CPF para {}: {}", appointmentId, ex.getMessage());
            return new AccessValidationResult(false, patient.getName(), patient.getAccessCredential(), false, "Não foi possível liberar a catraca com este CPF: " + ex.getMessage());
        }

        // Sincroniza a correção do CPF também com a ficha do Feegow (se o paciente possuir prontuário no ERP)
        try {
            FeegowPatient feegowP = null;
            if (!oldCpf.isEmpty()) {
                feegowP = patientExternalPort.patientInfo(oldCpf);
            }
            if ((feegowP == null || feegowP.id() == null) && !cleanCpf.isEmpty()) {
                feegowP = patientExternalPort.patientInfo(cleanCpf);
            }
            if (feegowP != null && feegowP.id() != null && !feegowP.id().isBlank()) {
                log.info("[AccessService] Sincronizando correção de CPF com a ficha Feegow ID {} (Paciente: {})", feegowP.id(), patient.getName());
                patientExternalPort.updatePatientCpf(feegowP.id(), cleanCpf, patient.getName(), feegowP.birthdate());
            }
        } catch (Exception ex) {
            log.warn("[AccessService] Não foi possível sincronizar correção de CPF com o Feegow para {}: {}", appointmentId, ex.getMessage());
        }

        accessCredentialRepositoryPort.save(patient);
        String token = generateAccessToken(appointmentId, patient.getPhone());
        String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
        return new AccessValidationResult(true, patient.getName(), patient.getAccessCredential(), false, "Acesso liberado com sucesso!", token, accessUrl);
    }

    /**
     * Consulta credenciais ativas associadas a um CPF em janela de até 7 dias futuros (ignora consultas passadas).
     */
    public List<AccessCredential> lookupCredentialsByCpf(String rawCpf, String clinic) {
        if (rawCpf == null || rawCpf.isBlank()) return List.of();
        String cleanCpf = rawCpf.replaceAll("\\D", "");
        if (cleanCpf.length() != 11) return List.of();

        LocalDate today = LocalDate.now(CLINIC_ZONE);
        LocalDate maxAllowedDate = today.plusDays(7);

        // 1) Auto-cadastro público recente (ex: INOV-20260903-CPF ou IMG-20260903-CPF)
        // Validação estrita em memória pela data embutida no ID (0 chamadas HTTP externas)
        List<AccessCredential> allByCpf = accessCredentialRepositoryPort.findByCpf(cleanCpf);
        if (allByCpf != null && !allByCpf.isEmpty()) {
            List<AccessCredential> validAutoRegistrations = allByCpf.stream()
                .filter(c -> {
                    String apptId = c.getAppointmentId();
                    if (apptId == null || apptId.length() < 13 || (!apptId.startsWith("INOV-") && !apptId.startsWith("IMG-"))) {
                        return false;
                    }
                    try {
                        String datePart = apptId.substring(5, 13);
                        LocalDate appDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                        return !appDate.isBefore(today) && !appDate.isAfter(maxAllowedDate);
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toList();

            if (!validAutoRegistrations.isEmpty()) {
                return expandAndSortCredentials(validAutoRegistrations);
            }
        }

        String todayIdSuffix = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = (clinic != null && clinic.toLowerCase().contains("inovare")) ? "INOV-" : "IMG-";
        String appointmentId = prefix + todayIdSuffix + "-" + cleanCpf;

        List<AccessCredential> credentials = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (credentials != null && !credentials.isEmpty()) {
            return expandAndSortCredentials(credentials);
        }

        // 2) Agendamentos Feegow: busca o paciente e suas consultas futuras em lote (1 única chamada rápida)
        // em vez de varrer agendamentos históricos antigos um a um sequencialmente.
        try {
            FeegowPatient feegowPatient = patientExternalPort.patientInfo(cleanCpf);
            if (feegowPatient != null && feegowPatient.id() != null && !feegowPatient.id().isBlank()) {
                List<FeegowAppointment> patientAppts = appointmentExternalPort.searchPatientAppointments(feegowPatient.id());
                if (patientAppts != null && !patientAppts.isEmpty()) {
                    List<FeegowAppointment> validUpcoming = patientAppts.stream()
                        .filter(a -> a.startAt() != null)
                        .filter(a -> {
                            LocalDate d = a.startAt().toLocalDate();
                            return !d.isBefore(today) && !d.isAfter(maxAllowedDate);
                        })
                        .sorted((a1, a2) -> a1.startAt().compareTo(a2.startAt()))
                        .toList();

                    if (!validUpcoming.isEmpty()) {
                        // Verifica se já existe credencial emitida no banco para qualquer um dos agendamentos futuros
                        for (FeegowAppointment appt : validUpcoming) {
                            String apptIdStr = String.valueOf(appt.id());
                            List<AccessCredential> existing = accessCredentialRepositoryPort.findByAppointmentId(apptIdStr);
                            if (existing != null && !existing.isEmpty()) {
                                return expandAndSortCredentials(existing);
                            }
                        }

                        // Se ainda não gerou credencial para o agendamento mais próximo, emite agora
                        FeegowAppointment closest = validUpcoming.getFirst();
                        String closestIdStr = String.valueOf(closest.id());
                        log.info("[AccessService] Agendamento Feegow {} encontrado para CPF {}. Gerando credencial automaticamente...", closestIdStr, cleanCpf);
                        processAccessRequest(closestIdStr, cleanCpf, null);
                        List<AccessCredential> newlyCreated = accessCredentialRepositoryPort.findByAppointmentId(closestIdStr);
                        if (newlyCreated != null && !newlyCreated.isEmpty()) {
                            return expandAndSortCredentials(newlyCreated);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[AccessService] Falha defensiva ao buscar agendamentos Feegow no lookup por CPF {}: {}", cleanCpf, ex.getMessage());
        }

        return List.of();
    }

    private List<AccessCredential> expandAndSortCredentials(List<AccessCredential> baseList) {
        if (baseList == null || baseList.isEmpty()) {
            return List.of();
        }
        Set<String> validAppointmentIds = new HashSet<>();
        for (AccessCredential cred : baseList) {
            if (cred != null && cred.getAppointmentId() != null && !cred.getAppointmentId().isBlank()) {
                validAppointmentIds.add(cred.getAppointmentId());
            }
        }

        Map<UUID, AccessCredential> uniqueCreds = new LinkedHashMap<>();
        for (String apptId : validAppointmentIds) {
            List<AccessCredential> byAppt = accessCredentialRepositoryPort.findByAppointmentId(apptId);
            if (byAppt != null) {
                for (AccessCredential c : byAppt) {
                    if (c != null && c.getId() != null) {
                        uniqueCreds.put(c.getId(), c);
                    }
                }
            }
        }

        List<AccessCredential> result = new ArrayList<>(uniqueCreds.values());
        result.sort((a, b) -> {
            if (a.getUserType() == UserType.PATIENT && b.getUserType() != UserType.PATIENT) return -1;
            if (a.getUserType() != UserType.PATIENT && b.getUserType() == UserType.PATIENT) return 1;
            return 0;
        });

        return result.isEmpty() ? baseList : result;
    }

    /**
     * Reativa o acesso físico de um paciente ou acompanhante, gerando uma nova credencial no GerAcesso
     * para casos em que o paciente saiu do prédio e precisa retornar no mesmo dia (onde a catraca deu baixa na saída).
     */
    public List<AccessCredential> reactivateAccess(String appointmentId) {
        log.info("[AccessService] Reativando acesso físico para o agendamento ID: {}", appointmentId);

        List<AccessCredential> existingList = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (existingList == null || existingList.isEmpty()) {
            throw new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Nenhuma credencial encontrada para reativação.");
        }

        // Resolve os dados do médico deste agendamento para enviar à GerAcesso (obrigatório para cadastro de visitante)
        String matricula = "";
        String doctorCpf = "";
        if (appointmentId != null && !appointmentId.startsWith("IMG-") && !appointmentId.startsWith("INOV-")) {
            try {
                var accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
                if (accessInfoOpt.isPresent() && accessInfoOpt.get().doctorId() != null) {
                    DoctorAccessData docData = resolveDoctorAccessData(accessInfoOpt.get().doctorId());
                    matricula = docData.matricula();
                    doctorCpf = docData.cpf();
                    log.info("[AccessService] Médico resolvido para reativação: matricula={}, cpf={}", matricula, doctorCpf);
                }
            } catch (Exception ex) {
                log.warn("[AccessService] Não foi possível resolver dados do médico para reativação do agendamento {}: {}", appointmentId, ex.getMessage());
            }
        }

        LocalDate today = LocalDate.now(CLINIC_ZONE);
        LocalDateTime startWindow = LocalDateTime.now(CLINIC_ZONE);
        LocalDateTime endWindow = LocalDateTime.of(today, LocalTime.of(23, 59));
        String startVisit = startWindow.format(GERACESSO_DATE_FORMATTER);
        String endVisit = endWindow.format(GERACESSO_DATE_FORMATTER);

        List<AccessCredential> updatedList = new ArrayList<>();

        for (AccessCredential cred : existingList) {
            String cleanCpf = cred.getCpf() != null ? cred.getCpf().replaceAll("\\D", "") : "";

            if (!isValidCpf(cleanCpf)) {
                log.warn("[AccessService] CPF inválido ({}) cadastrado para '{}'. Não enviando para GerAcesso. Ativando contingência.", cleanCpf, cred.getName());
                String newCredentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                cred.setAccessCredential(newCredentialValue);
                cred.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
                updatedList.add(accessCredentialRepositoryPort.save(cred));
                continue;
            }

            GerAcessoRequest gerAcessoRequest = GerAcessoRequest.builder()
                    .name(cred.getName())
                    .cpf(cleanCpf)
                    .startVisit(startVisit)
                    .endVisit(endVisit)
                    .phone("")
                    .visitType(cred.getUserType() == UserType.PATIENT ? 1 : 2)
                    .visitedRegistration(matricula)
                    .visitedCpf(doctorCpf)
                    .build();

            String newCredentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String newLocator = cred.getLocator();

            try {
                Optional<GerAcessoResponse> responseOpt = gerAcessoClientPort.registerAccess(gerAcessoRequest);
                if (responseOpt.isPresent() && responseOpt.get().credential() != null && !responseOpt.get().credential().isBlank()) {
                    newCredentialValue = responseOpt.get().credential().trim();
                    if (responseOpt.get().locator() != null) {
                        newLocator = responseOpt.get().locator().trim();
                    }
                    log.info("[AccessService] Acesso reativado na GerAcesso para '{}' ({}) com nova credencial: {}", 
                            cred.getName(), cred.getUserType(), newCredentialValue);
                }
            } catch (Exception ex) {
                log.warn("[AccessService] Falha ao reativar no GerAcesso para '{}' (usando contingência): {}", cred.getName(), ex.getMessage());
            }

            cred.setAccessCredential(newCredentialValue);
            cred.setLocator(newLocator);
            cred.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
            updatedList.add(accessCredentialRepositoryPort.save(cred));
        }

        return updatedList;
    }

    /**
     * Consulta prévia no Feegow ao digitar CPF para preenchimento automático
     * de dados cadastrais e agendamentos futuros no auto-cadastro.
     */
    public FeegowPreRegistrationLookupResponse lookupFeegowPreRegistration(String rawCpf, String clinic) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "CPF não informado.");
        }

        String cleanCpf = rawCpf.replaceAll("\\D", "");
        if (cleanCpf.length() != 11) {
            return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "CPF deve ter 11 dígitos.");
        }

        log.info("[AccessService] Consulta prévia no Feegow para auto-cadastro por CPF: {}", cleanCpf);

        try {
            FeegowPatient patient = patientExternalPort.patientInfo(cleanCpf);
            if (patient == null || patient.id() == null || patient.id().isBlank() || patient.name() == null || patient.name().isBlank()) {
                log.info("[AccessService] Paciente não localizado no Feegow para o CPF {}", cleanCpf);
                return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "Paciente não localizado no Feegow.");
            }

            String patientName = patient.name().trim();
            String patientPhone = patient.phone() != null ? patient.phone().trim() : "";
            String patientBirthDate = patient.birthdate() != null ? patient.birthdate().trim() : "";

            // Normaliza data de nascimento para padrão brasileiro (dd/MM/yyyy)
            if (patientBirthDate.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}")) {
                try {
                    String clean = patientBirthDate.replace('/', '-');
                    LocalDate bDate = LocalDate.parse(clean);
                    patientBirthDate = bDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (Exception ignored) {}
            } else if (patientBirthDate.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}")) {
                patientBirthDate = patientBirthDate.replace('-', '/');
            }

            // Normaliza telefone removendo código de país 55 quando presente
            if (patientPhone.startsWith("+55")) {
                patientPhone = patientPhone.substring(3).trim();
            }
            String cleanPhoneDigits = patientPhone.replaceAll("\\D", "");
            if (cleanPhoneDigits.startsWith("55") && (cleanPhoneDigits.length() == 12 || cleanPhoneDigits.length() == 13)) {
                cleanPhoneDigits = cleanPhoneDigits.substring(2);
            }
            if (!cleanPhoneDigits.isEmpty()) {
                patientPhone = cleanPhoneDigits;
            }

            List<FeegowAppointment> feegowAppts = appointmentExternalPort.searchPatientAppointments(patient.id());
            List<FeegowPreRegistrationLookupResponse.FeegowAppointmentItemDto> appointmentDtos = new ArrayList<>();

            if (feegowAppts != null && !feegowAppts.isEmpty()) {
                LocalDate today = LocalDate.now(CLINIC_ZONE);
                LocalDate tomorrow = today.plusDays(1);

                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
                DateTimeFormatter dateIsoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter dateBrFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

                for (FeegowAppointment appt : feegowAppts) {
                    if (appt.startAt() == null) continue;

                    LocalDate apptDate = appt.startAt().toLocalDate();
                    // Limita a exibição a no máximo 7 dias no futuro a partir de hoje (ignora consultas passadas ou além de 7 dias)
                    if (apptDate.isBefore(today) || apptDate.isAfter(today.plusDays(7))) {
                        continue;
                    }

                    boolean isToday = apptDate.equals(today);
                    boolean isTomorrow = apptDate.equals(tomorrow);

                    String formattedDateLabel;
                    if (isToday) {
                        formattedDateLabel = "Hoje às " + appt.startAt().format(timeFmt);
                    } else if (isTomorrow) {
                        formattedDateLabel = "Amanhã às " + appt.startAt().format(timeFmt);
                    } else {
                        formattedDateLabel = appt.startAt().format(dateBrFmt) + " às " + appt.startAt().format(timeFmt);
                    }

                    String docName = appt.doctorName() != null && !appt.doctorName().isBlank() 
                            ? appt.doctorName().trim() 
                            : "";
                    String specialty = appt.procedureName() != null && !appt.procedureName().isBlank() 
                            ? appt.procedureName().trim() 
                            : "";

                    appointmentDtos.add(new FeegowPreRegistrationLookupResponse.FeegowAppointmentItemDto(
                        appt.id(),
                        docName,
                        specialty,
                        apptDate.format(dateIsoFmt),
                        appt.startAt().format(timeFmt),
                        formattedDateLabel,
                        isToday,
                        null
                    ));
                }

                appointmentDtos.sort((a, b) -> {
                    int c = a.date().compareTo(b.date());
                    return c != 0 ? c : a.time().compareTo(b.time());
                });
            }

            return new FeegowPreRegistrationLookupResponse(
                true,
                patientName,
                patientBirthDate,
                patientPhone,
                appointmentDtos,
                appointmentDtos.isEmpty() ? "Cadastro localizado no Feegow." : "Consultas localizadas no Feegow."
            );
        } catch (Exception ex) {
            log.error("[AccessService] Falha ao consultar pré-cadastro no Feegow para o CPF {}: {}", cleanCpf, ex.getMessage(), ex);
            return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "Falha temporária ao consultar Feegow.");
        }
    }

    /**
     * Classe de transporte de dados de validação de acesso.
     */
    public record AccessValidationResult(
        boolean authorized,
        String patientName,
        String accessCredential,
        boolean requiresCpfFallback,
        String message,
        String token,
        String accessUrl
    ) {
        public AccessValidationResult(boolean authorized, String patientName, String accessCredential, boolean requiresCpfFallback, String message) {
            this(authorized, patientName, accessCredential, requiresCpfFallback, message, null, null);
        }
    }
}
