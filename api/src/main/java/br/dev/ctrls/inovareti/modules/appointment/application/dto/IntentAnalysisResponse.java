package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO de resposta contendo o resultado do processamento da intenção do usuário.
 * Suporta respostas de tipo "RESULTADO_UNICO", "MULTIPLOS_RESULTADOS", "TRIGGER_ITSM", "NENHUM_RESULTADO"
 * e propriedades de decisão de roteamento (routeType, acaoSeguinte, selectedQueue).
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentAnalysisResponse {

    @JsonProperty("tipo")
    private String tipo;

    @JsonProperty("termoBuscado")
    private String termoBuscado;

    @JsonProperty("medico")
    private String medico;

    @JsonProperty("especialidade")
    private String especialidade;

    @JsonProperty("fila")
    private String fila;

    @JsonProperty("rota")
    private String rota;

    @JsonProperty("isInternal")
    private String isInternal;

    @JsonProperty("linkWa")
    private String linkWa;

    @JsonProperty("acao")
    private String acao;

    @JsonProperty("routeType")
    private String routeType;

    @JsonProperty("acaoSeguinte")
    private String acaoSeguinte;

    @JsonProperty("selectedQueue")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String selectedQueue;

    @JsonProperty("doctorName")
    public String getDoctorName() {
        return medico;
    }

    @JsonProperty("queue")
    public String getQueue() {
        return fila;
    }

    @JsonProperty("queueName")
    public String getQueueName() {
        return fila;
    }

    @JsonProperty("externalLink")
    public String getExternalLink() {
        return linkWa;
    }

    @JsonProperty("opcoes")
    private List<DoctorOption> opcoes;

    @JsonProperty("opcoesFormatadas")
    private String opcoesFormatadas;

    /**
     * Opção individual de candidato utilizada para desambiguação em "MULTIPLOS_RESULTADOS".
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DoctorOption {

        @JsonProperty("medico")
        private String medico;

        @JsonProperty("especialidade")
        private String especialidade;

        @JsonProperty("fila")
        private String fila;

        @JsonProperty("rota")
        private String rota;

        @JsonProperty("isInternal")
        private String isInternal;

        @JsonProperty("linkWa")
        private String linkWa;

        @JsonProperty("doctorName")
        public String getDoctorName() {
            return medico;
        }

        @JsonProperty("queue")
        public String getQueue() {
            return fila;
        }

        @JsonProperty("queueName")
        public String getQueueName() {
            return fila;
        }

        @JsonProperty("externalLink")
        public String getExternalLink() {
            return linkWa;
        }
    }
}
