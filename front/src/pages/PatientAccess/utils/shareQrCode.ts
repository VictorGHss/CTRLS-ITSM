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
