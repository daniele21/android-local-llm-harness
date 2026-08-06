# Piano di implementazione della configurazione di generazione e del prompting

**Stato:** implementato localmente — gate fisico e review PR pendenti
**Data:** 2026-08-06
**Base di integrazione:** ultimo `dev` verde
**Target iniziale:** Playground one-shot di `apps/local-llm-phone-test`
**Target successivo:** contratti riutilizzabili dagli adapter Android nativi e Capacitor
**Release target:** non assegnato; non è automaticamente un blocker di Harness 0.5.0

L'implementazione locale copre contratti, preset, risoluzione per campo, prompt planning
model-aware, tokenizzazione esatta, context lazy Auto/manuale, grammar JSON/JSON Schema,
telemetry/Room e controlli Playground. Restano intenzionalmente aperti i punti di evidenza su
GGUF reale, qualità dei preset, memoria/thermal e accessibilità/screenshot su device.

## 1. Obiettivo

Introdurre una configurazione di inferenza comprensibile e modificabile dall'utente senza
esporre token speciali, template proprietari del backend o dettagli di `llama.cpp` alle
applicazioni chiamanti.

Il risultato deve separare esplicitamente:

1. la configurazione richiesta dall'utente;
2. i preset applicativi versionati;
3. la policy dello use case e del profilo modello;
4. il rendering del prompt e il chat template specifico del modello;
5. la configurazione effettiva della singola esecuzione;
6. la configurazione e il lifecycle del context nativo.

La prima esperienza completa è il Playground one-shot. I contratti, la risoluzione e il
backend non devono però dipendere da Compose o dall'app phone-test.

## 2. Stato iniziale verificato

La baseline corrente offre già:

- binding esplicito `applicationId + useCaseId` verso use case e profilo GGUF;
- `GenerationDefaults` con max output, temperatura, top-p, top-k e seed;
- override pubblici per max output, temperatura e seed;
- context nativo creato con il `contextSize` fisso di `GgufModelProfile`;
- sampler greedy quando la temperatura è zero;
- sampler top-k, top-p, temperatura e distribuzione per temperature positive;
- tokenizzazione e controllo dell'overflow nel backend immediatamente prima del prefill;
- sessione Playground creata e chiusa per ogni esecuzione one-shot;
- telemetry metadata-only priva di prompt e output.

Restano aperti:

- `null` seed viene risolto in `0L` dal runtime invece di rappresentare una policy casuale;
- top-p e top-k non sono override pubblici;
- il tipo pubblico `Long` non esprime il range nativo `uint32_t` del seed;
- il runtime inoltra direttamente `request.input` senza system prompt o chat template;
- la tokenizzazione esatta avviene dopo la creazione del context e non può guidarne la dimensione;
- l'inspector install-time non espone context massimo o capacità di chat template;
- grammar, stop sequence e stop reason non attraversano ancora il contratto end-to-end;
- il Playground limita il max output a 512 token e non espone preset, top-p, top-k o context;
- la telemetry non registra la configurazione effettiva usata.

## 3. Decisioni architetturali da fissare prima del codice

La prima pull request deve aggiungere un ADR proposto e aggiornare l'architettura prima di
modificare i contratti. L'ADR deve fissare almeno queste decisioni:

- `GenerationOverrides` contiene soltanto proprietà applicabili alla richiesta;
- `ContextPolicy` appartiene alla sessione, non agli override di generazione;
- un context `Auto` viene materializzato pigramente dopo rendering e tokenizzazione esatti;
- la prima versione supporta resize automatico soltanto per sessioni stateless;
- un context non viene ridotto durante la vita della sessione; può crescere solo quando la
  policy lo consente e nessuna richiesta lo possiede;
- un resize incompatibile con stato conversazionale non scarta silenziosamente il KV state;
- i preset e gli override dei template sono posseduti dall'applicazione e revisionati nel
  codice; il catalogo remoto può selezionare soltanto un `profileKey` approvato;
- il testo di prompt, template, schema e stop sequence resta fuori da telemetry, log, Room e
  report condivisi;
- i fallback di preset, template e context sono espliciti, deterministici e osservabili;
- nessuna configurazione non valida viene corretta o limitata silenziosamente.

Non creare un nuovo modulo finché le responsabilità possono rimanere nei moduli esistenti con
dipendenze unidirezionali. Un modulo dedicato diventa giustificato solo quando il compilatore di
prompt ha più backend o più consumer reali e un proprio confine di test autonomo.

## 4. Modello target

### 4.1 Flusso di risoluzione

```text
GenerationRequest + logical session
                |
                v
resolve app/use-case/model binding
                |
                v
resolve preset + per-request overrides + seed effettivo
                |
                v
resolve system-prompt policy + output constraint
                |
                v
render model chat template + generation marker
                |
                v
tokenize exactly with the loaded model vocabulary
                |
                v
resolve effective context from prompt tokens + output budget + policy reserve
                |
                v
create/reuse one compatible native context
                |
                v
generate with sampling, grammar and stop policy
                |
                v
events + privacy-safe effective configuration telemetry
```

