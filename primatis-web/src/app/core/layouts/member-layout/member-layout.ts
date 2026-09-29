import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PublicShell } from '../public-layout/public-shell/public-shell';
import { MemberNavigation } from './member-navigation/member-navigation';


@Component({
  selector: 'app-member-layout',
  imports: [RouterOutlet, PublicShell, MemberNavigation],
  templateUrl: './member-layout.html',
  styleUrl: './member-layout.scss',
})
export class MemberLayout {}
