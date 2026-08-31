package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta Feegow de detalhes de paciente. {@code content} é JSON dinâmico (objeto ou lista).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeegowPatientDetailsDto {

    @JsonProperty("content")
    private Object content;

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PatientItem {

        @JsonProperty("id")
        @JsonAlias({"id", "paciente_id", "patient_id", "id_paciente"})
        private String id;

        @JsonProperty("nome")
        @JsonAlias({"Nome", "nome_completo", "name"})
        private String nome;

        @JsonProperty("nome_social")
        @JsonAlias({"NomeSocial", "nomeSocial"})
        private String nomeSocial;

        @JsonProperty("celulares")
        @JsonAlias({"Celulares", "celular", "Celular", "mobile", "phones"})
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> celulares;

        @JsonProperty("telefones")
        @JsonAlias({"Telefones", "telefone", "Telefone", "phone"})
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> telefones;

        @JsonProperty("cpf")
        @JsonAlias({"CPF", "Cpf", "cpf_cnpj", "num_cpf", "numero_cpf", "paciente_cpf", "doc_cpf"})
        private String cpf;

        @JsonProperty("nascimento")
        @JsonAlias({"Nascimento", "birthdate", "data_nascimento"})
        private String nascimento;

        @JsonProperty("documentos")
        @JsonAlias({"Documentos", "documents", "docs", "documento"})
        private Object documentos;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public List<String> getCelulares() {
            return celulares;
        }

        public void setCelulares(List<String> celulares) {
            this.celulares = celulares;
        }

        public List<String> getTelefones() {
            return telefones;
        }

        public void setTelefones(List<String> telefones) {
            this.telefones = telefones;
        }

        public String getCpf() {
            String directCpf = null;
            if (cpf != null && !cpf.isBlank()) {
                String clean = cpf.replaceAll("\\D", "");
                if (clean.length() == 11) {
                    return clean;
                }
                directCpf = cpf;
            }
            if (documentos != null) {
                if (documentos instanceof java.util.Map<?, ?> map) {
                    for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                        String key = String.valueOf(entry.getKey()).toLowerCase();
                        if (key.contains("cpf")) {
                            Object val = entry.getValue();
                            if (val instanceof java.util.Map<?, ?> subMap) {
                                Object num = subMap.get("numero") != null ? subMap.get("numero") : subMap.get("val");
                                if (num != null) {
                                    String clean = String.valueOf(num).replaceAll("\\D", "");
                                    if (clean.length() == 11) return clean;
                                }
                            }
                            if (val != null) {
                                String clean = String.valueOf(val).replaceAll("\\D", "");
                                if (clean.length() == 11) return clean;
                            }
                        }
                    }
                } else if (documentos instanceof java.util.List<?> list) {
                    for (Object item : list) {
                        if (item instanceof java.util.Map<?, ?> map) {
                            String tipo = String.valueOf(map.get("tipo") != null ? map.get("tipo") : map.get("type"));
                            String tipoDoc = String.valueOf(map.get("tipo_documento") != null ? map.get("tipo_documento") : "");
                            if (tipo.equalsIgnoreCase("cpf") || tipo.equalsIgnoreCase("1") || tipoDoc.equalsIgnoreCase("1") || tipoDoc.equalsIgnoreCase("cpf")) {
                                Object num = map.get("numero") != null ? map.get("numero") : (map.get("num") != null ? map.get("num") : map.get("documento"));
                                if (num != null) {
                                    String clean = String.valueOf(num).replaceAll("\\D", "");
                                    if (clean.length() == 11) return clean;
                                }
                            }
                        }
                    }
                } else if (documentos instanceof String str && !str.isBlank()) {
                    String clean = str.replaceAll("\\D", "");
                    if (clean.length() == 11) {
                        return clean;
                    }
                }
            }
            return directCpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }

        public String getNascimento() {
            return nascimento;
        }

        public void setNascimento(String nascimento) {
            this.nascimento = nascimento;
        }

        public Object getDocumentos() {
            return documentos;
        }

        public void setDocumentos(Object documentos) {
            this.documentos = documentos;
        }
    }
}
