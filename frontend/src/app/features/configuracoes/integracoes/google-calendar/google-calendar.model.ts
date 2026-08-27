export type ModoCalendario = 'CALENDARIO_UNICO' | 'POR_PROFISSIONAL';

export interface StatusIntegracaoGoogleCalendar {
  conectado: boolean;
  modo: ModoCalendario;
  calendarioIdUnico: string | null;
  conectadoEm: string | null;
  ultimoErro: string | null;
}

export interface UrlAutorizacao {
  url: string;
}

export interface AtualizarModoCalendarioRequest {
  modo: ModoCalendario;
  calendarioIdUnico: string | null;
}

export interface AgendamentoForaDeSincronia {
  agendamentoUuid: string;
  clienteNome: string;
  inicioAgendamento: string;
  tipoOperacao: 'CRIAR' | 'ATUALIZAR' | 'REMOVER';
  status: 'PENDENTE' | 'CONCLUIDO' | 'FALHA_PERMANENTE';
  tentativas: number;
  ultimoErro: string | null;
}
