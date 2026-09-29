import { Component, input } from '@angular/core';


@Component({
  selector: 'app-empty-state',
  templateUrl: './empty-state.html',
  styleUrl: './empty-state.scss',
})
export class EmptyState {
  readonly title = input('Aucun résultat');
  readonly message = input('Il n’y a rien à afficher pour le moment.');
}