La risoluzione dell'identità del modello rimane separata e precedente a questo flusso. Nessun
preset può cambiare il digest GGUF o sostituire implicitamente il modello associato allo use
case.

### 4.2 Contratti pubblici proposti

I nomi finali vengono fissati nell'ADR e nei test dei contratti. La forma prevista è:

```kotlin
@JvmInline
value class InferencePresetId(val value: String)

data class InferencePresetRef(
    val id: InferencePresetId,
    val version: Int,
)

sealed interface SeedPolicy {
    data object Random : SeedPolicy
    data class Fixed(val value: Long) : SeedPolicy
}

sealed interface ContextPolicy {
    data object Auto : ContextPolicy
    data class Manual(val tokens: Int) : ContextPolicy
}

data class SessionOptions(
    val contextPolicy: ContextPolicy = ContextPolicy.Auto,
)

data class GenerationOverrides(
    val preset: InferencePresetRef? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val seedPolicy: SeedPolicy? = null,
    val maxOutputTokens: Int? = null,
)
```

`SeedPolicy.Fixed` usa `Long` per interoperabilità Java/Kotlin, ma valida il range
`0..4_294_967_295`. La configurazione backend usa un tipo o una validazione che preservi
esattamente il valore `uint32_t` senza overflow o troncamento.

### 4.3 Input del prompt

La prima tranche mantiene il Playground one-shot, ma il contratto deve preparare il percorso
SDK senza accettare combinazioni ambigue di campi nullable:

```kotlin
sealed interface GenerationInput {
    data class Text(val value: String) : GenerationInput
    data class Messages(val values: List<ConversationMessage>) : GenerationInput
    data class RawCompletion(val value: String) : GenerationInput
}
```

Vincoli iniziali:

- `Text` viene trasformato in un singolo messaggio user;
- `Messages` accetta soltanto ruoli supportati e una lista non vuota e bounded;
- il system prompt applicativo non viene duplicato nei messaggi chiamanti;
- `RawCompletion` è permesso soltanto da una policy esplicita dello use case;
- storia conversazionale persistita e KV state nativo restano responsabilità separate;
- input, messaggi e prompt compilato rimangono soltanto in memoria di processo.

### 4.4 Preset applicativi

Il registro dei preset è applicativo, versionato e fail-closed. Nome e descrizione visibili
restano risorse localizzate della UI; il runtime riceve soltanto ID stabili e policy neutrali.

Ogni preset contiene:

- configurazione di generazione di base;
- riferimento a una prompt intent policy versionata;
- preferenza di context, non un limite hardware inventato;
- eventuali output mode ammessi;
- lista degli use case nei quali è selezionabile.

Preset iniziali da calibrare, non ancora dichiarati definitivi:

| ID stabile | Etichetta UI iniziale | Sampling iniziale | Intent |
| --- | --- | --- | --- |
| `precise-structured` | Preciso e strutturato | greedy, seed fisso | output controllato |
| `short-form` | Titoli e sintesi brevi | bassa temperatura | risposta breve |
| `accurate-summary` | Riassunto accurato | bassa temperatura | aderenza all'input |
| `balanced-conversation` | Conversazione bilanciata | sampling moderato | general purpose |
| `creative-conversation` | Conversazione creativa | sampling più ampio | variazione creativa |

Il preset strutturato non attiva automaticamente una JSON grammar quando manca uno schema o
una output constraint esplicita.

### 4.5 Precedenza

La precedenza viene risolta per campo e ne viene conservata la provenienza:

```text
override manuale valido
        -> preset esplicitamente selezionato
        -> default dello use case
        -> raccomandazione approvata del profilo modello
        -> fallback bounded del runtime
```

Regole:

- preset inesistente, non ammesso o con versione sconosciuta produce errore typed;
- assenza del preset usa i default dello use case senza inventare un preset;
- una modifica UI produce `Personalizzato`, conservando soltanto in memoria il riferimento
  `basedOnPreset`;
- nessun valore viene clamped senza restituire una decisione esplicita;
- il piano effettivo conserva valore e origine di ogni campo per test e diagnostica.

### 4.6 Configurazione effettiva

Separare gli oggetti interni per evitare di mescolare lifecycle e sampling:

```kotlin
data class ResolvedSamplingConfiguration(...)
data class ResolvedPromptPlan(...)
data class ResolvedSessionConfiguration(...)
data class ResolvedOutputConstraint(...)
data class ResolvedGenerationPlan(...)
```

Il testo in `ResolvedPromptPlan` è process-memory-only. La parte pubblica e persistibile è un
riepilogo metadata-only, per esempio:

