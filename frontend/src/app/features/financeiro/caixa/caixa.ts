import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { CaixaDoDia, FormaPagamento, RUTULOS_FORMA_PAGAMENTO } from '../financeiro.model';
import { FinanceiroService } from '../financeiro.service';

@Component({
  selector: 'app-caixa',
  imports: [
    CurrencyPipe,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './caixa.html',
  styleUrl: './caixa.css',
})
export class Caixa {
  private readonly financeiroService = inject(FinanceiroService);

  protected readonly rotulosFormaPagamento = RUTULOS_FORMA_PAGAMENTO;
  protected readonly colunasFormaPagamento = ['formaPagamento', 'total'];
  protected readonly colunasProfissional = ['profissionalNome', 'totalFaturado', 'totalComissao'];

  protected readonly data = signal(formatarDataHoje());
  protected readonly carregando = signal(true);
  protected readonly caixa = signal<CaixaDoDia | null>(null);

  constructor() {
    this.carregar();
  }

  protected rotuloForma(forma: FormaPagamento): string {
    return this.rotulosFormaPagamento[forma];
  }

  protected mudarData(novaData: string): void {
    this.data.set(novaData);
    this.carregar();
  }

  private carregar(): void {
    this.carregando.set(true);
    this.financeiroService.caixaDoDia(this.data()).subscribe({
      next: (caixa) => {
        this.caixa.set(caixa);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }
}

function formatarDataHoje(): string {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
}
