-- =============================================================================
-- Migration V51: Normalização de Médicos (3FN) e Unificação de Configurações
-- =============================================================================

-- 1. Adicionar colunas de integração financeira/Conta Azul em doctor_configurations
ALTER TABLE doctor_configurations
    ADD COLUMN IF NOT EXISTS contaazul_customer_uuid VARCHAR(64),
    ADD COLUMN IF NOT EXISTS doctor_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS doctor_cpf_cnpj VARCHAR(20);

-- 2. Criar índice único para buscas rápidas pelo UUID do Conta Azul
CREATE UNIQUE INDEX IF NOT EXISTS uq_doctor_configurations_contaazul_uuid
    ON doctor_configurations(contaazul_customer_uuid)
    WHERE contaazul_customer_uuid IS NOT NULL;

-- 3. Migrar dados existentes de doctor_email_mapping para doctor_configurations
-- vinculando pelo nome do médico ou CPF se coincidir
UPDATE doctor_configurations dc
SET
    contaazul_customer_uuid = dem.contaazul_customer_uuid,
    doctor_email = dem.doctor_email,
    doctor_cpf_cnpj = dem.doctor_cpf_cnpj
FROM doctor_email_mapping dem
WHERE UPPER(TRIM(dc.doctor_name)) = UPPER(TRIM(dem.doctor_name))
  AND dc.contaazul_customer_uuid IS NULL;
