package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.GestaoDsAppointmentItem;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.GestaoDsPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.GestaoDsClientPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.client.GestaoDsRestClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsAgendamentoItemDto;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsPacienteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de infraestrutura que implementa {@link GestaoDsClientPort}
 * delegando para {@link GestaoDsRestClient} e convertendo DTOs em modelos de domínio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GestaoDsAppointmentAdapter implements GestaoDsClientPort {

    private final GestaoDsRestClient gestaoDsRestClient;

    @Override
    public Optional<GestaoDsPatient> fetchPatient(String cpf) {
        try {
            return gestaoDsRestClient.findPatientByCpf(cpf).map(this::toDomainPatient);
        } catch (Exception ex) {
            log.error("[GestaoDsAdapter] Erro ao buscar paciente por CPF {}: {}", cpf, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<GestaoDsAppointmentItem> fetchPatientAppointments(String cpf) {
        try {
            return gestaoDsRestClient.findPatientAppointments(cpf).stream()
                    .map(this::toDomainAppointment)
                    .toList();
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

    private GestaoDsPatient toDomainPatient(GestaoDsPacienteDto dto) {
        return new GestaoDsPatient(
            dto.id(),
            dto.nomeCompleto(),
            dto.cpf(),
            dto.email(),
            dto.celular(),
            dto.telefone(),
            dto.nascimento(),
            dto.rg(),
            dto.sexo(),
            dto.estadoCivil(),
            dto.cep(),
            dto.endereco(),
            dto.numero(),
            dto.complemento(),
            dto.bairro(),
            dto.cidade(),
            dto.uf(),
            dto.responsavel(),
            dto.telefoneResponsavel()
        );
    }

    private GestaoDsAppointmentItem toDomainAppointment(GestaoDsAgendamentoItemDto dto) {
        return new GestaoDsAppointmentItem(
            dto.agendamento(),
            dto.dataAgendamento(),
            dto.dataFimAgendamento(),
            dto.confirmado(),
            dto.cancelado(),
            dto.motivoCancelamento(),
            dto.profissionalNome(),
            dto.profissionalId(),
            dto.procedimentoNome(),
            dto.pacienteNome(),
            dto.pacienteCpf(),
            dto.unidade(),
            dto.primeiroAtendimento()
        );
    }
}