```kotlin
data class EffectiveGenerationMetadata(
    val preset: InferencePresetRef?,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val requestedSeedPolicy: SeedPolicyType,
    val effectiveSeed: Long,
    val maxOutputTokens: Int,
    val contextSize: Int,
    val promptTokenCount: Int,
    val chatTemplateId: String,
    val chatTemplateSource: ChatTemplateSource,
    val systemPromptVersion: String?,
)
```

Il lifecycle pubblico aggiunge un evento metadata-only dopo il planning e prima del prefill:

```text
Queued -> Prepared(effective metadata) -> Started -> TextDelta* -> Completed/Failed
```

`Prepared` non contiene prompt, messaggi, template text, schema o stop sequence. Il planning
viene eseguito nella stessa corsia serializzata che protegge model/context mutation e controlla
la cancellazione prima e dopo rendering, tokenizzazione e context creation.

## 5. Ownership per modulo

| Responsabilità | Modulo proprietario | Consumer diretti |
| --- | --- | --- |
| Request, session options, seed/context policy, event metadata, stop reason | `core/contracts` | runtime, transport, health, console, phone app |
| Orchestrazione, precedenza, seed materialization, prompt planning e context lifecycle | `core/runtime-core` | in-process transport e app runtime owners |
| Preset/versioni, use-case prompt policy e raccomandazioni approvate | `models/model-profile` | runtime e composition root applicativi |
| Rendering/tokenizzazione capability e adattamento neutralizzato | `backends/llama-cpp` | runtime adapter |
| Grammar, sampler, stop token/sequence e context nativo | `backends/llama-cpp` C++/JNI | Kotlin backend bridge |
| Run metadata e retention | `observability/contracts` e store | Diagnostics e benchmark engine |
| Controlli, stato `Personalizzato` e diagnostica effettiva | `apps/local-llm-phone-test` | utente Playground |
| Token visuali e componenti riusabili | `ui/design-system` | app Android |

Il catalogo remoto non possiede preset, system prompt, template o parametri nativi. Può
continuare a selezionare soltanto un `profileKey` applicativo approvato.

## 6. Sequenza di implementazione

Ogni tranche nasce dall'ultimo `dev` verde, apre una pull request verso `dev` e deve essere
integrabile indipendentemente. Non mantenere una lunga catena di branch stacked.

### CFG-00 — ADR e specifica eseguibile

Ambito:

- [ ] aggiungere un ADR proposto su generation planning, template trust e context Auto;
- [ ] aggiornare `docs/architecture.md` con i nuovi confini e il flusso di risoluzione;
- [ ] fissare compatibilità e strategia di migrazione dei contratti pubblici;
- [ ] definire semantica stateless/conversational e resize del context;
- [ ] definire elenco iniziale di error code, stop reason e provenance;
- [ ] confermare che nessun nuovo modulo è necessario nella prima versione.

Criterio di uscita:

- decisioni accettate senza conflitto con ADR 0001 e ADR 0005;
- nessun dettaglio specifico di `llama.cpp` compare nei contratti pubblici;
- il piano dei consumer diretti è completo prima del primo cambio API.

### CFG-01 — Contratti di sampling e seed

Ambito:

- [ ] aggiungere `SeedPolicy` e gli override top-p/top-k/repeat penalty;
- [ ] introdurre un `SeedSource` iniettato nel runtime;
- [ ] materializzare il seed casuale una sola volta per request plan;
- [ ] validare temperatura `0..2`, top-p `(0, 1]`, top-k bounded, repeat penalty/window e seed uint32;
- [ ] preservare il comportamento greedy esistente per temperatura zero;
- [ ] aggiornare health engine, console, trasporto in-process, fake e app consumer;
- [ ] mantenere temporaneamente compatibilità sorgente dove una delega sicura è possibile.

Test minimi:

- precedenza degli override;
- seed fisso ai due estremi del range;
- seed casuale deterministico con fake `SeedSource`;
- valori non finiti e fuori range;
- temperatura zero che non costruisce sampler stocastici;
- consumer compilation per tutti i contratti pubblici.

Criterio di uscita:

- `null` non significa più implicitamente seed zero;
- ogni richiesta possiede un seed effettivo riproducibile;
- top-p, top-k e repeat penalty/window attraversano contratto, runtime, backend e test.

### PRM-01 — Input strutturato e registry applicativi

Ambito:

- [ ] introdurre `GenerationInput` e messaggi neutralizzati;
- [ ] introdurre `InferencePresetRef` negli override pubblici;
- [ ] aggiungere registry fail-closed per preset e system prompt versionati;
- [ ] spostare la risoluzione dei default in un resolver puro e deterministico;
- [ ] collegare `UseCaseProfile.systemPromptVersion` alla risoluzione reale;
- [ ] impedire al catalogo remoto di fornire testo o override arbitrari;
- [ ] definire raw completion come capability esplicita dello use case;
- [ ] bounded validation di messaggi, ruoli e lunghezze senza persistenza.

