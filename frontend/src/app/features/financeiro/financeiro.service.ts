import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CaixaDoDia, Comanda, FormaPagamento } from './financeiro.model';

@Injectable({ providedIn: 'root' })
export class FinanceiroService {
  private readonly http = inject(HttpClient);

  abrirParaAgendamento(agendamentoUuid: string): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/comandas/abrir-para-agendamento/${agendamentoUuid}`, {});
  }

  obterComanda(uuid: string): Observable<Comanda> {
    return this.http.get<Comanda>(`/api/comandas/${uuid}`);
  }

  adicionarItem(comandaUuid: string, servicoUuid: string, quantidade = 1): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/comandas/${comandaUuid}/itens`, { servicoUuid, quantidade });
  }

  adicionarItemProduto(comandaUuid: string, produtoUuid: string, quantidade = 1): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/comandas/${comandaUuid}/itens/produto`, { produtoUuid, quantidade });
  }

  removerItem(comandaUuid: string, itemUuid: string): Observable<Comanda> {
    return this.http.delete<Comanda>(`/api/comandas/${comandaUuid}/itens/${itemUuid}`);
  }

  aplicarDesconto(comandaUuid: string, valor: number, motivo: string | null): Observable<Comanda> {
    return this.http.put<Comanda>(`/api/comandas/${comandaUuid}/desconto`, { valor, motivo });
  }

  definirFormaPagamento(comandaUuid: string, formaPagamento: FormaPagamento): Observable<Comanda> {
    return this.http.put<Comanda>(`/api/comandas/${comandaUuid}/forma-pagamento`, { formaPagamento });
  }

  fechar(comandaUuid: string): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/comandas/${comandaUuid}/fechar`, {});
  }

  estornar(comandaUuid: string, motivo: string): Observable<Comanda> {
    return this.http.post<Comanda>(`/api/comandas/${comandaUuid}/estornar`, { motivo });
  }

  caixaDoDia(data: string): Observable<CaixaDoDia> {
    const params = new HttpParams().set('data', data);
    return this.http.get<CaixaDoDia>('/api/caixa', { params });
  }
}
