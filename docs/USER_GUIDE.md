# Guida di Photograin per Android

## Aprire e modificare una foto

Dalla schermata iniziale, tocca **Apri foto** e scegli un’immagine. Photograin supporta anche diversi formati RAW, oltre a JPEG XL. Le modifiche vengono salvate in una ricetta separata. Usa **Annulla** e **Ripeti** per spostarti nella cronologia e tieni premuto sulla foto per vedere l’originale. Il menu dell’editor consente anche il confronto affiancato e la gestione delle versioni di una foto.

## Area di lavoro

Scorri la barra inferiore per scegliere una categoria. Tocca la freccia a fianco delle categorie per richiudere il pannello e dare più spazio all’anteprima. I pulsanti sulla foto aprono il confronto prima/dopo e la vista a schermo intero. Nella barra superiore, l’icona **Strumenti IA** apre le azioni disponibili sul dispositivo.

Ogni cursore mostra il valore accanto alla traccia. Tocca il valore per ripristinare la regolazione iniziale oppure tieni premuto il nome per aprire la guida, quando disponibile.

## Passare da una foto all’altra

Apri una foto dalla griglia o dalla vista confronto, poi tocca **Pellicola** accanto ai comandi dell’anteprima. Tocca una miniatura oppure usa **Foto precedente** e **Foto successiva**. Con una tastiera fisica puoi usare **←** e **→**.

La pellicola segue l’album, i filtri e l’ordine visibili al momento dell’apertura. Mostra una miniatura per scatto. Per la prima foto apre la versione selezionata, mentre per gli altri scatti apre la prima versione visibile nella raccolta. Puoi cambiare versione dal menu **Versioni**. La pellicola ricorda la versione usata per lo scatto durante la sessione. Prima del passaggio Photograin salva le regolazioni della foto corrente e riapre lo stesso pannello di editing sullo scatto successivo. Tornando alla galleria ritrovi album, filtri e ordinamento.

Per valutare una serie, usa **stelle** e **bandierina** sotto la pellicola. Le miniature mostrano la valutazione e l’eventuale stato selezionato o scartato. Gli stessi controlli si trovano in **⋮ → Informazioni foto**. Tutte le versioni dello scatto condividono stelle e stato, che trovi anche in galleria. La pellicola mantiene la selezione con cui l’hai aperta, anche se durante la revisione cambi una valutazione.

Quando passi a un altro scatto con la pellicola, apri **⋮ → Applica da foto precedente**. Puoi applicare tutte le regolazioni globali o scegliere i pannelli, le curve, il mix HSL e la LUT. La foto di origine è quella appena lasciata nell’editor, anche se l’hai raggiunta saltando alcune miniature. Ritaglio, maschere, ritocchi, livelli, metadati e appunti restano intatti. Se serve, usa **Annulla** per tornare alle impostazioni precedenti.

## Pannelli dell’editor

| Pannello | Strumenti |
|---|---|
| **Stili** | Preset integrati e personali, importazione di LUT .cube e controllo dell’intensità. |
| **Luce** | Esposizione, contrasto, ombre, luci, bianchi, neri, curve e regolazioni per fasce tonali. **Luce automatica** stima esposizione, contrasto, ombre e luci dall’originale. **Istogramma RGB** mostra i canali colore e la luminanza della foto elaborata. |
| **Colore** | Bilanciamento automatico o con contagocce, temperatura, tinta, HSL, viraggi e regolazioni dei singoli canali RGB. |
| **Dettagli** | Nitidezza, chiarezza, riduzione del rumore, foschia, fuoco morbido, bordi, correzione manuale della distorsione dell’obiettivo e riduzione delle frange viola e verdi. |
| **Effetti** | Grana, vignettatura, bagliore, alone pellicola, filtri creativi, cornici, testo e forme. |
| **Locale** | Maschere a pennello, lineari, radiali e del soggetto, con regolazioni locali e strumenti di ritocco. |
| **Ritaglio** | Proporzioni, rotazione, specchio, raddrizzamento e prospettiva manuale o a quattro punti. |

L’**Avviso esposizione** nel menu ⋮ colora in rosso le luci estreme e in blu le ombre estreme nell’anteprima. Puoi usarlo insieme all’istogramma.

### Sfocatura e privacy

