import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Bloqueio, CriarBloqueioRequest, FiltroBloqueios, PaginaBloqueios } from './bloqueios.model';

@Injectable({ providedIn: 'root' })
export class BloqueiosService {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroBloqueios): Observable<PaginaBloqueios> {
    let params = new HttpParams()
      .set('page', String(filtro.page ?? 0))
      .set('size', String(filtro.size ?? 20));

    if (filtro.profissionalUuid) {
      params = params.set('profissionalUuid', filtro.profissionalUuid);
    }

    return this.http.get<PaginaBloqueios>('/api/bloqueios', { params });
  }

  criar(dados: CriarBloqueioRequest): Observable<Bloqueio> {
    return this.http.post<Bloqueio>('/api/bloqueios', dados);
  }

  remover(uuid: string): Observable<void> {
    return this.http.delete<void>(`/api/bloqueios/${uuid}`);
  }
}
