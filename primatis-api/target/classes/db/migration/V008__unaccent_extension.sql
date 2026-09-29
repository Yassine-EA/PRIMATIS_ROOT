-- =============================================================
-- DEV-18.5 — Extension unaccent (recherche catalogue, F-04)
-- =============================================================
--
-- La recherche catalogue (TitleSpecifications, paramètre q) compare
-- lower(title) / lower(author.full_name) à un motif LIKE : « Ils etaient
-- dix » ne retrouvait donc pas « Ils étaient dix ». L'extension contrib
-- PostgreSQL « unaccent » fournit la fonction unaccent(text) utilisée des
-- deux côtés de la comparaison (colonne ET motif), ce qui rend la recherche
-- insensible aux accents sans colonne dérivée ni index supplémentaire
-- (aucun index n'est justifié par la mesure : ~5 ms sur 14 600 titres).
--
-- Prérequis de déploiement : le paquet contrib de PostgreSQL doit être
-- installé et le rôle de migration (SPRING_DATASOURCE_USERNAME) doit pouvoir
-- exécuter CREATE EXTENSION. « unaccent » est une extension « trusted »
-- (PostgreSQL >= 13) : le propriétaire de la base suffit, pas besoin de
-- superutilisateur.

CREATE EXTENSION IF NOT EXISTS unaccent;
