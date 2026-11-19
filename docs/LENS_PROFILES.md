# Formato del catalogo dei profili ottici

Il catalogo contiene calibrazioni ottenute regolando **Distorsione obiettivo** in Photograin su scatti con linee rette. Usa i dati EXIF di ciascuno scatto per identificare il profilo. Le stringhe sono confrontate ignorando maiuscole e spazi iniziali e finali. La focale effettiva è confrontata al centesimo di millimetro.

Questo esempio illustra il formato con un modello e un valore fittizi.

```json
{
  "version": 1,
  "profiles": [
    {
      "cameraMake": "Marca di esempio",
      "cameraModel": "Fotocamera di esempio",
      "lensModel": "Obiettivo di esempio",
      "focalMm": 24.0,
      "focal35Mm": 24,
      "distortion": -0.12
    }
  ]
}
```

- `cameraModel` e `focalMm` sono obbligatori. `focalMm` è la focale effettiva.
- `cameraMake` e `lensModel` si possono omettere quando i rispettivi tag EXIF sono assenti.
- `focal35Mm` è la focale equivalente riportata in EXIF. Usa 0 o ometti il campo quando il tag è assente.
- `distortion` va da −1 a 1. Per convertire il valore mostrato dal cursore, dividilo per 100.
- Il file può contenere da 1 a 2000 profili e pesare al massimo 1 MiB. Ogni combinazione di fotocamera, obiettivo e focale deve comparire una sola volta.

Il catalogo usa i valori del cursore Photograin. I coefficienti di modelli ottici esterni richiedono una conversione e una verifica sulle foto.

## Convertire misure o calibrazioni esterne

Lo script `tools/lens-profiles/fit.py` ricava il valore del cursore da una mappa di correzione campionata. Fornisci un JSON con un array `profiles`. Per ciascun profilo usa gli stessi campi EXIF del catalogo, aggiungi `width`, `height` e almeno 12 elementi in `samples`. Ogni elemento è `[x, y, sx, sy]`, dove i primi due valori indicano un punto della foto corretta e gli altri due il punto corrispondente nella foto originale. Tutte le coordinate sono comprese fra 0 e 1, con origine in alto a sinistra. Usa un ritaglio centrato e campiona più direzioni dal centro fino agli angoli.

```sh
python3 tools/lens-profiles/fit.py misure.json catalogo.json
```

Lo script rifiuta i profili che superano un errore massimo dello 0,2% del lato lungo sui punti forniti. Verifica il risultato su scatti distinti da quelli usati per la misura. Per una calibrazione Lensfun, genera le coordinate con il relativo motore e con il sensore e la focale corrispondenti. Conserva l’attribuzione e la licenza della fonte quando distribuisci dati derivati.

### Campionare Lensfun

Installa `lensfunpy` sul computer che prepara il catalogo. Crea un JSON con l’array `profiles`. Per ogni elemento indica i campi EXIF attesi da Photograin, la larghezza e l’altezza in pixel del fotogramma, e due coppie `[marca, modello]` con i nomi esatti nel database Lensfun.

```json
{
  "profiles": [
    {
      "cameraMake": "Marca EXIF",
      "cameraModel": "Modello EXIF",
      "lensModel": "Obiettivo EXIF",
      "focalMm": 24,
      "focal35Mm": 24,
      "width": 6000,
      "height": 4000,
      "lensfunCamera": ["Marca nel database", "Fotocamera nel database"],
      "lensfunLens": ["Marca nel database", "Obiettivo nel database"]
    }
  ]
}
```

```sh
python3 tools/lens-profiles/sample_lensfun.py associazioni.json misure.json
python3 tools/lens-profiles/fit.py misure.json catalogo.json
```

Per rendere la conversione riproducibile, passa i file XML di una versione precisa del database con `--database file.xml`, ripetendo l’opzione per i file necessari. Conserva versione e attribuzione accanto al catalogo. Il campionatore verifica che ogni associazione sia univoca. Il fit verifica quanto bene il modello di Photograin riproduce la correzione prima di produrre il JSON importabile.
