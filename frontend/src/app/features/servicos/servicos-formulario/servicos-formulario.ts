import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ServicosService } from '../servicos.service';

@Component({
  selector: 'app-servicos-formulario',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './servicos-formulario.html',
  styleUrl: './servicos-formulario.css',
})
export class ServicosFormulario {
  private readonly formBuilder = inject(FormBuilder);
  private readonly servicosService = inject(ServicosService);
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
    preco: [0, [Validators.required, Validators.min(0.01)]],
    duracaoMinutos: [30, [Validators.required, Validators.min(1)]],
  });

  constructor() {
    if (this.uuid) {
      this.servicosService.obter(this.uuid).subscribe((servico) => {
        this.formulario.setValue({
          nome: servico.nome,
          descricao: servico.descricao ?? '',
          categoria: servico.categoria ?? '',
          preco: servico.preco,
          duracaoMinutos: servico.duracaoMinutos,
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
      preco: valores.preco,
      duracaoMinutos: valores.duracaoMinutos,
    };

    const operacao = this.uuid
      ? this.servicosService.atualizar(this.uuid, requisicao)
      : this.servicosService.criar(requisicao);

    operacao.subscribe({
      next: () => this.router.navigateByUrl('/servicos'),
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
