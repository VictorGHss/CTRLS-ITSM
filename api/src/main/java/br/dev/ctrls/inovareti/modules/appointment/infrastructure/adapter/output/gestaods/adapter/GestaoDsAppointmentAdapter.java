package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.GestaoDsClientPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.client.GestaoDsRestClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsAgendamentoItemDto;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsPacienteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de infraestrutura que implementa {@link GestaoDsClientPort}
 * delegando para {@link GestaoDsRestClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GestaoDsAppointmentAdapter implements GestaoDsClientPort {

    private final GestaoDsRestClient gestaoDsRestClient;

    @Override
    public Optional<GestaoDsPacienteDto> fetchPatient(String cpf) {
        try {
            return gestaoDsRestClient.findPatientByCpf(cpf);
        } catch (Exception ex) {
            log.error("[GestaoDsAdapter] Erro ao buscar paciente por CPF {}: {}", cpf, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<GestaoDsAgendamentoItemDto> fetchPatientAppointments(String cpf) {
        try {
            return gestaoDsRestClient.findPatientAppointments(cpf);
        } catch (Exception ex) {
            log.error("[GestaoDsAdapter] Erro ao buscar agendamentos por CPF {}: {}", cpf, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean updateAppointmentStatus(String appointmentId, Boolean confirmed, Boolean canceled, String reason) {
        try {
            return gestaoDsRestClient.updateAppointmentStatus(appointmentId, confirmed, canceled, reason);
        } catch (Exception ex) {
            log.error("[GestaoDsAdapter] Erro ao atualizar status do agendamento {}: {}", appointmentId, ex.getMessage());
            return false;
        }
    }
}
