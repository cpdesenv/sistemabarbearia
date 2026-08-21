export type StatusAgendamento =
  | 'AGENDADO'
  | 'CONFIRMADO'
  | 'EM_ATENDIMENTO'
  | 'FINALIZADO'
  | 'CANCELADO'
  | 'NAO_COMPARECEU';

export type OrigemAgendamento = 'WHATSAPP' | 'PORTAL' | 'PAINEL' | 'MANUAL';

export interface AgendamentoServicoItem {
  servicoUuid: string;
  nome: string;
  duracaoMinutos: number;
  preco: number;
}

export interface Agendamento {
  uuid: string;
  clienteUuid: string;
  clienteNome: string;
  clienteTelefone: string | null;
  profissionalUuid: string;
  profissionalNome: string;
  profissionalCorAgenda: string;
  servicos: AgendamentoServicoItem[];
  inicio: string;
  fim: string;
  valorTotal: number;
  status: StatusAgendamento;
  origem: OrigemAgendamento;
  observacao: string | null;
  motivoCancelamento: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface SalvarAgendamentoRequest {
  clienteUuid: string;
  profissionalUuid: string;
  servicoUuids: string[];
  inicio: string;
  observacao: string | null;
}

export interface SlotDisponivel {
  profissionalUuid: string;
  profissionalNome: string;
  profissionalCorAgenda: string;
  inicio: string;
  fim: string;
}

export interface FiltroAgenda {
  de: string;
  ate: string;
  profissionalUuid?: string;
  clienteUuid?: string;
  status?: StatusAgendamento;
}

export const RUTULOS_STATUS: Record<StatusAgendamento, string> = {
  AGENDADO: 'Agendado',
  CONFIRMADO: 'Confirmado',
  EM_ATENDIMENTO: 'Em atendimento',
  FINALIZADO: 'Finalizado',
  CANCELADO: 'Cancelado',
  NAO_COMPARECEU: 'Não compareceu',
};
