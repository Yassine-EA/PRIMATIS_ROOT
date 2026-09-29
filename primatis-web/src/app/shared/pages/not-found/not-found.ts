import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { PublicShell } from '../../../core/layouts/public-layout/public-shell/public-shell';


@Component({
  selector: 'app-not-found',
  imports: [RouterLink, ButtonModule, PublicShell],
  template: `
    <app-public-shell>
      <section>
        <h1>Page introuvable</h1>
        <p>La page demandée n'existe pas.</p>

        <p-button label="Retour à l'accueil" routerLink="/" />
      </section>
    </app-public-shell>
  `,
})
export class NotFound {}
