import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal, ViewChild } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
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

/**
 * Paleta curada para "Cor na agenda", em vez de um seletor de cor livre.
 * Os 16 tons cobrem a roda de cores inteira (nao so' azul/ciano/indigo
 * como antes), gerados com a mesma formula HSL (saturacao 55%, luminancia
 * 78%) para manter o mesmo "peso" visual entre eles, e todos conferidos
 * por calculo de contraste (nao so' inspecao visual): o pior caso da
 * paleta (Indigo) fica em 8.06:1 contra --ink, folgado acima do minimo AA
 * de 4,5:1. Nenhum desses tons passaria AA contra texto branco, por isso
 * os blocos da agenda usam texto escuro (ver agenda.css) em vez de branco
 * - se um dia a paleta precisar de tons escuros/saturados, os blocos
 * precisam antes calcular a cor do texto pelo contraste (nao mais fixo).
 */
export const PALETA_CORES_AGENDA: { cor: string; nome: string }[] = [
  { cor: '#E6A8A8', nome: 'Vermelho' },
  { cor: '#E6C2A8', nome: 'Laranja' },
  { cor: '#E6D6A8', nome: 'Âmbar' },
  { cor: '#E1E6A8', nome: 'Amarelo' },
  { cor: '#BDE6A8', nome: 'Lima' },
  { cor: '#A8E6B2', nome: 'Verde' },
  { cor: '#A8E6D1', nome: 'Verde-menta' },
  { cor: '#A8E1E6', nome: 'Turquesa' },
  { cor: '#A8CCE6', nome: 'Ciano' },
  { cor: '#A8BDE6', nome: 'Azul-celeste' },
  { cor: '#A8ADE6', nome: 'Azul' },
  { cor: '#B2A8E6', nome: 'Indigo' },
  { cor: '#C7A8E6', nome: 'Violeta' },
  { cor: '#DBA8E6', nome: 'Roxo' },
  { cor: '#E6A8D6', nome: 'Magenta' },
  { cor: '#E6A8BD', nome: 'Rosa' },
];

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
    MatMenuModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './profissionais-formulario.html',
  styleUrl: './profissionais-formulario.css',
})
export class ProfissionaisFormulario {
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly servicosService = inject(ServicosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly confirmDialog = inject(ConfirmDialogService);

  @ViewChild('corTrigger') private corTrigger?: MatMenuTrigger;

  protected readonly diasSemana = DIAS_SEMANA;
  protected readonly paletaCores = PALETA_CORES_AGENDA;
  protected readonly colunasServicos = ['servico', 'comissao'];
  protected readonly colunasJanelas = ['selecionar', 'dia', 'inicio', 'fim'];

  private readonly uuid = this.route.snapshot.paramMap.get('uuid');
  protected readonly modoEdicao = this.uuid !== null;

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly servicosSelecionaveis = signal<ServicoSelecionavel[]>([]);
  protected readonly janelas = new FormArray<GrupoJanela>([]);
  /**
   * O mat-table so' reage a uma NOVA referencia de array no [dataSource] -
   * FormArray.push()/removeAt() mutam janelas.controls no lugar (mesma
   * referencia), entao bindar [dataSource]="janelas.controls" direto faz
   * a tabela parar de atualizar depois da primeira adicao (o @if/@else em
   * volta da tabela mascarava isso: a troca 0->1 forca a criacao de uma
   * table nova do zero, entao so' a PRIMEIRA janela aparecia certo).
   * Este signal e' recriado (nao mutado) toda vez que janelas muda.
   */
  protected readonly janelasVisiveis = signal<GrupoJanela[]>([]);
  protected readonly janelasSelecionadas = signal<Set<number>>(new Set());

  private sincronizarJanelasVisiveis(): void {
    this.janelasVisiveis.set([...this.janelas.controls]);
  }

  protected readonly formulario = new FormGroup({
    nome: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true }),
    telefone: new FormControl('', { nonNullable: true }),
    corAgenda: new FormControl(PALETA_CORES_AGENDA[0].cor, { nonNullable: true, validators: [Validators.required] }),
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
        this.sincronizarJanelasVisiveis();

        this.carregando.set(false);
      },
    );
  }

  protected selecionarCor(cor: string): void {
    this.formulario.controls.corAgenda.setValue(cor);
    this.corTrigger?.closeMenu();
  }

  protected corSelecionadaNome(): string {
    const cor = this.formulario.controls.corAgenda.value;
    return this.paletaCores.find((item) => item.cor === cor)?.nome ?? 'Selecionar cor';
  }

  protected adicionarJanela(): void {
    this.janelas.push(criarGrupoJanela());
    this.sincronizarJanelasVisiveis();
  }

  protected estaJanelaSelecionada(indice: number): boolean {
    return this.janelasSelecionadas().has(indice);
  }

  protected alternarSelecaoJanela(indice: number): void {
    const atual = new Set(this.janelasSelecionadas());
    if (atual.has(indice)) {
      atual.delete(indice);
    } else {
      atual.add(indice);
    }
    this.janelasSelecionadas.set(atual);
  }

  protected todasJanelasSelecionadas(): boolean {
    return this.janelas.length > 0 && this.janelasSelecionadas().size === this.janelas.length;
  }

  protected algumaJanelaSelecionada(): boolean {
    const total = this.janelasSelecionadas().size;
    return total > 0 && total < this.janelas.length;
  }

  protected alternarSelecaoTodasJanelas(): void {
    this.janelasSelecionadas.set(
      this.todasJanelasSelecionadas() ? new Set() : new Set(this.janelas.controls.map((_, indice) => indice)),
    );
  }

  protected excluirJanelasSelecionadas(): void {
    const quantidade = this.janelasSelecionadas().size;
    if (quantidade === 0) {
      return;
    }

    this.confirmDialog
      .confirm({
        title: 'Excluir grade de horários',
        message:
          quantidade === 1
            ? 'Remover a janela de horário selecionada?'
            : `Remover as ${quantidade} janelas de horário selecionadas?`,
        confirmLabel: 'Excluir grade',
        danger: true,
      })
      .subscribe((resultado) => {
        if (!resultado.confirmed) {
          return;
        }

        [...this.janelasSelecionadas()]
          .sort((a, b) => b - a)
          .forEach((indice) => this.janelas.removeAt(indice));
        this.janelasSelecionadas.set(new Set());
        this.sincronizarJanelasVisiveis();
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
