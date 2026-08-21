import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../core/auth/auth.service';
import { Profissional } from '../profissionais/profissionais.model';
import { ProfissionaisService } from '../profissionais/profissionais.service';
import { Agendamento, RUTULOS_STATUS, StatusAgendamento } from './agenda.model';
import { AgendaService } from './agenda.service';

interface BlocoAgendamento {
  agendamento: Agendamento;
  topoPx: number;
  alturaPx: number;
}

interface DiaSemana {
  data: Date;
  rotulo: string;
  agendamentos: Agendamento[];
}

const ALTURA_LINHA_PX = 32;
const MINUTOS_POR_LINHA = 30;
const HORA_GRADE_INICIO = 7;
const HORA_GRADE_FIM = 21;

@Component({
  selector: 'app-agenda',
  imports: [DatePipe, MatButtonModule, MatButtonToggleModule, MatProgressSpinnerModule],
  templateUrl: './agenda.html',
  styleUrl: './agenda.css',
})
export class Agenda {
  private readonly agendaService = inject(AgendaService);
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly rotulosStatus = RUTULOS_STATUS;
  protected readonly linhas = Array.from(
    { length: ((HORA_GRADE_FIM - HORA_GRADE_INICIO) * 60) / MINUTOS_POR_LINHA },
    (_, i) => i,
  );
  protected readonly alturaGradePx = this.linhas.length * ALTURA_LINHA_PX;

