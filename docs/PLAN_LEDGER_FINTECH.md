# Plan Complet — Service Ledger Fintech Multi-Devises (Open Source)

### Spring Boot · Clean Architecture · Distribué · Idempotent · Auditable · Configurable

---

## 0. Vision & Principes Directeurs

Ce projet est un **ledger comptable ultra-sécurisé pour des systèmes de paiement agrégateurs** : open source, réutilisable, destiné aux startups fintech qui construisent une solution de paiement. La mission est **unique et étroite** — ce n'est ni une plateforme de paiement complète, ni un pricing engine, ni un hub d'intégration multi-protocole. Le socle doit rester le composant le plus difficile et le plus critique à bien faire (la comptabilité double-entry, multi-devises, idempotente, auditable), pas "tout le reste" qu'une plateforme type Stripe couvre avec des dizaines d'équipes.

Chaque proposition d'extension se juge à cette aune : si elle n'améliore pas la rigueur, la sécurité ou l'auditabilité du ledger, elle n'appartient pas au cœur.

**Conséquence directe sur le design : le service ne doit présumer d'aucun outil tiers spécifique.** Une startup qui adopte ce ledger a déjà (ou choisira plus tard) son propre IAM, son propre broker de messages, son propre stockage de secrets, son propre système de scoring fraude, son propre moteur de frais. Le projet doit donc être :

