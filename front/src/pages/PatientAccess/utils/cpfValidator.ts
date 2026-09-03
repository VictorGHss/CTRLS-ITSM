/**
 * Validação rigorosa de CPF conforme algoritmo oficial da Receita Federal do Brasil.
 * Remove caracteres não-numéricos, valida comprimento (11 dígitos),
 * rejeita sequências com todos os dígitos repetidos e valida ambos os dígitos verificadores.
 */
export function isValidCpf(rawCpf: string | null | undefined): boolean {
  if (!rawCpf) return false;

  const cpf = rawCpf.replace(/\D/g, '');
  if (cpf.length !== 11) return false;

  // Rejeita números com todos os dígitos repetidos (ex: 00000000000, 11111111111, etc.)
  if (/^(\d)\1{10}$/.test(cpf)) return false;

  // Validação do 1º dígito verificador
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    sum += parseInt(cpf.charAt(i), 10) * (10 - i);
  }
  let remainder = sum % 11;
  const firstCheck = remainder < 2 ? 0 : 11 - remainder;
  if (parseInt(cpf.charAt(9), 10) !== firstCheck) {
    return false;
  }

  // Validação do 2º dígito verificador
  sum = 0;
  for (let i = 0; i < 10; i++) {
    sum += parseInt(cpf.charAt(i), 10) * (11 - i);
  }
  remainder = sum % 11;
  const secondCheck = remainder < 2 ? 0 : 11 - remainder;
  return parseInt(cpf.charAt(10), 10) === secondCheck;
}