Nel menu ⋮, **Sfoca lo sfondo** crea una maschera del soggetto invertita. In **Locale** puoi regolare il cursore **Sfocatura** e rifinire la maschera. Il **Pennello privacy** crea una maschera vuota con **Pixelatura** attiva. Dipingi sull’intera area da coprire, poi controlla il file esportato a grandezza naturale. La pixelatura leggera e la sfocatura possono lasciare dettagli riconoscibili.

### Sfocatura con mappa di profondità

In **Locale → Importa mappa di profondità**, scegli un PNG, JPEG o WebP in scala di grigi con le stesse proporzioni della foto originale e allineato alla sua orientazione prima del ritaglio. Il bianco e il nero rappresentano gli estremi di profondità. La mappa deve provenire da una cattura o da uno strumento esterno.

Usa **Scegli il fuoco sulla foto** e tocca il piano che vuoi mantenere nitido. **Intervallo a fuoco** allarga la zona nitida lungo la profondità, **Transizione di profondità** ammorbidisce il passaggio e **Sfocatura** controlla l’intensità. L’icona dell’occhio mostra la zona interessata dalla sfocatura. Photograin salva una copia ridotta della mappa con la versione della foto, per usarla anche dopo aver riaperto il progetto o in esportazione.

### Estendere la tela

1. Apri **Effetti** e passa al pannello delle cornici.
2. Seleziona **Estendi bordi**.
3. Regola **Espansione tela** e controlla i quattro lati nell’anteprima.
4. Esporta nel formato desiderato. Per mantenere eventuali zone trasparenti, scegli PNG o WebP.

La tela cresce in modo simmetrico, fino a metà del lato corto della foto su ciascun bordo. Il riempimento prolunga i pixel già presenti ai margini. L’effetto è più discreto su sfondi uniformi.

## Aggiungere un livello immagine

Apri **Effetti → Immagini → Aggiungi immagine**. Puoi aggiungere fino a otto immagini, comprese quelle con trasparenza. Seleziona un livello e regola posizione orizzontale e verticale, dimensione, opacità e rotazione. Per rimuoverlo usa **Rimuovi immagine**. Puoi annullare le modifiche e riaprire la ricetta per continuare a lavorare.

Le immagini si sovrappongono nell’ordine d’importazione, sotto forme e testo. Photograin conserva una copia privata con lato lungo fino a 1600 px. Per una doppia esposizione modificabile, importa la seconda foto come livello e riduci l’opacità.

## Sviluppare le alte luci di un RAW

Apri un file RAW e scegli **Dettagli → Sviluppo RAW**. In **Gestione alte luci** puoi confrontare quattro modalità di LibRaw.

| Modalità | Uso |
|---|---|
| **Taglia** | Limita i canali al valore di saturazione. |
| **Mantieni i canali** | Conserva le differenze fra i canali nelle zone sovraesposte. Possono comparire dominanti. |
| **Miscela** | È la modalità già usata da Photograin e resta il valore predefinito. |
| **Ricostruisci** | Prova a stimare i valori delle alte luci a partire dai canali utilizzabili. |

La scelta è salvata nella ricetta di ciascuna versione ed è applicata prima delle regolazioni dell’editor. Per provare, usa un DNG o un altro RAW con alte luci quasi bruciate, attendi il ricalcolo dell’anteprima e confronta **Miscela** e **Ricostruisci** anche nel file esportato. Puoi tornare alla scelta precedente con **Annulla**.

## Guide di composizione

In **Ritaglio → Guide**, scegli **Regola dei terzi**, **Griglia**, **Centro**, **Diagonali**, **Triangoli**, **Sezione aurea**, **Spirale aurea** o **Nessuna**. Le linee seguono il riquadro mentre sposti o ridimensioni il ritaglio. Con triangoli e spirale, tocca **Orientamento** per passare alle altre disposizioni, comprese le versioni specchiate della spirale. Photograin ricorda quale guida hai selezionato. La guida appare solo mentre ritagli e l’immagine esportata mostra la foto senza linee.

## Prospettiva con guide verticali

Apri **Ritaglio → Guide verticali**. Posiziona le guide gialle sui due lati di una facciata o su due bordi che dovrebbero risultare verticali. Le maniglie superiori condividono la stessa altezza, così come quelle inferiori. **Applica** raddrizza il quadrilatero e adatta il ritaglio. Inquadra una porzione abbastanza ampia per poter applicare la correzione.

## Combinare più immagini

Dalla schermata iniziale, tocca **Combina**, scegli la modalità e poi **Seleziona foto**. L’ordine restituito dal selettore determina l’ordine delle immagini nella composizione. Le celle sono quadrate e la foto viene ritagliata al centro per riempirle.

