import { Component, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { GeorgesLemaitreTopicFooter } from './georges-lemaitre-topic-footer';

type TopicKey = 'univers-en-expansion' | 'atome-primitif' | 'charleroi' | 'pourquoi-primatis' | 'parcours' | 'distinctions';

type TopicSection = { eyebrow?: string; title: string; paragraphs: string[] };
type TopicSource = { label: string; url: string };
type Topic = {
  kicker: string;
  title: string;
  subtitle: string;
  image: string;
  imageAlt: string;
  icon: string;
  noteYear: string;
  noteTitle: string;
  noteText: string;
  facts: Array<{ label: string; value: string }>;
  sections: TopicSection[];
  sources: TopicSource[];
};

const UCL = 'https://archives.uclouvain.be/exhibits/show/georges-lemaitre';
const UCL_PERSON = 'https://archives.uclouvain.be/atom/index.php/lemaitre-georges';
const WIKI = 'https://fr.wikipedia.org/wiki/Georges_Lema%C3%AEtre';
const IAU = 'https://www.iau.org/static/resolutions/IAU2018_ResolB4_French.pdf';
const ESA = 'https://www.esa.int/Space_in_Member_States/Belgium_-_Francais/Le_cinquieme_ATV_baptise_du_nom_de_Georges_Lemaitre';

const TOPICS: Record<TopicKey, Topic> = {
  'univers-en-expansion': {
    kicker: '1927 · Cosmologie', title: "L'Univers en expansion",
    subtitle: "Une publication décisive qui relie la relativité générale à l'observation d'un cosmos en évolution.",
    image: '/assets/public/georges-lemaitre/science/universe-expansion-960.webp', imageAlt: "Illustration cosmologique de l'expansion de l'Univers",
    icon:'pi-chart-line', noteYear:'1927', noteTitle:'Expansion', noteText:"Le mémoire qui relie relativité et observations d'un Univers en évolution.",
    facts: [{label:'Année',value:'1927'},{label:'Discipline',value:'Cosmologie relativiste'},{label:'Lieu',value:'Louvain'},{label:'Héritage',value:'Loi de Hubble-Lemaître'}],
    sections: [
      { title: 'Un problème scientifique nouveau', paragraphs: ["Au début des années 1920, la relativité générale d'Einstein offre un nouveau cadre pour penser la structure globale de l'Univers. Lemaître cherche à confronter ces équations aux observations astronomiques plutôt qu'à imposer un cosmos nécessairement statique.", "Ses séjours auprès d'Arthur Eddington à Cambridge, de Harlow Shapley à Harvard puis au MIT le placent au contact direct de la relativité, de l'astronomie stellaire et des mesures des nébuleuses extragalactiques."]},
      { eyebrow: 'Publication majeure', title: 'Le mémoire de 1927', paragraphs: ["Dans les Annales de la Société scientifique de Bruxelles, Lemaître publie « Un Univers homogène de masse constante et de rayon croissant rendant compte de la vitesse radiale des nébuleuses extragalactiques ». Il y décrit une expansion physique de l'Univers.", "Il met également en relation les distances disponibles des galaxies et leurs vitesses de récession. Cette articulation entre théorie et observation constitue l'une des contributions historiques majeures de son travail."]},
      { title: 'De Lemaître à la loi de Hubble-Lemaître', paragraphs: ["Edwin Hubble publie en 1929 des observations qui consolident la relation distance-vitesse. En 2018, l'Union astronomique internationale recommande l'appellation « loi de Hubble-Lemaître » afin de mieux reconnaître la contribution historique de Lemaître à cette découverte."]},
    ], sources:[{label:'UCLouvain — Archives Georges Lemaître',url:UCL_PERSON},{label:'UAI — Résolution B4, loi de Hubble-Lemaître',url:IAU},{label:'Wikipédia — Georges Lemaître',url:WIKI}]
  },
  'atome-primitif': {
    kicker: '1931 · Origines', title: "L'atome primitif",
    subtitle: "L'hypothèse d'un état initial extraordinairement concentré, formulée bien avant que l'expression « Big Bang » ne s'impose.",
    image: '/assets/public/georges-lemaitre/science/primitive-atom-1280.webp', imageAlt: "Représentation artistique de l'idée d'atome primitif",
    icon:'pi-sun', noteYear:'1931', noteTitle:'Atome primitif', noteText:"Une intuition fondatrice sur l'origine physique du cosmos.",
    facts:[{label:'Année',value:'1931'},{label:'Concept',value:'Atome primitif'},{label:'Contexte',value:'Univers en expansion'},{label:'Postérité',value:'Cosmologie du Big Bang'}],
    sections:[
      {title:'Remonter le temps cosmique', paragraphs:["Si l'Univers est aujourd'hui en expansion, Lemaître comprend qu'il devait être plus dense dans le passé. Cette idée le conduit à s'interroger sur un commencement physique possible du cosmos."]},
      {eyebrow:'1931', title:"L'hypothèse du quantum initial", paragraphs:["Lors d'échanges et publications de 1931, Lemaître propose l'idée d'un début singulier de l'Univers en expansion et parle d'« atome primitif ». L'hypothèse suscite alors de vives réactions dans la communauté scientifique.", "Le vocabulaire et les modèles évolueront profondément par la suite, mais cette intuition constitue une étape fondatrice de l'histoire de la cosmologie moderne."]},
      {title:'Une idée devenue héritage scientifique', paragraphs:["La cosmologie contemporaine repose sur un ensemble de preuves et de modèles bien plus riches que ceux disponibles à Lemaître. Son mérite historique tient à avoir ouvert très tôt la possibilité d'un Univers ayant une histoire physique et un état initial très différent de celui observé aujourd'hui."]},
    ], sources:[{label:'UCLouvain — Georges Lemaître, père de la théorie du Big Bang',url:UCL},{label:'UCLouvain — Notice Georges Lemaître',url:UCL_PERSON},{label:'Wikipédia — Georges Lemaître',url:WIKI}]
  },
  charleroi: {
    kicker: '1894 · Ancrage local', title: 'Georges Lemaître et Charleroi',
    subtitle: "Du bassin carolorégien aux grands centres scientifiques internationaux : le point de départ d'un parcours exceptionnel.",
    image:'/assets/public/georges-lemaitre/editorial/charleroi-link-960-decorative.webp', imageAlt:'Ciel étoilé et coupole évoquant Charleroi et Georges Lemaître',
    icon:'pi-map-marker', noteYear:'1894', noteTitle:'Charleroi', noteText:"Le point de départ local d'un parcours scientifique international.",
    facts:[{label:'Naissance',value:'17 juillet 1894'},{label:'Ville',value:'Charleroi'},{label:'Études secondaires',value:'Collège du Sacré-Cœur'},{label:'Décès',value:'20 juin 1966, Louvain'}],
    sections:[
      {title:'Une enfance carolorégienne', paragraphs:["Georges Lemaître naît à Charleroi le 17 juillet 1894. Il grandit dans une famille de quatre enfants et suit une partie de sa scolarité au collège jésuite du Sacré-Cœur de Charleroi.", "À dix-sept ans, il entre à l'Université catholique de Louvain avec l'intention initiale de devenir ingénieur. La Première Guerre mondiale interrompt ce projet et transforme profondément son parcours."]},
      {title:"D'une ville industrielle à une œuvre universelle", paragraphs:["Après la guerre, Lemaître choisit finalement les mathématiques et la physique. Son itinéraire le mène ensuite à Cambridge, Harvard et au MIT avant son retour à Louvain.", "Cette trajectoire donne à son origine carolorégienne une valeur particulière : celle d'un point de départ local pour une contribution qui dépassera largement les frontières belges."]},
      {title:'Une mémoire locale à transmettre', paragraphs:["Pour PRIMATIS, rappeler son lien avec Charleroi ne consiste pas à réduire Lemaître à sa ville natale. Il s'agit au contraire de montrer comment un territoire peut être relié aux grandes aventures intellectuelles du XXe siècle."]},
    ], sources:[{label:'UCLouvain — Archives Georges Lemaître',url:UCL},{label:'Wikipédia — Biographie de Georges Lemaître',url:WIKI},{label:'ESA — Hommage au scientifique belge',url:ESA}]
  },
  'pourquoi-primatis': {
    kicker: 'Science · culture · transmission', title: 'Pourquoi PRIMATIS porte son nom ?',
    subtitle: "Une identité culturelle qui relie une bibliothèque publique, son territoire et l'histoire des sciences.",
    image:'/assets/public/georges-lemaitre/editorial/why-primatis-1920.webp', imageAlt:'Composition scientifique aux couleurs de PRIMATIS',
    icon:'pi-book', noteYear:'PRIMATIS', noteTitle:'Transmission', noteText:"Science, culture et accès partagé à la connaissance.",
    facts:[{label:'Territoire',value:'Charleroi'},{label:'Valeur',value:'Connaissance'},{label:'Mission',value:'Transmission'},{label:'Ouverture',value:'Science et culture'}],
    sections:[
      {title:'Un nom enraciné dans le territoire', paragraphs:["Georges Lemaître est né à Charleroi et demeure l'une des grandes figures scientifiques belges du XXe siècle. Son nom donne à la bibliothèque un lien immédiat entre mémoire locale et rayonnement international."]},
      {title:'La connaissance comme bien partagé', paragraphs:["Une bibliothèque publique n'est pas un laboratoire de cosmologie. Elle partage néanmoins avec la démarche scientifique une exigence essentielle : permettre à chacun d'accéder aux connaissances, de confronter les idées et de poursuivre sa curiosité."]},
      {title:'Science et culture sur un même horizon', paragraphs:["PRIMATIS assume une identité où littérature, histoire, sciences et culture dialoguent. Le nom de Lemaître devient ainsi un symbole de curiosité intellectuelle et d'ouverture, plutôt qu'un simple élément décoratif."]},
    ], sources:[{label:'UCLouvain — Projet Georges Lemaître',url:UCL},{label:'ESA — Hommage à Georges Lemaître',url:ESA}]
  },
  parcours: {
    kicker: '1894–1966 · Biographie', title: 'Le parcours de Georges Lemaître',
    subtitle: "De Charleroi à Louvain, en passant par la guerre, Cambridge, Harvard et le MIT.",
    image:'/assets/public/georges-lemaitre/hero/georges-lemaitre-background-1280.webp', imageAlt:'Ciel étoilé associé à Georges Lemaître',
    icon:'pi-graduation-cap', noteYear:'1894–1966', noteTitle:'Parcours', noteText:"De Charleroi à Louvain, Cambridge, Harvard et le MIT.",
    facts:[{label:'1894',value:'Naissance à Charleroi'},{label:'1914–1918',value:'Armée belge'},{label:'1923–1924',value:'Cambridge'},{label:'1924–1927',value:'Harvard et MIT'}],
    sections:[
      {eyebrow:'1894–1914', title:'Jeunesse et premières études', paragraphs:["Né à Charleroi en 1894, Lemaître poursuit des études secondaires chez les jésuites puis entre à l'Université catholique de Louvain en 1911. Il se destine alors à l'ingénierie."]},
      {eyebrow:'1914–1920', title:'La guerre puis le choix des mathématiques', paragraphs:["Au déclenchement de la Première Guerre mondiale, il s'engage comme volontaire dans l'armée belge et participe notamment à la bataille de l'Yser. Après la guerre, décoré de la Croix de guerre, il reprend ses études et s'oriente vers les mathématiques et les sciences physiques.", "Il soutient son doctorat en mathématiques en 1920 sous la direction de Charles de la Vallée Poussin."]},
      {eyebrow:'1920–1923', title:'Séminaire et ordination', paragraphs:["Lemaître entre au grand séminaire de Malines et est ordonné prêtre en 1923. Sa vocation religieuse se poursuit parallèlement à sa carrière scientifique ; il ne les confond pas et défend l'autonomie de la recherche scientifique."]},
      {eyebrow:'1923–1927', title:'Cambridge, Harvard et le MIT', paragraphs:["À Cambridge, Arthur Eddington l'initie à l'astronomie stellaire moderne et aux méthodes numériques. Lemaître passe ensuite par Harvard auprès de Harlow Shapley et poursuit un doctorat au MIT.", "En 1927, il y soutient une thèse de sciences sur le champ gravitationnel dans un fluide relativiste, tout en publiant la même année son travail majeur sur l'Univers en expansion."]},
      {eyebrow:'1925–1966', title:'Louvain et une carrière internationale', paragraphs:["Nommé chargé de cours à Louvain en 1925 puis professeur ordinaire en 1927, Lemaître y mène l'essentiel de sa carrière académique. Ses travaux, voyages et participations à des congrès entretiennent toutefois un réseau scientifique très international jusqu'à la fin de sa vie."]},
    ], sources:[{label:'UCLouvain — Notice Georges Lemaître',url:UCL_PERSON},{label:'Wikipédia — Études et carrière',url:WIKI}]
  },
  distinctions: {
    kicker: '1934–1966 · Reconnaissance', title: 'Distinctions et postérité',
    subtitle: "Récompenses scientifiques, académies et hommages : la reconnaissance progressive d'une œuvre majeure.",
    image:'/assets/public/georges-lemaitre/hero/georges-lemaitre-portrait-1024.webp', imageAlt:'Portrait illustré de Georges Lemaître',
    icon:'pi-trophy', noteYear:'1934–1966', noteTitle:'Reconnaissance', noteText:"Prix, académies et postérité d'une œuvre scientifique majeure.",
    facts:[{label:'1934',value:'Prix Francqui'},{label:'1936',value:'Académie pontificale des sciences'},{label:'1953',value:'Médaille Eddington'},{label:'1960–1966',value:'Présidence de l’Académie pontificale'}],
    sections:[
      {eyebrow:'1934', title:'Le Prix Francqui', paragraphs:["Le 17 mars 1934, Georges Lemaître reçoit le Prix Francqui, l'une des plus hautes distinctions scientifiques belges. Parmi ses parrains figure Albert Einstein ; le jury international réunit notamment Arthur Eddington et Paul Langevin."]},
      {eyebrow:'Académies', title:'Une reconnaissance institutionnelle', paragraphs:["Lemaître est élu en 1936 à l'Académie pontificale des sciences. Il en devient président en 1960 et conserve cette fonction jusqu'à sa mort en 1966. Il reçoit également plusieurs distinctions belges et internationales au fil de sa carrière."]},
      {eyebrow:'1953', title:'La médaille Eddington', paragraphs:["La médaille Eddington, attribuée par la Royal Astronomical Society, vient saluer la portée de ses travaux en astrophysique et cosmologie. Elle porte le nom de celui qui fut l'un de ses maîtres scientifiques à Cambridge."]},
      {eyebrow:'Postérité', title:"Un nom désormais associé à l'histoire de la cosmologie", paragraphs:["La reconnaissance de Lemaître continue après sa mort. L'Union astronomique internationale recommande en 2018 l'appellation « loi de Hubble-Lemaître ». L'Agence spatiale européenne a également donné son nom à l'ATV-5, véhicule de ravitaillement de la Station spatiale internationale."]},
    ], sources:[{label:'UCLouvain — Notice Georges Lemaître',url:UCL_PERSON},{label:'UAI — Loi de Hubble-Lemaître',url:IAU},{label:'ESA — ATV Georges Lemaître',url:ESA},{label:'Wikipédia — Distinctions',url:WIKI}]
  },
};

@Component({
  selector: 'app-georges-lemaitre-topic',
  imports: [RouterLink, GeorgesLemaitreTopicFooter],
  templateUrl: './georges-lemaitre-topic.html',
  styleUrl: './georges-lemaitre-topic.scss',
})
export class GeorgesLemaitreTopicPage {
  private readonly route = inject(ActivatedRoute);
  private readonly titleService = inject(Title);

  readonly topicKey = this.route.snapshot.data['topic'] as TopicKey;
  readonly topic = TOPICS[this.topicKey];

  constructor() {
    this.titleService.setTitle(`${this.topic.title} — Georges Lemaître — PRIMATIS`);
  }

  sectionHref(sectionNumber: number): string {
    return `/georges-lemaitre/${this.topicKey}#section-${sectionNumber}`;
  }

  scrollToSection(event: Event, sectionNumber: number): void {
    event.preventDefault();

    const fragment = `section-${sectionNumber}`;
    const target = document.getElementById(fragment);

    if (!target) {
      return;
    }

    window.history.replaceState(
      window.history.state,
      '',
      `/georges-lemaitre/${this.topicKey}#${fragment}`,
    );

    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
