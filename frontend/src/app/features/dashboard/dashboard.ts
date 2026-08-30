import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { Produto } from '../produtos/produtos.model';
import { ProdutosService } from '../produtos/produtos.service';
import { DashboardResumo } from './dashboard.model';
import { DashboardService } from './dashboard.service';
import { GraficoBarras } from './graficos/grafico-barras/grafico-barras';
import { GraficoLinha } from './graficos/grafico-linha/grafico-linha';
import { GraficoRosca } from './graficos/grafico-rosca/grafico-rosca';

@Component({
  selector: 'app-dashboard',
  imports: [
    RouterLink,
    CurrencyPipe,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    GraficoBarras,
    GraficoLinha,
    GraficoRosca,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly produtosService = inject(ProdutosService);
  private readonly dashboardService = inject(DashboardService);

  protected readonly produtosParaRepor = signal<Produto[]>([]);
  protected readonly resumo = signal<DashboardResumo | null>(null);
  protected readonly carregando = signal(true);

  constructor() {
    this.produtosService.alertasEstoqueMinimo().subscribe((produtos) => {
      this.produtosParaRepor.set(produtos);
    });
    this.carregarResumo();
  }

  protected carregarResumo(): void {
    this.carregando.set(true);
    this.dashboardService.resumo().subscribe({
      next: (resumo) => {
        this.resumo.set(resumo);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }
}