| Modalità | Come funziona |
|---|---|
| **Collage** | Dispone da 2 a 12 foto su una griglia da due o tre colonne, con margini bianchi. |
| **Striscia orizzontale** | Affianca da 2 a 12 foto su una sola riga. |
| **Panorama** | Cerca la sovrapposizione orizzontale tra 2–6 scatti in ordine da sinistra a destra e sfuma le giunzioni. Usa lo stesso zoom, tieni l’orizzonte alla stessa altezza e lascia circa un terzo di inquadratura comune. |
| **Doppia esposizione** | Usa le prime due foto selezionate. Regola l’opacità della seconda e, da Android 10, prova la modalità **Scolora**. |

Tocca **Esporta** e scegli dove salvare il PNG. L’uscita usa celle da 1200 × 1200 px. Puoi riaprire il PNG nell’editor per ritoccarlo. La composizione usa i file selezionati e produce un’immagine unica.

## Correggere la distorsione dell’obiettivo

In **Dettagli**, muovi **Distorsione obiettivo** in una delle due direzioni finché le linee vicino ai bordi appaiono dritte. Parti da una foto di una facciata o di una griglia e confronta il risultato con l’originale. La correzione è radiale, centrata sulla foto ritagliata, e regola l’inquadratura per riempire il quadro. Con foto molto grandi la risoluzione effettiva può essere ridotta per adattare il rendering alla GPU.

Per riusare una calibrazione, dopo aver regolato la distorsione apri **Dettagli → Profilo obiettivo → Salva valore**. Su un’altra foto, **Applica** carica il valore salvato se corrispondono fotocamera, obiettivo riportato e focale nei metadati. **Salva valore** aggiorna la calibrazione esistente e **Elimina** la rimuove. La correzione applicata alla foto resta modificabile e annullabile.

### Importare un catalogo di profili

In **Dettagli → Profilo obiettivo → Importa catalogo profili**, seleziona un file JSON con le calibrazioni di Photograin. L’importazione sostituisce il catalogo precedente. Le calibrazioni personali vengono conservate e hanno precedenza. Apri il profilo della foto e premi **Applica** per usare la correzione trovata.

Il formato è documentato in [Formato del catalogo](LENS_PROFILES.md). Servono calibrazioni effettuate per il cursore **Distorsione obiettivo** di Photograin.

**Rimuovi frange viola e verdi** attenua le dominanti sui contorni ad alto contrasto. Verifica il risultato ingrandendo i bordi, in particolare nelle zone con vegetazione o soggetti viola.

## Estrarre testo da una foto

Apri la foto e scegli **⋮ → Estrai testo (OCR)**. Seleziona **Italiano** o **Inglese**. Al primo utilizzo scarica il file `ita.traineddata` o `eng.traineddata` da [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) e selezionalo con **Importa modello lingua**. In seguito usa **Riconosci**. Puoi selezionare una parte del risultato o premere **Copia testo**.

Il riconoscimento usa l’anteprima dell’originale orientata secondo EXIF. Modelli e analisi restano sul dispositivo. Per una prova, usa una pagina stampata ben illuminata con caratteri nitidi.

## Esportazione

- Seleziona JPEG, PNG, WebP, AVIF, JPEG XL o TIFF a 16 bit. La disponibilità di alcune operazioni dipende dalle librerie native incluse nella build.
- Scegli la risoluzione originale, un preset, un lato lungo personalizzato o un ingrandimento. Il lato lungo personalizzato si riferisce alla foto prima dell’eventuale cornice.
- Per JPEG e WebP, **Peso massimo del file (KB)** riduce la qualità e, se necessario, la risoluzione. Il limite include i metadati. Per 1 MB inserisci 1000.
- Se mantieni i metadati, **Modifica EXIF** consente di impostare autore, copyright, descrizione e data sulla copia. Inserisci la data come `2026-11-13T18:30:00`. Il GPS è escluso per impostazione predefinita.
- In **Serie**, seleziona più foto per applicare lo stesso preset o le stesse regolazioni copiate. Il limite di peso si applica a ogni file esportato.

## Verifiche sul telefono

