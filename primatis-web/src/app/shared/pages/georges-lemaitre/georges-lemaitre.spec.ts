import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GeorgesLemaitrePage } from './georges-lemaitre';

describe('GeorgesLemaitrePage', () => {
  let fixture: ComponentFixture<GeorgesLemaitrePage>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [GeorgesLemaitrePage], providers: [provideRouter([])] }).compileComponents();
    fixture = TestBed.createComponent(GeorgesLemaitrePage); fixture.detectChanges();
  });
  it('should render the title', () => expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Georges Lemaître'));
  it('should expose six clickable timeline destinations', () => expect(fixture.nativeElement.querySelectorAll('.lemaitre-route-timeline a').length).toBe(6));
  it('should expose four thematic cards', () => expect(fixture.nativeElement.querySelectorAll('.lemaitre-v4-card').length).toBe(4));
  it('should expose parcours and distinctions routes', () => {
    const html: string = fixture.nativeElement.innerHTML;
    expect(html).toContain('/georges-lemaitre/parcours');
    expect(html).toContain('/georges-lemaitre/distinctions');
  });
});
