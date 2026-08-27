import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { Mensagem } from '../mensageria.model';
import { MensageriaService } from '../mensageria.service';

@Component({
  selector: 'app-conversa-detalhe',
  imports: [DatePipe, MatProgressSpinnerModule],
  templateUrl: './conversa-detalhe.html',
  styleUrl: './conversa-detalhe.css',
})
export class ConversaDetalhe {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly mensageriaService = inject(MensageriaService);

  protected readonly mensagens = signal<Mensagem[]>([]);
  protected readonly carregando = signal(true);

  constructor() {
    const uuid = this.activatedRoute.snapshot.paramMap.get('uuid')!;
    this.mensageriaService.listarMensagens(uuid).subscribe((mensagens) => {
      this.mensagens.set(mensagens);
      this.carregando.set(false);
    });
  }
}
