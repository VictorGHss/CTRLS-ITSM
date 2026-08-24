package br.dev.ctrls.inovareti.modules.appointment.application.service;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de observabilidade e métricas Prometheus para o motor de agendamentos.
 * Permite monitoramento executivo em tempo real no Grafana.
 */
@Service
@RequiredArgsConstructor
public class AppointmentMetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * Incrementa o contador de mensagens disparadas com sucesso.
     */
    public void incrementDispatched(String doctorName, String specialtyName) {
        String doc = doctorName != null ? doctorName : "Desconhecido";
        String spec = specialtyName != null ? specialtyName : "Geral";
        Counter.builder("inovareti_appointment_dispatches_total")
                .description("Total de mensagens ativas de confirmação disparadas via WhatsApp")
                .tag("doctor", doc)
                .tag("specialty", spec)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Incrementa o contador de confirmações de consulta realizadas.
     */
    public void incrementConfirmed(String doctorName, String specialtyName, String source) {
        String doc = doctorName != null ? doctorName : "Desconhecido";
        String spec = specialtyName != null ? specialtyName : "Geral";
        String src = source != null ? source : "bot";
        Counter.builder("inovareti_appointment_confirmations_total")
                .description("Total de consultas confirmadas pelos pacientes via WhatsApp ou Desk")
                .tag("doctor", doc)
                .tag("specialty", spec)
                .tag("source", src)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Incrementa o contador de cancelamentos ou pedidos de alteração.
     */
    public void incrementCanceled(String doctorName, String specialtyName, String source) {
        String doc = doctorName != null ? doctorName : "Desconhecido";
        String spec = specialtyName != null ? specialtyName : "Geral";
        String src = source != null ? source : "bot";
        Counter.builder("inovareti_appointment_cancellations_total")
                .description("Total de consultas canceladas ou com solicitação de remarcação")
                .tag("doctor", doc)
                .tag("specialty", spec)
                .tag("source", src)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Incrementa o contador de agendamentos ignorados por falta de telefone no cadastro Feegow.
     */
    public void incrementPhoneMissing(String doctorName) {
        String doc = doctorName != null ? doctorName : "Desconhecido";
        Counter.builder("inovareti_appointment_phone_missing_total")
                .description("Total de agendamentos sem telefone cadastrado na Feegow")
                .tag("doctor", doc)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Registra o tempo de execução da rotina de ingestão matinal.
     */
    public void recordIngestionDuration(long durationMs) {
        Timer.builder("inovareti_appointment_ingestion_duration_seconds")
                .description("Tempo de execução da rotina de ingestão e processamento matinal")
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }
}
