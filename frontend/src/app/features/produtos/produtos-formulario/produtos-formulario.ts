import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ProdutosService } from '../produtos.service';

@Component({
  selector: 'app-produtos-formulario',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './produtos-formulario.html',
  styleUrl: './produtos-formulario.css',
})
export class ProdutosFormulario {
  private readonly formBuilder = inject(FormBuilder);
  private readonly produtosService = inject(ProdutosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly uuid = this.route.snapshot.paramMap.get('uuid');
  protected readonly modoEdicao = this.uuid !== null;

  protected readonly carregando = signal(this.modoEdicao);
  protected readonly salvando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    nome: ['', [Validators.required]],
    descricao: [''],
    categoria: [''],
    unidade: ['UN'],
    precoVenda: [0, [Validators.required, Validators.min(0)]],
    precoCusto: [0, [Validators.min(0)]],
    estoqueMinimo: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    if (this.uuid) {
      this.produtosService.obter(this.uuid).subscribe((produto) => {
        this.formulario.setValue({
          nome: produto.nome,
          descricao: produto.descricao ?? '',
          categoria: produto.categoria ?? '',
          unidade: produto.unidade,
          precoVenda: produto.precoVenda,
          precoCusto: produto.precoCusto,
          estoqueMinimo: produto.estoqueMinimo,
        });
        this.carregando.set(false);
      });
    }
  }

  protected salvar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.mensagemErro.set(null);

    const valores = this.formulario.getRawValue();
    const requisicao = {
      nome: valores.nome,
      descricao: valores.descricao.trim() === '' ? null : valores.descricao.trim(),
      categoria: valores.categoria.trim() === '' ? null : valores.categoria.trim(),
      unidade: valores.unidade.trim() === '' ? null : valores.unidade.trim(),
      precoVenda: valores.precoVenda,
      precoCusto: valores.precoCusto,
      estoqueMinimo: valores.estoqueMinimo,
    };

    const operacao = this.uuid
      ? this.produtosService.atualizar(this.uuid, requisicao)
      : this.produtosService.criar(requisicao);

    operacao.subscribe({
      next: () => this.router.navigateByUrl('/produtos'),
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);
        this.mensagemErro.set(
          erro.status === 400
            ? 'Verifique os campos destacados e tente novamente.'
            : 'Nao foi possivel salvar agora. Tente novamente em instantes.',
        );
      },
    });
  }
}
