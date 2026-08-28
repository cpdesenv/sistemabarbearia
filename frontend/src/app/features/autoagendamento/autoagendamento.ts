import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AutoagendamentoService } from './autoagendamento.service';
import {
  AgendamentoConfirmado,
  ConfiguracaoAutoagendamento,
  ProfissionalPublico,
  ServicoPublico,
  SlotDisponivel,
} from './autoagendamento.model';

type Etapa = 'carregando' | 'indisponivel' | 'servicos' | 'profissional' | 'horario' | 'dados' | 'confirmado';

@Component({
  selector: 'app-autoagendamento',
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
  templateUrl: './autoagendamento.html',
  styleUrl: './autoagendamento.css',
})
export class Autoagendamento {
  private readonly formBuilder = inject(FormBuilder);
  private readonly autoagendamentoService = inject(AutoagendamentoService);

  protected readonly etapa = signal<Etapa>('carregando');
  protected readonly configuracao = signal<ConfiguracaoAutoagendamento | null>(null);
  protected readonly erro = signal<string | null>(null);

  protected readonly servicos = signal<ServicoPublico[]>([]);
  protected readonly servicosSelecionados = signal<Set<string>>(new Set());
  protected readonly temServicoSelecionado = computed(() => this.servicosSelecionados().size > 0);

  protected readonly profissionais = signal<ProfissionalPublico[]>([]);
  protected readonly profissionalSelecionado = signal<ProfissionalPublico | null>(null);

  protected readonly data = signal<string>(new Date().toISOString().slice(0, 10));
  protected readonly slots = signal<SlotDisponivel[]>([]);
  protected readonly slotSelecionado = signal<SlotDisponivel | null>(null);
  protected readonly carregandoSlots = signal(false);

  protected readonly enviando = signal(false);
  protected readonly agendamentoConfirmado = signal<AgendamentoConfirmado | null>(null);

  protected readonly formularioDados = this.formBuilder.nonNullable.group({
    nome: ['', [Validators.required]],
    telefone: ['', [Validators.required]],
    email: [''],
    consentimentoLgpd: [false, [Validators.requiredTrue]],
  });

  constructor() {
    this.autoagendamentoService.obterConfiguracao().subscribe({
      next: (configuracao) => {
        this.configuracao.set(configuracao);
        if (!configuracao.ativo) {
          this.etapa.set('indisponivel');
          return;
        }
        this.carregarServicosEProfissionais();
      },
      error: () => this.etapa.set('indisponivel'),
    });
  }

  private carregarServicosEProfissionais(): void {
    this.autoagendamentoService.consultarServicos().subscribe({
      next: (servicos) => this.servicos.set(servicos),
      error: () => this.erro.set('Não foi possível carregar os serviços.'),
    });
    this.autoagendamentoService.consultarProfissionais().subscribe({
      next: (profissionais) => this.profissionais.set(profissionais),
      error: () => this.erro.set('Não foi possível carregar os profissionais.'),
    });
    this.etapa.set('servicos');
  }

  protected alternarServico(uuid: string): void {
    const selecionados = new Set(this.servicosSelecionados());
    if (selecionados.has(uuid)) {
      selecionados.delete(uuid);
    } else {
      selecionados.add(uuid);
    }
    this.servicosSelecionados.set(selecionados);
  }

  protected irParaProfissional(): void {
    this.etapa.set('profissional');
  }

  protected selecionarProfissional(profissional: ProfissionalPublico): void {
    this.profissionalSelecionado.set(profissional);
    this.etapa.set('horario');
    this.consultarDisponibilidade();
  }

  protected onDataAlterada(novaData: string): void {
    this.data.set(novaData);
    this.slotSelecionado.set(null);
    this.consultarDisponibilidade();
  }

  private consultarDisponibilidade(): void {
    const profissional = this.profissionalSelecionado();
    if (!profissional) {
      return;
    }
    this.carregandoSlots.set(true);
    this.slots.set([]);
    this.autoagendamentoService
      .consultarDisponibilidade(this.data(), Array.from(this.servicosSelecionados()), profissional.uuid)
      .subscribe({
        next: (slots) => {
          this.slots.set(slots);
          this.carregandoSlots.set(false);
        },
        error: () => {
          this.erro.set('Não foi possível consultar os horários disponíveis.');
          this.carregandoSlots.set(false);
        },
      });
  }

  protected selecionarSlot(slot: SlotDisponivel): void {
    this.slotSelecionado.set(slot);
    this.etapa.set('dados');
  }

  protected confirmar(): void {
    if (this.formularioDados.invalid) {
      this.formularioDados.markAllAsTouched();
      return;
    }
    const slot = this.slotSelecionado();
    if (!slot) {
      return;
    }

    this.enviando.set(true);
    this.erro.set(null);
    const valores = this.formularioDados.getRawValue();

    this.autoagendamentoService
      .agendar({
        nome: valores.nome,
        telefone: valores.telefone,
        email: valores.email.trim() === '' ? null : valores.email,
        consentimentoLgpd: valores.consentimentoLgpd,
        profissionalUuid: slot.profissionalUuid,
        servicoUuids: Array.from(this.servicosSelecionados()),
        inicio: slot.inicio,
      })
      .subscribe({
        next: (agendamento) => {
          this.agendamentoConfirmado.set(agendamento);
          this.etapa.set('confirmado');
          this.enviando.set(false);
        },
        error: (erro: HttpErrorResponse) => {
          this.enviando.set(false);
          this.erro.set(
            erro.status === 400 || erro.status === 409
              ? erro.error?.mensagem ?? 'Verifique os dados informados e tente novamente.'
              : 'Não foi possível concluir o agendamento agora. Tente novamente em instantes.',
          );
        },
      });
  }

  protected voltarParaServicos(): void {
    this.etapa.set('servicos');
  }

  protected voltarParaProfissional(): void {
    this.etapa.set('profissional');
  }

  protected voltarParaHorario(): void {
    this.etapa.set('horario');
  }

  protected nomesServicos(agendamento: AgendamentoConfirmado): string {
    return agendamento.servicos.map((s) => s.nome).join(', ');
  }
}