| Prova | Risultato da controllare |
|---|---|
| Estendi una foto con bordi di colori diversi. Esporta anche una foto grande. | La cornice usa i colori dei bordi corretti. Controlla le giunzioni nei file ad alta risoluzione. |
| Componi quattro immagini orientate diversamente. | Ordine, rotazione, ritaglio e margini corrispondono all’anteprima. |
| Esporta una doppia esposizione con opacità 0%, 50% e 100%. | La visibilità del secondo scatto segue il cursore. Controlla anche la modalità Scolora. |
| Modifica esposizione e bilanciamento del bianco. | L’istogramma reagisce e Annulla ripristina i valori precedenti. |
| Esporta un JPEG grande con limite di 500 KB e autore personalizzato. | Il file rispetta il limite e contiene l’autore impostato. |

## Funzioni ancora da integrare

Distribuzione di un catalogo di calibrazioni misurate e verificate per obiettivi reali. Il panorama compensa piccoli spostamenti verticali e rotazioni. Le deformazioni prospettiche richiedono ulteriori interventi. La striscia orizzontale usa una composizione affiancata.

In **Esporta → Profili di esportazione**, scegli **Messaggi** per un JPEG sRGB fino a 2048 px senza metadati, **Stampa** per un TIFF a 16 bit fino a 3543 px sul lato lungo a 300 PPI, o **Archivio** per un JPEG XL senza perdita a piena risoluzione. La dimensione massima limita il lato lungo senza ingrandire gli originali piccoli. I profili sono disponibili anche in **Serie**. Puoi salvare le opzioni correnti con un nome e rimuovere i profili personali dallo stesso menu.

## Album e ricerca locale

Dalla schermata iniziale apri **Album** per creare o scegliere una raccolta. Usa **Album** sotto una miniatura per aggiungere o togliere la foto da una raccolta. **Foto recenti** torna alla vista di partenza. **Elimina album corrente** rimuove la raccolta e conserva le foto originali.

Cerca per nome del file, fotocamera, obiettivo, data, ISO o parole chiave. I risultati dipendono dai metadati presenti nei file selezionati. Puoi combinare la ricerca con **RAW**, **Solo modificate**, stelle e bandierine. La ricerca riguarda l’album aperto o le foto recenti e legge i metadati sul dispositivo.

Per assegnare parole chiave, tocca **Parole chiave** sotto una foto nella griglia o apri **⋮ → Informazioni foto → Parole chiave** nell’editor. Separa i termini con virgole o andando a capo. Tutte le versioni della stessa foto condividono le parole chiave.

Usa **Filtra per parola chiave** per selezionare una voce esatta nella raccolta aperta. Il filtro si combina con testo, RAW, modifiche e valutazioni. La ricerca testuale nelle parole chiave ignora maiuscole e accenti. Le parole chiave sono salvate nel catalogo di Photograin sul dispositivo.

Tocca **Ordina** per scegliere l’ordine della raccolta, la data di scatto, il nome del file o le stelle. Usa **Inverti ordine** per cambiare verso e **Raggruppa per** per mostrare sezioni per anno, mese o giorno di scatto, oppure per stelle. La data è quella originale nei metadati EXIF. Le foto senza data compaiono alla fine dell’ordinamento cronologico e hanno una sezione dedicata nei raggruppamenti per data. Le versioni dello stesso scatto restano vicine e i filtri attivi si applicano anche ai gruppi.

Applica i filtri di ricerca, RAW, modifiche, parole chiave e valutazione, poi tocca **Salva come album intelligente**. Dai un nome alla ricerca e, se vuoi, imposta un minimo di stelle. Aprila da **Album**. Le regole si applicano alle foto recenti e a quelle presenti negli album manuali, e si aggiornano quando cambiano valutazioni, metadati letti o modifiche della foto. I filtri della vista possono restringere ulteriormente l’album aperto.

Tocca **Confronta raffiche** per raggruppare gli originali visibili con data di scatto EXIF e intervalli fino a due secondi tra uno scatto e il successivo, per la stessa fotocamera quando indicata. Le due anteprime condividono zoom e spostamento. Porta il mirino sul soggetto e confronta l’indicatore **Dettaglio**, una stima che può essere influenzata da rumore e trama. **Seleziona** assegna la bandierina allo scatto scelto.

## Organizzare e trasferire il lavoro

