import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { SimuladorService } from './simulador.service';

@Component({
  selector: 'app-simulador',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './simulador.html',
  styleUrl: './simulador.css',
})
export class Simulador {
  private readonly formBuilder = inject(FormBuilder);
  private readonly simuladorService = inject(SimuladorService);

  protected readonly enviando = signal(false);
  protected readonly mensagemSucesso = signal<string | null>(null);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    telefone: ['', [Validators.required]],
    texto: ['', [Validators.required]],
  });

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.mensagemSucesso.set(null);
    this.mensagemErro.set(null);
    const valores = this.formulario.getRawValue();

    this.simuladorService.injetarMensagem(valores).subscribe({
      next: () => {
        this.enviando.set(false);
        this.mensagemSucesso.set('Mensagem injetada — veja a resposta em Conversas.');
        this.formulario.controls.texto.reset('');
      },
      error: () => {
        this.enviando.set(false);
        this.mensagemErro.set('Não foi possível injetar a mensagem agora. Tente novamente.');
      },
    });
  }

  protected simularFalha(): void {
    this.mensagemErro.set(null);
    this.simuladorService.simularFalhaNoProximoEnvio().subscribe({
      next: () => this.mensagemSucesso.set('Próximo envio vai falhar (para testar a retentativa).'),
      error: () => this.mensagemErro.set('Não foi possível armar a falha simulada.'),
    });
  }
}
