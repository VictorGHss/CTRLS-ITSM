import React from 'react';
import { MapPin, MessageCircle, Instagram, Facebook, Github } from 'lucide-react';

export const PatientAccessFooter: React.FC = () => {
  return (
    <footer className="mt-auto border-t border-brand-secondary/30 bg-white py-8 px-6 text-center space-y-6">
      <div className="flex flex-col items-center text-center gap-4">
        <div className="flex h-28 w-28 items-center justify-center rounded-3xl bg-brand-secondary/30 p-4 shadow-sm border border-brand-primary/10">
          <img
            src="/Logo.png"
            alt="Inovare – Serviços de Saúde"
            className="h-full w-full object-contain"
            onError={(e) => {
              e.currentTarget.src = 'https://placehold.co/120x120/feb56c/ffffff?text=Inovare';
            }}
          />
        </div>

        <div className="space-y-1.5 max-w-xs sm:max-w-md">
          <p className="text-sm font-extrabold uppercase tracking-wider text-brand-primary-dark">
            Inovare – Serviços de Saúde
          </p>
          <p className="text-[11px] text-slate-500 leading-relaxed font-semibold font-sans">
            R. Carlos Osternack, 111 - Vila Placidina, Ponta Grossa - PR, 84040-120
          </p>
          <p className="text-[10px] text-slate-400 font-bold">
            Atendimento: Segunda a sexta, 08h – 12h e 13h – 18h30
          </p>
        </div>
      </div>

      <div className="flex flex-wrap justify-center gap-2">
        <a 
          href="https://maps.app.goo.gl/S2BaxmJFgr4YAjRT7" 
          target="_blank" 
          rel="noopener noreferrer" 
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-655 transition-all shadow-sm"
        >
          <MapPin className="w-4 h-4 text-brand-primary" />
          Ver no Maps
        </a>
        <a 
          href="https://wa.me/554230262601" 
          target="_blank" 
          rel="noopener noreferrer" 
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
        >
          <MessageCircle className="w-4 h-4 text-emerald-500" />
          WhatsApp
        </a>
        <a 
          href="https://www.instagram.com/inovaress/" 
          target="_blank" 
          rel="noopener noreferrer" 
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
        >
          <Instagram className="w-4 h-4 text-pink-500" />
          Instagram
        </a>
        <a 
          href="https://www.facebook.com/inovarepg" 
          target="_blank" 
          rel="noopener noreferrer" 
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
        >
          <Facebook className="w-4 h-4 text-blue-600" />
          Facebook
        </a>
      </div>

      <div className="border-t border-slate-100 pt-4 flex flex-col items-center gap-2">
        <p className="text-xs text-slate-400 flex items-center justify-center gap-1">
          Feito por
          <Github className="inline w-4 h-4 mx-1 text-slate-400" />
          <a
            href="https://github.com/VictorGHss"
            target="_blank"
            rel="noopener noreferrer"
            className="text-brand-primary-dark hover:text-brand-primary font-bold transition-colors underline underline-offset-2"
          >
            VictorGHss
          </a>
        </p>
      </div>
    </footer>
  );
};
