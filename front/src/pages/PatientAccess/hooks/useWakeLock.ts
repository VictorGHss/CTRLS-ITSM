import { useEffect } from 'react';

/**
 * Hook para a Screen Wake Lock API.
 * Impede que a tela do dispositivo apague ou bloqueie enquanto o QR Code está em exibição fullscreen.
 */
export function useWakeLock(enabled: boolean) {
  useEffect(() => {
    let activeLock: WakeLockSentinel | null = null;

    const acquireLock = async () => {
      if (enabled && 'wakeLock' in navigator && navigator.wakeLock) {
        try {
          activeLock = await navigator.wakeLock.request('screen');
        } catch (err: unknown) {
          console.warn('[WakeLock] Erro ao solicitar trava de tela:', err);
        }
      }
    };

    void acquireLock();

    return () => {
      if (activeLock) {
        activeLock
          .release()
          .catch((err: unknown) => console.warn('[WakeLock] Erro ao liberar trava de tela:', err));
      }
    };
  }, [enabled]);
}
