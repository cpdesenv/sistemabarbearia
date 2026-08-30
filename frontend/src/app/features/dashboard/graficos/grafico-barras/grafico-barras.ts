import { Component, computed, input } from '@angular/core';

import { ItemContagem } from '../../dashboard.model';

@Component({
  selector: 'app-grafico-barras',
  imports: [],
  templateUrl: './grafico-barras.html',
  styleUrl: './grafico-barras.css',
})
export class GraficoBarras {
  readonly itens = input.required<ItemContagem[]>();

  protected readonly maximo = computed(() =>
    Math.max(1, ...this.itens().map((item) => item.quantidade)),
  );

  protected percentual(quantidade: number): number {
    return (quantidade / this.maximo()) * 100;
  }
}
