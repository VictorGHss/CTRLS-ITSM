-- V56__seed_white_label_settings.sql
-- Sementes para personalização de marca (White-Label) do sistema CTRLS ITSM

INSERT INTO system_settings (id, value, description) VALUES
    ('BRANDING_APP_NAME', 'CTRLS ITSM', 'Nome da aplicação exibido em títulos, abas e cabeçalhos'),
    ('BRANDING_COMPANY_NAME', 'CTRLS Tecnologia', 'Razão social ou nome da organização cliente'),
    ('BRANDING_LOGO_URL', '', 'URL pública da imagem do logotipo da empresa'),
    ('BRANDING_PRIMARY_COLOR', '#feb56c', 'Cor primária de destaque da marca (hexadecimal)'),
    ('BRANDING_PRIMARY_DARK_COLOR', '#f1a154', 'Cor primária escura para hover e contraste (hexadecimal)'),
    ('BRANDING_SECONDARY_COLOR', '#fed8b0', 'Cor secundária suave para fundos e badges (hexadecimal)'),
    ('BRANDING_SUPPORT_EMAIL', 'suporte@ctrls.dev.br', 'E-mail oficial para exibição e suporte ao usuário')
ON CONFLICT (id) DO NOTHING;
