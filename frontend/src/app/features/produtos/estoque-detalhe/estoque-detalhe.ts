import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { MovimentoEstoque, Produto, RUTULOS_TIPO_MOVIMENTO } from '../produtos.model';
import { ProdutosService } from '../produtos.service';

@Component({
  selector: 'app-estoque-detalhe',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './estoque-detalhe.html',
  styleUrl: './estoque-detalhe.css',
})
export class EstoqueDetalhe {
  private readonly produtosService = inject(ProdutosService);
  private readonly route = inject(ActivatedRoute);

  private readonly uuid = this.route.snapshot.paramMap.get('uuid')!;

  protected readonly rotulosTipo = RUTULOS_TIPO_MOVIMENTO;
  protected readonly colunasMovimentos = ['tipo', 'quantidade', 'custoUnitario', 'motivo', 'criadoEm'];

  protected readonly carregando = signal(true);
  protected readonly executandoAcao = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly produto = signal<Produto | null>(null);
  protected readonly movimentos = signal<MovimentoEstoque[]>([]);
  protected readonly totalMovimentos = signal(0);
  protected readonly tamanhoPagina = signal(20);

  protected readonly entradaQuantidade = signal(1);
  protected readonly entradaCustoUnitario = signal<number | null>(null);
  protected readonly entradaMotivo = signal('');

  protected readonly ajusteNovaQuantidade = signal(0);
  protected readonly ajusteMotivo = signal('');

  private pagina = 0;

  constructor() {
    this.carregarProduto();
    this.carregarMovimentos();
  }

  protected rotuloTipo(tipo: MovimentoEstoque['tipo']): string {
    return this.rotulosTipo[tipo];
  }

  protected estaAbaixoDoMinimo(): boolean {
    const produto = this.produto();
    return !!produto && produto.estoqueAtual <= produto.estoqueMinimo;
  }

  protected registrarEntrada(): void {
    if (this.entradaQuantidade() < 1) {
      return;
    }
    this.executandoAcao.set(true);
    this.mensagemErro.set(null);
    this.produtosService
      .entradaEstoque(this.uuid, this.entradaQuantidade(), this.entradaCustoUnitario(),
        this.entradaMotivo().trim() === '' ? null : this.entradaMotivo().trim())
      .subscribe({
        next: (produto) => {
          this.produto.set(produto);
          this.entradaQuantidade.set(1);
          this.entradaCustoUnitario.set(null);
          this.entradaMotivo.set('');
          this.executandoAcao.set(false);
          this.carregarMovimentos();
        },
        error: (erro: HttpErrorResponse) => {
          this.executandoAcao.set(false);
          this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível registrar a entrada agora.');
        },
      });
  }

  protected registrarAjuste(): void {
    if (this.ajusteMotivo().trim() === '') {
      this.mensagemErro.set('Informe o motivo do ajuste.');
      return;
    }
    this.executandoAcao.set(true);
    this.mensagemErro.set(null);
    this.produtosService.ajusteEstoque(this.uuid, this.ajusteNovaQuantidade(), this.ajusteMotivo().trim()).subscribe({
      next: (produto) => {
        this.produto.set(produto);
        this.ajusteMotivo.set('');
        this.executandoAcao.set(false);
        this.carregarMovimentos();
      },
      error: (erro: HttpErrorResponse) => {
        this.executandoAcao.set(false);
        this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível registrar o ajuste agora.');
      },
    });
  }

  protected mudarPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanhoPagina.set(evento.pageSize);
    this.carregarMovimentos();
  }

  private carregarProduto(): void {
    this.carregando.set(true);
    this.produtosService.obter(this.uuid).subscribe({
      next: (produto) => {
        this.produto.set(produto);
        this.ajusteNovaQuantidade.set(produto.estoqueAtual);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  private carregarMovimentos(): void {
    this.produtosService.movimentos(this.uuid, this.pagina, this.tamanhoPagina()).subscribe((pagina) => {
      this.movimentos.set(pagina.content);
      this.totalMovimentos.set(pagina.page.totalElements);
      const produtoAtual = this.produto();
      if (produtoAtual) {
        this.ajusteNovaQuantidade.set(produtoAtual.estoqueAtual);
      }
    });
  }
}
