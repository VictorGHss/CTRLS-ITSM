package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.client.GestaoDsRestClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsAgendamentoItemDto;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsPacienteDto;

class GestaoDsAppointmentAdapterTest {

    private GestaoDsRestClient restClient;
    private GestaoDsAppointmentAdapter adapter;

    @BeforeEach
    void setUp() {
        restClient = mock(GestaoDsRestClient.class);
        adapter = new GestaoDsAppointmentAdapter(restClient);
    }

    @Test
    @DisplayName("Deve delegar busca de paciente para o restClient")
    void shouldDelegateFetchPatient() {
        String cpf = "12345678900";
        GestaoDsPacienteDto dto = new GestaoDsPacienteDto(
                1L, "João da Silva", cpf, "joao@email.com", "42999991111", null, "10/10/1990",
                null, "M", null, null, null, null, null, null, null, null, null, null
        );

        when(restClient.findPatientByCpf(cpf)).thenReturn(Optional.of(dto));

        Optional<GestaoDsPacienteDto> result = adapter.fetchPatient(cpf);

        assertThat(result).isPresent();
        assertThat(result.get().nomeCompleto()).isEqualTo("João da Silva");
        verify(restClient).findPatientByCpf(cpf);
    }

    @Test
    @DisplayName("Deve delegar busca de agendamentos para o restClient")
    void shouldDelegateFetchAppointments() {
        String cpf = "12345678900";
        GestaoDsAgendamentoItemDto item = new GestaoDsAgendamentoItemDto(
                "APP-1", "2026-09-08 10:00:00", null, true, false, null, "Dra. Maria", null, "Dermatologia", "João", cpf, null, false
        );

        when(restClient.findPatientAppointments(cpf)).thenReturn(List.of(item));

        List<GestaoDsAgendamentoItemDto> result = adapter.fetchPatientAppointments(cpf);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).agendamento()).isEqualTo("APP-1");
        verify(restClient).findPatientAppointments(cpf);
    }

    @Test
    @DisplayName("Deve delegar atualização de status para o restClient")
    void shouldDelegateUpdateStatus() {
        when(restClient.updateAppointmentStatus("APP-1", true, false, null)).thenReturn(true);

        boolean updated = adapter.updateAppointmentStatus("APP-1", true, false, null);

        assertThat(updated).isTrue();
        verify(restClient).updateAppointmentStatus("APP-1", true, false, null);
    }
}