- In **⋮ → Trova regolazioni** cerca i cursori globali per nome o pannello. Tocca la stella per aggiungere una regolazione ai preferiti. **Solo modificate** mostra i valori diversi da quelli predefiniti. I filtri si possono combinare.
- Nella griglia iniziale usa **Valuta foto** per assegnare da una a cinque stelle oppure una bandierina di selezione o scarto. Il filtro sopra la griglia funziona anche nella vista confronto. Le versioni della stessa foto condividono la valutazione.
- In **Stili**, applica un preset e usa **Intensità preset**. A 0% rivedi le regolazioni precedenti, a 100% ottieni il preset completo. L’anteprima si aggiorna mentre trascini e la modifica viene salvata quando rilasci il cursore. Tocca il valore per tornare al 100%. Il cursore è disponibile anche per i preset selettivi e XMP. Se cambia un colore campionato o si passa tra due LUT attive diverse, il preset si applica al 100%.
- In **Stili → Salva preset**, scegli quali regolazioni includere. Luce, Colore, Dettagli, Effetti, Curve, HSL e LUT si possono selezionare separatamente. Quando applichi il preset, le regolazioni escluse mantengono i valori della foto di destinazione.
- In **⋮ → Condividi preset** esporta un preset personale come JSON e importalo su un altro dispositivo dallo stesso menu. La LUT associata viene inclusa quando il relativo file è disponibile.

### Importare preset Lightroom e Camera Raw

In **Stili → Importa preset JSON o XMP**, seleziona un file `.xmp` (UTF-8, fino a 1 MiB). Il preset appare in **Stili** e può essere usato anche in **Serie**. Photograin trasferisce solo i controlli compatibili presenti nel file, mantenendo le altre regolazioni della foto anche all’interno dello stesso pannello. Un valore zero scritto nel file ripristina il relativo controllo.

Sono supportati esposizione, contrasto, luci, ombre, bianchi, neri, vividezza, saturazione, chiarezza, texture, rimozione foschia, nitidezza, riduzione rumore, vignettatura, grana, mix HSL e curve a punti RGB. Le scale dei controlli sono convertite e il risultato può differire da Adobe. Profili fotocamera, bilanciamento del bianco, maschere, ritocchi, geometria, preset DNG e archivi ZIP restano fuori dall’importazione. Lo stesso preset può essere condiviso come JSON Photograin, che conserva l’elenco delle regolazioni importate.
- In **⋮ → Backup progetto → Esporta backup** salva un ZIP con le versioni, le immagini dei livelli e le LUT. Se selezioni **Includi originale**, il file originale è anche nel ZIP. Per ripristinare, estrai prima l’originale dal ZIP se necessario, aprilo in Photograin e scegli **Ripristina versioni**. Le ricette importate compaiono come versioni aggiuntive.

## Maschere, prova colore e fusione esposizioni

- In **Locale**, seleziona una maschera a pennello o del soggetto. Con **Dipingi** o **Cancella** e il lazo disattivato, riduci **Flusso** per applicare l’effetto gradualmente. Solleva il dito e ripassa sulla stessa area per aumentarlo. **Densità** imposta il limite di copertura o cancellazione. I nuovi valori valgono per le pennellate successive. Per lavorare a piena forza, imposta entrambi al 100%.
- In **Locale**, usa **Intensità maschera** per regolare insieme le correzioni da 0% a 200%. A 0% ritrovi la foto senza quelle correzioni e a 100% ritrovi i valori originali. I singoli cursori, l’area selezionata e l’eventuale ritaglio dello sfondo conservano le loro impostazioni.
- Usa **Rotazione tonalità** per cambiare i colori dentro la maschera, per esempio per spostare il verde della vegetazione verso il giallo. Il cursore va da −180° a +180°. Entrambi i controlli aggiornano l’anteprima e si applicano all’esportazione.
- Nella maschera, usa **Nitidezza** per accentuare i bordi con valori positivi o ammorbidire i dettagli con valori negativi. **Riduzione rumore** attenua la grana di luminanza, per esempio nelle ombre. **Rimuovi frange viola e verdi** attenua le dominanti lungo i bordi ad alto contrasto. Questi cursori seguono l’intensità e la sfumatura della maschera, in anteprima e in esportazione.
- In **Locale**, seleziona una maschera e scorri fino a **Bianchi**, **Neri**, **Texture fine** e **Rimozione foschia**. Per esempio, aumenta la texture su una roccia o riducila su una zona di pelle. Regola la foschia del cielo e i punti di bianco e di nero senza intervenire sul resto della foto. I controlli seguono la sfumatura della maschera e si applicano anche all’esportazione.

