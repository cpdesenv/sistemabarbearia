export type StatusComanda = 'ABERTA' | 'FECHADA' | 'ESTORNADA';

export type FormaPagamento = 'DINHEIRO' | 'CARTAO_DEBITO' | 'CARTAO_CREDITO' | 'PIX' | 'OUTRO';

export type TipoItemComanda = 'SERVICO' | 'PRODUTO';

export interface ComandaItem {
  uuid: string;
  tipo: TipoItemComanda;
  servicoUuid: string | null;
  produtoUuid: string | null;
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

export interface Despesa {
  uuid: string;
  data: string;
  categoria: string | null;
  valor: number;
  descricao: string | null;
  comprovanteUrl: string | null;
  criadoEm: string;
}

export interface CriarDespesaRequest {
  data: string;
  categoria: string | null;
  valor: number;
  descricao: string | null;
  comprovanteUrl: string | null;
}

export type StatusContaPagar = 'PENDENTE' | 'PAGA' | 'CANCELADA';

export interface ContaPagar {
  uuid: string;
  descricao: string;
  valor: number;
  dataVencimento: string;
  status: StatusContaPagar;
  dataPagamento: string | null;
  vencida: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface CriarContaPagarRequest {
  descricao: string;
  valor: number;
  dataVencimento: string;
}

export type StatusContaReceber = 'PENDENTE' | 'RECEBIDA' | 'CANCELADA';

export interface ContaReceber {
  uuid: string;
  clienteUuid: string;
  clienteNome: string;
  descricao: string | null;
  valor: number;
  dataVencimento: string;
  status: StatusContaReceber;
  dataRecebimento: string | null;
  vencida: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface CriarContaReceberRequest {
  clienteUuid: string;
  descricao: string | null;
  valor: number;
  dataVencimento: string;
}

export interface FluxoCaixa {
  caixaEmMaos: number;
  contasAReceberEsperadas: number;
  contasAPagarVencidas: number;
  fluxoCaixa: number;
}

export const RUTULOS_STATUS_CONTA_PAGAR: Record<StatusContaPagar, string> = {
  PENDENTE: 'Pendente',
  PAGA: 'Paga',
  CANCELADA: 'Cancelada',
};

export const RUTULOS_STATUS_CONTA_RECEBER: Record<StatusContaReceber, string> = {
  PENDENTE: 'Pendente',
  RECEBIDA: 'Recebida',
  CANCELADA: 'Cancelada',
};
