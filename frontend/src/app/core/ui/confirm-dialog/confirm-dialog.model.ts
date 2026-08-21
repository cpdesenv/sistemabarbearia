export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  /** Quando true, exibe um campo de texto obrigatório (ex.: motivo do cancelamento/estorno). */
  requireReason?: boolean;
  reasonLabel?: string;
}

export interface ConfirmDialogResult {
  confirmed: boolean;
  /** Preenchido apenas quando `requireReason` foi pedido e o usuário confirmou. */
  reason?: string;
}
