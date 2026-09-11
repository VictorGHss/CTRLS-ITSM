import React from 'react';
import { MapPin, MessageCircle, Instagram, Facebook, Phone } from 'lucide-react';
import { CLINIC_THEMES } from '../utils/clinicThemes';
import type { ClinicTheme } from '../utils/clinicThemes';

interface PatientAccessFooterProps {
  clinicTheme?: ClinicTheme;
}

export const PatientAccessFooter: React.FC<PatientAccessFooterProps> = ({ clinicTheme }) => {
  const theme = clinicTheme || CLINIC_THEMES.portal;
  const isImagem = theme.id === 'imagem';

  return (
    <footer className="mt-auto border-t border-slate-200/60 bg-white py-8 px-6 text-center space-y-6">
      <div className="flex flex-col items-center text-center gap-4">
        <div className={`flex h-24 w-auto min-w-[120px] max-w-[200px] items-center justify-center rounded-2xl ${isImagem ? 'bg-rose-50/50 border-rose-200/50' : 'bg-brand-secondary/30 border-brand-primary/10'} p-3 shadow-sm border`}>
          <img
            src={theme.logoUrl}
            alt={theme.name}
            className="h-full w-full object-contain max-h-16"
            onError={(e) => {
              e.currentTarget.src = isImagem 
                ? 'https://placehold.co/180x60/b8004b/ffffff?text=Cl%C3%ADnica+da+Imagem'
                : 'https://placehold.co/120x120/feb56c/ffffff?text=Portal';
            }}
          />
        </div>

        <div className="space-y-1.5 max-w-xs sm:max-w-md">
          <p 
            className="text-sm font-extrabold uppercase tracking-wider"
            style={{ color: theme.primaryDarkColor }}
          >
            {theme.name}
          </p>
          <p className="text-[11px] text-slate-500 leading-relaxed font-semibold font-sans">
            {theme.address}
          </p>
          <p className="text-[10px] text-slate-400 font-bold">
            Atendimento: {theme.openingHours}
          </p>
        </div>
      </div>

      <div className="flex flex-wrap justify-center gap-2">
        {theme.mapsUrl && (
          <a 
            href={theme.mapsUrl} 
            target="_blank" 
            rel="noopener noreferrer" 
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-slate-100 hover:text-slate-900 border border-slate-200 rounded-xl text-xs font-bold text-slate-700 transition-all shadow-sm"
          >
            <MapPin className="w-4 h-4 text-red-500" />
            Ver no Maps
          </a>
        )}
        {theme.phone && (
          <a 
            href={`tel:${theme.phone.replace(/\D/g, '')}`} 
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-slate-100 hover:text-slate-900 border border-slate-200 rounded-xl text-xs font-bold text-slate-700 transition-all shadow-sm"
          >
            <Phone className="w-4 h-4 text-blue-500" />
            {theme.phone}
          </a>
        )}
        {theme.whatsappUrl && (
          <a 
            href={theme.whatsappUrl} 
            target="_blank" 
            rel="noopener noreferrer" 
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-emerald-50 hover:text-emerald-900 border border-slate-200 rounded-xl text-xs font-bold text-slate-700 transition-all shadow-sm"
          >
            <MessageCircle className="w-4 h-4 text-emerald-500" />
            WhatsApp
          </a>
        )}
        {theme.instagramUrl && (
          <a 
            href={theme.instagramUrl} 
            target="_blank" 
            rel="noopener noreferrer" 
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-pink-50 hover:text-pink-900 border border-slate-200 rounded-xl text-xs font-bold text-slate-700 transition-all shadow-sm"
          >
            <Instagram className="w-4 h-4 text-pink-500" />
            Instagram
          </a>
        )}
        {theme.facebookUrl && (
          <a 
            href={theme.facebookUrl} 
            target="_blank" 
            rel="noopener noreferrer" 
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-blue-50 hover:text-blue-900 border border-slate-200 rounded-xl text-xs font-bold text-slate-700 transition-all shadow-sm"
          >
            <Facebook className="w-4 h-4 text-blue-600" />
            Facebook
          </a>
        )}
      </div>

      <div className="border-t border-slate-100 pt-4 flex flex-col items-center gap-2">
        <p className="text-xs text-slate-500 font-semibold text-center">
          {isImagem ? 'Clínica da Imagem © Todos os direitos reservados' : `${theme.name} © Todos os direitos reservados`}
        </p>
      </div>
    </footer>
  );
};
