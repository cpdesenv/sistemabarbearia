import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';

import { Produto } from '../produtos/produtos.model';
import { ProdutosService } from '../produtos/produtos.service';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MatCardModule],
  templateUrl: './dashboard.html'
})
export class Dashboard {
  private readonly produtosService = inject(ProdutosService);

  protected readonly produtosParaRepor = signal<Produto[]>([]);

  constructor() {
    this.produtosService.alertasEstoqueMinimo().subscribe((produtos) => {
      this.produtosParaRepor.set(produtos);
    });
  }
}
