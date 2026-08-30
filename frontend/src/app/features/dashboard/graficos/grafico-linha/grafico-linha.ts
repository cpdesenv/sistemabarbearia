import { CurrencyPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

import { PontoMensal } from '../../dashboard.model';

const MESES_ABREVIADOS = [
  'jan',
  'fev',
  'mar',
  'abr',
  'mai',
  'jun',
  'jul',
  'ago',
  'set',
  'out',
  'nov',
  'dez',
];

const LARGURA = 600;
const ALTURA = 220;
const MARGEM_ESQUERDA = 40;
const MARGEM_DIREITA = 55;
const MARGEM_TOPO = 16;
const MARGEM_BASE = 28;

interface PontoDesenhado {
  x: number;
  y: number;
  rotuloMes: string;
  valor: number;
}

@Component({
  selector: 'app-grafico-linha',
  imports: [CurrencyPipe],
  templateUrl: './grafico-linha.html',
  styleUrl: './grafico-linha.css',
})
export class GraficoLinha {
  readonly pontos = input.required<PontoMensal[]>();

  protected readonly largura = LARGURA;
  protected readonly altura = ALTURA;

  protected readonly valorMaximo = computed(() => {
    const maiorValor = Math.max(0, ...this.pontos().map((p) => p.valor));
    return maiorValor === 0 ? 1 : maiorValor * 1.15;
  });

  protected readonly pontosDesenhados = computed<PontoDesenhado[]>(() => {
    const dados = this.pontos();
    const areaUtilLargura = LARGURA - MARGEM_ESQUERDA - MARGEM_DIREITA;
    const areaUtilAltura = ALTURA - MARGEM_TOPO - MARGEM_BASE;
    const maximo = this.valorMaximo();

    return dados.map((ponto, indice) => {
      const x =
        dados.length === 1
          ? MARGEM_ESQUERDA + areaUtilLargura / 2
          : MARGEM_ESQUERDA + (indice / (dados.length - 1)) * areaUtilLargura;
      const y = MARGEM_TOPO + areaUtilAltura - (ponto.valor / maximo) * areaUtilAltura;
      const [, mes] = ponto.mes.split('-');
      return { x, y, rotuloMes: MESES_ABREVIADOS[Number(mes) - 1], valor: ponto.valor };
    });
  });

  protected readonly linha = computed(() =>
    this.pontosDesenhados()
      .map((p, indice) => `${indice === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`)
      .join(' '),
  );

  protected readonly linhaBase = computed(() => ALTURA - MARGEM_BASE);

  protected readonly ultimoPonto = computed(() => {
    const pontos = this.pontosDesenhados();
    return pontos.length === 0 ? null : pontos[pontos.length - 1];
  });
}
