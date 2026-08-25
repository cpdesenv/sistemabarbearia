import { Component, EventEmitter, Input, Output, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import { Cliente } from '../../features/clientes/clientes.model';
import { ClientesService } from '../../features/clientes/clientes.service';

/**
 * Campo de busca de cliente por nome/telefone com sugestões ao digitar
 * (mat-autocomplete), reaproveitado por qualquer tela que precise
 * escolher um cliente (Agenda, Contas a receber) - antes duplicado como
 * campo + botão "Buscar" + lista de resultados em cada uma dessas telas,
 * de forma ligeiramente diferente em cada lugar.
 */
@Component({
  selector: 'app-cliente-busca',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  templateUrl: './cliente-busca.html',
  styleUrl: './cliente-busca.css',
})
export class ClienteBusca {
  @Input() label = 'Buscar cliente por nome ou telefone';
  @Output() readonly clienteEscolhido = new EventEmitter<Cliente>();

  private readonly clientesService = inject(ClientesService);

  protected readonly controle = new FormControl('', { nonNullable: true });
  protected readonly opcoes = signal<Cliente[]>([]);
  private readonly termoAtual = toSignal(this.controle.valueChanges, { initialValue: '' });

  /** So' mostra "nenhum cliente encontrado" depois de uma busca de verdade - sem isso, a opcao "vazia" aparece so' por focar o campo, antes de digitar nada. */
  protected readonly mostrarSemResultado = computed(
    () => this.termoAtual().trim().length >= 2 && this.opcoes().length === 0,
  );

  /** Mantém o campo sempre em branco após escolher um cliente - a tela que usa este componente exibe o cliente escolhido em outro lugar, não dentro do próprio campo de busca. */
  protected readonly semExibicao = (): string => '';

  constructor() {
    this.controle.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((termo) => {
          const busca = termo.trim();
          if (busca.length < 2) {
            return of([]);
          }
          return this.clientesService.listar({ busca, size: 6 }).pipe(
            map((pagina) => pagina.content),
            // Uma busca que falha nao pode matar a subscription inteira (switchMap
            // propaga erro do observable interno pro externo) - sem isso, depois da
            // primeira falha de rede o campo para de buscar silenciosamente, sem
            // nenhum jeito de recuperar sem recarregar a pagina.
            catchError(() => of([])),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((clientes) => this.opcoes.set(clientes));
  }

  protected selecionar(evento: MatAutocompleteSelectedEvent): void {
    const cliente = evento.option.value as Cliente;
    this.clienteEscolhido.emit(cliente);
    this.opcoes.set([]);
    this.controle.setValue('', { emitEvent: false });
  }
}
