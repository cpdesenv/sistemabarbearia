import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { Cliente } from '../../clientes/clientes.model';
import { ClientesService } from '../../clientes/clientes.service';
import {
  ContaPagar,
  ContaReceber,
  Despesa,
  RUTULOS_STATUS_CONTA_PAGAR,
  RUTULOS_STATUS_CONTA_RECEBER,
  StatusContaPagar,
  StatusContaReceber,
} from '../financeiro.model';
import { FinanceiroService } from '../financeiro.service';

@Component({
  selector: 'app-contas',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './contas.html',
  styleUrl: './contas.css',
})
export class Contas {
  private readonly financeiroService = inject(FinanceiroService);
  private readonly clientesService = inject(ClientesService);
  private readonly authService = inject(AuthService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  protected readonly rotulosStatusContaPagar = RUTULOS_STATUS_CONTA_PAGAR;
  protected readonly rotulosStatusContaReceber = RUTULOS_STATUS_CONTA_RECEBER;

  protected readonly colunasDespesas = ['data', 'categoria', 'valor', 'descricao'];
  protected readonly colunasContasPagar = ['descricao', 'valor', 'dataVencimento', 'status', 'acoes'];
  protected readonly colunasContasReceber = ['clienteNome', 'valor', 'dataVencimento', 'status', 'acoes'];

  protected readonly carregando = signal(true);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly despesas = signal<Despesa[]>([]);
  protected readonly contasPagar = signal<ContaPagar[]>([]);
  protected readonly contasReceber = signal<ContaReceber[]>([]);

  protected readonly novaDespesaData = signal(formatarDataHoje());
  protected readonly novaDespesaCategoria = signal('');
  protected readonly novaDespesaValor = signal(0);
  protected readonly novaDespesaDescricao = signal('');

  protected readonly novaContaPagarDescricao = signal('');
  protected readonly novaContaPagarValor = signal(0);
  protected readonly novaContaPagarVencimento = signal('');

  protected readonly buscaCliente = signal('');
  protected readonly resultadosClientes = signal<Cliente[]>([]);
  protected readonly clienteSelecionado = signal<{ uuid: string; nome: string } | null>(null);
  protected readonly novaContaReceberDescricao = signal('');
  protected readonly novaContaReceberValor = signal(0);
  protected readonly novaContaReceberVencimento = signal('');

  constructor() {
    this.carregarTudo();
  }

  protected rotuloStatusPagar(status: StatusContaPagar): string {
    return this.rotulosStatusContaPagar[status];
  }

  protected rotuloStatusReceber(status: StatusContaReceber): string {
    return this.rotulosStatusContaReceber[status];
  }

  protected podeGerenciar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE';
  }

  protected podeLancarContaReceber(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE' || perfil === 'RECEPCAO';
  }

  protected registrarDespesa(): void {
    if (this.novaDespesaValor() <= 0) {
      this.mensagemErro.set('Informe um valor de despesa maior que zero.');
      return;
    }
    this.financeiroService
      .criarDespesa({
        data: this.novaDespesaData(),
        categoria: this.novaDespesaCategoria().trim() || null,
        valor: this.novaDespesaValor(),
        descricao: this.novaDespesaDescricao().trim() || null,
        comprovanteUrl: null,
      })
      .subscribe({
        next: () => {
          this.novaDespesaCategoria.set('');
          this.novaDespesaValor.set(0);
          this.novaDespesaDescricao.set('');
          this.carregarDespesas();
        },
        error: (erro: HttpErrorResponse) => this.tratarErro(erro),
      });
  }

  protected registrarContaPagar(): void {
    if (!this.novaContaPagarDescricao().trim() || this.novaContaPagarValor() <= 0
        || !this.novaContaPagarVencimento()) {
      this.mensagemErro.set('Preencha descrição, valor e data de vencimento.');
      return;
    }
    this.financeiroService
      .criarContaPagar({
        descricao: this.novaContaPagarDescricao().trim(),
        valor: this.novaContaPagarValor(),
        dataVencimento: this.novaContaPagarVencimento(),
      })
      .subscribe({
        next: () => {
          this.novaContaPagarDescricao.set('');
          this.novaContaPagarValor.set(0);
          this.novaContaPagarVencimento.set('');
          this.carregarContasPagar();
        },
        error: (erro: HttpErrorResponse) => this.tratarErro(erro),
      });
  }

