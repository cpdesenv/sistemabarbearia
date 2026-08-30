import { FormaPagamento, TotalPorFormaPagamento } from '../financeiro/financeiro.model';

export interface LinhaFaturamento {
  nome: string;
  quantidade: number;
  valorTotal: number;
  comissaoTotal: number;
}

export interface RelatorioFaturamento {
  dataInicial: string;
  dataFinal: string;
  valorTotal: number;
  comissaoTotal: number;
  quantidadeAtendimentos: number;
  porServico: LinhaFaturamento[];
  porProfissional: LinhaFaturamento[];
  porFormaPagamento: TotalPorFormaPagamento[];
}

export interface ComparativoFaturamento {
  mes: string;
  valorMesAtual: number;
  valorMesAnterior: number;
  variacaoPercentualMesAnterior: number | null;
  valorMesmoMesAnoAnterior: number;
  variacaoPercentualAnoAnterior: number | null;
}

export interface FiltroRelatorioFaturamento {
  dataInicial: string;
  dataFinal: string;
  profissionalUuid?: string;
  servicoUuid?: string;
  formaPagamento?: FormaPagamento | '';
}