- In **Locale**, crea la maschera principale e le selezioni da usare come componenti. Sulla maschera principale aggiungile una alla volta, scegli **Aggiungi**, **Sottrai** o **Interseca**, e usa **Anticipa** per cambiare l’ordine. Tocca il nome di un componente per modificarne la forma o l’intervallo di colore o luminanza. Il risultato usa la selezione di base di ciascun componente. Se vuoi applicare le correzioni solo alla maschera principale, disattiva quelle dei singoli componenti dal loro menu.
- Apri **⋮** accanto alla maschera selezionata e scegli **Duplica maschera**, **Duplica e inverti** o **Inverti tutta la selezione**. L’inversione completa include l’intervallo colore o luminanza e il risultato di Aggiungi, Sottrai o Interseca. **Inverti base** agisce sulla forma o sulla selezione automatica prima di questi passaggi. Una copia mantiene le regolazioni del livello. Il ritaglio dello sfondo rimane associato all’originale.
- Usa **Disattiva maschera** per sospenderne le regolazioni e l’eventuale ritaglio dello sfondo. Puoi continuare a visualizzarne la sovrapposizione e usarla come riferimento in una combinazione. **Attiva maschera** ripristina l’effetto.
- **Seleziona cielo** usa colore, luminosità e posizione nell’inquadratura. Controlla la sovrapposizione della maschera. Per rifinirla, puoi sottrarre o aggiungere una maschera a pennello. Verifica soprattutto tramonti, riflessi e tetti chiari.
- In **⋮ → Prova colore**, seleziona sRGB, Display P3 o Adobe RGB. L’avviso magenta marca i pixel fuori gamma per il profilo scelto. La simulazione riguarda la foto nell’anteprima. Nell’esportazione scegli il profilo desiderato nella finestra **Esporta**.
- In **Combina → Fusione esposizioni**, seleziona da due a cinque foto della stessa scena, con inquadrature molto simili e scattate con esposizioni differenti. Il risultato è un PNG SDR fino a 2400 px sul lato lungo. L’allineamento corregge piccole traslazioni. Nelle zone che differiscono dallo scatto di riferimento il contributo degli altri scatti viene ridotto. Un treppiede e una scena ferma aiutano a evitare sdoppiamenti.


## Strumenti avanzati

- In **Effetti → Immagini**, usa **Porta avanti**, **Porta indietro**, **Duplica livello** e **Visibile**. La maschera ovale ritaglia il livello attorno al centro. Da Android 10, l’esportazione applica **Moltiplica**, **Scolora** o **Luce soffusa**. L’anteprima mostra il livello con fusione normale.
- In **⋮ → Cronologia modifiche**, seleziona un passaggio numerato della sessione. **Salva come nuova versione** crea un punto di ripristino permanente. Puoi rinominarlo dalla finestra **Versioni**.
- In **Combina → Fusione dei piani di fuoco**, usa 2–5 scatti già allineati, con la stessa inquadratura e messe a fuoco differenti. La fusione pesa i dettagli locali ed esporta un PNG fino a 2400 px sul lato lungo.
- **⋮ → Evidenzia zone a fuoco** mostra in giallo i bordi con gradienti forti. Ingrandisci la foto per valutare i dettagli. L’indicatore è visibile solo nell’anteprima.
- Nella schermata iniziale, filtra le foto con stelle o bandierine e tocca **Provino della selezione**. Esporta un PNG con le miniature degli originali e i nomi dei file, fino a 12 foto distinte.


## Regolazioni RAW, confronto e strumenti di serie

