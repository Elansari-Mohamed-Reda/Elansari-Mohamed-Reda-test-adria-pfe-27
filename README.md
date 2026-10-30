# Elansari-Mohamed-Reda-test-adria-pfe-27

Plateforme bancaire — épreuve technique ADRIA PFE 2027.

## État : étape 2, initialisation

Projet Maven parent avec sept applications Spring Boot exécutables :

- `account-service` — futur service métier des comptes, port 8081.
- `transaction-service` — futur service métier des transactions, port 8082.
- `discovery-service` — futur serveur Eureka, port 8761.
- `config-service` — futur serveur Spring Cloud Config, port 8888.
- `gateway-service` — future Gateway réactive, port 8080.
- `assistant-service` — futur assistant Spring AI, port 8083.
- `event-consumer` — futur consumer Kafka, port 8084.

À cette étape, ces modules sont des squelettes : démarrage Spring Boot et endpoints Actuator uniquement. Eureka, Config Server, routage Gateway, Kafka, bases, sécurité, chatbot et opérations bancaires seront ajoutés progressivement. Les noms des modules ne signifient pas que ces fonctions sont déjà actives.

## Prérequis

- JDK 17 ou supérieur ; cible de compilation Java 17.
- `JAVA_HOME` pointant vers le JDK.
- Accès réseau pour le premier téléchargement Maven et ses dépendances.

Le Maven Wrapper fixe Maven à la version 3.9.11 ; aucune installation globale de Maven n’est nécessaire.

## Compiler sous Windows / PowerShell

Depuis le dossier contenant le `pom.xml` parent :

```powershell
java -version
.\mvnw.cmd --version
.\mvnw.cmd clean verify
```

Les sept JAR sont produits dans `<module>/target/<module>.jar`.
Il n’y a pas encore de tests métier : `verify` vérifie le build et exécutera les tests quand ils seront ajoutés.

## Démarrer un module

```powershell
java -jar account-service\target\account-service.jar
```

Dans un autre terminal :

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
Invoke-RestMethod http://localhost:8081/actuator/health/liveness
```

Chaque réponse doit contenir `status: UP`. Arrêt avec `Ctrl+C`.
Le port 8080 ne redirige encore aucun appel ; il appartient au squelette de la future Gateway.
Les applications écoutent uniquement sur `127.0.0.1` pendant cette étape sans authentification.

## Suite

Suivre `docs/ETAPE-2.md`, puis implémenter Account-Service.
Les diagrammes de conception sont dans `docs/diagrams/`.

## Versions choisies

Java 17, Spring Boot 3.5.16, Spring Cloud 2025.0.3.
La famille Cloud 2025.0.x correspond à Boot 3.5.x : [compatibilité officielle](https://spring.io/projects/spring-cloud/).
Ce socle pédagogique reste sur Boot 3.5 pour la suite Spring AI 1.1 ; la documentation Spring signale la fin du support communautaire du train Cloud 2025.0.x. Une mise à niveau devra être étudiée avant un usage en production.
Le BOM Spring Cloud gère les versions ; il n’active aucun composant à lui seul.
Les dépendances fonctionnelles seront ajoutées au module concerné à chaque étape.
