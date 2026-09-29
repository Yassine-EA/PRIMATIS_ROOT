import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface TopicSource {
  label: string;
  url: string;
}


@Component({
  selector: 'app-georges-lemaitre-topic-footer',
  imports: [RouterLink],
  templateUrl: './georges-lemaitre-topic-footer.html',
  styleUrl: './georges-lemaitre-topic-footer.scss',
})
export class GeorgesLemaitreTopicFooter {
  readonly sources = input.required<TopicSource[]>();
  readonly topicKey = input.required<string>();
}
