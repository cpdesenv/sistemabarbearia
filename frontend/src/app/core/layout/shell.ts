import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthService } from '../auth/auth.service';
import { Perfil } from '../auth/auth.model';

interface ItemMenu {
  rota: string;
  rotulo: string;
  perfis?: Perfil[];
}

const ITENS_MENU: ItemMenu[] = [
  { rota: '/dashboard', rotulo: 'Dashboard' },
  { rota: '/agenda', rotulo: 'Agenda' },
  { rota: '/clientes', rotulo: 'Clientes' },
  { rota: '/servicos', rotulo: 'Serviços' },
  { rota: '/profissionais', rotulo: 'Profissionais' },
  { rota: '/bloqueios', rotulo: 'Bloqueios' },
  { rota: '/configuracoes/barbearia', rotulo: 'Configurações', perfis: ['ADMIN'] },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly usuario = this.authService.usuario;

  protected readonly itensMenu = computed(() => {
    const perfilAtual = this.usuario()?.perfil;
    return ITENS_MENU.filter((item) => !item.perfis || (perfilAtual && item.perfis.includes(perfilAtual)));
  });

  protected sair(): void {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
