package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.gestaods.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para dados do paciente retornados pela API do Gestão DS.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GestaoDsPacienteDto(
        @JsonProperty("id") @JsonAlias({"id", "paciente_id", "codigo", "pk"}) Object id,
        @JsonProperty("nome_completo") @JsonAlias({"nome", "nome_completo", "nomeCompleto", "paciente_nome"}) String nomeCompleto,
        @JsonProperty("cpf") @JsonAlias({"cpf", "CPF", "paciente_cpf"}) String cpf,
        @JsonProperty("email") @JsonAlias({"email", "e_mail"}) String email,
        @JsonProperty("celular") @JsonAlias({"celular", "telefone_celular", "whatsapp", "phone"}) String celular,
        @JsonProperty("telefone") @JsonAlias({"telefone", "telefone_fixo"}) String telefone,
        @JsonProperty("nascimento") @JsonAlias({"nascimento", "data_nascimento", "dataNascimento", "birthdate"}) String nascimento,
        @JsonProperty("rg") @JsonAlias({"rg", "RG"}) String rg,
        @JsonProperty("sexo") @JsonAlias({"sexo", "genero"}) String sexo,
        @JsonProperty("estado_civil") @JsonAlias({"estado_civil", "estadoCivil"}) String estadoCivil,
        @JsonProperty("cep") String cep,
        @JsonProperty("endereco") @JsonAlias({"endereco", "logradouro"}) String endereco,
        @JsonProperty("numero") String numero,
        @JsonProperty("complemento") String complemento,
        @JsonProperty("bairro") String bairro,
        @JsonProperty("cidade") String cidade,
        @JsonProperty("uf") String uf,
        @JsonProperty("responsavel") String responsavel,
        @JsonProperty("telefone_responsavel") String telefoneResponsavel
) {
    /**
     * Retorna o primeiro nome do paciente.
     */
    public String primeiroNome() {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return "";
        }
        return nomeCompleto.trim().split("\\s+")[0];
    }

    /**
     * Retorna o CPF higienizado (apenas números).
     */
    public String cpfLimpo() {
        if (cpf == null) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }
}
