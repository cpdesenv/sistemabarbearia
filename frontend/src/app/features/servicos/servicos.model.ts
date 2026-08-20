export interface Servico {
  uuid: string;
  nome: string;
  descricao: string | null;
  categoria: string | null;
  preco: number;
  duracaoMinutos: number;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface SalvarServicoRequest {
  nome: string;
  descricao: string | null;
  categoria: string | null;
  preco: number;
  duracaoMinutos: number;
}

export interface PaginaServicos {
  content: Servico[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface FiltroServicos {
  nome?: string;
  categoria?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
}
