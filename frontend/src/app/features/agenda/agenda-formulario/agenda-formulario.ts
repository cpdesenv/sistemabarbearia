import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { Cliente } from '../../clientes/clientes.model';
import { ClientesService } from '../../clientes/clientes.service';
import { FinanceiroService } from '../../financeiro/financeiro.service';
import { Profissional } from '../../profissionais/profissionais.model';
import { ProfissionaisService } from '../../profissionais/profissionais.service';
import { Servico } from '../../servicos/servicos.model';
import { ServicosService } from '../../servicos/servicos.service';
import { Agendamento, RUTULOS_STATUS, SalvarAgendamentoRequest } from '../agenda.model';
import { AgendaService } from '../agenda.service';

@Component({
  selector: 'app-agenda-formulario',
  imports: [
    CurrencyPipe,
    FormsModule,
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
  templateUrl: './agenda-formulario.html',
  styleUrl: './agenda-formulario.css',
})
export class AgendaFormulario {
  private readonly formBuilder = inject(FormBuilder);
  private readonly agendaService = inject(AgendaService);
  private readonly financeiroService = inject(FinanceiroService);
  private readonly clientesService = inject(ClientesService);
  private readonly profissionaisService = inject(ProfissionaisService);
  private readonly servicosService = inject(ServicosService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly rotulosStatus = RUTULOS_STATUS;

  private readonly uuid = this.route.snapshot.paramMap.get('uuid');
  protected readonly modoEdicao = this.uuid !== null;

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly executandoAcao = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly profissionais = signal<Profissional[]>([]);
  protected readonly servicos = signal<Servico[]>([]);
  protected readonly servicosSelecionados = signal<Set<string>>(new Set());
  protected readonly agendamentoAtual = signal<Agendamento | null>(null);

  protected readonly buscaCliente = signal('');
  protected readonly resultadosClientes = signal<Cliente[]>([]);
  protected readonly clienteSelecionado = signal<{ uuid: string; nome: string } | null>(null);

  protected readonly podeRemarcar = computed(() => {
    const status = this.agendamentoAtual()?.status;
    return !this.modoEdicao || status === 'AGENDADO' || status === 'CONFIRMADO';
  });

  protected readonly valorTotalSelecionado = computed(() => {
    const uuids = this.servicosSelecionados();
    return this.servicos()
      .filter((s) => uuids.has(s.uuid))
      .reduce((total, s) => total + s.preco, 0);
  });

  protected readonly formulario = this.formBuilder.nonNullable.group({
    profissionalUuid: ['', [Validators.required]],
    data: ['', [Validators.required]],
    hora: ['', [Validators.required]],
    observacao: [''],
  });

  constructor() {
    this.profissionaisService.listar({ ativo: true, size: 100 }).subscribe((pagina) => {
      this.profissionais.set(pagina.content);
    });
    this.servicosService.listar({ ativo: true, size: 100 }).subscribe((pagina) => {
      this.servicos.set(pagina.content);
    });

    if (this.modoEdicao && this.uuid) {
      this.agendaService.obter(this.uuid).subscribe((agendamento) => {
        this.preencherComAgendamento(agendamento);
        this.carregando.set(false);
      });
    } else {
      const params = this.route.snapshot.queryParamMap;
      const inicio = params.get('inicio');
      const profissionalUuid = params.get('profissionalUuid');
      if (inicio) {
        const data = new Date(inicio);
        this.formulario.patchValue({
          data: formatarData(data),
          hora: formatarHora(data),
          profissionalUuid: profissionalUuid ?? '',
        });
      }
      this.carregando.set(false);
    }
  }

  protected alternarServico(uuid: string): void {
    const atual = new Set(this.servicosSelecionados());
    if (atual.has(uuid)) {
      atual.delete(uuid);
    } else {
      atual.add(uuid);
    }
    this.servicosSelecionados.set(atual);
  }

  protected buscarClientes(): void {
    const termo = this.buscaCliente().trim();
    if (!termo) {
      this.resultadosClientes.set([]);
      return;
    }
    this.clientesService.listar({ busca: termo, size: 5 }).subscribe((pagina) => {
      this.resultadosClientes.set(pagina.content);
    });
  }

  protected selecionarCliente(cliente: Cliente): void {
    this.clienteSelecionado.set({ uuid: cliente.uuid, nome: cliente.nome });
    this.resultadosClientes.set([]);
    this.buscaCliente.set('');
  }

  protected salvar(): void {
    const cliente = this.clienteSelecionado();
    if (!cliente || this.servicosSelecionados().size === 0 || this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.mensagemErro.set('Selecione o cliente, ao menos um serviço e preencha os campos obrigatórios.');
      return;
    }

    this.salvando.set(true);
    this.mensagemErro.set(null);

    const valores = this.formulario.getRawValue();
    const inicio = new Date(`${valores.data}T${valores.hora}:00`);

    const requisicao: SalvarAgendamentoRequest = {
      clienteUuid: cliente.uuid,
      profissionalUuid: valores.profissionalUuid,
      servicoUuids: Array.from(this.servicosSelecionados()),
      inicio: inicio.toISOString(),
      observacao: valores.observacao.trim() === '' ? null : valores.observacao.trim(),
    };

    const operacao =
      this.modoEdicao && this.uuid
        ? this.agendaService.alterar(this.uuid, requisicao)
        : this.agendaService.criar(requisicao);

    operacao.subscribe({
      next: () => this.router.navigateByUrl('/agenda'),
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);
        this.mensagemErro.set(
          erro.error?.mensagem ?? 'Não foi possível salvar agora. Tente novamente em instantes.',
        );
      },
    });
  }

  protected executarAcao(acao: 'confirmar' | 'finalizar' | 'naoCompareceu'): void {
    if (!this.uuid) {
      return;
    }
    this.executandoAcao.set(true);
    this.mensagemErro.set(null);

    const operacao =
      acao === 'confirmar'
        ? this.agendaService.confirmar(this.uuid)
        : acao === 'finalizar'
          ? this.agendaService.finalizar(this.uuid)
          : this.agendaService.marcarNaoComparecimento(this.uuid);

    operacao.subscribe({
      next: (agendamento) => {
        this.preencherComAgendamento(agendamento);
        this.executandoAcao.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.executandoAcao.set(false);
        this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível executar essa ação agora.');
      },
    });
  }

  /** Iniciar atendimento abre a comanda e leva o usuario direto pra ela, em vez de so' trocar o status aqui. */
  protected iniciarAtendimento(): void {
    if (!this.uuid) {
      return;
    }
    this.executandoAcao.set(true);
    this.mensagemErro.set(null);

    this.financeiroService.abrirParaAgendamento(this.uuid).subscribe({
      next: (comanda) => this.router.navigate(['/financeiro/comandas', comanda.uuid]),
      error: (erro: HttpErrorResponse) => {
        this.executandoAcao.set(false);
        this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível abrir a comanda agora.');
      },
    });
  }

  /** Agendamento ja finalizado: leva para a comanda (e o comprovante) em vez de tentar abrir uma nova. */
  protected verComanda(): void {
    if (!this.uuid) {
      return;
    }
    this.executandoAcao.set(true);
    this.mensagemErro.set(null);

    this.financeiroService.obterComandaPorAgendamento(this.uuid).subscribe({
      next: (comanda) => this.router.navigate(['/financeiro/comandas', comanda.uuid]),
      error: (erro: HttpErrorResponse) => {
        this.executandoAcao.set(false);
        this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível encontrar a comanda deste agendamento.');
      },
    });
  }

  protected cancelar(): void {
    if (!this.uuid) {
      return;
    }
    const uuid = this.uuid;

    this.confirmDialog
      .confirm({
        title: 'Cancelar agendamento',
        message: 'O agendamento será cancelado e o horário liberado na agenda.',
        confirmLabel: 'Cancelar agendamento',
        danger: true,
        requireReason: true,
        reasonLabel: 'Motivo do cancelamento',
      })
      .subscribe((resultado) => {
        if (!resultado.confirmed || !resultado.reason) {
          return;
        }

        this.executandoAcao.set(true);
        this.mensagemErro.set(null);
        this.agendaService.cancelar(uuid, resultado.reason).subscribe({
          next: (agendamento) => {
            this.preencherComAgendamento(agendamento);
            this.executandoAcao.set(false);
          },
          error: (erro: HttpErrorResponse) => {
            this.executandoAcao.set(false);
            this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível cancelar agora.');
          },
        });
      });
  }

  private preencherComAgendamento(agendamento: Agendamento): void {
    this.agendamentoAtual.set(agendamento);
    this.clienteSelecionado.set({ uuid: agendamento.clienteUuid, nome: agendamento.clienteNome });
    this.servicosSelecionados.set(new Set(agendamento.servicos.map((s) => s.servicoUuid)));

    const inicio = new Date(agendamento.inicio);
    this.formulario.patchValue({
      profissionalUuid: agendamento.profissionalUuid,
      data: formatarData(inicio),
      hora: formatarHora(inicio),
      observacao: agendamento.observacao ?? '',
    });
  }
}

function formatarData(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

function formatarHora(data: Date): string {
  return `${String(data.getHours()).padStart(2, '0')}:${String(data.getMinutes()).padStart(2, '0')}`;
}
