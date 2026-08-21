export type StatusComanda = 'ABERTA' | 'FECHADA' | 'ESTORNADA';

export type FormaPagamento = 'DINHEIRO' | 'CARTAO_DEBITO' | 'CARTAO_CREDITO' | 'PIX' | 'OUTRO';

export interface ComandaItem {
  uuid: string;
  servicoUuid: string;
  descricao: string;
  quantidade: number;
  valorUnitario: number;
  valorBruto: number;
  valorDescontoRateado: number;
  valorLiquido: number;
  comissaoPercentualAplicado: number | null;
  comissaoValor: number | null;
}

export interface Comanda {
  uuid: string;
  agendamentoUuid: string;
  clienteUuid: string;
  clienteNome: string;
  profissionalUuid: string;
  profissionalNome: string;
  status: StatusComanda;
  itens: ComandaItem[];
  descontoValor: number;
  descontoMotivo: string | null;
  formaPagamento: FormaPagamento | null;
  subtotal: number;
  valorTotal: number;
  comissaoTotal: number;
  fechadaEm: string | null;
  estornadaEm: string | null;
  motivoEstorno: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface TotalPorFormaPagamento {
  formaPagamento: FormaPagamento;
  total: number;
}

export interface TotalPorProfissional {
  profissionalUuid: string;
  profissionalNome: string;
  totalFaturado: number;
  totalComissao: number;
}

export interface CaixaDoDia {
  data: string;
  totalGeral: number;
  porFormaPagamento: TotalPorFormaPagamento[];
  porProfissional: TotalPorProfissional[];
}

export const RUTULOS_STATUS_COMANDA: Record<StatusComanda, string> = {
  ABERTA: 'Aberta',
  FECHADA: 'Fechada',
  ESTORNADA: 'Estornada',
};

export const RUTULOS_FORMA_PAGAMENTO: Record<FormaPagamento, string> = {
  DINHEIRO: 'Dinheiro',
  CARTAO_DEBITO: 'Cartão de débito',
  CARTAO_CREDITO: 'Cartão de crédito',
  PIX: 'Pix',
  OUTRO: 'Outro',
};
