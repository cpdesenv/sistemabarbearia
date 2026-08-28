import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Conversa, Mensagem, ModoAtendimento, PaginaConversas } from './mensageria.model';

@Injectable({ providedIn: 'root' })
export class MensageriaService {
  private readonly http = inject(HttpClient);

  listarConversas(page = 0, size = 20, status?: ModoAtendimento | null): Observable<PaginaConversas> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PaginaConversas>('/api/conversas', { params });
  }

  obterConversa(conversaUuid: string): Observable<Conversa> {
    return this.http.get<Conversa>(`/api/conversas/${conversaUuid}`);
  }

  listarMensagens(conversaUuid: string): Observable<Mensagem[]> {
    return this.http.get<Mensagem[]>(`/api/conversas/${conversaUuid}/mensagens`);
  }

  assumirConversa(conversaUuid: string): Observable<Conversa> {
    return this.http.post<Conversa>(`/api/conversas/${conversaUuid}/assumir`, {});
  }
}
