import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { ClienteBusca } from '../../../shared/cliente-busca/cliente-busca';
import { Cliente } from '../../clientes/clientes.model';
import { Servico } from '../../servicos/servicos.model';
import { ServicosService } from '../../servicos/servicos.service';
import {
  Assinatura,
  AssinaturaResumo,
  PlanoAssinatura,
  RUTULOS_STATUS_ASSINATURA,
  StatusAssinatura,
} from '../assinaturas.model';
import { AssinaturasService } from '../assinaturas.service';

@Component({
  selector: 'app-clube-cavalinho',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    ClienteBusca,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './clube-cavalinho.html',
  styleUrl: './clube-cavalinho.css',
})
export class ClubeCavalinho {
  private readonly assinaturasService = inject(AssinaturasService);
  private readonly servicosService = inject(ServicosService);
  private readonly authService = inject(AuthService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  protected readonly rotulosStatus = RUTULOS_STATUS_ASSINATURA;
  protected readonly colunasPlanos = ['nome', 'precoMensal', 'cortes', 'desconto', 'status', 'acoes'];
  protected readonly colunasAssinaturas = ['cliente', 'plano', 'status', 'saldo', 'proximaRenovacao', 'acoes'];

  protected readonly carregando = signal(true);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly planos = signal<PlanoAssinatura[]>([]);
  protected readonly servicosAtivos = signal<Servico[]>([]);
  protected readonly assinaturas = signal<Assinatura[]>([]);
  protected readonly resumo = signal<AssinaturaResumo | null>(null);
  protected readonly filtroStatus = signal<StatusAssinatura | ''>('');

  protected readonly novoPlanoNome = signal('');
  protected readonly novoPlanoPreco = signal(0);
  protected readonly novoPlanoCortes = signal(1);
  protected readonly novoPlanoDesconto = signal(0);
  protected readonly novoPlanoServicosUuids = signal<string[]>([]);

  protected readonly clienteSelecionado = signal<{ uuid: string; nome: string } | null>(null);
  protected readonly planoEscolhidoUuid = signal('');

  constructor() {
    this.carregarTudo();
  }

  protected podeGerenciar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE';
  }

