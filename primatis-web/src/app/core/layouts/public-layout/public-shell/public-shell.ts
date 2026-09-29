import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicNavigation } from '../public-navigation/public-navigation';


@Component({
  selector: 'app-public-shell',
  imports: [RouterLink, PublicNavigation],
  templateUrl: './public-shell.html',
  styleUrl: './public-shell.scss',
})
export class PublicShell {
  /** Année du copyright pied de page (DESIGN-V2-A) — jamais codée en dur. */
  protected readonly currentYear = new Date().getFullYear();
}
