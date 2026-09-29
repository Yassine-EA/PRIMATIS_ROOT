import { Component, input } from '@angular/core';


@Component({
  selector: 'app-article-content',
  templateUrl: './article-content.html',
  styleUrl: './article-content.scss',
})
export class ArticleContent {
  readonly html = input.required<string>();
}