  protected podeAssinar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE' || perfil === 'RECEPCAO';
  }

  protected rotuloStatus(status: StatusAssinatura): string {
    return this.rotulosStatus[status];
  }

  protected classeBadgeStatus(status: StatusAssinatura): string {
    if (status === 'ATIVA') {
      return 'badge--sucesso';
    }
    if (status === 'INADIMPLENTE') {
      return 'badge--erro';
    }
    if (status === 'CANCELADA') {
      return 'badge--erro';
    }
    return 'badge--pendente';
  }

  protected alternarServicoNoPlano(servicoUuid: string, marcado: boolean): void {
    this.novoPlanoServicosUuids.update((atual) =>
      marcado ? [...atual, servicoUuid] : atual.filter((uuid) => uuid !== servicoUuid),
    );
  }

  protected registrarPlano(): void {
    if (!this.novoPlanoNome().trim() || this.novoPlanoPreco() <= 0 || this.novoPlanoCortes() < 1
        || this.novoPlanoServicosUuids().length === 0) {
      this.mensagemErro.set('Preencha nome, preço mensal, ao menos 1 corte por ciclo e selecione os serviços inclusos.');
      return;
    }
    this.assinaturasService
      .criarPlano({
        nome: this.novoPlanoNome().trim(),
        descricao: null,
        precoMensal: this.novoPlanoPreco(),
        cortesIncluidosPorCiclo: this.novoPlanoCortes(),
        percentualDescontoAdicional: this.novoPlanoDesconto(),
        servicosInclusosUuids: this.novoPlanoServicosUuids(),
      })
      .subscribe({
        next: () => {
          this.novoPlanoNome.set('');
          this.novoPlanoPreco.set(0);
          this.novoPlanoCortes.set(1);
          this.novoPlanoDesconto.set(0);
          this.novoPlanoServicosUuids.set([]);
          this.carregarPlanos();
        },
        error: (erro: HttpErrorResponse) => this.tratarErro(erro),
      });
  }

  protected alternarStatusPlano(plano: PlanoAssinatura): void {
    if (!plano.ativo) {
      this.assinaturasService.atualizarStatusPlano(plano.uuid, true).subscribe(() => this.carregarPlanos());
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Desativar plano',
        message: `Desativar "${plano.nome}"? Assinaturas em curso continuam válidas, mas novas assinaturas não poderão usar este plano.`,
        confirmLabel: 'Desativar',
        danger: true,
      })
      .subscribe((resultado) => {
        if (resultado.confirmed) {
          this.assinaturasService.atualizarStatusPlano(plano.uuid, false).subscribe(() => this.carregarPlanos());
        }
      });
  }

  protected selecionarCliente(cliente: Cliente): void {
    this.clienteSelecionado.set({ uuid: cliente.uuid, nome: cliente.nome });
  }

  protected registrarAssinatura(): void {
    const cliente = this.clienteSelecionado();
    if (!cliente || !this.planoEscolhidoUuid()) {
      this.mensagemErro.set('Selecione o cliente e o plano.');
      return;
    }
    this.assinaturasService.assinar(cliente.uuid, this.planoEscolhidoUuid()).subscribe({
      next: () => {
        this.clienteSelecionado.set(null);
        this.planoEscolhidoUuid.set('');
        this.carregarAssinaturas();
        this.carregarResumo();
      },
      error: (erro: HttpErrorResponse) => this.tratarErro(erro),
    });
  }

  protected cancelarAssinatura(assinatura: Assinatura): void {
    this.confirmDialog
      .confirm({
        title: 'Cancelar assinatura',
        message: `Cancelar a assinatura de "${assinatura.clienteNome}"? O saldo de cortes segue válido até `
          + `${new Date(assinatura.dataProximaRenovacao + 'T00:00:00').toLocaleDateString('pt-BR')}, quando a`
          + ' assinatura efetivamente encerra.',
        confirmLabel: 'Cancelar assinatura',
        danger: true,
        requireReason: true,
        reasonLabel: 'Motivo do cancelamento',
      })
      .subscribe((resultado) => {
        if (resultado.confirmed && resultado.reason) {
          this.assinaturasService.cancelar(assinatura.uuid, resultado.reason, assinatura.dataProximaRenovacao)
            .subscribe({
              next: () => {
                this.carregarAssinaturas();
                this.carregarResumo();
              },
              error: (erro: HttpErrorResponse) => this.tratarErro(erro),
            });
        }
      });
  }

  protected filtrarPorStatus(status: StatusAssinatura | ''): void {
    this.filtroStatus.set(status);
    this.carregarAssinaturas();
  }

  private carregarTudo(): void {
    this.carregando.set(true);
    this.carregarPlanos();
    this.carregarServicosAtivos();
    this.carregarAssinaturas();
    this.carregarResumo();
    this.carregando.set(false);
  }

  private carregarPlanos(): void {
    this.assinaturasService.listarPlanos().subscribe((planos) => this.planos.set(planos));
  }

  private carregarServicosAtivos(): void {
    this.servicosService.listar({ ativo: true, size: 100 }).subscribe((pagina) => {
      this.servicosAtivos.set(pagina.content);
    });
  }

  private carregarAssinaturas(): void {
    const status = this.filtroStatus() || undefined;
    this.assinaturasService.listarAssinaturas(status).subscribe((assinaturas) => this.assinaturas.set(assinaturas));
  }

  private carregarResumo(): void {
    this.assinaturasService.resumo().subscribe((resumo) => this.resumo.set(resumo));
  }

  private tratarErro(erro: HttpErrorResponse): void {
    this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível executar essa ação agora.');
  }
}
