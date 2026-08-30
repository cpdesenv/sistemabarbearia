import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

import { FormaPagamento, RUTULOS_FORMA_PAGAMENTO, TotalPorFormaPagamento } from '../../../financeiro/financeiro.model';

/**
 * Ordem e cor fixas por forma de pagamento (nunca reatribuidas conforme o
 * que aparece nos dados) - identidade visual estavel entre uma consulta e
 * outra, mesmo que uma forma de pagamento fique sem uso num periodo.
 */
const COR_POR_FORMA: Record<FormaPagamento, string> = {
  DINHEIRO: '#2a78d6',
  CARTAO_DEBITO: '#eb6834',
  CARTAO_CREDITO: '#1baf7a',
  PIX: '#eda100',
  OUTRO: '#e87ba4',
};

const RAIO = 45;
const CENTRO = 60;
const CIRCUNFERENCIA = 2 * Math.PI * RAIO;
const GAP_PX = 2;

interface FatiaDesenhada {
  formaPagamento: FormaPagamento;
  rotulo: string;
  cor: string;
  valor: number;
  percentual: number;
  tracoDasharray: string;
  tracoDashoffset: number;
}

@Component({
  selector: 'app-grafico-rosca',
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './grafico-rosca.html',
  styleUrl: './grafico-rosca.css',
})
export class GraficoRosca {
  readonly itens = input.required<TotalPorFormaPagamento[]>();

  protected readonly raio = RAIO;
  protected readonly centro = CENTRO;

  protected readonly total = computed(() => this.itens().reduce((soma, item) => soma + item.total, 0));

  protected readonly fatias = computed<FatiaDesenhada[]>(() => {
    const total = this.total();
    if (total <= 0) {
      return [];
    }

    const gapsTotais = this.itens().length > 1 ? GAP_PX * this.itens().length : 0;
    let acumulado = 0;

    return this.itens().map((item) => {
      const percentual = item.total / total;
      const comprimentoBruto = percentual * CIRCUNFERENCIA;
      const comprimento = Math.max(0, comprimentoBruto - gapsTotais / this.itens().length);
      const fatia: FatiaDesenhada = {
        formaPagamento: item.formaPagamento,
        rotulo: RUTULOS_FORMA_PAGAMENTO[item.formaPagamento],
        cor: COR_POR_FORMA[item.formaPagamento],
        valor: item.total,
        percentual: percentual * 100,
        tracoDasharray: `${comprimento} ${CIRCUNFERENCIA - comprimento}`,
        tracoDashoffset: -acumulado,
      };
      acumulado += comprimentoBruto;
      return fatia;
    });
  });
}
