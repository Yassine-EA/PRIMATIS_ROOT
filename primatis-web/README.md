# primatis-web

Application web de PRIMATIS : l'interface utilisée par les visiteurs, les membres, le personnel et les administrateurs de la Bibliothèque Georges Lemaître. Elle communique avec l'API [`primatis-api`](../primatis-api/README.md) en HTTP / JSON.

## Technologies

Angular 21.2, PrimeNG 21, TypeScript 5.9, RxJS, Quill (éditeur des articles), Vitest (tests), Playwright (tests de bout en bout). Versions utilisées : [TECHNOLOGIES.md](../TECHNOLOGIES.md).

## Prérequis

- Node.js 24 et npm 11 (versions utilisées : 24.18.0 et 11.16.0) ;
- l'API démarrée sur `localhost:8080` pour utiliser l'application (voir [INSTALLATION.md](../INSTALLATION.md)).

## Installation

```bash
npm ci
```

`npm ci` installe exactement les versions figées dans `package-lock.json` : c'est la commande d'installation de référence (elle ne modifie pas le fichier de verrouillage).

## Démarrage

```bash
npm start
```

L'application est servie sur <http://localhost:4200>.

### Proxy vers l'API

L'application appelle toujours l'URL relative `/api/v1` (fichier `src/environments/environment.ts`). Le serveur de développement transmet `/api` et `/media` à l'API sur `http://localhost:8080` grâce à `proxy.conf.json`, ce qui évite toute configuration CORS côté navigateur. Si l'API écoute sur un autre port, modifier la cible dans `proxy.conf.json`. Ce proxy ne concerne que `ng serve` ; il n'intervient pas dans un build de production.

## Structure

```text
primatis-web/
├── package.json, package-lock.json
├── angular.json, tsconfig*.json
├── proxy.conf.json             proxy de développement (API sur :8080)
├── proxy.conf.e2e.json         proxy des tests de bout en bout (API sur :8180)
├── playwright.config.ts        configuration des tests de bout en bout
├── public/                     fichiers statiques : images, logos, couvertures du catalogue
├── src/app/
│   ├── core/, shared/          authentification, intercepteurs, gardes, thème, composants génériques
│   ├── auth/, catalogue/, articles/, settings/, user/     modèles et services d'accès à l'API
│   ├── member/                 espace membre (prêts, réservations, amendes, notifications, profil)
│   ├── staff/                  espace du personnel
│   └── admin/                  administration (utilisateurs, paramètres)
└── e2e/                        tests de bout en bout (tests, fixtures, vérification de la cible)
```

Zones de navigation : publique (`/`, `/catalogue`, `/articles`, `/georges-lemaitre`, `/login`), membre (`/member/…`), personnel (`/staff/…`), administration (`/admin/…`). Les gardes de route améliorent l'expérience mais ne sont pas une barrière de sécurité : l'API revalide chaque opération.

## Tests

### Tests unitaires (Vitest)

```bash
npm test -- --watch=false
```

Sans `--watch=false`, la commande reste en mode surveillance dans un terminal interactif.

### Tests de bout en bout (Playwright) — optionnels

Ces tests ne sont nécessaires ni à l'installation ni à la démonstration. Ils utilisent une base **séparée et jetable** `primatis_e2e` et ne doivent **jamais** être exécutés contre `primatis_demo`.

```bash
npx playwright install chromium   # une seule fois
set -a && source ../primatis-api/.env.local && set +a   # variables lues par le script de remise à zéro
npm run e2e:reset                 # remet primatis_e2e à zéro
npm run e2e                       # démarre l'API (:8180) et l'interface de test (:4300), puis exécute la suite
```

La suite démarre ses propres serveurs sur des ports dédiés (`8180` et `4300`), sans jamais réutiliser un serveur existant, et refuse de s'exécuter si l'API jointe n'est pas celle du profil de test de bout en bout. Prérequis complets (base, shell `bash`, fichier `primatis-api/.env.local`) : [INSTALLATION.md](../INSTALLATION.md#tests).

## Build de production

```bash
npm run build
```

Le résultat est écrit dans `dist/`. La démonstration n'en a pas besoin : elle utilise `npm start`.
