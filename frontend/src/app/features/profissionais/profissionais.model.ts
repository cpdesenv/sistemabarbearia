export interface Profissional {
  uuid: string;
  nome: string;
  email: string | null;
  telefone: string | null;
  corAgenda: string;
  comissaoPercentualPadrao: number;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface SalvarProfissionalRequest {
  nome: string;
  email: string | null;
  telefone: string | null;
  corAgenda: string;
  comissaoPercentualPadrao: number;
}

export interface ServicoVinculado {
  servicoUuid: string;
  nomeServico: string;
  comissaoPercentual: number | null;
  comissaoEfetiva: number;
}

export interface AssociarServicoRequest {
  servicoUuid: string;
  comissaoPercentual: number | null;
}

export interface PaginaProfissionais {
  content: Profissional[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface FiltroProfissionais {
  nome?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
}