- In **Dettagli → Sviluppo RAW**, scegli **Come scattato**, **Automatico** o **Guadagni manuali**. In manuale i guadagni del rosso e del blu sono relativi al verde. Tocca **Applica bilanciamento RAW** e attendi il ricalcolo dell’anteprima. La ricetta conserva la scelta per l’esportazione.
- In **Esporta → Profili di esportazione**, assegna un nome alle impostazioni correnti e scegli **Salva o sostituisci**. Richiama il profilo dallo stesso menu, anche nell’esportazione in serie. Verifica le opzioni EXIF e GPS prima di esportare.
- Dopo **Copia regolazioni**, usa **⋮ → Incolla regolazioni selettive**. Scegli i pannelli da trasferire. Curve, HSL e LUT si selezionano separatamente.
- Nella griglia iniziale usa **Confronta due scatti** e seleziona due immagini. Zoom e spostamento sono sincronizzati sulle anteprime degli originali.
- In **Combina → Sovrapposizione mediana**, seleziona 2–7 scatti già allineati della stessa scena. Un elemento in movimento deve lasciare scoperta la stessa zona nella maggior parte delle foto per essere rimosso. Il risultato è un PNG fino a 2000 px sul lato lungo.
- In **Ritaglio → Suggerisci guide verticali**, Photograin cerca un bordo dominante per lato e propone le guide. Rifiniscile prima di premere **Applica**. In assenza di bordi adatti puoi usare **Guide verticali** manualmente.
- Nel pannello **Luce**, scegli **Waveform RGB** per vedere, per ciascuna colonna, la distribuzione dei valori dei canali dall’ombra in basso alla luce in alto. Scorri le opzioni del pannello e scegli **RGB Parade** per vedere i tre canali affiancati o **Vettorscopio** per leggere la distribuzione cromatica. Nel vettorscopio i grigi si accumulano al centro e i colori più saturi si allontanano verso i riferimenti colorati.


## Esportazioni e ritocco avanzati

- In **Serie → Esporta con più profili**, seleziona i profili salvati da applicare alla stessa serie. Le copie usano la destinazione di esportazione corrente o la cartella scelta. Il formato e le impostazioni si applicano per profilo.
- In **Esporta**, **Nitidezza finale** aggiunge contrasto ai dettagli alla risoluzione di rendering. Usa un’intensità moderata per evitare aloni.
- In **Esporta**, imposta **Risoluzione di stampa (PPI)** e, se necessario, **Lato lungo di stampa (cm)**. Photograin calcola il lato lungo in pixel mantenendo le proporzioni. Il lato lungo è un limite di riduzione. Per un originale più piccolo, scegli l’ingrandimento separatamente. I metadati di risoluzione vengono scritti nei formati e nei lettori che supportano i relativi campi.
- In **Serie → Esporta**, inserisci un prefisso e un numero iniziale. Per esempio, con prefisso `torneo` e numero 7, il primo JPEG sarà `torneo-007.jpg`. Gli originali restano al loro posto.
- Dopo **Copia regolazioni**, in **Serie** abilita **Copia maschere soggetto e cielo**. Le selezioni automatiche vengono ricreate per ciascuna foto. Le pennellate del soggetto e i riferimenti ad altre maschere vengono esclusi. Si applica il limite di maschere già previsto dall’editor.
- In **Effetti → Mappa gradiente**, scegli i colori di ombre, mezzitoni e luci, sposta il punto medio e regola l’intensità. La mappa si applica globalmente.
- In **Home**, attiva la vista confronto e tocca **Differenza modifiche** su una foto elaborata. Le tre viste condividono lo zoom. Il riferimento usa la stessa geometria e lo stesso sviluppo RAW della versione per mostrare le differenze di ritocco e colore.
- In **Effetti → Immagini**, **Centra livello** centra l’immagine e **Aggancia ai terzi** sposta il suo centro al punto più vicino della griglia. In **Testo** e **Forme**, puoi centrare l’elemento selezionato.

## Negativi, riferimenti colore e trasparenza

Apri **⋮ → Strumenti con campionamento colore**, scegli uno strumento e tocca il riferimento nella foto.

- **Base non esposta del negativo**. Tocca un tratto pulito del bordo della pellicola, poi regola esposizione e neri nello stesso pannello. Il modello usa la densità relativa alla base. Ritaglia i bordi del negativo dopo il campionamento.
- **Riferimento grigio neutro**. Tocca una tacca grigia media di una carta fotografata sotto la stessa luce. La correzione neutralizza quella tacca e la porta verso il grigio medio. La calibrazione a più tacche e la creazione di profili fotocamera restano da sviluppare.
- **Colore da rendere trasparente**. Tocca il colore e regola **Tolleranza colore**. Esporta in PNG o WebP per conservare la trasparenza. La stessa tonalità può essere rimossa anche da altre parti della foto.

## Pixel caldi, GPX e fusione Schiarisci

- In **Dettagli → Sviluppo RAW**, attiva **Correggi pixel caldi (RAW Bayer)** e premi il pulsante di applicazione dello sviluppo. La correzione agisce prima della demosaicizzazione sui campioni luminosi isolati dei sensori Bayer.
- In **⋮ → Posizione da GPX**, inserisci il fuso dell’orologio della fotocamera e scegli la traccia. Photograin usa il punto più vicino entro due minuti dall’ora EXIF. La posizione viene scritta nella copia esportata quando abiliti i metadati di posizione.
- In **Combina → Fusione Schiarisci**, scegli da due a sette scatti già allineati. Il risultato conserva il valore più luminoso per canale e viene esportato come PNG. Usa un treppiede per scie stellari o light painting.

