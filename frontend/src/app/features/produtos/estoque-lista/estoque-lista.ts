import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { Produto } from '../produtos.model';
import { ProdutosService } from '../produtos.service';

@Component({
  selector: 'app-estoque-lista',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './estoque-lista.html',
  styleUrl: './estoque-lista.css',
})
export class EstoqueLista {
  private readonly produtosService = inject(ProdutosService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly colunas = ['nome', 'categoria', 'estoqueAtual', 'estoqueMinimo', 'acoes'];
  protected readonly produtos = signal<Produto[]>([]);
  protected readonly totalElementos = signal(0);
  protected readonly tamanhoPagina = signal(20);
  protected readonly carregando = signal(true);

  protected readonly filtro = this.formBuilder.nonNullable.group({
    nome: [''],
    apenasAtivos: [false],
  });

  private pagina = 0;

  constructor() {
    this.buscar();
  }

  protected estaAbaixoDoMinimo(produto: Produto): boolean {
    return produto.estoqueAtual <= produto.estoqueMinimo;
  }

  protected buscar(): void {
    this.pagina = 0;
    this.carregarPagina();
  }

  protected mudarPagina(evento: PageEvent): void {
    this.pagina = evento.pageIndex;
    this.tamanhoPagina.set(evento.pageSize);
    this.carregarPagina();
  }

  private carregarPagina(): void {
    this.carregando.set(true);
    const valores = this.filtro.getRawValue();

    this.produtosService
      .listar({
        nome: valores.nome || undefined,
        ativo: valores.apenasAtivos ? true : undefined,
        page: this.pagina,
        size: this.tamanhoPagina(),
      })
      .subscribe((resposta) => {
        this.produtos.set(resposta.content);
        this.totalElementos.set(resposta.page.totalElements);
        this.carregando.set(false);
      });
  }
}