Test minimi:

- preset noto, sconosciuto, non ammesso e versione non disponibile;
- provenienza per campo nella catena di precedenza;
- system prompt mancante o incompatibile;
- input testuale, messaggi e raw completion autorizzata/non autorizzata;
- nessun prompt o messaggio in errori, log e fixture condivise.

Criterio di uscita:

- il runtime produce un intent plan completo senza accedere a UI o catalog DTO;
- il chiamante non inserisce token speciali o generation marker;
- display name e descrizioni localizzate restano fuori dai contratti core.

### PRM-02 — Chat template, compilazione e tokenizzazione esatta

Ambito:

- [ ] aggiungere al backend una capability neutralizzata sul modello già caricato;
- [ ] risolvere il template secondo la policy approvata;
- [ ] usare il template GGUF soltanto quando supportato dal `llama.cpp` pinnato;
- [ ] applicare system prompt, messaggi e generation marker una sola volta;
- [ ] restituire token esatti, special token e stop token IDs necessari;
- [ ] distinguere template assente da template presente ma non supportato;
- [ ] conservare soltanto ID, versione, origine e fingerprint non reversibile fuori memoria;
- [ ] condividere lo stesso percorso tra generazione aggregata e streaming.

Ordine di template:

```text
template GGUF supportato
        -> override applicativo approvato
        -> fallback di famiglia approvato
        -> raw completion esplicitamente autorizzata
        -> errore typed
```

Test minimi:

- Qwen supportato, template applicativo esplicito e fallback verificato;
- GGUF senza template;
- template GGUF non supportato dal renderer pinnato;
- token count che include marker e special token;
- system role supportato, adattato o rifiutato secondo policy;
- equivalenza del prompt tra percorso streaming e aggregato;
- nessun testo compilato in telemetry o messaggi di errore.

Criterio di uscita:

- il token count è disponibile prima della creazione del context;
- un'app chiamante fornisce contenuto strutturato, non un prompt backend-specifico;
- il fallback utilizzato è deterministico e osservabile.

### CTX-01 — Context Auto e lifecycle lazy

Ambito:

- [ ] rendere `GgufModelProfile.contextSize` una raccomandazione o separarla dalla
  configurazione effettiva del context;
- [ ] introdurre `SessionOptions` e `ContextPolicy` nel contratto pubblico;
- [ ] cambiare `InferenceBackend.createContext` affinché riceva una configurazione neutrale
  esplicita;
- [ ] creare una logical session senza allocare immediatamente il context nativo;
- [ ] calcolare `requiredTokens = promptTokens + maxOutputTokens + policyReserveTokens`;
- [ ] interrogare il modello caricato per il context massimo effettivo;
- [ ] selezionare il più piccolo candidato sufficiente tra quelli consentiti dalla policy;
- [ ] trattare la raccomandazione del device come dato benchmark-backed, non come garanzia;
- [ ] materializzare o riusare un solo context compatibile per sessione;
- [ ] non ridurre automaticamente un context esistente;
- [ ] ricreare un context stateless quando deve crescere e nessuna richiesta lo possiede;
- [ ] rifiutare resize manuali o conversazionali incompatibili senza perdere stato;
- [ ] emettere `Prepared` con configurazione effettiva prima di iniziare il prefill;
- [ ] mantenere planning e context mutation nella corsia serializzata del runtime;
- [ ] gestire close, cancellazione, partial creation e memory pressure in modo idempotente.

Modalità manuale:

- accetta soltanto valori positivi e consentiti dal profilo applicativo;
- non viene modificata silenziosamente;
- fallisce prima del prefill se non contiene prompt e output budget;
- espone required, selected, model maximum e recommendation disponibili senza contenuto.

Test minimi:

- scelta Auto 1K/2K/4K/8K su token count di confine;
- overflow oltre il massimo del modello;
- manuale sufficiente, insufficiente e non supportato;
- reuse di context già sufficiente;
- crescita stateless e assenza di shrink automatico;
- rifiuto di crescita conversazionale con stato;
- cancellation durante planning/creation e cleanup di context parzialmente creato;
- close session prima della prima generazione e close idempotente;
- memory pressure prima e dopo la materializzazione del context.

Criterio di uscita:

- la validazione context avviene prima del prefill con errore typed;
- nessun prompt, system prompt o schema viene troncato automaticamente;
- il runtime conserva il default di un solo modello caricato e un solo decode attivo;
- la dimensione effettiva del context è disponibile negli eventi e nella diagnostica.

### OUT-01 — Output constraint, grammar, stop e stop reason

Ambito:

