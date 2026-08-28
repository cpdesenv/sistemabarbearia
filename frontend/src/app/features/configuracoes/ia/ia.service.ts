import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AtualizarConfiguracaoIaRequest, ConfiguracaoIa } from './ia.model';

@Injectable({ providedIn: 'root' })
export class ConfiguracaoIaService {
  private readonly http = inject(HttpClient);

  obter(): Observable<ConfiguracaoIa> {
    return this.http.get<ConfiguracaoIa>('/api/configuracoes/ia');
  }

  atualizar(dados: AtualizarConfiguracaoIaRequest): Observable<ConfiguracaoIa> {
    return this.http.put<ConfiguracaoIa>('/api/configuracoes/ia', dados);
  }
}
