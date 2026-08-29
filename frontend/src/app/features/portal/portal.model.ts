export interface PortalServico {
  uuid: string;
  nome: string;
  descricao: string | null;
  categoria: string | null;
  preco: number;
  duracaoMinutos: number;
}

export interface PortalProfissional {
  uuid: string;
  nome: string;
  corAgenda: string;
}

export interface PortalSlotDisponivel {
  profissionalUuid: string;
  profissionalNome: string;
  profissionalCorAgenda: string;
  inicio: string;
  fim: string;
}

export interface PortalAgendamentoRequest {
  nome: string;
  telefone: string;
  email: string | null;
  profissionalUuid: string;
  servicoUuids: string[];
  inicio: string;
  consentimentoLgpd: boolean;
}

export interface PortalAgendamentoServico {
  servicoUuid: string;
  nome: string;
  duracaoMinutos: number;
  preco: number;
}

export interface PortalAgendamentoConfirmado {
  uuid: string;
  clienteNome: string;
  profissionalNome: string;
  profissionalCorAgenda: string;
  servicos: PortalAgendamentoServico[];
  inicio: string;
  fim: string;
  valorTotal: number;
  status: string;
}
