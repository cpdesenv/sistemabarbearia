import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { Produto } from '../produtos.model';
import { ProdutosService } from '../produtos.service';

@Component({
  selector: 'app-estoque-lista',
  imports: [RouterLink, MatButtonModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './estoque-lista.html',
  styleUrl: './estoque-lista.css',
})
export class EstoqueLista {
  private readonly produtosService = inject(ProdutosService);

  protected readonly colunas = ['nome', 'categoria', 'estoqueAtual', 'estoqueMinimo', 'acoes'];
  protected readonly carregando = signal(true);
  protected readonly produtos = signal<Produto[]>([]);

  constructor() {
    this.produtosService.listar({ ativo: true, size: 200 }).subscribe((pagina) => {
      this.produtos.set(pagina.content);
      this.carregando.set(false);
    });
  }

  protected estaAbaixoDoMinimo(produto: Produto): boolean {
    return produto.estoqueAtual <= produto.estoqueMinimo;
  }
}
