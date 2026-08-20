import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AtualizarBarbeariaRequest, Barbearia } from './barbearia.model';

@Injectable({ providedIn: 'root' })
export class BarbeariaService {
  private readonly http = inject(HttpClient);

  obter(): Observable<Barbearia> {
    return this.http.get<Barbearia>('/api/barbearia');
  }

  atualizar(dados: AtualizarBarbeariaRequest): Observable<Barbearia> {
    return this.http.put<Barbearia>('/api/barbearia', dados);
  }
}
