-- V54: Adiciona coluna doctor_name na tabela access_credentials para guardar o médico ou especialidade associado ao agendamento
ALTER TABLE access_credentials ADD COLUMN IF NOT EXISTS doctor_name VARCHAR(255);