- [ ] introdurre `OutputConstraint` separato dal preset;
- [ ] supportare testo, JSON e JSON Schema soltanto dove la capability è disponibile;
- [ ] costruire grammar e sampler nello stesso percorso streaming/aggregato;
- [ ] aggiungere stop token IDs e stop sequence bounded;
- [ ] evitare di emettere nel delta terminale il testo appartenente alla stop sequence;
- [ ] aggiungere uno `StopReason` stabile al risultato pubblico e alla telemetry;
- [ ] rifiutare schema o grammar non validi prima del decode;
- [ ] mantenere schema e stop sequence fuori dalla persistenza.

Stop reason iniziali:

```text
END_OF_GENERATION
MAX_OUTPUT_TOKENS
STOP_SEQUENCE
GRAMMAR_COMPLETE
```

La cancellazione continua a essere un esito cancellato, non una completion ordinaria.

Test minimi:

- EOS/EOG, max output, stop token e stop sequence su confini di chunk;
- stop sequence UTF-8 distribuita su più token e callback;
- grammar JSON valida e schema non valido;
- cancellazione durante prefill e decode con grammar attiva;
- cleanup sampler/grammar su fallimento;
- parità streaming/aggregato.

Criterio di uscita:

- il preset preciso non promette JSON valido senza constraint;
- ogni completion espone un motivo terminale stabile;
- grammar e stop non duplicano il loop di generazione nativo.

### OBS-01 — Configurazione effettiva e migrazione telemetry

Ambito:

- [ ] estendere `GenerationRunRecord` con soli campi metadata bounded;
- [ ] registrare preset ID/version, sampling incluso repeat penalty/window, seed policy/effettivo, max output, context,
  prompt token count, template ID/origine, system prompt version e stop reason;
- [ ] registrare tempi di prompt planning e context creation come misure nullable e source-backed;
- [ ] aggiornare repository in-memory, Room, DAO, mapper, query e fake;
- [ ] aggiungere una migrazione Room non distruttiva con campi nullable per record storici;
- [ ] aggiornare run timeline e Diagnostics senza inventare valori per vecchi record;
- [ ] mantenere la persistenza best-effort e isolata dall'esito dell'inferenza;
- [ ] aggiungere limiti espliciti per ogni stringa e nessun campo libero arbitrario.

Non persistere:

- prompt o messaggi;
- output generato;
- testo del system prompt o del chat template;
- schema, grammar o stop sequence;
- document URI, URL o path privati;
- eccezioni native arbitrarie.

Test minimi:

- parità in-memory/Room;
- migrazione del database dalla versione precedente;
- record nuovi e storici nella stessa query;
- failure isolation della telemetry;
- privacy assertion su record, log, timeline e report condivisi;
- bounded retention invariata.

Criterio di uscita:

- un'esecuzione può essere diagnosticata e riprodotta disponendo dello stesso input privato;
- la diagnostica non contiene materiale dal quale ricostruire prompt o output;
- la migrazione preserva tutti i record precedenti.

### UX-01 — Preset e controlli del Playground

Dipendenza: completamento della migrazione Playground ViewModel/UDF e contratti core stabili.

Ambito:

- [ ] sostituire `PlaygroundRequestOptions` app-specifico con mapping verso contratti condivisi;
- [ ] introdurre selezione preset e stato `Personalizzato`/`Basato su` in memoria;
- [ ] aggiungere temperatura, top-p, top-k, repeat penalty/window, seed policy, max output e context policy;
- [ ] usare slider più input numerico dove migliora precisione e accessibilità;
- [ ] disabilitare top-p, top-k e seed quando temperatura zero rende il percorso greedy;
- [ ] distinguere valori richiesti, default e configurazione effettiva;
- [ ] mostrare prima del run `Auto`; mostrare la dimensione effettiva appena il planning termina;
- [ ] mostrare massimo modello e recommendation device soltanto se disponibili da fonti reali;
- [ ] presentare gli errori di capacity con azioni per aumentare context, ridurre output o input;
- [ ] mostrare template ID/origine, prompt token count, system prompt version, seed effettivo e
  stop reason nella diagnostica post-run;
- [ ] mantenere prompt/output soltanto nel ViewModel/process memory;
- [ ] localizzare label e numeri senza cambiare il formato serializzato dei contratti.

Regole UI:

- `Max output tokens`, non `Max tokens`;
- top-p usa `(0, 1]` e non accetta zero;
- top-k zero significa filtro disabilitato;
- repeat penalty `1` significa filtro disabilitato e un valore maggiore di `1` richiede una finestra positiva;
- temperatura zero rende chiaramente inattivi gli altri sampler;
- un manual context insufficiente non viene aumentato automaticamente;
- preset e valori benchmark-dependent sono etichettati come configurazioni iniziali finché non
  esiste evidenza rappresentativa.

Test minimi:

