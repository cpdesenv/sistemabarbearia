export interface Bloqueio {
  uuid: string;
  profissionalUuid: string | null;
  profissionalNome: string | null;
  inicio: string;
  fim: string;
  motivo: string;
  criadoEm: string;
}

export interface CriarBloqueioRequest {
  profissionalUuid: string | null;
  inicio: string;
  fim: string;
  motivo: string;
}

export interface PaginaBloqueios {
  content: Bloqueio[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface FiltroBloqueios {
  profissionalUuid?: string;
  page?: number;
  size?: number;
}
