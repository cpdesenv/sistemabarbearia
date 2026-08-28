import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { ConfiguracaoIaService } from './ia.service';
import { AtualizarConfiguracaoIaRequest, ConfiguracaoIa } from './ia.model';

@Component({
  selector: 'app-configuracao-ia',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './ia.html',
  styleUrl: './ia.css',
})
export class ConfiguracaoIaConfig {
  private readonly formBuilder = inject(FormBuilder);
  private readonly configuracaoIaService = inject(ConfiguracaoIaService);

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly mensagemSucesso = signal<string | null>(null);
  protected readonly mensagemErro = signal<string | null>(null);

  protected readonly formulario = this.formBuilder.nonNullable.group({
    ativo: [true],
    limiteTurnos: [25, [Validators.required, Validators.min(1)]],
    tetoCustoMensalReais: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.configuracaoIaService.obter().subscribe({
      next: (configuracao) => {
        this.preencherFormulario(configuracao);
        this.carregando.set(false);
      },
      error: () => {
        this.mensagemErro.set('Não foi possível carregar a configuração da IA.');
        this.carregando.set(false);
      },
    });
  }

  protected salvar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.salvando.set(true);
    this.mensagemSucesso.set(null);
    this.mensagemErro.set(null);

    const valores = this.formulario.getRawValue();
    const requisicao: AtualizarConfiguracaoIaRequest = {
      ativo: valores.ativo,
      limiteTurnos: valores.limiteTurnos,
      tetoCustoMensalCentavos: Math.round(valores.tetoCustoMensalReais * 100),
    };

    this.configuracaoIaService.atualizar(requisicao).subscribe({
      next: (configuracao) => {
        this.preencherFormulario(configuracao);
        this.salvando.set(false);
        this.mensagemSucesso.set('Configuração salva com sucesso.');
      },
      error: (erro: HttpErrorResponse) => {
        this.salvando.set(false);
        this.mensagemErro.set(
          erro.status === 400
            ? 'Verifique os campos destacados e tente novamente.'
            : 'Não foi possível salvar agora. Tente novamente em instantes.',
        );
      },
    });
  }

  private preencherFormulario(configuracao: ConfiguracaoIa): void {
    this.formulario.setValue({
      ativo: configuracao.ativo,
      limiteTurnos: configuracao.limiteTurnos,
      tetoCustoMensalReais: configuracao.tetoCustoMensalCentavos / 100,
    });
  }
}
