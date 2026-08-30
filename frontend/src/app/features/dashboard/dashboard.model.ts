import { TotalPorFormaPagamento } from '../financeiro/financeiro.model';

export interface DashboardCards {
  faturamentoDia: number;
  faturamentoMes: number;
  percentualFaturamentoVsMesAnterior: number | null;
  atendimentosDia: number;
  ticketMedioDia: number;
  taxaOcupacaoHoje: number;
}

export interface IndicadoresSaude {
  clientesNovosMes: number;
  cancelamentosMes: number;
  faltasMes: number;
  agendamentosForaDeSincronia: number;
}

export interface IndicadoresAssinatura {
  receitaRecorrente: number;
  taxaChurnMes: number;
}

export interface PontoMensal {
  mes: string;
  valor: number;
}

export interface ItemContagem {
  nome: string;
  quantidade: number;
}

export interface DashboardGraficos {
  faturamentoUltimos12Meses: PontoMensal[];
  servicosMaisVendidos: ItemContagem[];
  atendimentosPorProfissional: ItemContagem[];
  distribuicaoFormaPagamento: TotalPorFormaPagamento[];
}

export interface DashboardResumo {
  cards: DashboardCards;
  indicadoresSaude: IndicadoresSaude;
  indicadoresAssinatura: IndicadoresAssinatura;
  graficos: DashboardGraficos;
}
