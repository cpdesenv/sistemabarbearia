import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { ConfirmDialogData, ConfirmDialogResult } from './confirm-dialog.model';

@Component({
  selector: 'app-confirm-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.css',
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmDialogComponent, ConfirmDialogResult>);

  protected readonly reason = signal('');

  protected confirmar(): void {
    if (this.data.requireReason && this.reason().trim() === '') {
      return;
    }
    this.dialogRef.close({ confirmed: true, reason: this.reason().trim() || undefined });
  }

  protected cancelar(): void {
    this.dialogRef.close({ confirmed: false });
  }
}
