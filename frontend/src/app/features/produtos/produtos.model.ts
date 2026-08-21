export interface Produto {
  uuid: string;
  nome: string;
  descricao: string | null;
  categoria: string | null;
  unidade: string;
  precoVenda: number;
  precoCusto: number;
  estoqueMinimo: number;
  estoqueAtual: number;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface SalvarProdutoRequest {
  nome: string;
  descricao: string | null;
  categoria: string | null;
  unidade: string | null;
  precoVenda: number;
  precoCusto: number | null;
  estoqueMinimo: number;
}

export interface PaginaProdutos {
  content: Produto[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface FiltroProdutos {
  nome?: string;
  categoria?: string;
  ativo?: boolean;
  page?: number;
  size?: number;
}

export type TipoMovimentoEstoque = 'ENTRADA' | 'SAIDA' | 'AJUSTE' | 'DEVOLUCAO';

export interface MovimentoEstoque {
  tipo: TipoMovimentoEstoque;
  quantidade: number;
  custoUnitario: number | null;
  motivo: string | null;
  comandaId: number | null;
  criadoEm: string;
}

export interface PaginaMovimentos {
  content: MovimentoEstoque[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export const RUTULOS_TIPO_MOVIMENTO: Record<TipoMovimentoEstoque, string> = {
  ENTRADA: 'Entrada',
  SAIDA: 'Saída',
  AJUSTE: 'Ajuste',
  DEVOLUCAO: 'Devolução',
};
