package br.dev.ctrls.itsm.modules.appointment.domain.model;

/**
 * Modelo de Domínio: Paciente do ERP Gestão DS.
 * Isolado de anotações e dependências de infraestrutura/JSON.
 */
public record GestaoDsPatient(
    Object id,
    String fullName,
    String cpf,
    String email,
    String cellPhone,
    String phone,
    String birthdate,
    String rg,
    String gender,
    String maritalStatus,
    String zipCode,
    String address,
    String number,
    String complement,
    String neighborhood,
    String city,
    String state,
    String guardian,
    String guardianPhone
) {
    public String firstName() {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return fullName.trim().split("\\s+")[0];
    }

    public String cleanCpf() {
        if (cpf == null) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }
}
