import { CurrencyPipe } from '@angular/common';
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

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { Produto } from '../produtos.model';
import { ProdutosService } from '../produtos.service';

@Component({
  selector: 'app-produtos-lista',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './produtos-lista.html',
  styleUrl: './produtos-lista.css',
})
export class ProdutosLista {
  private readonly produtosService = inject(ProdutosService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  protected readonly colunas = ['nome', 'categoria', 'precoVenda', 'estoque', 'status', 'acoes'];
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

  protected podeGerenciar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE';
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

  protected alternarStatus(produto: Produto): void {
    if (!produto.ativo) {
      this.produtosService.atualizarStatus(produto.uuid, true).subscribe(() => this.carregarPagina());
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Desativar produto',
        message: `Desativar "${produto.nome}"? Ele deixa de aparecer para venda, mas pode ser reativado depois.`,
        confirmLabel: 'Desativar',
        danger: true,
      })
      .subscribe((resultado) => {
        if (resultado.confirmed) {
          this.produtosService.atualizarStatus(produto.uuid, false).subscribe(() => this.carregarPagina());
        }
      });
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
