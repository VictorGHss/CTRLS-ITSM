package br.dev.ctrls.itsm.modules.appointment.domain.port.output;

import java.util.List;
import java.util.Optional;

import br.dev.ctrls.itsm.modules.appointment.domain.model.GestaoDsAppointmentItem;
import br.dev.ctrls.itsm.modules.appointment.domain.model.GestaoDsPatient;

/**
 * Porta de Saída do Domínio: GestaoDsClientPort.
 * Interface pura que abstrai as operações de consulta e atualização junto ao ERP Gestão DS.
 * Isolada da camada de infraestrutura.
 */
public interface GestaoDsClientPort {

    /**
     * Busca os dados cadastrais do paciente por CPF no Gestão DS.
     *
     * @param cpf CPF do paciente
     * @return Optional com o paciente localizado
     */
    Optional<GestaoDsPatient> fetchPatient(String cpf);

    /**
     * Busca todos os agendamentos vinculados ao CPF no Gestão DS.
     *
     * @param cpf CPF do paciente
     * @return Lista de agendamentos
     */
    List<GestaoDsAppointmentItem> fetchPatientAppointments(String cpf);

    /**
     * Atualiza o status de confirmação ou cancelamento do agendamento no Gestão DS.
     *
     * @param appointmentId Identificador do agendamento
     * @param confirmed Se confirmado
     * @param canceled Se cancelado
     * @param reason Motivo do cancelamento (opcional)
     * @return true se atualizado com sucesso
     */
    boolean updateAppointmentStatus(String appointmentId, Boolean confirmed, Boolean canceled, String reason);
}
