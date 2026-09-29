# Installation de PRIMATIS

Cette procédure permet d’installer et de lancer PRIMATIS sur une machine locale.

Les commandes ci-dessous sont prévues pour Linux ou WSL Ubuntu.

## 1. Prérequis

Installer :

- Java 21
- PostgreSQL 17
- Node.js 24
- npm 11
- OpenSSL

Maven ne doit pas être installé séparément : le projet utilise Maven Wrapper.

Les versions principales sont reprises dans `TECHNOLOGIES.md`.

## 2. Base PostgreSQL

Ouvrir PostgreSQL avec un compte administrateur :

```bash
sudo -u postgres psql
```

Créer le rôle applicatif :

```sql
CREATE ROLE primatis WITH LOGIN;
```

Définir son mot de passe :

```text
\password primatis
```

Créer la base :

```sql
CREATE DATABASE primatis_demo
OWNER primatis
ENCODING 'UTF8'
TEMPLATE template0;
```

Quitter PostgreSQL :

```text
\q
```

Restaurer le dump fourni :

```bash
pg_restore \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  --single-transaction \
  --host localhost \
  --port 5432 \
  --username primatis \
  --dbname primatis_demo \
  database/primatis_demo.dump
```

Le mot de passe du rôle `primatis` sera demandé.

## 3. Configuration

Depuis la racine du projet :

```bash
cp .env.example .env
```

Ouvrir `.env` et renseigner uniquement le mot de passe PostgreSQL :

```text
SPRING_DATASOURCE_PASSWORD=VOTRE_MOT_DE_PASSE
```

Toujours depuis la racine du projet :

```bash
export PRIMATIS_HOME="$(pwd)"
set -a
source .env
set +a
```

La variable `PRIMATIS_HOME` permet d’utiliser automatiquement le chemin du dossier PRIMATIS, quel que soit son emplacement sur la machine.

## 4. Génération des clés JWT

Depuis la racine du projet :

```bash
mkdir -p runtime/keys

openssl genpkey \
  -algorithm RSA \
  -out runtime/keys/jwt-private.pem \
  -pkeyopt rsa_keygen_bits:2048

openssl rsa \
  -pubout \
  -in runtime/keys/jwt-private.pem \
  -out runtime/keys/jwt-public.pem
```

Aucun chemin absolu ne doit être renseigné manuellement dans `.env`.

## 5. Lancement du backend

Si nécessaire, rendre Maven Wrapper exécutable :

```bash
chmod +x primatis-api/mvnw
```

Dans le même terminal où les variables d’environnement ont été chargées :

```bash
cd primatis-api
./mvnw spring-boot:run
```

L’API démarre sur :

`http://localhost:8080`

## 6. Lancement du frontend

Ouvrir un second terminal :

```bash
cd /chemin/vers/PRIMATIS/primatis-web
npm ci
npm start
```

L’application est accessible à l’adresse :

`http://localhost:4200`

## 7. Comptes de démonstration

Les comptes permettant de tester les rôles administrateur, bibliothécaire et membre sont disponibles dans :

`docs/demo/DEMO-CREDENTIALS.md`
