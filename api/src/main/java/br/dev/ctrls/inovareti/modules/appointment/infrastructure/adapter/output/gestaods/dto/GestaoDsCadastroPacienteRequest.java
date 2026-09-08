package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de requisição para cadastro de novo paciente no Gestão DS.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GestaoDsCadastroPacienteRequest(
        @NotBlank @JsonProperty("nome_completo") String nomeCompleto,
        @NotBlank @JsonProperty("cpf") String cpf,
        @NotBlank @JsonProperty("email") String email,
        @NotBlank @JsonProperty("token") String token,
        @JsonProperty("celular") String celular,
        @JsonProperty("nascimento") String nascimento,
        @JsonProperty("rg") String rg,
        @JsonProperty("sexo") String sexo,
        @JsonProperty("telefone") String telefone,
        @JsonProperty("cep") String cep,
        @JsonProperty("endereco") String endereco,
        @JsonProperty("numero") String numero,
        @JsonProperty("complemento") String complemento,
        @JsonProperty("bairro") String bairro,
        @JsonProperty("cidade") String cidade,
        @JsonProperty("uf") String uf,
        @JsonProperty("enviar_whatsapp_lembrete") Boolean enviarWhatsappLembrete,
        @JsonProperty("enviar_email_lembrete") Boolean enviarEmailLembrete
) {}
