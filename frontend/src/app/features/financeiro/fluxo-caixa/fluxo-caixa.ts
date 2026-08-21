import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FluxoCaixa } from '../financeiro.model';
import { FinanceiroService } from '../financeiro.service';

@Component({
  selector: 'app-fluxo-caixa',
  imports: [CurrencyPipe, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './fluxo-caixa.html',
  styleUrl: './fluxo-caixa.css',
})
export class FluxoCaixaComponent {
  private readonly financeiroService = inject(FinanceiroService);

  protected readonly carregando = signal(true);
  protected readonly fluxo = signal<FluxoCaixa | null>(null);

  constructor() {
    this.financeiroService.fluxoCaixa().subscribe({
      next: (fluxo) => {
        this.fluxo.set(fluxo);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }
}
