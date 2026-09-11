import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import api from '../services/api';

export interface BrandingConfig {
  appName: string;
  companyName: string;
  logoUrl: string;
  primaryColor: string;
  primaryDarkColor: string;
  secondaryColor: string;
  supportEmail: string;
}

export const DEFAULT_BRANDING: BrandingConfig = {
  appName: 'CTRLS ITSM',
  companyName: 'CTRLS Tecnologia',
  logoUrl: '',
  primaryColor: '#feb56c',
  primaryDarkColor: '#f1a154',
  secondaryColor: '#fed8b0',
  supportEmail: 'suporte@ctrls.dev.br',
};

const STORAGE_KEY = '@Itsm:branding';

interface BrandingContextData {
  branding: BrandingConfig;
  loading: boolean;
  updateBranding: (newConfig: Partial<BrandingConfig>) => Promise<BrandingConfig>;
  resetToDefaults: () => Promise<BrandingConfig>;
}

const BrandingContext = createContext<BrandingContextData>({} as BrandingContextData);

export const applyThemeCssVariables = (branding: BrandingConfig) => {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.style.setProperty('--brand-primary', branding.primaryColor || DEFAULT_BRANDING.primaryColor);
  root.style.setProperty('--brand-primary-dark', branding.primaryDarkColor || DEFAULT_BRANDING.primaryDarkColor);
  root.style.setProperty('--brand-secondary', branding.secondaryColor || DEFAULT_BRANDING.secondaryColor);
};

export const BrandingProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [branding, setBranding] = useState<BrandingConfig>(() => {
    try {
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        const parsed = JSON.parse(cached);
        applyThemeCssVariables(parsed);
        return { ...DEFAULT_BRANDING, ...parsed };
      }
    } catch {
      // Ignora erro de parse e usa defaults
    }
    applyThemeCssVariables(DEFAULT_BRANDING);
    return DEFAULT_BRANDING;
  });

  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    async function loadBranding() {
      try {
        const { data } = await api.get<BrandingConfig>('/v1/branding');
        if (isMounted && data) {
          const merged = { ...DEFAULT_BRANDING, ...data };
          setBranding(merged);
          applyThemeCssVariables(merged);
          localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
        }
      } catch (err) {
        console.warn('Usando identidade visual em cache/padrão:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }
    loadBranding();
    return () => {
      isMounted = false;
    };
  }, []);

  const updateBranding = useCallback(async (newConfig: Partial<BrandingConfig>): Promise<BrandingConfig> => {
    const payload = { ...branding, ...newConfig };
    const { data } = await api.put<BrandingConfig>('/v1/admin/branding', payload);
    const merged = { ...DEFAULT_BRANDING, ...data };
    setBranding(merged);
    applyThemeCssVariables(merged);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
    return merged;
  }, [branding]);

  const resetToDefaults = useCallback(async (): Promise<BrandingConfig> => {
    return updateBranding(DEFAULT_BRANDING);
  }, [updateBranding]);

  return (
    <BrandingContext.Provider value={{ branding, loading, updateBranding, resetToDefaults }}>
      {children}
    </BrandingContext.Provider>
  );
};

export function useBranding(): BrandingContextData {
  const context = useContext(BrandingContext);
  if (!context) {
    throw new Error('useBranding deve ser utilizado dentro de um BrandingProvider');
  }
  return context;
}