  protected marcarPaga(uuid: string): void {
    this.financeiroService.marcarContaPagarPaga(uuid).subscribe({
      next: () => this.carregarContasPagar(),
      error: (erro: HttpErrorResponse) => this.tratarErro(erro),
    });
  }

  protected cancelarContaPagar(uuid: string): void {
    this.confirmDialog
      .confirm({
        title: 'Cancelar conta a pagar',
        message: 'Esta conta deixará de contar no fluxo de caixa.',
        confirmLabel: 'Cancelar conta',
        danger: true,
        requireReason: true,
        reasonLabel: 'Motivo do cancelamento',
      })
      .subscribe((resultado) => {
        if (resultado.confirmed && resultado.reason) {
          this.financeiroService.cancelarContaPagar(uuid, resultado.reason).subscribe({
            next: () => this.carregarContasPagar(),
            error: (erro: HttpErrorResponse) => this.tratarErro(erro),
          });
        }
      });
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

  protected registrarContaReceber(): void {
    const cliente = this.clienteSelecionado();
    if (!cliente || this.novaContaReceberValor() <= 0 || !this.novaContaReceberVencimento()) {
      this.mensagemErro.set('Selecione o cliente, informe o valor e a data de vencimento.');
      return;
    }
    this.financeiroService
      .criarContaReceber({
        clienteUuid: cliente.uuid,
        descricao: this.novaContaReceberDescricao().trim() || null,
        valor: this.novaContaReceberValor(),
        dataVencimento: this.novaContaReceberVencimento(),
      })
      .subscribe({
        next: () => {
          this.clienteSelecionado.set(null);
          this.novaContaReceberDescricao.set('');
          this.novaContaReceberValor.set(0);
          this.novaContaReceberVencimento.set('');
          this.carregarContasReceber();
        },
        error: (erro: HttpErrorResponse) => this.tratarErro(erro),
      });
  }

  protected marcarRecebida(uuid: string): void {
    this.financeiroService.marcarContaReceberRecebida(uuid).subscribe({
      next: () => this.carregarContasReceber(),
      error: (erro: HttpErrorResponse) => this.tratarErro(erro),
    });
  }

  protected cancelarContaReceber(uuid: string): void {
    this.confirmDialog
      .confirm({
        title: 'Cancelar conta a receber',
        message: 'Esta conta deixará de contar no fluxo de caixa.',
        confirmLabel: 'Cancelar conta',
        danger: true,
        requireReason: true,
        reasonLabel: 'Motivo do cancelamento',
      })
      .subscribe((resultado) => {
        if (resultado.confirmed && resultado.reason) {
          this.financeiroService.cancelarContaReceber(uuid, resultado.reason).subscribe({
            next: () => this.carregarContasReceber(),
            error: (erro: HttpErrorResponse) => this.tratarErro(erro),
          });
        }
      });
  }

  private carregarTudo(): void {
    this.carregando.set(true);
    this.carregarDespesas();
    this.carregarContasPagar();
    this.carregarContasReceber();
    this.carregando.set(false);
  }

  private carregarDespesas(): void {
    this.financeiroService.listarDespesas().subscribe((despesas) => this.despesas.set(despesas));
  }

  private carregarContasPagar(): void {
    this.financeiroService.listarContasPagar().subscribe((contas) => this.contasPagar.set(contas));
  }

  private carregarContasReceber(): void {
    this.financeiroService.listarContasReceber().subscribe((contas) => this.contasReceber.set(contas));
  }

  private tratarErro(erro: HttpErrorResponse): void {
    this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível executar essa ação agora.');
  }
}

function formatarDataHoje(): string {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}-${String(hoje.getDate()).padStart(2, '0')}`;
}
