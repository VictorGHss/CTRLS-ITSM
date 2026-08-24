-- V50__add_discord_channel_id_to_doctor_configurations.sql
-- Adiciona suporte a canal do Discord dedicado por médico para relatórios matinais e avisos de secretárias

ALTER TABLE doctor_configurations
ADD COLUMN IF NOT EXISTS discord_channel_id VARCHAR(50);

COMMENT ON COLUMN doctor_configurations.discord_channel_id IS 'ID do canal no Discord exclusivo do médico e secretárias para relatórios de agendamento e alertas.';
