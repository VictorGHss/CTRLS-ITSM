-- =============================================================================
-- Migração V53: Adicionar coluna phone na tabela access_credentials
-- =============================================================================
-- Contexto: Permite armazenar o número de telefone/WhatsApp informado pelo paciente
-- ou acompanhante durante o auto-cadastro, check-in ou confirmação de presença.
-- =============================================================================

ALTER TABLE access_credentials 
ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
