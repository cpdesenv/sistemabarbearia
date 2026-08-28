export interface ConfiguracaoIa {
  ativo: boolean;
  limiteTurnos: number;
  tetoCustoMensalCentavos: number;
}

export type AtualizarConfiguracaoIaRequest = ConfiguracaoIa;
