import { motion } from 'framer-motion';
import { ArrowLeft } from 'lucide-react';
import type { SubSectionItem, SubSectionType } from '../types';

interface SettingsSubSectionGridProps {
  subSections: SubSectionItem[];
  onSelectSubSection: (id: SubSectionType) => void;
}

export default function SettingsSubSectionGrid({
  subSections,
  onSelectSubSection,
}: SettingsSubSectionGridProps) {
  return (
    <motion.div
      key="menu"
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -15 }}
      transition={{ duration: 0.2 }}
      className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
    >
      {subSections.map(({ id, title, desc, icon: Icon }) => (
        <motion.button
          key={id}
          onClick={() => onSelectSubSection(id)}
          whileHover={{ scale: 1.02, translateY: -4 }}
          whileTap={{ scale: 0.98 }}
          className="flex flex-col text-left p-6 rounded-2xl border border-[#feb56c]/30 bg-white shadow-sm hover:shadow-md hover:border-[#feb56c] transition-all cursor-pointer group relative overflow-hidden"
        >
          <div className="flex items-center gap-4 mb-3">
            <div className="w-12 h-12 rounded-xl bg-[#feb56c]/10 text-[#feb56c] flex items-center justify-center shrink-0 shadow-sm transition-transform group-hover:scale-110 group-hover:bg-[#feb56c]/20">
              <Icon size={22} />
            </div>
            <h3 className="text-sm font-bold text-slate-800 group-hover:text-[#feb56c] transition-colors">
              {title}
            </h3>
          </div>
          <p className="text-xs text-slate-500 leading-relaxed pr-4">{desc}</p>

          <div className="absolute bottom-4 right-4 text-[#feb56c] opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all">
            <ArrowLeft size={16} className="rotate-180" />
          </div>
        </motion.button>
      ))}
    </motion.div>
  );
}
