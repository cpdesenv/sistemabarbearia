export interface ConfiguracaoAutoagendamento {
  ativo: boolean;
  nomeBarbearia: string;
}

export interface ServicoPublico {
  uuid: string;
  nome: string;
  descricao: string | null;
  categoria: string | null;
  preco: number;
  duracaoMinutos: number;
}

export interface ProfissionalPublico {
  uuid: string;
  nome: string;
  corAgenda: string | null;
}

export interface SlotDisponivel {
  profissionalUuid: string;
  profissionalNome: string;
  profissionalCorAgenda: string | null;
  inicio: string;
  fim: string;
}

export interface CriarAutoagendamentoRequest {
  nome: string;
  telefone: string;
  email: string | null;
  consentimentoLgpd: boolean;
  profissionalUuid: string;
  servicoUuids: string[];
  inicio: string;
}

export interface AgendamentoConfirmado {
  uuid: string;
  profissionalNome: string;
  servicos: { servicoUuid: string; nome: string; duracaoMinutos: number; preco: number }[];
  inicio: string;
  fim: string;
  valorTotal: number;
}
