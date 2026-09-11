package br.dev.ctrls.itsm.modules.appointment.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeegowPatientDocSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deveria extrair CPF e telefones com precisão a partir do JSON oficial da documentação do Feegow /patient/search")
    void shouldExtractCpfFromOfficialDocumentationSearchPayload() throws Exception {
        String json = """
        {
            "success": true,
            "content": {
                "nome": "JOSE RENATO BARONI",
                "nascimento": "18-07-1998",
                "sexo": "Masculino",
                "endereco": "AV THIAGO CASTRO",
                "numero": "05",
                "complemento": "CASA",
                "bairro": "CENTRO",
                "cidade": "NATAL",
                "estado": "RJ",
                "cep": "59022020",
                "profissao": "Funcionário Público",
                "foto": "https://clinic7.feegow.com.br//uploads/105/Perfil/2a0f36efcad971ada76383844d963285.jpg",
                "telefones": [
                    "2907-1177",
                    null
                ],
                "celulares": [
                    "99762-1587",
                    null
                ],
                "documentos": {
                    "rg": "11111111111111",
                    "cpf": "11111111"
                },
                "email": [
                    "josebaroni@feegow.com.br",
                    null
                ]
            }
        }
        """;

        FeegowPatientDetailsDto root = objectMapper.readValue(json, FeegowPatientDetailsDto.class);
        assertThat(root.getContent()).isNotNull();

        FeegowPatientDetailsDto.PatientItem patient = objectMapper.convertValue(
                root.getContent(), FeegowPatientDetailsDto.PatientItem.class);

        assertThat(patient.getNome()).isEqualTo("JOSE RENATO BARONI");
        assertThat(patient.getNascimento()).isEqualTo("18-07-1998");
        assertThat(patient.getCpf()).isEqualTo("11111111");
        assertThat(patient.getCelulares()).contains("99762-1587");
    }

    @Test
    @DisplayName("Deveria extrair dados a partir do JSON oficial da documentação do Feegow /patient/list")
    void shouldExtractDataFromOfficialDocumentationListPayload() throws Exception {
        String json = """
        {
            "success": true,
            "content": [
                {
                    "patient_id": 999889071,
                    "nome": "JOSE Re. BARONI",
                    "nome_social": "Zé BARONI",
                    "nascimento": "1994-01-30",
                    "bairro": "BAIRRO SEM NOME",
                    "tabela_id": 2,
                    "sexo_id": 1,
                    "email": "josebaroni@novoemail.com",
                    "celular": "(21) 95555-4321",
                    "criado_em": "2023-03-10 14:04:00",
                    "alterado_em": "2023-03-10 18:30:05"
                }
            ],
            "total": 1
        }
        """;

        FeegowPatientDetailsDto root = objectMapper.readValue(json, FeegowPatientDetailsDto.class);
        assertThat(root.getContent()).isNotNull();

        java.util.List<?> list = (java.util.List<?>) root.getContent();
        FeegowPatientDetailsDto.PatientItem patient = objectMapper.convertValue(
                list.getFirst(), FeegowPatientDetailsDto.PatientItem.class);

        assertThat(patient.getId()).isEqualTo("999889071");
        assertThat(patient.getNome()).isEqualTo("JOSE Re. BARONI");
        assertThat(patient.getCelulares()).contains("(21) 95555-4321");
    }
}
