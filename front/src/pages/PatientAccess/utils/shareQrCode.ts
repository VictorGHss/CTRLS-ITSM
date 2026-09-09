/**
 * Utilitário para download direto da imagem PNG do QR Code formatado como cartão de acesso.
 * O paciente pode salvar diretamente na galeria/downloads do celular para uso offline na catraca.
 */
export async function downloadQrCodeImage(
  canvasId: string,
  personName: string,
  userType: 'PATIENT' | 'COMPANION',
  doctorOrClinicName?: string,
  locatorCode?: string
): Promise<boolean> {
  const sourceCanvas = document.getElementById(canvasId) as HTMLCanvasElement | null;
  if (!sourceCanvas) {
    console.warn(`[downloadQrCode] Canvas com ID ${canvasId} não encontrado no DOM.`);
    return false;
  }

  // Gera um cartão digital de alta resolução (600 x 780)
  const width = 600;
  const height = 780;
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return false;

  // 1. Fundo branco puro
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, width, height);

  // 2. Moldura sutil
  ctx.strokeStyle = '#E2E8F0';
  ctx.lineWidth = 4;
  ctx.strokeRect(8, 8, width - 16, height - 16);

  // 3. Faixa de cabeçalho
  ctx.fillStyle = '#0F172A';
  ctx.font = 'bold 22px system-ui, -apple-system, sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('ACESSO À CATRACA', width / 2, 55);

  // 4. Subtítulo / Clínica
  ctx.fillStyle = '#00875F';
  ctx.font = '600 16px system-ui, -apple-system, sans-serif';
  const clinicTitle = doctorOrClinicName ? doctorOrClinicName.toUpperCase() : 'CLÍNICA INOVARE';
  ctx.fillText(clinicTitle.substring(0, 36), width / 2, 85);

  // 5. Nome do Paciente / Tipo
  ctx.fillStyle = '#334155';
  ctx.font = 'bold 18px system-ui, -apple-system, sans-serif';
  const typeText = userType === 'PATIENT' ? 'Paciente Titular' : 'Acompanhante';
  const nameText = `${(personName || 'Paciente').toUpperCase()} (${typeText})`;
  ctx.fillText(nameText.substring(0, 38), width / 2, 125);

  // Linha divisória tracejada
  ctx.strokeStyle = '#CBD5E1';
  ctx.lineWidth = 2;
  ctx.setLineDash([8, 6]);
  ctx.beginPath();
  ctx.moveTo(40, 145);
  ctx.lineTo(width - 40, 145);
  ctx.stroke();
  ctx.setLineDash([]);

  // 6. QR Code Centralizado em alta resolução (390x390)
  const qrSize = 390;
  const qrX = (width - qrSize) / 2;
  const qrY = 165;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(sourceCanvas, qrX, qrY, qrSize, qrSize);

  // 7. Rodapé com instruções
  ctx.fillStyle = '#059669';
  ctx.font = 'bold 17px system-ui, -apple-system, sans-serif';
  ctx.fillText('📏 Aproxime a 15 cm da catraca', width / 2, 595);

  ctx.fillStyle = '#64748B';
  ctx.font = '14px system-ui, -apple-system, sans-serif';
  ctx.fillText('Mantenha a tela virada para o leitor', width / 2, 625);

  if (locatorCode) {
    ctx.fillStyle = '#94A3B8';
    ctx.font = 'bold 14px monospace';
    ctx.fillText(`CÓDIGO: ${locatorCode}`, width / 2, 665);
  }

  return new Promise((resolve) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        resolve(false);
        return;
      }

      const cleanName = (personName || 'qrcode')
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]/g, '_')
        .replace(/_+/g, '_')
        .substring(0, 25);

      const typeLabel = userType === 'PATIENT' ? 'titular' : 'acompanhante';
      const fileName = `qrcode_catraca_${typeLabel}_${cleanName}.png`;

      try {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 2000);
        resolve(true);
      } catch (err) {
        console.error('[downloadQrCode] Erro ao baixar imagem:', err);
        resolve(false);
      }
    }, 'image/png');
  });
}

/**
 * Utilitário para geração e compartilhamento de imagem PNG de alta resolução do QR Code.
 * Utiliza a Web Share API (navigator.share) nativa em dispositivos móveis (iOS/Android)
 * com fallback para download direto do arquivo de imagem PNG.
 */
export async function shareQrCodeImage(
  canvasId: string,
  personName: string,
  userType: 'PATIENT' | 'COMPANION'
): Promise<boolean> {
  const sourceCanvas = document.getElementById(canvasId) as HTMLCanvasElement | null;
  if (!sourceCanvas) {
    console.warn(`[shareQrCode] Canvas com ID ${canvasId} não encontrado no DOM.`);
    return false;
  }

  // Gera um canvas em alta resolução (512x512) com fundo branco sólido e margem de segurança
  const highResCanvas = document.createElement('canvas');
  highResCanvas.width = 512;
  highResCanvas.height = 512;
  const ctx = highResCanvas.getContext('2d');
  if (!ctx) return false;

  // Fundo branco sólido (protege contra force dark mode e fundos transparentes)
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, 512, 512);

  // Desenha o QR Code original centralizado com margem de segurança (quiet zone)
  ctx.imageSmoothingEnabled = false;
  const margin = 40;
  const qrSize = 512 - margin * 2;
  ctx.drawImage(sourceCanvas, margin, margin, qrSize, qrSize);

  return new Promise((resolve) => {
    highResCanvas.toBlob(async (blob) => {
      if (!blob) {
        resolve(false);
        return;
      }

      const cleanName = (personName || 'qrcode')
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]/g, '_')
        .replace(/_+/g, '_')
        .substring(0, 25);

      const typeLabel = userType === 'PATIENT' ? 'titular' : 'acompanhante';
      const fileName = `qrcode_${typeLabel}_${cleanName}.png`;
      const file = new File([blob], fileName, { type: 'image/png' });

      // Se o navegador suportar compartilhamento de arquivos nativo (ex: Safari no iOS, Chrome no Android)
      if (typeof navigator !== 'undefined' && navigator.canShare && navigator.canShare({ files: [file] })) {
        try {
          await navigator.share({
            files: [file],
            title: `QR Code de Acesso — ${personName}`
          });
          resolve(true);
          return;
        } catch (err: unknown) {
          if ((err as Error).name === 'AbortError') {
            resolve(true); // O usuário apenas cancelou a tela de compartilhamento
            return;
          }
          console.warn('[shareQrCode] Erro ao invocar navigator.share, acionando fallback:', err);
        }
      }

      // Fallback para download direto da imagem PNG
      try {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        resolve(true);
      } catch (dlErr) {
        console.error('[shareQrCode] Erro ao realizar download da imagem:', dlErr);
        resolve(false);
      }
    }, 'image/png');
  });
}
