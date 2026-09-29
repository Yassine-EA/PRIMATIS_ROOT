import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { PublicShell } from '../../../core/layouts/public-layout/public-shell/public-shell';


@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, ButtonModule, PublicShell],
  template: `
    <app-public-shell>
      <section>
        <h1>Accès interdit</h1>
        <p>Vous ne disposez pas des autorisations nécessaires pour accéder à cette page.</p>

        <p-button label="Retour à l'accueil" routerLink="/" />
      </section>
    </app-public-shell>
  `,
})
export class Forbidden {}
