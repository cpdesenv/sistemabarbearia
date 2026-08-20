export type OrigemCadastro = 'WHATSAPP' | 'PORTAL' | 'PAINEL';

export interface Cliente {
  uuid: string;
  nome: string;
  telefone: string | null;
  whatsapp: string | null;
  cpf: string | null;
  email: string | null;
  logradouro: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string | null;
  uf: string | null;
  cep: string | null;
  dataNascimento: string | null;
  observacoes: string | null;
  optInWhatsapp: boolean;
  origemCadastro: OrigemCadastro;
  consentimentoLgpd: boolean;
  consentimentoLgpdEm: string | null;
  anonimizado: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface SalvarClienteRequest {
  nome: string;
  telefone: string;
  whatsapp: string | null;
  cpf: string | null;
  email: string | null;
  logradouro: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string | null;
  uf: string | null;
  cep: string | null;
  dataNascimento: string | null;
  observacoes: string | null;
  optInWhatsapp: boolean;
  consentimentoLgpd: boolean;
}

export interface PaginaClientes {
  content: Cliente[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface FiltroClientes {
  busca?: string;
  page?: number;
  size?: number;
}

export interface FichaCliente {
  cliente: Cliente;
  agendamentos: unknown[];
  atendimentos: unknown[];
  notasFiscais: unknown[];
}

export interface ClienteResumo {
  uuid: string;
  nome: string;
  telefone: string;
}

export interface ClienteDuplicadoResposta {
  mensagem: string;
  clienteExistente: ClienteResumo;
}

export interface ExportacaoCliente {
  exportadoEm: string;
  dados: Cliente;
}