- UDF per selezione, modifica, reset e `Personalizzato`;
- validazione condivisa dei limiti senza duplicazione in Compose;
- semantica e focus dei controlli disabilitati;
- font scale, TalkBack, tastiera e valori localizzati;
- stati Auto planning, manual overflow, template unavailable e generation failure;
- rotazione e navigazione senza prompt/output in SavedState;
- screenshot dark/light compact ed expanded con fixture non illustrative.

Criterio di uscita:

- tutti i controlli modificano il piano runtime reale;
- la UI non possiede precedence, seed generation, context sizing o template policy;
- valori diagnostici e stati unavailable provengono da contratti reali.

### VAL-01 — Calibrazione preset e device evidence

Ambito:

- [ ] definire una matrice di Qwen presenti nel catalogo, quantizzazioni e device rappresentativi;
- [ ] eseguire corpus privacy-safe per classificazione, short form, summary, balanced e creative;
- [ ] confrontare qualità, ripetibilità, TTFT, throughput, PSS, context allocation e thermal state;
- [ ] validare context 1K/2K/4K/8K senza dichiarare 16K supportato in assenza di evidenza;
- [ ] verificare JSON grammar e stop behavior sul backend reale;
- [ ] verificare resize/reuse e ripetuti lifecycle senza crescita PSS non bounded;
- [ ] aggiornare versioni dei preset quando i valori cambiano;
- [ ] conservare report privacy-safe senza prompt o output normali;
- [ ] mantenere aperto il gate di produzione finché la matrice fisica non è completata.

Criterio di uscita:

- i valori dei preset sono basati su evidenza ripetibile e versionata;
- le recommendation di context/device sono associate alla matrice misurata;
- nessuna evidenza emulator/host viene descritta come fisica o production-ready.

## 7. Errori pubblici e recovery

La prima tranche contrattuale deve introdurre reason code stabili senza persistere messaggi
arbitrari:

| Reason code | Momento | Recovery previsto |
| --- | --- | --- |
| `PRESET_NOT_FOUND` | risoluzione | scegliere una versione disponibile |
| `PRESET_NOT_ALLOWED` | risoluzione | usare un preset ammesso dallo use case |
| `INVALID_GENERATION_CONFIGURATION` | validazione | correggere il campo indicato |
| `RAW_COMPLETION_NOT_ALLOWED` | prompt planning | usare input strutturato |
| `CHAT_TEMPLATE_UNAVAILABLE` | template resolution | configurare un fallback approvato |
| `CHAT_TEMPLATE_UNSUPPORTED` | rendering | aggiornare profilo/backend o scegliere un modello compatibile |
| `PROMPT_TOKENIZATION_FAILED` | planning | correggere input o modello |
| `CONTEXT_CAPACITY_EXCEEDED` | context planning | aumentare context o ridurre input/output |
| `CONTEXT_RECONFIGURATION_REQUIRED` | session lifecycle | creare o autorizzare una nuova sessione stateless |
| `OUTPUT_CONSTRAINT_UNSUPPORTED` | output planning | rimuovere constraint o usare capability supportata |

Dopo ogni errore di planning la sessione deve rimanere chiudibile e il runtime deve poter
eseguire una richiesta valida successiva senza riavvio dell'applicazione.

## 8. Matrice di test trasversale

| Area | Unit | Integration | Native | UI/device |
| --- | --- | --- | --- | --- |
| Precedenza e preset | resolver puro | runtime + registry fake | n/a | modifica/reset preset |
| Seed | source fake/range | request plan + telemetry | uint32 exact | seed effettivo visibile |
| Prompt | registry/compiler | runtime + backend fake | template/tokenization | output con Qwen reale |
| Context | selector/lifecycle | orchestrator + fake handles | create/release/overflow | PSS e resize fisico |
| Grammar/stop | config validation | streaming parity | sampler/UTF-8/chunk | JSON e stop reali |
| Telemetry | mapping/retention | in-memory/Room parity | n/a | Diagnostics e privacy |
| Playground | ViewModel/UDF | controller/runtime fake | n/a | Compose/accessibility |

I test devono includere successo, input invalido, fallimento, cancellazione, cleanup parziale,
close idempotente e recovery. I test di prompt non devono inserire contenuti sensibili nei
fixture o nei report.

## 9. Validazione per le tranche

Durante l'iterazione eseguire i gate dei moduli modificati. Prima di ogni push di una tranche
multi-dominio eseguire almeno:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :core:runtime-core:testDebugUnitTest \
  :models:model-profile:testDebugUnitTest \
  :backends:llama-cpp:testDebugUnitTest \
  :observability:in-memory-store:testDebugUnitTest \
  :observability:room-store:testDebugUnitTest \
  :observability:health-engine:testDebugUnitTest \
  :apps:local-llm-phone-test:testDebugUnitTest
