# Ubiquitous Language v1.1

![[img/DS_v1.1.png]]
![[img/DS_v1.2.png]]

| Termine                             | Definizione                                                                                                                                                                                                                                                                                         |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Centrale Operativa**              | Unità operativa che abilita un Soccorritore e un Veicolo di Soccorso (Ambulanza o Elisoccorso) per dirigersi verso il luogo in cui prestare soccorso                                                                                                                                                |
| **Soccorritore**                    | Unità in grado di operare nel Scenario di Soccorso. Spesso è un volontario o dipendente che ha un addestramento al primo soccorso. Non può fare diagnosi o somministrare farmaci. Può guidare un mezzo (solitamente ambulanze e automediche) e fare manovre base di primo soccorso.                 |
| **Infermiere**                      | Unità in grado di fare le manovre di un Soccorritore ed in più può eseguire manovre avanzate                                                                                                                                                                                                        |
| **Medico**                          | Unità che ha la responsabilità clinica totale e viene coinvolto in casi in cui il codice gravità è gialla/rossa                                                                                                                                                                                     |
| **Valutazione Preventiva (Triage)** | Valutazione preventiva che viene fatta per valutare la situazione critica del Paziente. In particolare è un'intervista fatta dalla Centrale Operativa al cittadino che ha chiamato il numero di emergenza. Il risultato è il Codice colore che determina quale mezzo coinvolgere.                   |
| **Codice colore**                   | Codice relativo alla situazione del paziente e viene determinato durante l'intervista (che segue dei protocolli) con l'ente che chiama il 118. Può essere Bianco(situazione non critica), Verde(bassa gravità), Azzurro(urgenza differibile), Gialla (rischio potenziale) o Rosso(pericolo di vita) |
| **Missione**                        | L'unità di lavoro che inizia quando viene conclusa la chiamata alla Centrale Operativa e termina con l'Handover                                                                                                                                                                                     |
| **Equipaggio**                      | Team di professionisti (che può includere Soccorritori, Infermieri e Medici) a bordo di un veicolo di soccorso. Dipende dal veicolo di soccorso                                                                                                                                                     |
| **Ambulanza**                       | Veicolo di soccorso via terra dotato di un mezzo di comunicazione con il PS e tiene a bordo un Soccorritore (che non è quello che guida). Equipaggio: Soccorritore (autista) e Infermiere                                                                                                           |
| **Automedica**                      | Veicolo di soccorso in cui a bordo è presente un medico e un infermiere che forniscono supporto all'ambulanza in caso di codice di massima gravità. Equipaggio: Infermiere e Medico oppure Soccorritore e Medico                                                                                    |
| **Cinematica del trauma**           | Il che cosa ha provocato il Trauma, i.e. incidente stradale, incendio, ...                                                                                                                                                                                                                          |
| **Segnalazione**                    | Evento di innesco del sistema in cui sono presenti possibili casi di Trauma. Solitamente viene fatto da un ente (i.e. cittadino) che chiama al numero di emergenza(112/118) per contattare la Centrale Operativa                                                                                    |
| **Valutazione Clinica**             | Valutazione fatta sul posto per determinare parametri vitali e GCS                                                                                                                                                                                                                                  |
| **Scenario di Soccorso**            | Il luogo fisico in cui "è stato innescato" il Trauma nel Paziente                                                                                                                                                                                                                                   |
| **Elisoccorso**                     | Veicolo di soccorso aereo dotato di dispositivi per tenere traccia di Parametri Vitali. Equipaggio: Infermiere coordinatore, Infermiere assistente di volo, Medico e Soccorritore pilota.                                                                                                           |
| **Parametri vitali**                | Vengono valutate al momento dell'arrivo dell'Equipaggio e servono per confermare il codice di gravità del paziente                                                                                                                                                                                  |
| **Paziente**                        | Entità principale del dominio di cui si deve monitorare la sua situazione in caso di Trauma.                                                                                                                                                                                                        |
| **Evento**                          | Qualsiasi evento registrato dall'inizio della chiamata alla Centrale Operativa fino all'Handover, i.e. arrivo mezzo di soccorso, avvio missione, ... (solitamente con un timestamp dell'evento)                                                                                                     |
| **Report**                          | Documento in cui vengono registrati tutti gli eventi rilevati e rilevanti sia da un punto di vista medico che legale                                                                                                                                                                                |
| **Handover**                        | Fase di intermezzo dalla fase extra-ospedaliera a quella intra-ospedaliera, in cui l'evento più importante è l'arrivo del Paziente al PS. Durante la consegna vengono date le informazioni sul paziente, sia in maniera verbale che documentale, consegnando il Report PreH                         |
| **Pronto Soccorso**                 | Luogo di destinazione finale della fase PreH                                                                                                                                                                                                                                                        |

Fonti:
- https://www.118er.it/romagna/index
- https://www.auslromagna.it/luoghi/pronto-soccorso/ticket

## Scenario principale
### Attivazione
La **Missione** si attiva nel momento in cui un cittadino chiama il numero di emergenza (112/118) per contattare la **Centrale Operativa**. La Centrale Operativa segue dei protocolli per effettuare un'analisi preventiva sul **Paziente**, determinando il **Codice colore**. Dal Codice colore si determina la tempestività con la quale il Paziente deve essere soccorso
- Bianco -> Non urgenza, Ambulanza
- Verde -> Urgenza minore, Ambulanza
- Azzurro -> Urgenza differibile (Al momento da non considerare, guarda https://www.118er.it/romagna/index)
- Giallo -> Urgenza indifferibile, Ambulanza (con infermiere a bordo)
- Rosso -> Emergenza, Ambulanza e automedica oppure Ambulanza e Elisoccorso (quest'ultimo in caso di zona impervia)
Una volta determinato il Codice colore, viene scelto un Equipaggio e i mezzi di soccorso.
### Valutazione
Il veicolo raggiunge lo Scenario di Soccorso. L'Equipaggio mette in sicurezza l'area ed effettua una **Valutazione Clinica** basata su protocolli standard (che include il calcolo del GCS e di altri **Parametri vitali**). Questa valutazione conferma la gravità e determina quale sarà l'ospedale di destinazione.

### Tracciamento
Durante l'intero svolgimento della missione, ogni azione significativa (arrivo del mezzo, avvio missione, ecc.) viene registrata come un **Evento**, dotato di un suo timestamp. Tutti gli Eventi, che riguardano direttamente il Paziente e il veicolo di soccorso coinvolto, vengono raccolti e storicizzati in un **Report**, un documento (fisico o digitale) che ha valenza sia medica che legale e mostra un resoconto della fase di tracciamento.

### Consegna
La Missione termina con la fase di **Handover**. Questo è il momento di intermezzo che segna il passaggio dalla fase extra-ospedaliera a quella intra-ospedaliera, il cui evento scatenante è l'arrivo fisico del veicolo e del Paziente al **Pronto Soccorso**. Il Pronto Soccorso rappresenta il luogo di destinazione finale di tutta la fase pre-ospedaliera (PreH).



## Scenario secondario: Elisoccorso

Se lo scenario è in zone impervie, molto distanti, o la Cinematica del trauma è critica, la Centrale Operativa attiva l'**Elisoccorso**. L'elicottero trasporta un Equipaggio specializzato in terapia intensiva direttamente sul target o presso una piazzola di rendez-vous concordata con l'Ambulanza.


## Scenario secondario: Automedica

Se la Centrale Operativa assegna un codice di massima gravità (Codice Rosso), attiva simultaneamente un'Ambulanza (per il trasporto) e un'**Automedica** (per portare Medico e Infermiere sul posto). Se l'Ambulanza arriva per prima, l'equipaggio base inizia la stabilizzazione in attesa dell'équipe medica avanzata.


# Requisiti

## Requisiti funzionali
- Tracciamento dei mezzi e target (pazienti e equipaggio) coinvolti
- Favorire il passaggio di informazioni tra equipaggiamento extra-ospedaliero e intra-ospedaliero
- Affidabilità e tracciabilità: tutti gli eventi registrati devono essere segnati su un report, sia per cause mediche che per cause legali 
## Requisiti non funzionali
- Tempestività: minimizzare il tempo di risposta e il tempo di trasporto in ospedale (seguendo la norma del Golden Hour, ovvero intervenire sul paziente entro 60 minuti)

