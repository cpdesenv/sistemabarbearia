import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PortalService } from '../portal.service';
import {
  PortalAgendamentoConfirmado,
  PortalAgendamentoRequest,
  PortalProfissional,
  PortalServico,
  PortalSlotDisponivel,
} from '../portal.model';

@Component({
  selector: 'app-portal-agendamento',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './portal-agendamento.html',
  styleUrl: './portal-agendamento.css',
})
export class PortalAgendamento {
  private readonly formBuilder = inject(FormBuilder);
  private readonly portalService = inject(PortalService);

  protected readonly carregando = signal(true);
  protected readonly portalIndisponivel = signal(false);
  protected readonly enviando = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly agendamentoConfirmado = signal<PortalAgendamentoConfirmado | null>(null);
  protected readonly resumoServicosConfirmados = computed(() => {
    const agendamento = this.agendamentoConfirmado();
    return agendamento ? agendamento.servicos.map((s) => s.nome).join(', ') : '';
  });

  protected readonly servicos = signal<PortalServico[]>([]);
  protected readonly servicosSelecionados = signal<Set<string>>(new Set());

  protected readonly profissionais = signal<PortalProfissional[]>([]);
  protected readonly carregandoProfissionais = signal(false);
  protected readonly profissionalSelecionado = signal<string | null>(null);

  protected readonly dataSelecionada = signal<string>('');
  protected readonly slots = signal<PortalSlotDisponivel[]>([]);
  protected readonly carregandoSlots = signal(false);
  protected readonly slotSelecionado = signal<PortalSlotDisponivel | null>(null);

  protected readonly valorTotalSelecionado = computed(() => {
    const uuids = this.servicosSelecionados();
    return this.servicos()
      .filter((s) => uuids.has(s.uuid))
      .reduce((total, s) => total + s.preco, 0);
  });

  protected readonly etapaProfissionalVisivel = computed(() => this.servicosSelecionados().size > 0);
  protected readonly etapaDataVisivel = computed(() => this.profissionalSelecionado() !== null);
  protected readonly etapaContatoVisivel = computed(() => this.slotSelecionado() !== null);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    nome: ['', [Validators.required]],
    telefone: ['', [Validators.required]],
    email: [''],
    consentimentoLgpd: [false, [Validators.requiredTrue]],
  });

  constructor() {
    this.portalService.status().subscribe({
      next: (status) => {
        if (!status.ativo) {
          this.portalIndisponivel.set(true);
          this.carregando.set(false);
          return;
        }
        this.portalService.listarServicos().subscribe({
          next: (servicos) => {
            this.servicos.set(servicos);
            this.carregando.set(false);
          },
          error: () => this.tratarIndisponivel(),
        });
      },
      error: () => this.tratarIndisponivel(),
    });
  }

  protected alternarServico(uuid: string): void {
    const atual = new Set(this.servicosSelecionados());
    if (atual.has(uuid)) {
      atual.delete(uuid);
    } else {
      atual.add(uuid);
    }
    this.servicosSelecionados.set(atual);
    this.profissionalSelecionado.set(null);
    this.profissionais.set([]);
    this.reiniciarDataEHorario();

    if (atual.size > 0) {
      this.carregandoProfissionais.set(true);
      this.portalService.listarProfissionais(Array.from(atual)).subscribe({
        next: (profissionais) => {
          this.profissionais.set(profissionais);
          this.carregandoProfissionais.set(false);
        },
        error: () => this.carregandoProfissionais.set(false),
      });
    }
  }

  protected selecionarProfissional(uuid: string): void {
    this.profissionalSelecionado.set(uuid);
    this.reiniciarDataEHorario();
  }

  protected consultarDisponibilidade(data: string): void {
    this.dataSelecionada.set(data);
    this.slotSelecionado.set(null);
    const profissionalUuid = this.profissionalSelecionado();
    if (!data || !profissionalUuid) {
      this.slots.set([]);
      return;
    }

    this.carregandoSlots.set(true);
    this.mensagemErro.set(null);
    this.portalService
      .disponibilidade(data, Array.from(this.servicosSelecionados()), profissionalUuid)
      .subscribe({
        next: (slots) => {
          this.slots.set(slots);
          this.carregandoSlots.set(false);
        },
        error: (erro: HttpErrorResponse) => {
          this.carregandoSlots.set(false);
          this.slots.set([]);
          this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível consultar os horários agora.');
        },
      });
  }

  protected selecionarSlot(slot: PortalSlotDisponivel): void {
    this.slotSelecionado.set(slot);
  }

  protected confirmar(): void {
    const slot = this.slotSelecionado();
    const profissionalUuid = this.profissionalSelecionado();
    if (!slot || !profissionalUuid || this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.mensagemErro.set('Preencha seus dados e aceite o consentimento para continuar.');
      return;
    }

    this.enviando.set(true);
    this.mensagemErro.set(null);

    const valores = this.formulario.getRawValue();
    const requisicao: PortalAgendamentoRequest = {
      nome: valores.nome.trim(),
      telefone: valores.telefone.trim(),
      email: valores.email.trim() === '' ? null : valores.email.trim(),
      profissionalUuid,
      servicoUuids: Array.from(this.servicosSelecionados()),
      inicio: slot.inicio,
      consentimentoLgpd: valores.consentimentoLgpd,
    };

    this.portalService.criarAgendamento(requisicao).subscribe({
      next: (agendamento) => {
        this.enviando.set(false);
        this.agendamentoConfirmado.set(agendamento);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        if (erro.status === 409) {
          this.mensagemErro.set(
            'Esse horário acabou de ser ocupado. Escolha outro horário abaixo.',
          );
          this.consultarDisponibilidade(this.dataSelecionada());
          this.slotSelecionado.set(null);
        } else {
          this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível agendar agora. Tente novamente.');
        }
      },
    });
  }

  private reiniciarDataEHorario(): void {
    this.dataSelecionada.set('');
    this.slots.set([]);
    this.slotSelecionado.set(null);
  }

  private tratarIndisponivel(): void {
    this.portalIndisponivel.set(true);
    this.carregando.set(false);
  }
}
