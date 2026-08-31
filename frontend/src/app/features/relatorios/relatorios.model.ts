import { ContaPagar, FormaPagamento, TotalPorFormaPagamento } from '../financeiro/financeiro.model';
import { Produto } from '../produtos/produtos.model';

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

export interface LinhaAgenda {
  profissionalNome: string;
  quantidadeFinalizados: number;
  quantidadeCancelados: number;
  quantidadeNaoCompareceu: number;
  taxaOcupacao: number;
}

export interface RelatorioAgenda {
  dataInicial: string;
  dataFinal: string;
  quantidadeFinalizados: number;
  quantidadeCancelados: number;
  quantidadeNaoCompareceu: number;
  taxaOcupacao: number;
  porProfissional: LinhaAgenda[];
}

export interface RelatorioClientes {
  dataInicial: string;
  dataFinal: string;
  clientesNovos: number;
  clientesRecorrentes: number;
  atendimentosTotais: number;
  taxaDeRetorno: number;
}

export interface LinhaProduto {
  nome: string;
  quantidadeVendida: number;
  valorTotal: number;
  custoTotal: number;
  margemTotal: number;
  margemPercentual: number;
}

export interface RelatorioProduto {
  dataInicial: string;
  dataFinal: string;
  valorTotal: number;
  custoTotal: number;
  margemTotal: number;
  margemPercentual: number;
  porProduto: LinhaProduto[];
}

export interface CelulaHeatmap {
  diaSemana: number;
  hora: number;
  quantidadeFinalizados: number;
}

export interface RelatorioHeatmap {
  dataInicial: string;
  dataFinal: string;
  celulas: CelulaHeatmap[];
}

export interface RelatorioPrevisao {
  comissaoTotalMes: number;
  comissaoPorProfissional: LinhaFaturamento[];
  produtosParaRepor: Produto[];
  contasVencidas: ContaPagar[];
}