```

Prima del merge di cambi ai contratti pubblici, JNI o più domini usare il gate Android completo
definito nel root `AGENTS.md`. Per le tranche native eseguire anche:

```bash
cmake -S backends/llama-cpp/src/test-native -B build/native-tests -DCMAKE_BUILD_TYPE=Release
cmake --build build/native-tests --parallel 2
ctest --test-dir build/native-tests --output-on-failure
./gradlew :backends:llama-cpp:assembleDebug \
  :apps:device-test-runner:assembleDebug \
  :apps:device-test-runner:assembleDebugAndroidTest \
  :apps:local-llm-phone-test:assembleDebug
python3 scripts/verify-android-packaging.py
```

Verificare i nomi effettivi dei task Gradle durante CFG-00; il gate documentato non deve
introdurre comandi non esistenti. La produzione richiede infine la procedura fisica descritta in
`docs/device-e2e-testing.md`.

## 10. Dipendenze e parallelismo consentito

```text
CFG-00
  |
  v
CFG-01 -> PRM-01 -> PRM-02 -> CTX-01 -> OUT-01
                                  |          |
                                  +----+-----+
                                       v
                                     OBS-01
                                       |
UX ViewModel/UDF ----------------------+----> UX-01 -> VAL-01
```

- UX ViewModel/UDF può procedere in parallelo fino alla struttura dello stato e degli effetti;
- non deve implementare nuovi limiti o policy prima di CFG-01;
- telemetry schema parte quando `EffectiveGenerationMetadata` e `StopReason` sono stabili;
- benchmark preliminari possono esplorare valori, ma la versione definitiva dei preset viene
  pubblicata soltanto dopo UX-01 e validazione backend;
- CTX-01 non deve essere sviluppato in parallelo su una branch che modifica gli stessi contratti
  runtime di PRM-02.

## 11. Aggiornamenti documentali per tranche

| Quando | Documenti da aggiornare |
| --- | --- |
| CFG-00 | `architecture.md`, nuovo ADR, questo piano |
| CFG-01 | `api-usage.md`, `implementation-plan.md`, guide dei consumer se cambia navigazione |
| PRM-01/02 | `api-usage.md`, architecture, documentazione prompt/template |
| CTX-01 | `api-usage.md`, architecture, definition of done se cambiano i gate |
| OUT-01 | `api-usage.md`, implementation plan e backend guide se cambiano entry point/test |
| OBS-01 | documenti telemetry/Room, observability guide e migration notes |
| UX-01 | UX/UI plan, phone playground docs e app guide se cambia ownership |
| VAL-01 | roadmap, current-state ed evidence docs con soli risultati realmente ottenuti |

`docs/current-state.md` e le checklist di roadmap vengono aggiornati soltanto quando una tranche
è integrata o un'evidenza è realmente disponibile. Questo piano non costituisce avanzamento di
implementazione.

## 12. Fuori ambito della prima versione

- selezione o sostituzione automatica del modello;
- template o system prompt arbitrari provenienti dal catalogo remoto;
- modifica libera del system prompt nella UI;
- persistenza di conversazioni, prompt o output;
- più context nativi simultanei per la stessa sessione;
- shrink automatico del context durante una sessione;
- ripristino del KV state dopo resize conversazionale;
- prefix snapshot e deterministic result cache;
- GPU offload o variazioni automatiche dei thread basate sul preset;
- min-p, presence/frequency penalty, DRY e sampler ulteriori finché i controlli iniziali non sono stabili;
- dichiarazioni di supporto device o performance senza evidenza fisica rappresentativa.

## 13. Criterio di completamento del workstream

Il workstream è completo quando:

- gli otto controlli richiesti attraversano UI, contratti, resolver, runtime e backend reali;
- preset e prompt policy sono applicativi, versionati, localizzabili e fail-closed;
- il modello renderizza il proprio chat template supportato senza token speciali forniti dal
  chiamante;
- il context Auto usa tokenizzazione esatta e rispetta limiti modello/device senza truncation;
- manual context, resize e session lifecycle hanno errori e cleanup deterministici;
- output constraint, grammar, stop sequence e stop reason condividono il percorso streaming;
- la configurazione effettiva è visibile e persistita senza prompt, output o template text;
- tutti i consumer dei contratti compilano e i gate Android/native/Room sono verdi;
- la UI è coperta da test UDF, Compose, accessibilità e layout;
- i preset definitivi e le recommendation device sono supportati da benchmark versionati;
- il gate fisico rimane esplicitamente aperto fino alla cattura dell'evidenza richiesta.

## 14. Remediation dell'integrazione locale

Stato al 2026-08-06: **IN CORSO**. L'integrazione locale ha completato una prima verticale, ma
non soddisfa ancora il criterio di completamento del workstream. Le correzioni devono essere
integrate nell'ordine seguente; ogni tranche deve lasciare verdi i test mirati prima di iniziare
quella successiva.

### REM-01 — Regressioni eseguibili

- [x] aggiungere test nativi per UTF-8 frammentato, stop su confini di chunk e prima stop
  sequence per posizione;
- [x] aggiungere test runtime per cancellazione durante planning/context creation e recovery;
- [x] aggiungere test del selettore context sui confini 1K/2K/4K/8K e sui target range;
- [x] aggiungere test typed per output constraint non valido;
- [x] aggiungere un test Room reale della migrazione 4→5.
- [x] aggiungere test nativi e Kotlin per la catena repeat-penalty e i relativi limiti.

Criterio di uscita: ogni difetto corretto nelle tranche successive possiede prima una regressione
che fallisce sull'implementazione precedente.

### REM-02 — Motore nativo condiviso, UTF-8 e stop

- [x] eliminare il secondo loop nativo aggregato e derivare ogni aggregazione dall'unico percorso
  streaming sopra il boundary nativo;
- [x] emettere soltanto prefissi UTF-8 completi e conservare i byte incompleti tra callback;
- [x] introdurre una stop policy applicativa bounded e propagarla dal prompt plan al backend;
- [x] scegliere la stop sequence con posizione più precoce e non emetterne il testo;
- [x] preparare grammar e sampler prima del prefill e garantire cleanup RAII;
- [x] applicare repeat penalty/window prima del greedy o dei filtri probabilistici;
- [x] mantenere prompt, schema e stop sequence fuori da telemetry e report.

Criterio di uscita: streaming e aggregato condividono sampling, grammar, stop, cancellazione e
terminal reason; i test host coprono UTF-8 e stop senza richiedere un GGUF.

### REM-03 — Cancellazione e context planning

- [x] aggiungere checkpoint di cancellazione dopo ogni fase di planning e prima del prefill;
- [x] rilasciare un context creato dalla richiesta cancellata senza distruggere un context
  preesistente;
- [x] estrarre un selettore context puro con supported sizes, limiti hard e target range soft;
- [x] rappresentare separatamente target minimo, target massimo raccomandato e massimo hard;
- [x] accettare in Manual soltanto dimensioni applicativamente consentite;
- [ ] esporre required, selected e limiti disponibili come metadata bounded.

Criterio di uscita: una cancellazione durante planning/creation non entra nel prefill e una
richiesta valida successiva può riusare la sessione; Auto seleziona il candidato minimo coerente
con il target senza trasformare una recommendation in un hard cap.

### REM-04 — Errori typed e Room verificabile

- [x] aggiungere un reason code pubblico per output constraint non valido e mapparlo prima del
  decode;
- [x] abilitare l'export degli schemi Room e conservare gli schemi 4 e 5;
- [x] validare la migrazione 4→5 con `MigrationTestHelper`, record storico e record nuovo;
- [x] conservare lo schema 6 e validare la migrazione 5→6 con campi repeat nullable per i record storici;
- [x] verificare parità mapper/DAO e assenza di prompt, output, schema e stop nei record.

Criterio di uscita: gli errori correggibili dal chiamante non diventano `NATIVE_RUNTIME` e Room
valida automaticamente la migrazione contro lo schema esportato.

### REM-05 — Playground e diagnostica effettiva

- [x] mantenere label e descrizioni localizzate nella UI, separate dai preset di dominio;
- [x] eliminare la duplicazione dei valori preset tra registry e reducer;
- [x] aggiungere slider più input per temperatura/top-p, quick values per output/top-k e
  selettori espliciti per seed e context;
- [x] aggiungere override espliciti per repeat penalty/window e ripristino dai preset v2;
- [ ] rendere accessibile lo stato greedy e i controlli inattivi;
- [x] conservare `EffectiveGenerationMetadata` nello stato fino alla completion;
- [x] mostrare context, prompt tokens, template, system prompt version, seed e stop reason usando
  soltanto dati reali;
- [ ] aggiungere test UDF, Compose, accessibilità e layout compact/expanded.

Criterio di uscita: gli otto controlli modificano il piano runtime reale, la UI non replica policy
di dominio e la configurazione effettiva resta consultabile dopo la generazione.

### REM-06 — Gate e riallineamento dei ledger

- [ ] eseguire i gate mirati dopo ogni tranche e il gate Android completo alla fine;
- [ ] eseguire i test native host e il test instrumented della migrazione Room;
- [x] riallineare ADR, API usage, current state e roadmap ai soli risultati verificati;
- [ ] mantenere aperto VAL-01 fino a evidenza GGUF su dispositivo fisico rappresentativo.

Criterio di uscita: nessun documento dichiara il workstream completo prima dei gate automatici;
le sole affermazioni di qualità, memoria e performance provengono dall'evidenza fisica prevista.
