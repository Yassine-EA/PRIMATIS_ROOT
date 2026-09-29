import { Component, input, output } from '@angular/core';


@Component({
  selector: 'app-error-state',
  templateUrl: './error-state.html',
  styleUrl: './error-state.scss',
})
export class ErrorState {
  readonly message = input.required<string>();
  readonly showRetry = input(false);

  readonly retry = output<void>();
}
