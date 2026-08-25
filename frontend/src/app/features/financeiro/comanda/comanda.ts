import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { Produto } from '../../produtos/produtos.model';
import { ProdutosService } from '../../produtos/produtos.service';
import { Servico } from '../../servicos/servicos.model';
import { ServicosService } from '../../servicos/servicos.service';
import {
  Comanda,
  Comprovante,
  FormaPagamento,
  RUTULOS_FORMA_PAGAMENTO,
  RUTULOS_STATUS_COMANDA,
  RUTULOS_STATUS_COMPROVANTE,
} from '../financeiro.model';
import { FinanceiroService } from '../financeiro.service';

@Component({
  selector: 'app-comanda',
  imports: [
    CurrencyPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './comanda.html',
  styleUrl: './comanda.css',
})
export class ComandaComponent {
  private readonly financeiroService = inject(FinanceiroService);
  private readonly servicosService = inject(ServicosService);
  private readonly produtosService = inject(ProdutosService);
  private readonly authService = inject(AuthService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly route = inject(ActivatedRoute);

  private readonly uuid = this.route.snapshot.paramMap.get('uuid')!;

  protected readonly rotulosStatus = RUTULOS_STATUS_COMANDA;
  protected readonly rotulosFormaPagamento = RUTULOS_FORMA_PAGAMENTO;
  protected readonly rotulosStatusComprovante = RUTULOS_STATUS_COMPROVANTE;
  protected readonly formasPagamento: FormaPagamento[] = ['DINHEIRO', 'CARTAO_DEBITO', 'CARTAO_CREDITO', 'PIX', 'OUTRO'];
  protected readonly colunasItens = ['descricao', 'valorBruto', 'valorDescontoRateado', 'valorLiquido', 'comissaoValor', 'acoes'];

  protected readonly carregando = signal(true);
  protected readonly executandoAcao = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly comanda = signal<Comanda | null>(null);
  protected readonly comprovante = signal<Comprovante | null>(null);
  protected readonly baixandoComprovante = signal(false);
  protected readonly servicos = signal<Servico[]>([]);
  protected readonly produtos = signal<Produto[]>([]);

  protected readonly servicoSelecionado = signal('');
  protected readonly produtoSelecionado = signal('');
  protected readonly produtoQuantidade = signal(1);
  protected readonly descontoValor = signal(0);
  protected readonly descontoMotivo = signal('');
  protected readonly formaPagamentoSelecionada = signal<FormaPagamento | ''>('');

  constructor() {
    this.servicosService.listar({ ativo: true, size: 100 }).subscribe((pagina) => {
      this.servicos.set(pagina.content);
    });
    this.produtosService.listar({ ativo: true, size: 100 }).subscribe((pagina) => {
      this.produtos.set(pagina.content);
    });
    this.carregar();
  }

  protected podeEstornar(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE';
  }

  protected estaAberta(): boolean {
    return this.comanda()?.status === 'ABERTA';
  }

  protected adicionarItem(): void {
    const comanda = this.comanda();
    if (!comanda || !this.servicoSelecionado()) {
      return;
    }
    this.executarComTratamentoDeErro(
      this.financeiroService.adicionarItem(comanda.uuid, this.servicoSelecionado()),
    );
    this.servicoSelecionado.set('');
  }

  protected adicionarItemProduto(): void {
    const comanda = this.comanda();
    if (!comanda || !this.produtoSelecionado()) {
      return;
    }
    this.executarComTratamentoDeErro(
      this.financeiroService.adicionarItemProduto(comanda.uuid, this.produtoSelecionado(), this.produtoQuantidade()),
    );
    this.produtoSelecionado.set('');
    this.produtoQuantidade.set(1);
  }

  protected removerItem(itemUuid: string): void {
    const comanda = this.comanda();
    if (!comanda) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Remover item',
        message: 'Remover este item da comanda?',
        confirmLabel: 'Remover',
        danger: true,
      })
      .subscribe((resultado) => {
        if (resultado.confirmed) {
          this.executarComTratamentoDeErro(this.financeiroService.removerItem(comanda.uuid, itemUuid));
        }
      });
  }

  protected aplicarDesconto(): void {
    const comanda = this.comanda();
    if (!comanda) {
      return;
    }
    this.executarComTratamentoDeErro(
      this.financeiroService.aplicarDesconto(
        comanda.uuid,
        this.descontoValor(),
        this.descontoMotivo().trim() === '' ? null : this.descontoMotivo().trim(),
      ),
    );
  }

  protected definirFormaPagamento(): void {
    const comanda = this.comanda();
    const forma = this.formaPagamentoSelecionada();
    if (!comanda || !forma) {
      return;
    }
    this.executarComTratamentoDeErro(this.financeiroService.definirFormaPagamento(comanda.uuid, forma));
  }

  protected fechar(): void {
    const comanda = this.comanda();
    if (!comanda) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Fechar comanda',
        message: 'Fechar a comanda? Depois de fechada, só é possível corrigir por estorno.',
        confirmLabel: 'Fechar comanda',
      })
      .subscribe((resultado) => {
        if (resultado.confirmed) {
          this.executarComTratamentoDeErro(this.financeiroService.fechar(comanda.uuid));
        }
      });
  }

  protected estornar(): void {
    const comanda = this.comanda();
    if (!comanda) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Estornar comanda',
        message: 'O estorno não pode ser desfeito. Descreva o motivo da correção.',
        confirmLabel: 'Estornar',
        danger: true,
        requireReason: true,
        reasonLabel: 'Motivo do estorno',
      })
      .subscribe((resultado) => {
        if (resultado.confirmed && resultado.reason) {
          this.executarComTratamentoDeErro(this.financeiroService.estornar(comanda.uuid, resultado.reason));
        }
      });
  }

  protected baixarComprovante(): void {
    const comanda = this.comanda();
    if (!comanda) {
      return;
    }
    this.baixandoComprovante.set(true);
    this.financeiroService.baixarComprovante(comanda.uuid).subscribe({
      next: (blob) => {
        this.baixandoComprovante.set(false);
        baixarBlob(blob, `comprovante-${comanda.uuid}.pdf`);
      },
      error: () => this.baixandoComprovante.set(false),
    });
  }

  protected reenviarComprovante(): void {
    const comanda = this.comanda();
    if (!comanda) {
      return;
    }
    this.baixandoComprovante.set(true);
    this.financeiroService.reenviarComprovante(comanda.uuid).subscribe({
      next: (comprovante) => {
        this.comprovante.set(comprovante);
        this.baixandoComprovante.set(false);
      },
      error: () => this.baixandoComprovante.set(false),
    });
  }

  private carregarComprovante(comandaUuid: string): void {
    this.financeiroService.obterComprovante(comandaUuid).subscribe({
      next: (comprovante) => this.comprovante.set(comprovante),
      error: () => this.comprovante.set(null),
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    this.financeiroService.obterComanda(this.uuid).subscribe({
      next: (comanda) => this.preencher(comanda),
      error: () => this.carregando.set(false),
    });
  }

  private executarComTratamentoDeErro(operacao: Observable<Comanda>): void {
    this.executandoAcao.set(true);
    this.mensagemErro.set(null);
    operacao.subscribe({
      next: (comanda) => {
        this.preencher(comanda);
        this.executandoAcao.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        this.executandoAcao.set(false);
        this.mensagemErro.set(erro.error?.mensagem ?? 'Não foi possível executar essa ação agora.');
      },
    });
  }

  private preencher(comanda: Comanda): void {
    this.comanda.set(comanda);
    this.descontoValor.set(comanda.descontoValor);
    this.descontoMotivo.set(comanda.descontoMotivo ?? '');
    this.formaPagamentoSelecionada.set(comanda.formaPagamento ?? '');
    this.carregando.set(false);
    if (comanda.status === 'FECHADA') {
      this.carregarComprovante(comanda.uuid);
    } else {
      this.comprovante.set(null);
    }
  }
}

function baixarBlob(blob: Blob, nomeArquivo: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  link.click();
  URL.revokeObjectURL(url);
}