  protected readonly visao = signal<'dia' | 'semana'>('dia');
  protected readonly dataSelecionada = signal(iniciarNoMeioDia(new Date()));
  protected readonly profissionais = signal<Profissional[]>([]);
  protected readonly agendamentos = signal<Agendamento[]>([]);
  protected readonly carregando = signal(true);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly rotuloData = computed(() =>
    this.dataSelecionada().toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }),
  );

  protected readonly diasDaSemana = computed<DiaSemana[]>(() => {
    const inicioSemana = inicioDaSemana(this.dataSelecionada());
    return Array.from({ length: 7 }, (_, i) => {
      const data = new Date(inicioSemana);
      data.setDate(data.getDate() + i);
      const agendamentosDoDia = this.agendamentos()
        .filter((ag) => mesmoDia(new Date(ag.inicio), data))
        .sort((a, b) => a.inicio.localeCompare(b.inicio));
      return {
        data,
        rotulo: data.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' }),
        agendamentos: agendamentosDoDia,
      };
    });
  });

  constructor() {
    this.profissionaisService.listar({ ativo: true, size: 100 }).subscribe((pagina) => {
      this.profissionais.set(pagina.content);
    });
    this.carregarAgendamentos();
  }

  protected podeGerenciar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE' || perfil === 'RECEPCAO';
  }

  protected mudarVisao(visao: 'dia' | 'semana'): void {
    this.visao.set(visao);
    this.carregarAgendamentos();
  }

  protected navegar(passo: number): void {
    const nova = new Date(this.dataSelecionada());
    nova.setDate(nova.getDate() + passo * (this.visao() === 'semana' ? 7 : 1));
    this.dataSelecionada.set(nova);
    this.carregarAgendamentos();
  }

  protected irParaHoje(): void {
    this.dataSelecionada.set(iniciarNoMeioDia(new Date()));
    this.carregarAgendamentos();
  }

  protected blocosDoProfissional(profissional: Profissional): BlocoAgendamento[] {
    return this.agendamentos()
      .filter((ag) => ag.profissionalUuid === profissional.uuid && mesmoDia(new Date(ag.inicio),
        this.dataSelecionada()))
      .map((agendamento) => {
        const inicio = new Date(agendamento.inicio);
        const fim = new Date(agendamento.fim);
        const minutosInicio = minutosDesdeInicioGrade(inicio);
        const minutosFim = Math.max(minutosInicio + MINUTOS_POR_LINHA, minutosDesdeInicioGrade(fim));
        return {
          agendamento,
          topoPx: (minutosInicio / MINUTOS_POR_LINHA) * ALTURA_LINHA_PX,
          alturaPx: ((minutosFim - minutosInicio) / MINUTOS_POR_LINHA) * ALTURA_LINHA_PX,
        };
      });
  }

  protected rotuloLinha(indice: number): string {
    const minutos = HORA_GRADE_INICIO * 60 + indice * MINUTOS_POR_LINHA;
    const hora = Math.floor(minutos / 60)
      .toString()
      .padStart(2, '0');
    const min = (minutos % 60).toString().padStart(2, '0');
    return `${hora}:${min}`;
  }

  protected corDeFundo(status: StatusAgendamento, cor: string): string {
    if (status === 'CANCELADO' || status === 'NAO_COMPARECEU') {
      return '#9e9e9e';
    }
    return cor;
  }

  protected clicarCelulaVazia(profissional: Profissional, indiceLinha: number): void {
    if (!this.podeGerenciar()) {
      return;
    }
    const inicio = new Date(this.dataSelecionada());
    const minutos = HORA_GRADE_INICIO * 60 + indiceLinha * MINUTOS_POR_LINHA;
    inicio.setHours(Math.floor(minutos / 60), minutos % 60, 0, 0);
    this.router.navigate(['/agenda/novo'], {
      queryParams: { profissionalUuid: profissional.uuid, inicio: inicio.toISOString() },
    });
  }

  protected abrirAgendamento(uuid: string): void {
    this.router.navigate(['/agenda', uuid, 'editar']);
  }

  protected aoIniciarArrasto(evento: DragEvent, agendamento: Agendamento): void {
    if (!this.podeGerenciar() || !['AGENDADO', 'CONFIRMADO'].includes(agendamento.status)) {
      evento.preventDefault();
      return;
    }
    evento.dataTransfer?.setData('text/plain', agendamento.uuid);
  }

  protected aoSoltar(evento: DragEvent, profissional: Profissional, indiceLinha: number): void {
    evento.preventDefault();
    const uuid = evento.dataTransfer?.getData('text/plain');
    const agendamento = this.agendamentos().find((ag) => ag.uuid === uuid);
    if (!agendamento) {
      return;
    }

    const novoInicio = new Date(this.dataSelecionada());
    const minutos = HORA_GRADE_INICIO * 60 + indiceLinha * MINUTOS_POR_LINHA;
    novoInicio.setHours(Math.floor(minutos / 60), minutos % 60, 0, 0);

    this.mensagemErro.set(null);
    this.agendaService
      .alterar(agendamento.uuid, {
        clienteUuid: agendamento.clienteUuid,
        profissionalUuid: profissional.uuid,
        servicoUuids: agendamento.servicos.map((s) => s.servicoUuid),
        inicio: novoInicio.toISOString(),
        observacao: agendamento.observacao,
      })
      .subscribe({
        next: () => this.carregarAgendamentos(),
        error: () => this.mensagemErro.set('Não foi possível remarcar para esse horário. Tente outro horário.'),
      });
  }

  private carregarAgendamentos(): void {
    this.carregando.set(true);
    const referencia = this.dataSelecionada();
    const inicioIntervalo =
      this.visao() === 'semana' ? inicioDaSemana(referencia) : new Date(referencia.setHours(0, 0, 0, 0));
    const fimIntervalo = new Date(inicioIntervalo);
    fimIntervalo.setDate(fimIntervalo.getDate() + (this.visao() === 'semana' ? 7 : 1));
    fimIntervalo.setMilliseconds(-1);

    this.agendaService.listar({ de: inicioIntervalo.toISOString(), ate: fimIntervalo.toISOString() }).subscribe({
      next: (lista) => {
        this.agendamentos.set(lista);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }
}

function iniciarNoMeioDia(data: Date): Date {
  const copia = new Date(data);
  copia.setHours(12, 0, 0, 0);
  return copia;
}

function inicioDaSemana(data: Date): Date {
  const copia = new Date(data);
  copia.setHours(0, 0, 0, 0);
  const diferenca = (copia.getDay() + 6) % 7; // segunda-feira = inicio da semana
  copia.setDate(copia.getDate() - diferenca);
  return copia;
}

function mesmoDia(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function minutosDesdeInicioGrade(data: Date): number {
  return (data.getHours() - HORA_GRADE_INICIO) * 60 + data.getMinutes();
}
