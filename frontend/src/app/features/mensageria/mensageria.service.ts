import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Mensagem, PaginaConversas } from './mensageria.model';

@Injectable({ providedIn: 'root' })
export class MensageriaService {
  private readonly http = inject(HttpClient);

  listarConversas(page = 0, size = 20): Observable<PaginaConversas> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<PaginaConversas>('/api/conversas', { params });
  }

  listarMensagens(conversaUuid: string): Observable<Mensagem[]> {
    return this.http.get<Mensagem[]>(`/api/conversas/${conversaUuid}/mensagens`);
  }
}
