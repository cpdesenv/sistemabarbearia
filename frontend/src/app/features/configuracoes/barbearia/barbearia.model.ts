export interface Barbearia {
  nome: string;
  cnpj: string | null;
  telefone: string | null;
  email: string | null;
  logradouro: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string | null;
  uf: string | null;
  cep: string | null;
  fusoHorario: string;
  antecedenciaMinimaAgendamentoMinutos: number;
  antecedenciaMaximaAgendamentoDias: number;
  antecedenciaMinimaCancelamentoMinutos: number;
  granularidadeSlotMinutos: number;
  portalAutoagendamentoAtivo: boolean;
  atualizadoEm: string;
}

export type AtualizarBarbeariaRequest = Omit<Barbearia, 'atualizadoEm'>;
