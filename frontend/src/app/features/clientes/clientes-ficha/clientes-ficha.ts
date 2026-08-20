import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../../../core/auth/auth.service';
import { FichaCliente } from '../clientes.model';
import { ClientesService } from '../clientes.service';

@Component({
  selector: 'app-clientes-ficha',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './clientes-ficha.html',
  styleUrl: './clientes-ficha.css',
})
export class ClientesFicha {
  private readonly clientesService = inject(ClientesService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  private readonly uuid = this.route.snapshot.paramMap.get('uuid')!;

  protected readonly carregando = signal(true);
  protected readonly ficha = signal<FichaCliente | null>(null);
  protected readonly exportando = signal(false);
  protected readonly anonimizando = signal(false);
  protected readonly mostrarAnonimizacao = signal(false);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly formularioAnonimizacao = this.formBuilder.nonNullable.group({
    motivo: ['', [Validators.required]],
  });

  constructor() {
    this.carregarFicha();
  }

  protected podeGerenciarLgpd(): boolean {
    const perfil = this.authService.usuario()?.perfil;
    return perfil === 'ADMIN' || perfil === 'GERENTE';
  }

  protected exportarDados(): void {
    this.exportando.set(true);
    this.clientesService.exportarDados(this.uuid).subscribe({
      next: (exportacao) => {
        this.exportando.set(false);
        baixarComoJson(exportacao, `cliente-${this.uuid}.json`);
      },
      error: () => this.exportando.set(false),
    });
  }

  protected confirmarAnonimizacao(): void {
    if (this.formularioAnonimizacao.invalid) {
      this.formularioAnonimizacao.markAllAsTouched();
      return;
    }

    this.anonimizando.set(true);
    this.mensagemErro.set(null);
    const motivo = this.formularioAnonimizacao.getRawValue().motivo;

    this.clientesService.anonimizar(this.uuid, motivo).subscribe({
      next: () => {
        this.anonimizando.set(false);
        this.mostrarAnonimizacao.set(false);
        this.carregarFicha();
      },
      error: () => {
        this.anonimizando.set(false);
        this.mensagemErro.set('Nao foi possivel anonimizar o cliente agora. Tente novamente.');
      },
    });
  }

  private carregarFicha(): void {
    this.carregando.set(true);
    this.clientesService.ficha(this.uuid).subscribe((ficha) => {
      this.ficha.set(ficha);
      this.carregando.set(false);
    });
  }
}

function baixarComoJson(dados: unknown, nomeArquivo: string): void {
  const blob = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  link.click();
  URL.revokeObjectURL(url);
}
