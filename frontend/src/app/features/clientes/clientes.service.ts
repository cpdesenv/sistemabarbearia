import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Cliente,
  ExportacaoCliente,
  FichaCliente,
  FiltroClientes,
  PaginaClientes,
  SalvarClienteRequest,
} from './clientes.model';

@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroClientes): Observable<PaginaClientes> {
    let params = new HttpParams()
      .set('page', String(filtro.page ?? 0))
      .set('size', String(filtro.size ?? 20));

    if (filtro.busca) {
      params = params.set('busca', filtro.busca);
    }

    return this.http.get<PaginaClientes>('/api/clientes', { params });
  }

  obter(uuid: string): Observable<Cliente> {
    return this.http.get<Cliente>(`/api/clientes/${uuid}`);
  }

  ficha(uuid: string): Observable<FichaCliente> {
    return this.http.get<FichaCliente>(`/api/clientes/${uuid}/ficha`);
  }

  criar(dados: SalvarClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>('/api/clientes', dados);
  }

  atualizar(uuid: string, dados: SalvarClienteRequest): Observable<Cliente> {
    return this.http.put<Cliente>(`/api/clientes/${uuid}`, dados);
  }

  exportarDados(uuid: string): Observable<ExportacaoCliente> {
    return this.http.get<ExportacaoCliente>(`/api/clientes/${uuid}/exportar-dados`);
  }

  anonimizar(uuid: string, motivo: string): Observable<Cliente> {
    return this.http.post<Cliente>(`/api/clientes/${uuid}/anonimizar`, { motivo });
  }
}
