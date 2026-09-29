# primatis-api

API REST de PRIMATIS : authentification, autorisation, règles métier, transactions et persistance. Elle est consommée par l'application web [`primatis-web`](../primatis-web/README.md).

## Technologies

Java 21, Spring Boot 3.5.16 (Web, Security, Data JPA, Validation, OAuth2 Resource Server), Hibernate, PostgreSQL 17, Flyway, springdoc-openapi, jsoup. Versions utilisées : [TECHNOLOGIES.md](../TECHNOLOGIES.md).

## Structure

```text
primatis-api/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/          Maven Wrapper (Maven 3.9.9)
├── scripts/e2e-db-reset.sh        remise à zéro de la base de test de bout en bout
└── src/
    ├── main/java/be/primatis/     code, organisé par domaine (user, catalogue, loan, reservation,
    │                              fine, notification, article, setting, security, config, …)
    ├── main/resources/
    │   ├── application.yml        configuration commune (profil par défaut : développement)
    │   ├── application-*.yml      profils : demo, e2e, prod
    │   └── db/migration/          migrations Flyway V001 à V008
    └── test/                      tests JUnit
```

Chaque domaine suit la chaîne Controller → Service → Repository → Entity. Les Controllers ne portent que le contrat HTTP ; les règles métier et les transactions sont dans les Services.

## Prérequis

- JDK 21 ;
- PostgreSQL 17 avec les extensions contrib (`unaccent`) ;
- aucune installation de Maven (utiliser `./mvnw`, `mvnw.cmd` sous Windows).

## Profils et configuration

| Profil | Base | Usage |
|---|---|---|
| `demo` | `primatis_demo` (URL fournie par variable) | installation locale de démonstration — voir [INSTALLATION.md](../INSTALLATION.md) |
| (aucun, par défaut) | `primatis_dev` | développement |
| `test` | `primatis_test` | tests JUnit |
| `e2e` | `primatis_e2e` | tests de bout en bout (base jetable) |
| `prod` | fournie par l'environnement | variables toutes obligatoires ; OpenAPI désactivé |

Le profil `demo` (`application-demo.yml`) lit l'URL, le rôle et le mot de passe de la base, les clés JWT, l'origine CORS et le dossier des médias dans l'environnement, sans valeur par défaut : une variable absente empêche le démarrage. Il hérite de la configuration commune (Flyway actif, Hibernate en `validate`, schedulers à leur cadence normale) et laisse la description OpenAPI active, protégée par l'authentification ; les endpoints de test de bout en bout n'existent pas. Installation : [INSTALLATION.md](../INSTALLATION.md).

Aucun secret n'est stocké dans le projet : la configuration sensible passe par des variables d'environnement.

| Variable | Rôle |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC (profils `demo` et `prod` ; fixée dans le fichier pour les autres profils) |
| `SPRING_DATASOURCE_USERNAME` | rôle PostgreSQL (`primatis` par défaut sauf `prod`) |
| `SPRING_DATASOURCE_PASSWORD` | mot de passe PostgreSQL |
| `PRIMATIS_JWT_PRIVATE_KEY_PATH` / `PRIMATIS_JWT_PUBLIC_KEY_PATH` | clés RS256, URL `file:` absolue |
| `PRIMATIS_CORS_ALLOWED_ORIGINS` | origines de l'application web, séparées par des virgules (`http://localhost:4200` par défaut hors `prod`) |
| `PRIMATIS_ARTICLE_MEDIA_STORAGE_PATH` | dossier des images d'articles |
| `PRIMATIS_JWT_ISSUER`, `PRIMATIS_JWT_AUDIENCE` | option (défaut : `primatis-api`) |
| `SERVER_PORT` | option (défaut : `8080`) |

### Clés JWT

L'API signe ses jetons d'accès en RS256 (durée de vie : 1 heure). Les clés ne sont jamais versionnées ; génération d'une paire (clé privée au format PKCS#8) :

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
```

Les tests génèrent leur propre paire en mémoire et n'ont besoin d'aucun fichier de clé.

## Base de données et migrations

Le schéma est défini uniquement par Flyway (`src/main/resources/db/migration/`) ; Hibernate est configuré en `ddl-auto=validate`. Les migrations sont appliquées au démarrage. Une migration déjà appliquée ne se modifie jamais : une correction passe par une nouvelle migration.

La migration `V008` crée l'extension `unaccent` : le serveur PostgreSQL doit disposer des extensions contrib, et le rôle de l'application doit être propriétaire de la base.

Création d'une base de développement (avec un compte administrateur de PostgreSQL) :

```sql
CREATE ROLE primatis WITH LOGIN;
\password primatis
CREATE DATABASE primatis_dev OWNER primatis;
```

Pour la base de démonstration : [INSTALLATION.md](../INSTALLATION.md).

## Démarrage

```bash
export SPRING_DATASOURCE_USERNAME=primatis
export SPRING_DATASOURCE_PASSWORD='<votre mot de passe PostgreSQL>'

export PRIMATIS_HOME="$(cd .. && pwd)"
export PRIMATIS_JWT_PRIVATE_KEY_PATH=file:${PRIMATIS_HOME}/runtime/keys/jwt-private.pem
export PRIMATIS_JWT_PUBLIC_KEY_PATH=file:${PRIMATIS_HOME}/runtime/keys/jwt-public.pem
./mvnw spring-boot:run
```

L'API écoute sur <http://localhost:8080>. Pour la démonstration, ajouter le profil (`SPRING_PROFILES_ACTIVE=demo`) et les variables listées dans [INSTALLATION.md](../INSTALLATION.md#3-configuration).

## Tests

```bash
export SPRING_DATASOURCE_PASSWORD='<votre mot de passe PostgreSQL>'
./mvnw test
```

- Base PostgreSQL réelle `primatis_test` (à créer : `CREATE DATABASE primatis_test OWNER primatis;`), URL fixée à `localhost:5432` dans la configuration de test ; migrations Flyway réelles.
- Aucune base simulée ; les tests ne touchent jamais `primatis_demo`.

Les tests de bout en bout (dossier `primatis-web/e2e`) démarrent l'API avec le profil `e2e` sur `primatis_e2e` ; le script `scripts/e2e-db-reset.sh` remet cette base à zéro (cible littérale, jamais une autre base).

## Documentation de l'API

La description OpenAPI (`/v3/api-docs`) est générée par springdoc. Elle est protégée comme le reste de l'API : elle se lit avec un jeton Bearer obtenu par `POST /api/v1/auth/login`. Le profil `prod` la désactive.
