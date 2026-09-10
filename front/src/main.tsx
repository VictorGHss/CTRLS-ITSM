import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from '@/App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Service Worker: verificar atualizações e solicitar skipWaiting para ativar o novo SW imediatamente.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg) {
        if (reg.waiting) {
          try {
            reg.waiting.postMessage({ type: 'SKIP_WAITING' });
          } catch {
            // ignorar
          }
        }

        reg.addEventListener('updatefound', () => {
          const newWorker = reg.installing;
          if (newWorker) {
            newWorker.addEventListener('statechange', () => {
              if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                try {
                  newWorker.postMessage({ type: 'SKIP_WAITING' });
                } catch {
                  // ignorar
                }
              }
            });
          }
        });
      }
    } catch (e) {
      console.warn('Falha na verificação de registro do SW', e);
    }

    // Evita reload loop quando controllerchange for disparado
    let refreshing = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (refreshing) return;
      refreshing = true;
      window.location.reload();
    });
  });
}
