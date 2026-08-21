import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';

import { ConfirmDialogComponent } from './confirm-dialog';
import { ConfirmDialogData, ConfirmDialogResult } from './confirm-dialog.model';

/** Substitui window.confirm/window.prompt por um modal proprio, que nao trava a aba do navegador. */
@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly dialog = inject(MatDialog);

  confirm(data: ConfirmDialogData): Observable<ConfirmDialogResult> {
    return this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, ConfirmDialogResult>(ConfirmDialogComponent, {
        data,
        width: '420px',
        autoFocus: data.requireReason ? 'first-tabbable' : 'dialog',
      })
      .afterClosed()
      .pipe(map((resultado) => resultado ?? { confirmed: false }));
  }
}
