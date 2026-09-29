import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

/**
 * Porte d'entrée éditoriale de l'espace Georges Lemaître.
 * Les repères chronologiques et cartes thématiques conduisent vers six
 * dossiers publics statiques ; aucun appel réseau et aucune règle métier.
 */
@Component({
  selector: 'app-georges-lemaitre',
  imports: [RouterLink],
  templateUrl: './georges-lemaitre.html',
  styleUrl: './georges-lemaitre.scss',
})
export class GeorgesLemaitrePage {
  private readonly titleService = inject(Title);

  constructor() {
    this.titleService.setTitle('Georges Lemaître — PRIMATIS');
  }
}