1. **Append-only, jamais d'UPDATE sur les montants.** Le solde n'est jamais un champ mutable — toujours une projection calculée à partir d'écritures immuables (Posting). C'est la base de l'auditabilité.
2. **Double-entry bookkeeping.** Chaque transaction génère au moins 2 écritures (débit/crédit) dont la somme = 0. Garantit l'intégrité comptable structurellement, pas juste applicativement.
3. **Idempotence par construction**, à tous les niveaux (API, messaging, DB), pas en "best effort".
4. **Clean/Hexagonal Architecture stricte** : le domaine ne connaît ni Spring, ni JPA, ni Kafka, ni aucun SDK tiers.
5. **Auditabilité par conception** : chaque état est reconstructible, chaque décision est traçable, la donnée d'audit est infalsifiable.
6. **Non-présomption / configurabilité totale** : toute dépendance externe (broker, cache, IAM, secrets, moteur de fraude, moteur de frais/pricing, notification, rail de paiement) est branchée via un **port**, avec une implémentation par défaut minimale ("in-box") et la possibilité de la remplacer sans toucher au domaine ni aux use cases. Le cœur du projet doit tourner avec le strict minimum (Postgres + rien d'autre).
7. **Distribution sans friction.** Une entreprise doit pouvoir lancer le service via une image Docker officielle, avec une configuration en couches simple (défauts embarqués → fichier de config externe optionnel → variables d'environnement), sans wizard bloquant ni système de config propriétaire.

Ces principes structurent tout le document : à chaque section, on distingue le **cœur non-négociable** (ce qui fait la valeur du projet) du **point d'extension** (ce que chaque startup adaptera à sa stack).

---

## 1. Domain Model (cœur métier, zero dépendance framework — jamais négociable)

### 1.1 Agrégats principaux

```
Ledger (aggregate root)
 ├─ LedgerAccount        (id, ownerRef, currency, type, status, tenantId)
 ├─ JournalEntry          (aggregate immuable — LA transaction comptable, N postings)
 │    ├─ Posting[]        (au moins 2 : débit + crédit, somme = 0, settlementStatus)
 │    └─ IdempotencyKey
 └─ AccountBalance        (projection matérialisée par type de solde — voir §6)
```

**Décision clé** : la vérité n'est **jamais** un champ `balance` mutable sur `Account`. Le solde est une **projection calculée** à partir de la somme des `Posting` (pattern utilisé par Square, Stripe, TigerBeetle). Un `JournalEntry` n'est **pas limité à 2 postings** — il peut en contenir N tant que la somme reste nulle par devise pivot, ce qui permet de modéliser nativement un split de frais en une seule écriture atomique (voir §5).

### 1.2 Taxonomie des types de compte

| Type de compte                  | Rôle                                                                                                              |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `MERCHANT_WALLET`               | Solde virtuel d'un marchand/sous-marchand — n'existe que dans le ledger                                           |
| `AGGREGATOR_POOL`               | Représente le vrai compte bancaire externe d'un agrégateur regroupant plusieurs wallets virtuels                  |
| `RAIL_CLEARING` (nostro/vostro) | Compte tampon représentant "l'extérieur du système" pour un rail donné (voir §7)                                  |
| `SUSPENSE/HOLD`                 | Fonds en attente (revue fraude, litige, contrat, réconciliation en cours)                                         |
| `FEE_PLATFORM_REVENUE`          | Commission nette conservée par la plateforme (marge après coûts d'acquisition)                                    |
| `FEE_INTERCHANGE_COST`          | Coût d'interchange pass-through dû au scheme ou à la banque acquéreur — non négociable, à reverser                |
| `FEE_AGGREGATOR_MARKUP`         | Marge de l'agrégateur au-dessus du coût d'interchange                                                             |
| `RESERVE_HOLD`                  | Provision bloquée pour remboursement, chargeback ou risque de crédit                                              |
| `TAX_VAT`                       | TVA ou taxe de vente applicable sur la commission                                                                 |

**Pourquoi cette granularité** : un agrégateur qui ne peut pas distinguer sa marge de son coût d'interchange dans son ledger a un problème de conformité comptable, pas seulement de fonctionnalité. Chaque type est un compte distinct dans le double-entry, ce qui permet un audit trail complet et une reconstruction exacte du P&L par transaction. Ces comptes ne changent rien au modèle domaine (§1.1) — ce sont des instances de la même abstraction `LedgerAccount`, alimentées par le moteur de split (§5).

### 1.3 Value Objects incontournables

- `Money` : `{ BigDecimal amount, Currency currency }` — jamais de `double`/`float`. Scale ISO 4217, arrondi explicite (`RoundingMode.HALF_EVEN`).
- `Money` immutable, arithmétique interdite entre devises différentes sauf via `ExchangeOperation` explicite et tracée.
- `IdempotencyKey` : VO typé, pas un `String` brut.
- `TransactionReference` : identifiant métier externe, distinct de l'UUID technique.

### 1.4 Invariants du domaine (priorité n°1 des tests)

- Somme des postings d'un JournalEntry = 0 par devise pivot.
- Un JournalEntry posté ne peut jamais être modifié ni supprimé → seule opération : `reverse()`.
- Un compte `CLOSED` ne peut plus recevoir de postings.
- Pas de solde négatif sauf si `LedgerAccount.allowsOverdraft == true`.
- Un split (§5) doit toujours reconcilier exactement au montant total — aucun centime ne peut disparaître par arrondi.

---

## 2. Architecture Clean / Hexagonale

### 2.1 Structure des packages

```
src/main/java/com/xxx/ledger/
├── domain/                        # ZERO dépendance externe — cœur non-négociable
│   ├── model/                     # Money, JournalEntry, Posting, Account, SplitPlan...
│   ├── exception/
│   └── service/                   # DoubleEntryValidator, SplitPlanEvaluator (pur)
│
├── application/                   # Use cases + Ports (interfaces)
│   ├── port/in/                   # ex: PostTransactionUseCase
│   ├── port/out/                  # JournalEntryRepository, ExchangeRateProvider,
│   │                               # EventPublisher, SecretsProvider, RiskCheckPort,
│   │                               # SplitPlanResolver, RailAdapter
│   ├── usecase/
│   └── dto/
│
├── infrastructure/
│   ├── persistence/jpa/           # entités JPA + mapper vers/depuis domain
│   ├── persistence/outbox/        # Outbox pattern (§9)
│   ├── messaging/                 # adapters optionnels : kafka/ | rabbitmq/ | inmemory/
│   ├── exchangerate/
│   ├── security/                   # adapters optionnels
│   ├── secrets/                     # adapters optionnels : vault/ | env/ | awssecretsmanager/
│   ├── rails/                       # adapters optionnels par rail (§7)
│   └── audit/
│
├── adapter/in/
│   ├── rest/
│   ├── cli/                        # module séparé de préférence (§16)
│   └── event/
│
└── config/                         # wiring des ports→adapters, activation conditionnelle
```

**Règle ArchUnit dès le jour 1** (voir aussi `.cursor/rules/architecture.mdc`) :

```java
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "..springframework..", "..jpa..", "org.hibernate..", "..kafka..", "..vault.."
    );
```

### 2.2 Pourquoi pas de CQRS complet

Pas de CQRS/event-sourcing complet type Axon. On emprunte le pattern : write-model (JournalEntry immuable) séparé du read-model (`AccountBalance`), mis à jour dans la même transaction DB, rafraîchi de façon asynchrone pour les vues secondaires.

### 2.3 Points d'extension — ne rien présumer des outils tiers

| Concern                           | Port (application/port/out)                            | Implémentation par défaut ("in-box")          | Adapters alternatifs                                                                                                         |
| --------------------------------- | ------------------------------------------------------ | --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Messaging / événements            | `EventPublisher`                                       | Outbox + polling DB (zéro infra)              | Kafka, RabbitMQ, SNS/SQS, Pulsar                                                                                             |
| Cache taux / idempotence          | `RateCache`, `IdempotencyStore`                        | Caffeine (mémoire)                            | Redis, Hazelcast                                                                                                             |
| Fournisseur de taux               | `ExchangeRateProvider`                                 | Config statique en base                       | ECB, OpenExchangeRates, Fixer                                                                                                |
| IAM / AuthN-AuthZ                 | Spring Security Resource Server (OIDC standard)        | Config OIDC générique                         | Keycloak, Auth0, Cognito, Okta                                                                                               |
| Secrets                           | `SecretsProvider`                                      | Variables d'environnement (dev, avec warning) | Vault, AWS/GCP Secrets Manager                                                                                               |
| Détection de fraude               | `TransactionRiskCheckPort` (sync) + événement async    | Moteur de règles minimal in-box, désactivable | Moteur externe, ML custom (§17)                                                                                              |
| Rail de paiement                  | `RailAdapter`                                          | Aucun (compte clearing manuel)                | Mobile money, carte, SWIFT, crypto (§7)                                                                                      |
| Split de frais / remboursement    | `SplitPlanResolver` + `FeeReversalPolicy`              | Règles déclaratives structurées in-box (§5) ; `NO_REVERSE` par défaut | DSL scripté, moteur de règles externe, service fee dédié ; `PRO_RATA` ou politique custom (§5.2, §5.3) |
| Notifications sortantes           | `NotificationPort`                                     | Aucune (no-op)                                                        | Webhook signé, email, SMS, push                                                                       |
| Observabilité                     | Micrometer + OpenTelemetry (standards)                 | Logs structurés + Prometheus local                                    | Grafana/Tempo, Datadog, New Relic                                                                     |
| Interface entrante                | `HttpAdapter` (REST)                                   | Spring MVC + OpenAPI 3.1                                              | gRPC (`adapter/in/grpc`, optionnel, **non officiel en v1**)                                           |

**Règle de contribution** : chaque adapter vit dans son propre module Maven optionnel, activé via `@ConditionalOnClass`/`@ConditionalOnProperty`. `ledger-core` ne dépend d'aucun d'eux. Ce qui relève de la topologie de déploiement de l'adoptant (API Gateway, Service Mesh, WAF, SIEM) n'est délibérément **pas** dans cette table : ce ne sont pas des décisions d'architecture du ledger, et les y faire figurer contredirait le principe de non-présomption.

---

## 3. Multi-tenant hiérarchique (agrégateurs & sous-marchands)

Le multi-tenant plat (`tenant_id` + RLS, déjà posé en §11) devient une hiérarchie pour supporter les agrégateurs/PSP :

```
tenant(id, type: STANDALONE | AGGREGATOR | SUB_MERCHANT, parent_tenant_id nullable)
```

- Un **agrégateur** est un tenant `AGGREGATOR` sans parent ; chaque **sous-marchand** est `SUB_MERCHANT` avec `parent_tenant_id` pointant vers l'agrégateur.
- **RLS** passe d'une simple égalité à une vérification d'ascendance (table `tenant_ancestry` matérialisée ou CTE récursive) : un sous-marchand ne voit que ses propres données ; l'agrégateur voit les siennes et celles de ses descendants, jamais l'inverse.
- Reste dans le cœur non-négociable — extension naturelle du modèle de tenant déjà prévu, pas un point d'extension optionnel.
- Le split de commission entre agrégateur et sous-marchand se modélise nativement via §1.1 (N postings par JournalEntry) — voir §5.

---

## 4. Multi-devises & taux de change

### 4.1 Abstraction (Strategy Pattern via port `ExchangeRateProvider`)

```java
public interface ExchangeRateProvider {
    ExchangeRate getRate(CurrencyPair pair, Instant asOf);
}
```

Composite + Chain of Responsibility :

1. **`OverrideRateProvider`** — taux figés par tenant en base (`validFrom`/`validTo`). Implémentation par défaut.
2. **`ExternalRateProvider`** — port optionnel vers une source externe (ECB, OpenExchangeRates, taux propriétaire) avec circuit breaker + retry + cache.
3. **`FallbackRateProvider`** — dernier taux connu en cache si tout échoue, avec flag `isStale=true` propagé à l'audit trail.

Le taux utilisé est toujours figé et persisté dans le `JournalEntry` (`rateUsed`, `rateSource`, `rateTimestamp`) — jamais recalculé après coup.

### 4.2 Configuration multi-tenant

Chaque tenant choisit sa source de taux, ses devises supportées, sa devise de référence, et son spread (`rate * (1 + spreadBps/10000)`) — table `tenant_fx_config`.

---

## 5. Moteur de frais & de split (inspiré de Numscript/Formance, sans DSL complet)

Les agrégateurs ont besoin d'exprimer des splits de paiement (ex : 100 → 95 marchand + 3 frais agrégateur + 2 taxe) en une seule opération transactionnelle. Formance résout ça avec **Numscript**, un DSL de scripting complet — puissant, mais un investissement d'ingénierie lourd pour un v1 (contraire au principe KISS de `.cursor/rules/architecture.mdc`).

### 5.1 Approche retenue : configuration déclarative structurée

Pas un runtime de scripting. Le ledger **enregistre** ce qu'on lui donne ; il ne **calcule** pas de pricing.

```java
public interface SplitPlanResolver {
    SplitPlan resolve(SplitRuleSet rules, Money totalAmount, TransactionContext ctx);
}
```

- `SplitRuleSet` est une donnée (YAML/JSON) par tenant et par type de transaction, ex :
  ```yaml
  rules:
    - target: MERCHANT_WALLET
      percentage: 95.0
    - target: FEE_AGGREGATOR_MARKUP
      percentage: 3.0
    - target: TAX_VAT
      percentage: 2.0
  remainderTarget: MERCHANT_WALLET # tout centime résiduel d'arrondi va ici, jamais perdu
  ```
- Le `SplitPlanEvaluator` (domaine, pur) transforme ce plan en `Posting[]` d'un unique `JournalEntry` — réutilise directement l'invariant sum-zero déjà en place, aucun changement du modèle domaine.
- **Gestion de l'arrondi** : la somme des postings doit reconcilier exactement au montant total (§1.4) — le reliquat d'arrondi est explicitement assigné à un compte désigné (`remainderTarget`), jamais silencieusement perdu ou ignoré.
- **Point d'extension, pas cœur figé** : un tenant qui a besoin de règles conditionnelles complexes (seuils, cascades multi-niveaux, logique métier riche) peut brancher un adapter `SplitPlanResolver` alternatif — sans que le cœur du projet ait à porter cette complexité par défaut. Voir §5.2.

### 5.2 Intégration d'un moteur de frais externe

Le `SplitPlanResolver` (§5.1) est l'unique contrat entre le ledger et n'importe quel calculateur de frais externe. Le ledger **enregistre** ce qu'on lui donne, il ne **calcule** pas de pricing. Traiter les frais comme une simple conséquence mécanique du split est insuffisant pour un usage agrégateur réel — mais la solution n'est **pas** un "Fee Engine" comme nouveau domaine dans le cœur (violation directe de KISS et de la non-présomption, §0.6) : c'est un point d'extension du split existant, pas un bounded context au même niveau que fraude/réconciliation.

**Architecture d'intégration typique** :

```
[Client Fintech] ──► [Fee Engine externe] ──(calcule frais selon ses propres règles)──►
                                                                              │
                                                                              ▼
[Ledger Core] ◄──────────────────────────── SplitPlan ────────────────────────┘
       │
       ├─ JournalEntry (N postings : marchand, interchange, markup, taxe, réserve...)
       └─ Outbox event
```

- Le `Fee Engine` est un bounded context **extérieur** au ledger. Il peut être un moteur maison, Drools, ou un service dédié.
- Le ledger reste pur comptable : il valide l'équilibre sum-zero et l'immutabilité, indépendamment de la logique métier qui a produit le split.
- Un module Maven optionnel `ledger-fee-adapter` peut fournir une implémentation de référence de `SplitPlanResolver` appelant un service externe via REST/gRPC, mais reste un **adapter optionnel** (§2.3).
- Le ledger peut tourner **sans** Fee Engine (mode "split déclaratif simple", §5.1).

### 5.3 Politique de remboursement de frais — `FeeReversalPolicy`

Lorsqu'un remboursement total ou partiel est demandé sur une transaction, la question des frais déjà encaissés doit être traitée explicitement.

```java
public interface FeeReversalPolicy {
    // Calcule quels postings de frais doivent être reversés
    // lors d'un remboursement partiel ou total
    List<Posting> calculateReversal(JournalEntry originalEntry, Money refundAmount);
}
```

**Implémentations** :

| Politique     | Comportement                                                         |
| ------------- | -------------------------------------------------------------------- |
| `NO_REVERSE`  | Défaut — les frais sont acquis, non remboursés                       |
| `PRO_RATA`    | Reverse les frais au prorata du montant remboursé                    |

Chaque tenant configure sa politique via `tenant_fee_config`.

**Invariant** : le remboursement génère un nouveau `JournalEntry` de type `REVERSAL` (ou `REFUND`) avec ses propres postings — jamais de modification rétroactive de l'entrée originale (§1.4).

---

## 6. Types de solde (inspiré de Blnk)

Plutôt qu'un solde opaque unique, `AccountBalance` expose une décomposition par type — inspirée de l'expérience développeur de Blnk, sans introduire de nouveau concept de compte : c'est une lecture différente du même flux de `Posting`.

| Type de solde | Signification                                 | Alimenté par                                                                         |
| ------------- | --------------------------------------------- | ------------------------------------------------------------------------------------ |
| `AVAILABLE`   | Solde effectivement dépensable maintenant     | Postings avec `settlementStatus = SETTLED`                                           |
| `PENDING`     | Fonds entrants, pas encore réglés/compensés   | Postings avec `settlementStatus = PENDING` (ex : attente de règlement d'un rail, §7) |
| `HELD`        | Fonds bloqués (revue fraude, litige, contrat) | Postings routés vers un compte `SUSPENSE/HOLD` (§1.2, §17)                           |

- Chaque `Posting` porte un champ `settlementStatus` ; `AccountBalance` (projection, §1.1) agrège les trois vues à la lecture — pas de nouvelle table de vérité, juste une agrégation supplémentaire du même flux append-only.
- L'API expose les trois nombres plutôt qu'un solde unique — directement utile pour un agrégateur qui doit distinguer "ce que le marchand peut retirer aujourd'hui" de "ce qui arrive mais n'est pas encore réglé".

---

## 7. Rails de paiement & réconciliation

Chaque rail (mobile money, carte, virement bancaire, crypto) est modélisé sur le pattern **nostro/vostro** : un compte `RAIL_CLEARING` représente "l'extérieur du système", et toute transaction touchant le monde réel passe obligatoirement par ce compte tampon.

```java
public interface RailAdapter {
    RailTransactionResult initiate(RailTransactionRequest request);
    RailSettlementStatus checkStatus(String railReference);
}
```

- Chaque rail a son propre cycle de règlement (instantané pour mobile money, batch pour SWIFT, J+1/J+2 pour carte), géré entièrement par l'adapter — jamais par le domaine. C'est l'équivalent de ce que Formance appelle des "connecteurs" : le cœur ne présume d'aucun rail précis, il fournit le port + un adapter de référence.
- **Réconciliation** — bounded context séparé, même logique que le module fraude (§17) : compare en continu les écritures de `RAIL_CLEARING` avec les rapports de règlement externes (webhook, fichier MT940...), génère des `ReconciliationBreak` en cas d'écart, consomme les mêmes événements outbox (`TransactionPosted`) sans couplage direct avec le module fraude.

---

## 8. Idempotence (le vrai sujet dur en distribué)

### 8.1 Niveau API

- Header `Idempotency-Key` obligatoire sur tout endpoint mutatif.
- Table `idempotency_record(key, request_hash, response_snapshot, status, created_at, expires_at)`.
- `INSERT ... ON CONFLICT DO NOTHING` sur `(tenant_id, idempotency_key)` + hash du body ; hash identique → replay ; hash différent → `409 Conflict` explicite.
- TTL de rétention purgé par job planifié.

> **Distinction critique** : l'`Idempotency-Key` (header `Idempotency-Key`) protège contre le rejeu de requêtes **entrantes** (même appel répété par le client). La signature **HMAC** (§11) protège l'intégrité des **webhooks sortants** (le ledger signe ce qu'il envoie). Ce sont deux mécanismes orthogonaux : l'un ne remplace pas l'autre.

### 8.2 Niveau message (at-least-once, quel que soit le broker choisi)

- Pattern "idempotent consumer" : déduplication par `message_id` dans la même transaction DB que l'écriture métier.
- Si Kafka : Exactly-Once Semantics en filet supplémentaire, jamais en seule protection.

### 8.3 Niveau écriture comptable (concurrence sur agrégat)

- Verrouillage optimiste (`@Version` JPA), retry borné avec backoff exponentiel.
- Alternative avancée : partitionnement par compte pour éliminer la contention plutôt que la gérer.

---

## 9. Cohérence distribuée : Outbox Pattern (composant du cœur)

1. Dans la même transaction DB que l'écriture du `JournalEntry`, insertion d'une ligne `outbox_event` (payload, aggregate_id, created_at, status=PENDING).
2. Un poller (par défaut, zéro infra) ou un connecteur CDC (Debezium, en option) publie via le port `EventPublisher` — dont l'implémentation concrète est un choix de la startup adoptante.
3. Le consumer applique le pattern idempotent du §8.2, quel que soit le broker.

---

## 10. Audit Trail (infalsifiable)

- Le `JournalEntry` append-only est déjà, de fait, un audit trail des mouvements financiers.
- Table `audit_log` append-only avec hash chaining :
  ```
  current_hash = SHA256(prev_hash + payload_hash + timestamp + actor)
  ```
  Toute altération rétroactive casse la chaîne — vérifiable par un job d'intégrité périodique.
- Capture via AOP (`@Auditable` + `@Around`), vit en infrastructure.

**Corrélation avec l'observabilité** : la propagation du header W3C `traceparent` fait partie du contrat de corrélation entre les traces OpenTelemetry et le `audit_log`. Chaque entrée d'audit capture `traceId` et `spanId`, permettant de reconstruire le chemin complet d'une décision comptable depuis la requête API jusqu'à l'écriture en base.

- Stockage Postgres par défaut, réplication optionnelle vers un stockage WORM (S3 Object Lock) pour conformité renforcée — jamais imposé par défaut.

---

## 11. Sécurité

La sécurité d'un ledger vient de la rigueur de son modèle de menace, pas de la richesse de sa surface d'intégration. Les précisions ci-dessous ferment des classes de vulnérabilité réelles ; ce qui relève de la topologie de déploiement de l'adoptant reste volontairement hors de ce tableau.

| Sujet                         | Approche par défaut (in-box)                                                                                                                                                                                      | Ce qui reste au choix de l'adoptant            |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| AuthN/AuthZ                   | Spring Security Resource Server. **Toujours** un JWT signé (`RS256`/`ES256` only ; `alg=none`, `HS256` interdits). `exp` obligatoire + **plafond de durée max** côté ledger. Pas d'auth-off / trust_edge. Émetteur : IdP OIDC **externe** (prod) ou émetteur **interne** FinLedger (`client_id`/`client_secret` → JWT court) — [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md). Profils runtime `sandbox` / `normal`. | Keycloak, Auth0, Cognito, Okta ; ou issuer interne pour sandbox/CI |
| Scopes/permissions            | Scopes fins par opération (`ledger:write`, `ledger:read:tenant-x`) **explicitement vérifiés par tenant**, pas seulement par rôle global                                                                           | Granularité additionnelle définie par l'adoptant |
| TLS                           | **TLS 1.3 minimum** au point de terminaison (souvent un reverse-proxy/load balancer devant le service, pas nécessairement Spring Boot lui-même).                                                                  | Terminaison au niveau de l'infra déployante    |
| mTLS interne                  | **Couche transport additive** (mesh/sidecar) — **jamais** un substitut à la vérification JWT. Option ultérieure : contrôle SAN via un port.                                                                      | Implémentation par la stack de l'adoptant      |
| Isolation multi-tenant        | `tenant_id`/ancestry + Row-Level Security Postgres                                                                                                                                                                | —                                              |
| Secrets                       | Variables d'environnement (dev, avec avertissement explicite). Secrets long-vécus = mint uniquement, jamais Bearer API.                                                                                          | Vault, AWS/GCP Secrets Manager, HSM/KMS        |
| Chiffrement                   | TLS partout, colonnes sensibles chiffrées (`pgcrypto`/AES-GCM)                                                                                                                                                    | Choix du KMS                                   |
| Intégrité webhooks sortants   | Signature HMAC-SHA256 systématique (payload + timestamp + nonce) — distincte de l'`Idempotency-Key` (§8.1), qui protège les requêtes **entrantes**                                                                | —                                              |
| Anti-rejeu                    | Timestamp + nonce, fenêtre de tolérance courte                                                                                                                                                                    | —                                              |
| Rate limiting                 | Bucket4j en mémoire par défaut                                                                                                                                                                                    | Redis-backed si distribué                      |
| Audit des accès               | Chaque lecture de données sensibles loggée                                                                                                                                                                        | —                                              |
| Communication inter-services  | (1) JWT court (client credentials / workload identity) (2) mTLS **en plus** du JWT (3) HMAC pour edges async (rails) (4) ACL réseau / Bearer éternel — **rejetés** comme modèle primaire | IdP / mesh de l'adoptant |

**Hors périmètre volontaire** : API Gateway, Service Mesh, WAF, SIEM et rotation de certificats sont des choix de topologie d'infrastructure de l'adoptant, pas des décisions d'architecture de ce projet — les y inclure contredirait le principe de non-présomption (§0.6). Le design ne les empêche pas ; il ne les présume simplement pas. gRPC reste un adapter optionnel (§2.3), jamais une interface publique de référence en v1 (maintenir OpenAPI et Protobuf en lockstep serait une dette d'ingénierie permanente sans bénéfice de sécurité).

---

## 12. Stack technique & Spring Boot avancé

### 12.1 Choix runtime : Spring Boot + Virtual Threads (plutôt que Go)

Un ledger est I/O-bound (DB, broker, appels FX externes), pas CPU-bound. Les Virtual Threads (Java 21+) permettent du code bloquant, séquentiel, facile à auditer, tout en obtenant un débit proche d'un modèle async/Go sur charge I/O — sans perdre l'écosystème Spring (transactions, OAuth2, ORM) que ce projet exploite directement pour épargner ce travail aux startups adoptantes. Go garderait un avantage pour un moteur ultra-basse-latence CPU-bound — pas pour ce cas d'usage.

Configuration : `spring.threads.virtual.enabled=true`. Éviter `synchronized` (épingle le thread porteur) → `ReentrantLock`.

### 12.2 Modularité des dépendances : cœur minimal vs starters optionnels

**Cœur non-négociable** : `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa` + `spring-boot-starter-flyway` + `flyway-database-postgresql` + `postgresql`, `spring-boot-starter-security` + `spring-boot-starter-security-oauth2-resource-server`, ArchUnit (test).

**Modules optionnels** : `spring-boot-starter-kafka` (si l'outbox in-box ne suffit plus), Resilience4j (`ExternalRateProvider`), `spring-boot-starter-opentelemetry`, Redis optionnel plus tard (`RateCache` distribué — défaut in-memory aujourd'hui).

**Tests** : `net.jqwik:jqwik`, `org.pitest:pitest-maven`, Testcontainers — voir aussi `.cursor/rules/testing-rules.mdc`.

---

## 13. Pyramide de tests

```
        /\
       /E2E\          ~5%   — parcours complets via Testcontainers + API réelle
      /------\
     /Contract\       ~10%  — Pact ou Spring Cloud Contract
    /----------\
   / Integration \    ~20%  — repository, outbox, adapters (Testcontainers)
  /----------------\
 /   Unit (domain)   \ ~65% — domaine pur, property-based testing (jqwik)
/______________________\
```

Détail complet des règles de test (jqwik, PIT, Testcontainers, tests de concurrence/idempotence) : `.cursor/rules/testing-rules.mdc`. Objectif : >90% coverage sur `domain`, >80% global, piloté par mutation score plutôt que par ligne de coverage brute.

---

## 14. Extensibilité — synthèse

- Multi-tenant hiérarchique natif (§3), pas ajouté après coup.
- Ports/Adapters (§2.3) : brancher un nouveau fournisseur de taux, broker, rail, moteur de fraude, moteur de frais externe ou politique de remboursement = ajouter un adapter, zéro changement du domaine.
- Feature flags par tenant (devises, spread FX, activation du module fraude, comportement fail-open/fail-closed, règles de split, `FeeReversalPolicy`).
- Plugin de règles métier : port `TransactionValidationRule` injecté par tenant.

---

## 15. Structure du README

```markdown
# Ledger Service (Open Source)

[badges: build, coverage, docker pulls, licence]

## 1. Pourquoi ce projet

## 2. Ce que ce projet NE présume PAS (IAM, broker, secrets, rail — voir §2.3)

## 3. Architecture (diagramme hexagonal + ADRs)

## 4. Modèle de données (double-entry, diagramme ER)

## 5. Démarrage rapide — Docker (voir §18) — clone + `compose --profile sandbox`

## 6. Configuration (couches : image → fichier externe → env vars + finledger.env.example)

## 6b. Intégration CTO ([INTEGRATION_FOR_CTO.md](INTEGRATION_FOR_CTO.md))

## 7. API (OpenAPI/Swagger, exemples curl avec Idempotency-Key)

## 8. Idempotence & garanties de cohérence

## 9. Sécurité & conformité

## 10. Points d'extension (table des ports, comment écrire un adapter)

## 11. Observabilité

## 12. Tests

## 13. ADRs (/docs/adr)

## 14. Contribuer

## 15. Client de référence (non-officiel)

Un client de référence unique (Java ou TypeScript selon l'écosystème visé) est fourni dans `/sdk-reference/`. Il n'est **pas un SDK officiel maintenu** : c'est une implémentation documentée des patterns sensibles que tout intégrateur doit maîtriser :

- Génération et gestion des `Idempotency-Key`
- Signature HMAC des webhooks entrants (vérification côté client du webhook sortant signé par le ledger)
- Retries sûrs avec backoff exponentiel et jitter
- Propagation des headers `traceparent` et métadonnées de corrélation

**Objectif** : réduire les erreurs d'implémentation côté client sans engager le projet sur une charge de maintenance multi-langue prématurée. Les vrais SDK multi-langues restent un objectif **post-v1**, conditionné à une demande d'adoption réelle. Un client OpenAPI généré automatiquement suffit pour la structure des requêtes.
```

---

## 16. CLI de provisioning & opérations

Module Maven séparé (`finledger-cli`, Picocli + JLine, **sans Spring**), jar shaded
distribué à côté de l'image serveur. Deux surfaces dans le même binaire
([ADR-015](adr/ADR-015-operational-model.md)) :

```
finledger-cli
 ├─ ops  (local / SSH près de Compose) : config init|set|validate, status, doctor,
 │         up|down|restart|logs (wrappers docker compose) — cycle de vie = Compose/K8s
 └─ api  (HTTP vers le serveur) : tenants, accounts, FX, splits, health/ready
           → POST/GET /api/v1/... (pas de surface /admin dupliquée en v1 — ADR-010)
```

Ergonomie : bannière de contexte (mode `enforced|static-token|disabled`, env, profils),
validation avec messages actionnables, confirmation / `--dry-run` sur les mutateurs,
avertissement « restart le service ; volumes Postgres conservent les données » après
`config set`. Swagger/OpenAPI reste pour le DX local et la doc API ; désactivé ou
restreint en `prod`. Une UI web admin pourra s'appuyer plus tard sur la même API.

---

## 17. Module Fraude & Risque (bounded context séparé, activable)

```
Ledger (use case PostTransaction)
        │ (1) appel SYNCHRONE, via TransactionRiskCheckPort — désactivable (no-op par défaut)
        ▼
Fraud Module (module optionnel)
 ├─ Couche SYNC : règles déterministes rapides (vélocité, listes noires, seuils),
 │    budget latence <50ms, fail-open/fail-closed configurable par tenant
 ├─ Couche ASYNC : déclenchée par TransactionPosted, scoring comportemental,
 │    peut router les fonds vers un compte HOLD (§1.2, §6)
 └─ Rapports (read-model dédié)
```

Moteur de règles configurable (même pattern Strategy que §5). Chaque décision est elle-même auditée via le hash-chaining de §10.

---

## 18. CI/CD & Distribution (GitHub Actions + Docker Hub)

### 18.1 Stratégie de configuration au bootstrap — la question du "meilleur choix"

Ni "tout en variables d'environnement" (illisible à grande échelle) ni "wizard interactif au démarrage" (anti-pattern : bloque les health checks et les déploiements orchestrés type Kubernetes). **La bonne approche s'appuie sur la résolution de configuration native de Spring Boot, en couches, sans rien réinventer :**

1. **Défauts embarqués dans l'image** — `application.yml` avec des valeurs sûres permettant un `docker run` minimal (associé à un `docker-compose.yml` de quickstart qui fournit Postgres).
2. **Fichier de config externe optionnel** — l'image définit `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/workspace/config/` ; une entreprise monte simplement son propre `application.yml` sur ce volume (`-v ./my-config.yml:/workspace/config/application.yml`). Le préfixe `optional:` évite tout échec si le fichier est absent.
3. **Variables d'environnement (relaxed binding)** — pour les orchestrateurs qui préfèrent l'injection d'env vars (ConfigMap/Secret Kubernetes, task defs ECS) à un fichier monté ; toute clé YAML est overridable via son équivalent `SCREAMING_SNAKE_CASE`. Fichier de référence **`finledger.env.example`** à la racine : toutes les options documentées avec défauts et descriptions ; `cp finledger.env.example .env` pour démarrer.
4. **Secrets jamais dans le fichier de config** — toujours via le port `SecretsProvider` (§2.3, §11).
5. **Premier lancement sans tenant configuré** : le service démarre normalement (jamais de blocage), log un message explicite pointant vers la CLI (§16) ou `POST /api/v1/tenants` pour créer le premier tenant — pas de wizard qui empêcherait le conteneur de passer `healthy`.
6. **Profils runtime & JWT (ADR-016)** : `sandbox` (seed + émetteur interne, clés éphémères) vs `normal` (IdP externe par défaut, ou émetteur interne). Vérification JWT **toujours** active — pas d'auth-off, pas de modes `enforced`/`static-token`/`disabled`.
7. **Livraison** : image Hub = artefact prod canonique ; fat JAR = échappatoire documentée (garantie de liability **moindre**) ; clone + Compose = eval.

### 18.2 Pipeline GitHub Actions

- **`ci.yml`** (PR + push sur `develop`) : build Maven, tests unitaires domaine, vérification ArchUnit, tests d'intégration Testcontainers, mutation testing PIT sur `domain` (PR uniquement), build Docker de validation sans push.
- **`release-docker.yml`** (tag `v*.*.*` ou release GitHub) : build multi-arch (`linux/amd64`, `linux/arm64`) via Buildx/QEMU, push vers Docker Hub avec tag de version + `latest`.

Fichiers de référence : `.github/workflows/ci.yml`, `.github/workflows/release-docker.yml`, `Dockerfile`, `.dockerignore` (livrés à la racine du repo).

### 18.3 Image Docker & démarrage type Blnk

Build multi-stage (JDK pour build, JRE Alpine minimal pour runtime), jar en couches Spring Boot (`-Djarmode=tools ... extract --layers`) pour un meilleur cache de layers Docker, utilisateur non-root, healthcheck sur `/actuator/health` (port management `8081`), volume `/workspace/config` pour le point 2 ci-dessus. Entrypoint : `java -jar` (layout Boot 4).

**Chemin d'évaluation recommandé (inspiré Blnk) :**

```bash
git clone <repo> && cd finledger
cp finledger.env.example .env
docker compose --profile sandbox up -d --build
# credentials / curls : config/sandbox-ready.txt
```

**Production :** image Hub + `with-app` (ou K8s) avec profil `normal`, issuer OIDC **externe**, JWT always-on ([ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md)). Le fat JAR serveur reste une échappatoire hors conteneur (**liability moindre** que l'image). Redémarrer l'app (`compose restart`) sans perdre les données = volume Postgres nommé.

Voir [ADR-012](adr/ADR-012-docker-distribution.md), [ADR-015](adr/ADR-015-operational-model.md), [ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md).

---

## 19. Roadmap de développement

1. Domaine pur + tests unitaires/property-based.
2. Persistence + migrations Flyway, tests Testcontainers.
3. Use case `PostTransaction` bout-en-bout avec idempotence API, taux fixes en dur.
4. Outbox in-box (zéro infra) — événements `TransactionPosted`.
5. Multi-tenant hiérarchique (§3) — ancestry + RLS récursif.
6. Exchange rate providers — configuré, puis externe avec circuit breaker.
7. Moteur de split (§5) — règles déclaratives + taxonomie frais (§1.2) + `FeeReversalPolicy` (§5.3) ; adapter fee externe optionnel (§5.2).
8. Types de solde (§6) — `settlementStatus` sur Posting, agrégation API.
9. Audit trail via AOP + corrélation `traceparent` (§10).
10. Sécurité — OIDC générique (allowlist JWT RS256/ES256), TLS 1.3 au point de terminaison, RLS, chiffrement (§11).
11. Rails de paiement — port `RailAdapter` + un adapter de référence + réconciliation (§7).
12. CLI de provisioning (module séparé, §16).
13. Module Fraude (optionnel, §17).
14. CI/CD (§18) — pipelines GitHub Actions, image Docker publiée.
15. Observabilité — traces, dashboards.
15b. Modes de sécurité runnable + sandbox Compose (FL-151 / ADR-014).
15c. Modèle ops : `finledger.env.example`, CLI ops+api, doctor/status/restart hints (FL-152 / ADR-015).
15d. Profils `sandbox`/`normal` + émetteur JWT interne/externe, toujours-on verify ([ADR-016](adr/ADR-016-runtime-profiles-jwt-issuer.md), FL-154 → FL-155 → FL-156) ; puis UX API CLI (FL-153).
16. Contract tests vis-à-vis d'un "client fintech" fictif + client de référence non-officiel (`/sdk-reference/`, §15).
17. Durcissement : chaos testing, tests de charge, revue de sécurité.
18. **Guide CTO / intégration production** — runbook final pour intégrer FinLedger dans une stack fintech existante ([INTEGRATION_FOR_CTO.md](INTEGRATION_FOR_CTO.md), FL-190), après validation pas-à-pas.
19. **Post-v1 (conditionné à l'adoption)** : SDK multi-langues officiels ; éventuelle promotion d'un adapter gRPC au rang d'interface publique si un besoin réel le justifie.

---

## 20. Inspirations externes & positionnement

Ce projet s'inspire de mécanismes précis d'outils existants, sans en adopter la dépendance ni l'ampleur d'ingénierie complète — cohérent avec le principe de non-présomption (§0.6) et KISS.

| Inspiration                                | Ce qu'on emprunte                                                                                                                                                                             | Ce qu'on n'adopte pas, et pourquoi                                                                                                                                                                        |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Formance / Numscript**                   | Le concept de split multi-comptes en une transaction atomique (§5)                                                                                                                            | Pas de runtime DSL complet en v1 — trop d'investissement d'ingénierie pour la valeur apportée au socle de départ ; laissé comme adapter avancé possible                                                   |
| **TigerBeetle**                            | La discipline d'imposer les invariants comptables au niveau le plus bas possible (ici : contraintes/triggers Postgres en défense en profondeur, en plus d'ArchUnit et des tests de propriété) | Pas de moteur de stockage sur mesure (Zig/LSM/VSR) — hors de portée pour un socle Spring Boot généraliste ; si une startup a besoin de 100k+ TPS, c'est un sujet d'ADR dédié, pas une hypothèse de départ |
| **Blnk**                                   | Les types de solde `available`/`pending`/`held` (§6), l'esprit API REST développeur-friendly                                                                                                  | Pas de plateforme fermée — reste un module du cœur ouvert, pas un service managé séparé                                                                                                                   |
| **Modern Treasury / Twisp** (SaaS managés) | L'objectif de parité fonctionnelle sur la réconciliation (§7)                                                                                                                                 | Ce projet est délibérément self-hosted/open-source, pas un SaaS — c'est le socle qu'une startup héberge elle-même                                                                                         |

---

## 21. Résumé des décisions structurantes (à ne pas dévier sans ADR)

| Décision                                                                             | Alternative écartée                                         | Raison                                                                                        |
| ------------------------------------------------------------------------------------ | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Append-only + solde en projection                                                    | `balance` mutable sur Account                               | Auditabilité, pas de perte d'historique                                                       |
| Optimistic locking + retry                                                           | Lock pessimiste global                                      | Throughput sous charge                                                                        |
| Outbox transactionnel dans le cœur                                                   | 2PC / XA                                                    | Complexité opérationnelle, fragilité                                                          |
| Idempotency table + hash body                                                        | Idempotence "au mieux" via UUID seul                        | Détecte les réutilisations erronées de clé                                                    |
| Hash-chained audit log                                                               | Log applicatif classique                                    | Détectabilité de falsification                                                                |
| Taux figé dans le JournalEntry                                                       | Recalcul à la demande                                       | Reproductibilité historique                                                                   |
| Spring Boot + Virtual Threads                                                        | Go                                                          | Charge I/O-bound, écosystème Spring déjà exploité                                             |
| CLI en module séparé (surfaces ops + api)                                                | CLI embarquée dans le runtime / supervisord custom          | Découplage, Compose/K8s gardent le cycle de vie ; CLI SSH-first (ADR-015)                      |
| Module Fraude en bounded context optionnel                                           | Règles de fraude dans le domaine ledger                     | Cycle de vie différent, ne doit pas fragiliser le cœur comptable                              |
| Split déclaratif structuré (YAML/JSON) + ports `SplitPlanResolver` / `FeeReversalPolicy` | Fee Engine comme nouveau domaine dans le cœur ; DSL scripté complet dès le v1 | KISS + non-présomption — le pricing reste extérieur ; le ledger enregistre un `SplitPlan` valide (§5.2, §5.3) |
| Taxonomie frais granulaire (`FEE_PLATFORM_REVENUE`, `FEE_INTERCHANGE_COST`, …)           | Un seul compte `FEE_REVENUE`                                                | Conformité comptable / P&L auditable par transaction (§1.2)                                                   |
| REST + OpenAPI 3.1 comme seule interface officielle en v1                                | gRPC "officiel" au même rang que REST                                       | Double contrat versionné = dette permanente sans bénéfice de sécurité ; gRPC reste adapter optionnel (§2.3)  |
| Un seul client de référence non-garanti (`/sdk-reference/`)                              | 3 SDK officiels multi-langues dès le v1                                     | Charge disproportionnée pour un OSS naissant ; SDK multi-langues reportés post-v1 (§15, §19)                  |
| Précisions sécurité diluées dans §10/§11 (JWT allowlist, TLS 1.3, `traceparent`)         | Chapitre "Integration & Security Layer" dédié (Gateway/Mesh/WAF/SIEM)       | La sécurité vient du modèle de menace, pas de la surface d'intégration ; hors non-présomption (§0.6, §11)    |
| Types de solde comme agrégation de `settlementStatus`                                    | Nouveau concept de compte séparé pour "pending"/"held"                      | Réutilise l'unique flux append-only existant, zéro duplication de vérité                                      |
| Config en couches (image → fichier optionnel → env vars) + `finledger.env.example`       | Système de config propriétaire ou wizard interactif au boot                 | Zéro réinvention, compatible orchestrateurs, jamais de blocage au démarrage                                   |
| Install eval type Blnk (`compose --profile sandbox`) + image Hub pour la prod            | Wizard JVM ou CLI qui spawn le serveur comme chemin principal               | DX OSS rapide ; prod = image + JWT always-on (ADR-012, ADR-015, ADR-016)                                      |
| Profils `sandbox`/`normal` + JWT always-on (émetteur externe ou interne)                 | Modes `disabled` / Bearer static éternel / trust_edge                       | Zéro confiance non vérifiée ; secrets long-vécus = mint seulement (ADR-016)                                   |
| Aucun broker/IAM/secrets/rail/gateway/mesh imposé par défaut                             | Choix figé d'un fournisseur ou topologie en dur                             | Le projet est un socle open source pour startups qui ont déjà ou choisiront leur propre stack                 |
