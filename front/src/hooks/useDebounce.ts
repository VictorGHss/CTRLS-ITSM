import { useEffect, useState } from 'react';

/**
 * Hook customizado para aplicar debounce em valores que mudam com frequência (como termos de busca).
 * Previne rajadas de requisições ao backend e evita bloqueios por rate-limiting (ex: Cloudflare).
 *
 * @param value Valor de entrada a ser observado
 * @param delay Atraso em milissegundos (padrão: 400ms)
 * @returns Valor atualizado somente após o tempo de inatividade especificado
 */
export function useDebounce<T>(value: T, delay = 400): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(timer);
    };
  }, [value, delay]);

  return debouncedValue;
}

export default useDebounce;
