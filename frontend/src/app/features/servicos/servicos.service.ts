import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FiltroServicos, PaginaServicos, SalvarServicoRequest, Servico } from './servicos.model';

@Injectable({ providedIn: 'root' })
export class ServicosService {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroServicos): Observable<PaginaServicos> {
    let params = new HttpParams()
      .set('page', String(filtro.page ?? 0))
      .set('size', String(filtro.size ?? 20));

    if (filtro.nome) {
      params = params.set('nome', filtro.nome);
    }
    if (filtro.categoria) {
      params = params.set('categoria', filtro.categoria);
    }
    if (filtro.ativo !== undefined) {
      params = params.set('ativo', String(filtro.ativo));
    }

    return this.http.get<PaginaServicos>('/api/servicos', { params });
  }

  obter(uuid: string): Observable<Servico> {
    return this.http.get<Servico>(`/api/servicos/${uuid}`);
  }

  criar(dados: SalvarServicoRequest): Observable<Servico> {
    return this.http.post<Servico>('/api/servicos', dados);
  }

  atualizar(uuid: string, dados: SalvarServicoRequest): Observable<Servico> {
    return this.http.put<Servico>(`/api/servicos/${uuid}`, dados);
  }

  atualizarStatus(uuid: string, ativo: boolean): Observable<Servico> {
    return this.http.patch<Servico>(`/api/servicos/${uuid}/status`, { ativo });
  }
}
