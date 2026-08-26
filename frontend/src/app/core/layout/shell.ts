import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthService } from '../auth/auth.service';
import { Perfil } from '../auth/auth.model';

interface ItemMenu {
  rota: string;
  rotulo: string;
  perfis?: Perfil[];
}

const ROTULOS_PERFIL: Record<Perfil, string> = {
  ADMIN: 'Administrador',
  GERENTE: 'Gerente',
  BARBEIRO: 'Barbeiro',
  RECEPCAO: 'Recepção',
};

const ITENS_MENU: ItemMenu[] = [
  { rota: '/dashboard', rotulo: 'Dashboard' },
  { rota: '/agenda', rotulo: 'Agenda' },
  { rota: '/clientes', rotulo: 'Clientes' },
  { rota: '/servicos', rotulo: 'Serviços' },
  { rota: '/profissionais', rotulo: 'Profissionais' },
  { rota: '/bloqueios', rotulo: 'Bloqueios' },
  { rota: '/produtos', rotulo: 'Catálogo de produtos' },
  { rota: '/produtos/estoque', rotulo: 'Estoque e movimentações' },
  { rota: '/financeiro/caixa', rotulo: 'Caixa do dia' },
  { rota: '/financeiro/contas', rotulo: 'Contas a pagar/receber' },
  { rota: '/financeiro/fluxo-caixa', rotulo: 'Fluxo de caixa' },
  { rota: '/clube-cavalinho', rotulo: 'Clube Cavalinho' },
  { rota: '/configuracoes/barbearia', rotulo: 'Configurações', perfis: ['ADMIN'] },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatMenuModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly usuario = this.authService.usuario;

  /** Menu lateral em telas estreitas (ver shell.css): fechado por padrao, vira um painel deslizante. */
  protected readonly menuAberto = signal(false);

  protected readonly itensMenu = computed(() => {
    const perfilAtual = this.usuario()?.perfil;
    return ITENS_MENU.filter((item) => !item.perfis || (perfilAtual && item.perfis.includes(perfilAtual)));
  });

  protected readonly rotuloPerfil = computed(() => {
    const perfil = this.usuario()?.perfil;
    return perfil ? ROTULOS_PERFIL[perfil] : '';
  });

  protected readonly iniciais = computed(() => {
    const nome = this.usuario()?.nome.trim() ?? '';
    const partes = nome.split(/\s+/).filter(Boolean);
    if (partes.length === 0) {
      return '';
    }
    if (partes.length === 1) {
      return partes[0].slice(0, 2).toUpperCase();
    }
    return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
  });

  protected alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fecharMenu(): void {
    this.menuAberto.set(false);
  }

  protected sair(): void {
    this.authService.logout();
    this.router.navigateByUrl('/login');
  }
}
