export type Perfil = 'ADMIN' | 'GERENTE' | 'BARBEIRO' | 'RECEPCAO';

export interface UsuarioAutenticado {
  uuid: string;
  nome: string;
  email: string;
  perfil: Perfil;
}

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiraEmSegundos: number;
  usuario: UsuarioAutenticado;
}

export interface Sessao {
  accessToken: string;
  refreshToken: string;
  usuario: UsuarioAutenticado;
}
