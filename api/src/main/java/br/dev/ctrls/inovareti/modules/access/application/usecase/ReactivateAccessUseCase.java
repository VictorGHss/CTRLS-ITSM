package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.access.domain.service.DoctorAccessResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de Uso: Reativação de Acesso Físico às Catracas na GerAcesso API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactivateAccessUseCase {

    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final FeegowClientPort feegowClientPort;
    private final GerAcessoClientPort gerAcessoClientPort;
    private final DoctorAccessResolver doctorAccessResolver;

    public List<AccessCredential> reactivateAccess(String appointmentId) {
        log.info("[ReactivateAccess] Reativando acesso físico para o agendamento ID/CPF: {}", appointmentId);

        List<AccessCredential> existingList = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (existingList == null || existingList.isEmpty()) {
            String cleanDigits = appointmentId != null ? appointmentId.replaceAll("\\D", "") : "";
            if (!cleanDigits.isBlank()) {
                String cleanCpf = cleanDigits.length() == 11 ? cleanDigits : (cleanDigits.length() > 11 ? cleanDigits.substring(cleanDigits.length() - 11) : cleanDigits);
                if (cleanCpf.length() == 11) {
                    List<AccessCredential> byCpf = accessCredentialRepositoryPort.findByCpf(cleanCpf);
                    if (byCpf != null && !byCpf.isEmpty()) {
                        LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
                        String todayPattern = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                        existingList = byCpf.stream()
                            .filter(c -> (c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(today))
                                      || (c.getAppointmentId() != null && c.getAppointmentId().contains(todayPattern)))
                            .toList();
                        if (existingList.isEmpty()) {
                            String latestAppId = byCpf.stream()
                                .max((a, b) -> {
                                    LocalDateTime tA = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                                    LocalDateTime tB = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                                    return tA.compareTo(tB);
                                })
                                .map(c -> c.getAppointmentId())
                                .orElse(null);
                            if (latestAppId != null) {
                                existingList = byCpf.stream()
                                    .filter(c -> latestAppId.equals(c.getAppointmentId()))
                                    .toList();
                            }
                        }
                        if (!existingList.isEmpty()) {
                            log.info("[ReactivateAccess] Credencial localizada via fallback de CPF ({}). AppointmentId real: {}", 
                                    cleanCpf, existingList.getFirst().getAppointmentId());
                        }
                    }
                }
            }
        }

        if (existingList == null || existingList.isEmpty()) {
            throw new NotFoundException("Nenhuma credencial encontrada para reativação.");
        }

        String matricula = "";
        String doctorCpf = "";
        String effectiveAppId = existingList.getFirst().getAppointmentId();

        if (effectiveAppId != null && !effectiveAppId.startsWith("IMG-") && !effectiveAppId.startsWith("INOV-")) {
            try {
                var accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(effectiveAppId);
                if (accessInfoOpt.isPresent() && accessInfoOpt.get().doctorId() != null) {
                    DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessData(accessInfoOpt.get().doctorId());
                    matricula = docData.matricula();
                    doctorCpf = docData.cpf();
                    log.info("[ReactivateAccess] Médico resolvido para reativação: matricula={}, cpf={}", matricula, doctorCpf);
                }
            } catch (Exception ex) {
                log.warn("[ReactivateAccess] Não foi possível resolver dados do médico para reativação do agendamento {}: {}", effectiveAppId, ex.getMessage());
            }
        }

        if (matricula.isBlank() && doctorCpf.isBlank()) {
            String doctorName = "";
            for (AccessCredential c : existingList) {
                if (c != null && c.getDoctorName() != null && !c.getDoctorName().isBlank()) {
                    doctorName = c.getDoctorName().trim();
                    break;
                }
            }
            if (!doctorName.isBlank()) {
                DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessDataByName(doctorName);
                matricula = docData.matricula();
                doctorCpf = docData.cpf();
                log.info("[ReactivateAccess] Médico resolvido por NOME para reativação ('{}'): matricula={}, cpf={}", 
                        doctorName, matricula, doctorCpf);
            }
        }

        LocalDateTime now = LocalDateTime.now(AccessWindowCalculator.CLINIC_ZONE);
        LocalDate today = now.toLocalDate();
        // Início a partir das 06:00 da manhã do dia atual para evitar rejeição por relógio dessincronizado da catraca física
        LocalDateTime startWindow = LocalDateTime.of(today, LocalTime.of(6, 0));
        LocalDateTime endWindow = LocalDateTime.of(today, LocalTime.of(23, 59));
        String startVisit = startWindow.format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);
        String endVisit = endWindow.format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);
        log.info("[ReactivateAccess] Renovando visita na GerAcesso com janela do dia: {} até {}", startVisit, endVisit);

        List<AccessCredential> updatedList = new ArrayList<>();

        for (AccessCredential cred : existingList) {
            String cleanCpf = CpfValidator.cleanCpf(cred.getCpf());

            if (!CpfValidator.isValidCpf(cleanCpf)) {
                log.warn("[ReactivateAccess] CPF inválido ({}) para '{}'. Ativando contingência.", cleanCpf, cred.getName());
                String newCredentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                cred.setAccessCredential(newCredentialValue);
                cred.setCreatedAt(now);
                updatedList.add(accessCredentialRepositoryPort.save(cred));
                continue;
            }

            GerAcessoRequest gerAcessoRequest = GerAcessoRequest.builder()
                    .name(cred.getName())
                    .cpf(cleanCpf)
                    .status(1)
                    .startVisit(startVisit)
                    .endVisit(endVisit)
                    .phone(cred.getPhone() != null ? cred.getPhone().replaceAll("\\D", "") : "")
                    .visitType(1)
                    .visitedRegistration(matricula)
                    .visitedCpf(doctorCpf)
                    .build();

            String newCredentialValue = cred.getAccessCredential();
            String newLocator = cred.getLocator();

            try {
                Optional<GerAcessoResponse> responseOpt = gerAcessoClientPort.registerAccess(gerAcessoRequest);
                if (responseOpt.isPresent() 
                        && responseOpt.get().credential() != null 
                        && !responseOpt.get().credential().isBlank()
                        && !"null".equalsIgnoreCase(responseOpt.get().credential().trim())) {
                    newCredentialValue = responseOpt.get().credential().trim();
                    if (responseOpt.get().locator() != null) {
                        newLocator = responseOpt.get().locator().trim();
                    }
                    log.info("[ReactivateAccess] Acesso reativado na GerAcesso para '{}' ({}) com nova credencial: {}", 
                            cred.getName(), cred.getUserType(), newCredentialValue);
                } else {
                    log.warn("[ReactivateAccess] GerAcesso não retornou credencial válida para '{}'. Mantendo credencial atual: {}", 
                            cred.getName(), newCredentialValue);
                    if (newCredentialValue == null || newCredentialValue.isBlank()) {
                        newCredentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    }
                }
            } catch (Exception ex) {
                log.warn("[ReactivateAccess] Falha ao reativar no GerAcesso para '{}': {}", cred.getName(), ex.getMessage());
                if (newCredentialValue == null || newCredentialValue.isBlank()) {
                    newCredentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }
            }

            cred.setAccessCredential(newCredentialValue);
            cred.setLocator(newLocator);
            cred.setCreatedAt(now);
            updatedList.add(accessCredentialRepositoryPort.save(cred));
        }

        return updatedList;
    }
}