## JPEG Ultra HDR

Da Android 14, scegli JPEG e attiva **JPEG Ultra HDR · espandi alte luci**. Photograin calcola una gain map a partire dalle alte luci dell’immagine SDR elaborata. I lettori compatibili possono usare la mappa per aumentare la luminosità. La funzione espande la resa del file SDR e non recupera dettaglio già bruciato. L’esportazione controlla che il JPEG contenga la mappa prima di salvare il file. **Guadagno HDR massimo** imposta il limite della spinta luminosa da +0,25 a +3 EV. **Soglia delle alte luci** imposta da quale luminosità dell’immagine SDR inizia la spinta. Puoi salvare entrambi in un profilo di esportazione.


## Color grading per fasce tonali

Nel pannello **Colore → Color grading**, scegli **Ombre**, **Mezzitoni**, **Luci** o **Globale**. Regola tonalità, saturazione e luminanza. **Bilanciamento** sposta le fasce tonali e **Fusione** allarga o restringe la transizione fra esse. Ogni cursore ha un valore neutro ripristinabile e **Azzera viraggio** ripristina il pannello completo.

Le impostazioni sono nella ricetta e nei preset Photograin. **Incolla regolazioni selettive → Colore** trasferisce anche il color grading. La stessa elaborazione viene usata per anteprima ed esportazione.

## Vignettatura, grana e visualizzazione delle maschere

In **Effetti**, imposta **Vignettatura** per scurire o schiarire i bordi. Il **Punto medio** sposta la transizione verso il centro o gli angoli. **Rotondità** passa da una forma adattata alle proporzioni della foto a una circolare. **Sfumatura** regola l’ampiezza della transizione e **Protezione luci** riduce la vignettatura scura sui punti luminosi. Per la grana, **Irregolarità** regola il contributo dei dettagli più fini.

In **Locale**, seleziona una maschera e tocca **Rinomina maschera** per distinguerla dalle altre. Lascia il campo vuoto per ritornare al nome automatico. Attiva l’icona dell’occhio per scegliere colore e opacità della sovrapposizione. La sovrapposizione è visibile nell’editor.

Con l’icona dell’occhio attiva, scorri le modalità per visualizzare la foto su fondo nero, bianco o desaturato, la sovrapposizione a colori su bianco e nero oppure **Solo maschera**. In **Solo maschera**, il bianco indica l’effetto pieno, il nero le zone escluse e il grigio i bordi sfumati. Le modalità cambiano l’anteprima nel pannello **Locale**. I file esportati mostrano la foto elaborata.

## Regolare un colore direttamente sulla foto

Apri **Colore → HSL → Regola sulla foto**. Scegli **Tonalità**, **Saturazione** o **Luminanza**. Tocca un colore per selezionarne la banda oppure parti da quel punto e trascina orizzontalmente per modificare il parametro. Due dita spostano o ingrandiscono l’anteprima. Se il punto è quasi neutro, scegli una zona più colorata.

La regolazione interessa tutti i pixel della banda. Per limitarla a una parte della foto, usa gli strumenti in **Locale**. Un trascinamento completato corrisponde a un passaggio della cronologia.

## Scorciatoie con tastiera fisica

| Tasti | Azione nell’editor |
|---|---|
| `Ctrl+Z` | Annulla |
| `Ctrl+Maiusc+Z` | Ripeti |
| `Ctrl+C` / `Ctrl+V` | Copia o incolla le regolazioni |
| `Ctrl+S` | Apri le opzioni di esportazione |
| `Ctrl+0` | Apri Luce e attiva o disattiva l’istogramma RGB |
| `\` | Attiva o disattiva il confronto prima/dopo con divisore |
| `F` | Entra o esci dalla vista a schermo intero |
| `I` | Apri Informazioni foto |
| `Ctrl+Y` | Applica tutte le regolazioni globali della foto precedente |
| `Ctrl+Maiusc+Y` | Scegli le regolazioni da applicare dalla foto precedente |
| `1`–`5` | Assegna stelle |
| `0` | Rimuovi le stelle |
| `P` / `X` / `U` | Segna come selezionata, segna come scartata o togli la bandierina |
