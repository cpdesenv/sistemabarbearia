export type ModoAtendimento = 'IA' | 'HUMANO';

export interface Conversa {
  uuid: string;
  clienteUuid: string;
  clienteNome: string;
  telefoneE164: string;
  ultimaMensagemEm: string | null;
  modoAtendimento: ModoAtendimento;
  motivoEscalonamento: string | null;
  turnosIa: number;
  custoLlmAcumuladoCentavos: number;
}

export interface PaginaConversas {
  content: Conversa[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export type DirecaoMensagem = 'ENTRADA' | 'SAIDA';
export type TipoMensagem = 'TEXTO' | 'TEMPLATE' | 'INTERATIVO' | 'DOCUMENTO';
export type StatusMensagem = 'RECEBIDA' | 'PENDENTE' | 'ENVIADA' | 'ENTREGUE' | 'LIDA' | 'FALHA';

export interface Mensagem {
  uuid: string;
  direcao: DirecaoMensagem;
  tipo: TipoMensagem;
  conteudo: string;
  status: StatusMensagem;
  criadoEm: string;
}
