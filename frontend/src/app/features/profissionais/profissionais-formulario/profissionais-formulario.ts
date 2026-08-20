import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { Servico } from '../../servicos/servicos.model';
import { ServicosService } from '../../servicos/servicos.service';
import { JanelaHorario, ServicoVinculado } from '../profissionais.model';
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

type GrupoJanela = FormGroup<{
  diaSemana: FormControl<number>;
  horaInicio: FormControl<string>;
  horaFim: FormControl<string>;
}>;

function criarGrupoJanela(janela?: JanelaHorario): GrupoJanela {
  return new FormGroup({
    diaSemana: new FormControl(janela?.diaSemana ?? 1, { nonNullable: true }),
    horaInicio: new FormControl(normalizarHora(janela?.horaInicio) ?? '09:00', { nonNullable: true }),
    horaFim: new FormControl(normalizarHora(janela?.horaFim) ?? '18:00', { nonNullable: true }),
  });
}

/** A API pode retornar "HH:mm:ss"; o input type="time" espera "HH:mm". */
function normalizarHora(hora: string | undefined): string | undefined {
  return hora?.slice(0, 5);
}

export const DIAS_SEMANA = [
  { valor: 1, rotulo: 'Segunda' },
  { valor: 2, rotulo: 'Terça' },
  { valor: 3, rotulo: 'Quarta' },
  { valor: 4, rotulo: 'Quinta' },
  { valor: 5, rotulo: 'Sexta' },
  { valor: 6, rotulo: 'Sábado' },
  { valor: 7, rotulo: 'Domingo' },
];

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
    MatSelectModule,
  ],
  templateUrl: './profissionais-formulario.html',
  styleUrl: './profissionais-formulario.css',
})
export class ProfissionaisFormulario {
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly servicosService = inject(ServicosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly diasSemana = DIAS_SEMANA;

  private readonly uuid = this.route.snapshot.paramMap.get('uuid');
  protected readonly modoEdicao = this.uuid !== null;

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly servicosSelecionaveis = signal<ServicoSelecionavel[]>([]);
  protected readonly janelas = new FormArray<GrupoJanela>([]);

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
    const grade$ = this.uuid ? this.profissionaisService.obterGradeHoraria(this.uuid) : of([]);

    forkJoin([servicos$, vinculos$, profissional$, grade$]).subscribe(
      ([paginaServicos, vinculos, profissional, grade]) => {
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

        grade.forEach((janela) => this.janelas.push(criarGrupoJanela(janela)));

        this.carregando.set(false);
      },
    );
  }

  protected adicionarJanela(): void {
    this.janelas.push(criarGrupoJanela());
  }

  protected removerJanela(indice: number): void {
    this.janelas.removeAt(indice);
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

        const janelasParaSalvar: JanelaHorario[] = this.janelas.controls.map((grupo) => grupo.getRawValue());

        forkJoin([
          this.profissionaisService.sincronizarServicos(profissional.uuid, vinculosSelecionados),
          this.profissionaisService.sincronizarGradeHoraria(profissional.uuid, janelasParaSalvar),
        ]).subscribe({
          next: () => this.router.navigateByUrl('/profissionais'),
          error: (erro: HttpErrorResponse) => {
            this.salvando.set(false);
            this.mensagemErro.set(
              erro.status === 400
                ? 'Profissional salvo, mas a grade de horários tem um erro: verifique se as janelas não se sobrepõem.'
                : 'Profissional salvo, mas houve um erro ao atualizar serviços/horários vinculados.',
            );
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
