export interface PlanoAssinatura {
  uuid: string;
  nome: string;
  descricao: string | null;
  precoMensal: number;
  cortesIncluidosPorCiclo: number;
  percentualDescontoAdicional: number;
  ativo: boolean;
  servicosInclusosUuids: string[];
  criadoEm: string;
  atualizadoEm: string;
}

export interface SalvarPlanoAssinaturaRequest {
  nome: string;
  descricao: string | null;
  precoMensal: number;
  cortesIncluidosPorCiclo: number;
  percentualDescontoAdicional: number;
  servicosInclusosUuids: string[];
}

export type StatusAssinatura = 'ATIVA' | 'CANCELADA' | 'INADIMPLENTE' | 'SUSPENSA';

export const RUTULOS_STATUS_ASSINATURA: Record<StatusAssinatura, string> = {
  ATIVA: 'Ativa',
  CANCELADA: 'Cancelada',
  INADIMPLENTE: 'Inadimplente',
  SUSPENSA: 'Suspensa',
};

export interface Assinatura {
  uuid: string;
  clienteUuid: string;
  clienteNome: string;
  planoUuid: string;
  planoNome: string;
  status: StatusAssinatura;
  saldoCortesAtual: number;
  dataInicio: string;
  dataProximaRenovacao: string;
  dataCancelamento: string | null;
  motivoCancelamento: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface AssinaturaResumo {
  ativas: number;
  inadimplentes: number;
  suspensas: number;
  canceladas: number;
  receitaRecorrenteMensal: number;
}
