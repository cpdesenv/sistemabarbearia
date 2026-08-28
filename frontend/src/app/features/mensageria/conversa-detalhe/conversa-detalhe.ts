import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ConfirmDialogService } from '../../../core/ui/confirm-dialog/confirm-dialog.service';
import { Conversa, Mensagem } from '../mensageria.model';
import { MensageriaService } from '../mensageria.service';

@Component({
  selector: 'app-conversa-detalhe',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './conversa-detalhe.html',
  styleUrl: './conversa-detalhe.css',
})
export class ConversaDetalhe {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly mensageriaService = inject(MensageriaService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  private readonly uuid = this.activatedRoute.snapshot.paramMap.get('uuid')!;

  protected readonly conversa = signal<Conversa | null>(null);
  protected readonly mensagens = signal<Mensagem[]>([]);
  protected readonly carregando = signal(true);

  constructor() {
    this.carregar();
  }

  protected assumirConversa(): void {
    this.confirmDialog
      .confirm({
        title: 'Assumir conversa',
        message: 'A partir de agora a IA não responde mais nesta conversa — um atendente humano assume.',
        confirmLabel: 'Assumir conversa',
      })
      .subscribe((resultado) => {
        if (resultado.confirmed) {
          this.mensageriaService.assumirConversa(this.uuid).subscribe((conversa) => this.conversa.set(conversa));
        }
      });
  }

  private carregar(): void {
    this.mensageriaService.obterConversa(this.uuid).subscribe((conversa) => this.conversa.set(conversa));
    this.mensageriaService.listarMensagens(this.uuid).subscribe((mensagens) => {
      this.mensagens.set(mensagens);
      this.carregando.set(false);
    });
  }
}
