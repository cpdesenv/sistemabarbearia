import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CaixaDoDia,
  Comanda,
  ContaPagar,
  ContaReceber,
  CriarContaPagarRequest,
  CriarContaReceberRequest,
  CriarDespesaRequest,
  Despesa,
  FluxoCaixa,
  FormaPagamento,
  StatusContaPagar,
  StatusContaReceber,
} from './financeiro.model';

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

  listarDespesas(): Observable<Despesa[]> {
    return this.http.get<Despesa[]>('/api/despesas');
  }

  criarDespesa(dados: CriarDespesaRequest): Observable<Despesa> {
    return this.http.post<Despesa>('/api/despesas', dados);
  }

  listarContasPagar(status?: StatusContaPagar): Observable<ContaPagar[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ContaPagar[]>('/api/contas-pagar', { params });
  }

  criarContaPagar(dados: CriarContaPagarRequest): Observable<ContaPagar> {
    return this.http.post<ContaPagar>('/api/contas-pagar', dados);
  }

  marcarContaPagarPaga(uuid: string): Observable<ContaPagar> {
    return this.http.post<ContaPagar>(`/api/contas-pagar/${uuid}/pagar`, {});
  }

  cancelarContaPagar(uuid: string, motivo: string): Observable<ContaPagar> {
    return this.http.post<ContaPagar>(`/api/contas-pagar/${uuid}/cancelar`, { motivo });
  }

  listarContasReceber(status?: StatusContaReceber): Observable<ContaReceber[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ContaReceber[]>('/api/contas-receber', { params });
  }

  criarContaReceber(dados: CriarContaReceberRequest): Observable<ContaReceber> {
    return this.http.post<ContaReceber>('/api/contas-receber', dados);
  }

  marcarContaReceberRecebida(uuid: string): Observable<ContaReceber> {
    return this.http.post<ContaReceber>(`/api/contas-receber/${uuid}/receber`, {});
  }

  cancelarContaReceber(uuid: string, motivo: string): Observable<ContaReceber> {
    return this.http.post<ContaReceber>(`/api/contas-receber/${uuid}/cancelar`, { motivo });
  }

  fluxoCaixa(): Observable<FluxoCaixa> {
    return this.http.get<FluxoCaixa>('/api/financeiro/fluxo-caixa');
  }
}
