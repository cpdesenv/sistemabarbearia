import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { Servico } from '../../servicos/servicos.model';
import { ServicosService } from '../../servicos/servicos.service';
import { ServicoVinculado } from '../profissionais.model';
import { ProfissionaisService } from '../profissionais.service';

type GrupoServico = FormGroup<{
  selecionado: FormControl<boolean>;
  comissaoPercentual: FormControl<number | null>;
}>;

interface ServicoSelecionavel {
  servico: Servico;
  grupo: GrupoServico;
}

function criarGrupoServico(vinculo: ServicoVinculado | undefined): GrupoServico {
  return new FormGroup({
    selecionado: new FormControl(!!vinculo, { nonNullable: true }),
    comissaoPercentual: new FormControl<number | null>(vinculo?.comissaoPercentual ?? null),
  });
}

@Component({
  selector: 'app-profissionais-formulario',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './profissionais-formulario.html',
  styleUrl: './profissionais-formulario.css',
})
export class ProfissionaisFormulario {
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly servicosService = inject(ServicosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly uuid = this.route.snapshot.paramMap.get('uuid');
  protected readonly modoEdicao = this.uuid !== null;

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly servicosSelecionaveis = signal<ServicoSelecionavel[]>([]);

  protected readonly formulario = new FormGroup({
    nome: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true }),
    telefone: new FormControl('', { nonNullable: true }),
    corAgenda: new FormControl('#3F51B5', { nonNullable: true, validators: [Validators.required] }),
    comissaoPercentualPadrao: new FormControl(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.max(100)],
    }),
  });

  constructor() {
    const servicos$ = this.servicosService.listar({ ativo: true, size: 200 });
    const vinculos$ = this.uuid ? this.profissionaisService.listarServicosVinculados(this.uuid) : of([]);
    const profissional$ = this.uuid ? this.profissionaisService.obter(this.uuid) : of(null);

    forkJoin([servicos$, vinculos$, profissional$]).subscribe(([paginaServicos, vinculos, profissional]) => {
      if (profissional) {
        this.formulario.setValue({
          nome: profissional.nome,
          email: profissional.email ?? '',
          telefone: profissional.telefone ?? '',
          corAgenda: profissional.corAgenda,
          comissaoPercentualPadrao: profissional.comissaoPercentualPadrao,
        });
      }

      const vinculosPorUuid = new Map(vinculos.map((vinculo) => [vinculo.servicoUuid, vinculo]));
      this.servicosSelecionaveis.set(
        paginaServicos.content.map((servico) => ({
          servico,
          grupo: criarGrupoServico(vinculosPorUuid.get(servico.uuid)),
        })),
      );

      this.carregando.set(false);
    });
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
      email: valores.email.trim() === '' ? null : valores.email.trim(),
      telefone: valores.telefone.trim() === '' ? null : valores.telefone.trim(),
      corAgenda: valores.corAgenda,
      comissaoPercentualPadrao: valores.comissaoPercentualPadrao,
    };

    const operacaoSalvar = this.uuid
      ? this.profissionaisService.atualizar(this.uuid, requisicao)
      : this.profissionaisService.criar(requisicao);

    operacaoSalvar.subscribe({
      next: (profissional) => {
        const vinculosSelecionados = this.servicosSelecionaveis()
          .filter((item) => item.grupo.controls.selecionado.value)
          .map((item) => ({
            servicoUuid: item.servico.uuid,
            comissaoPercentual: item.grupo.controls.comissaoPercentual.value,
          }));

        this.profissionaisService.sincronizarServicos(profissional.uuid, vinculosSelecionados).subscribe({
          next: () => this.router.navigateByUrl('/profissionais'),
          error: () => {
            this.salvando.set(false);
            this.mensagemErro.set('Profissional salvo, mas houve um erro ao atualizar os serviços vinculados.');
          },
        });
      },
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
