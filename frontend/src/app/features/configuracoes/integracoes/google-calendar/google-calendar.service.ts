import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AgendamentoForaDeSincronia,
  AtualizarModoCalendarioRequest,
  StatusIntegracaoGoogleCalendar,
  UrlAutorizacao,
} from './google-calendar.model';

const BASE_URL = '/api/integracoes/google-calendar';

@Injectable({ providedIn: 'root' })
export class GoogleCalendarService {
  private readonly http = inject(HttpClient);

  obterStatus(): Observable<StatusIntegracaoGoogleCalendar> {
    return this.http.get<StatusIntegracaoGoogleCalendar>(`${BASE_URL}/status`);
  }

  conectar(): Observable<UrlAutorizacao> {
    return this.http.get<UrlAutorizacao>(`${BASE_URL}/conectar`);
  }

  desconectar(): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/desconectar`, {});
  }

  atualizarModo(requisicao: AtualizarModoCalendarioRequest): Observable<AtualizarModoCalendarioRequest> {
    return this.http.put<AtualizarModoCalendarioRequest>(`${BASE_URL}/modo`, requisicao);
  }

  listarForaDeSincronia(): Observable<AgendamentoForaDeSincronia[]> {
    return this.http.get<AgendamentoForaDeSincronia[]>(`${BASE_URL}/fora-de-sincronia`);
  }

  ressincronizar(): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/ressincronizar`, {});
  }
}
