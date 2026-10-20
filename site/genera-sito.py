#!/usr/bin/env python3
"""Genera il sito di Photograin (italiano in website/, inglese in website/en/) da un unico modello.
Per cambiare un testo modifica il dizionario della lingua e rilancia:  python3 genera-sito.py
Le immagini vengono copiate da img/ in website/img/.
Netlify lo esegue da solo a ogni push su GitHub che tocca la cartella site/ (vedi netlify.toml).
Il giorno in cui Photograin è su F-Droid basta mettere FDROID = True."""
import pathlib
import shutil

BASE = pathlib.Path(__file__).resolve().parent
OUT = BASE / "website"
SITE = "https://photograin.netlify.app"
REPO = "https://github.com/steve-linux/photograin"
RELEASE = REPO + "/releases/tag/v0.1.0"
VERSION = "0.1.0"
PKG = "io.github.steve_linux.photograin"
NB = "F&#8209;Droid"  # trattino che non va a capo
FDROID = False  # True quando l'app è pubblicata su F-Droid

IT = {
    "lang": "it", "og_locale": "it_IT", "og_alt": "en_GB", "root": "", "shots": "img/it/", "img": "img/",
    "title": "Photograin – Editor fotografico libero per Android, non distruttivo, con RAW e anteprima in tempo reale",
    "desc": "Photograin è un editor fotografico libero e gratuito per Android: regolazioni in tempo reale sulla GPU, file RAW, maschere, ritocco, prospettiva ed esportazione in JPEG XL, AVIF e TIFF a 16 bit. Nessun permesso, nessuna pubblicità.",
    "og_title": "Photograin – Editor fotografico libero per Android",
    "og_desc": "Regolazioni in tempo reale sulla GPU, RAW, maschere, ritocco ed esportazione a piena qualità. Software libero GPL-3.0, senza permessi né pubblicità.",
    "og_img": "og-it.jpg",
    "menu_open": "Apri il menu", "menu_close": "Chiudi il menu", "lang_label": "Lingua", "close": "Chiudi", "details": "Dettagli",
    "nav_features": "Funzionalità", "nav_tools": "Strumenti", "nav_export": "Esportazione", "nav_gallery": "Galleria", "nav_download": "Scarica",
    "badge": "Libero e gratuito · licenza GPL-3.0",
    "h1": "Le tue foto, <em>sviluppate come si deve.</em>",
    "lead": "Photograin è un editor fotografico per Android pensato per chi fotografa: ogni regolazione si vede subito grazie alla GPU, i file RAW si sviluppano direttamente sul telefono e l'originale non viene mai toccato.",
    "cta_get": "Scarica l'app", "cta_more": "Scopri le funzioni",
    "fact1": "Nessun permesso", "fact2": "Funziona senza internet", "fact3": "Niente pubblicità né tracciamento",
    "hero_alt": "Editor di Photograin con una foto di un lago e i cursori di esposizione, contrasto e luci",
    "feat_eyebrow": "Funzionalità", "feat_h2": "Uno studio fotografico in tasca",
    "feat_p": "Gli strumenti di un programma da computer, con comandi pensati per le dita.",
    "c1_h": "Non distruttivo", "c1_p": "Le modifiche sono una «ricetta» salvata a parte: riapri la foto quando vuoi e continui da dove eri, con annulla e ripeti illimitati.",
    "c2_h": "Anteprima in tempo reale", "c2_p": "Luce, curve, colore, HSL, nitidezza, chiarezza, foschia, riduzione del rumore, vignettatura e grana: tutto calcolato sulla GPU mentre muovi il cursore.",
    "c3_h": "RAW e colori ampi", "c3_p": "DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2 e altri, sviluppati con LibRaw a 16 bit per canale. Le foto Display P3 e Adobe RGB conservano i colori più saturi.",
    "c4_h": "Stili e LUT", "c4_p": "Preset pronti con intensità regolabile, i tuoi preset personali e l'importazione di LUT .cube da altri programmi.",
    "c5_h": "Maschere locali", "c5_p": "Sfumature lineari e circolari, pennello e foto intera, limitabili per luminosità o colore: scurisci solo il cielo o fai risaltare il soggetto.",
    "c6_h": "Modifica in serie", "c6_p": "Scegli più foto, applica a tutte un preset o le regolazioni copiate ed esportale in una cartella in un colpo solo.",
    "d1_title": "Regolazioni",
    "d1_intro": "La pipeline gira tutta sulla GPU (OpenGL ES 3.0): anteprima ed esportazione usano lo stesso codice, così il file salvato è identico a quello che vedi.",
    "d1_list": [
        ("Luce:", "esposizione, contrasto, luci, ombre, bianchi e neri, più le curve dei toni per un controllo fine."),
        ("Colore:", "temperatura, tinta, vividezza, saturazione; HSL per cambiare un solo colore, e il contagocce per il bilanciamento del bianco."),
        ("Dettagli:", "nitidezza, chiarezza, rimozione della foschia, riduzione del rumore di luminanza e di colore."),
        ("Effetti:", "vignettatura, grana da pellicola realistica, cornici (bordo o polaroid), testo, forme e filigrana."),
        ("Confronto:", "tieni premuto per vedere l'originale, o dividi la foto con una linea tra prima e dopo."),
    ],
    "d1_note": "Tocca due volte il nome di un cursore per riportarlo al valore iniziale.",
    "d3_title": "RAW e gestione del colore",
    "d3_intro": "I file RAW vengono sviluppati sul telefono con LibRaw: anteprima rapida a metà risoluzione, esportazione a piena risoluzione con demosaicizzazione AHD.",
    "d3_list": [
        ("16 bit fino alla GPU:", "ombre e luci si recuperano senza scalini né bande."),
        ("Profili ICC:", "le foto Display P3 o Adobe RGB e i RAW si modificano in Display P3 senza perdere i colori più saturi."),
        ("Esportazione:", "in sRGB, Display P3 o Adobe RGB, con il profilo incorporato nel file."),
    ],
    "d3_note": "<strong>Formati RAW:</strong> DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2 e gli altri supportati da LibRaw 0.22.",
    "d5_title": "Maschere locali",
    "d5_intro": "In Ritocco, con Maschere regoli solo una parte della foto, con tutti i cursori di luce, colore e dettaglio.",
    "d5_list": [
        ("Lineare:", "scurisce un cielo troppo chiaro o schiarisce il primo piano."),
        ("Circolare:", "fa risaltare un soggetto o crea una vignettatura su misura."),
        ("Pennello:", "dipingi la zona con un dito; Cancella corregge le sbavature, con due dita ingrandisci."),
        ("Intervallo:", "limita la maschera alle parti chiare, scure o a un colore scelto toccando la foto."),
    ],
    "d5_note": "Mostra maschera evidenzia dove agisce la regolazione; Inverti la applica a tutto il resto.",
    "crop_eyebrow": "Ritaglio e prospettiva", "crop_h2": "Linee dritte, anche quando non lo erano",
    "crop_p": "Formati predefiniti, rotazione e specchio, raddrizzamento a mano o automatico dalle linee della foto. Con la prospettiva raddrizzi palazzi che pendono e squadri un quadro, un documento o un'insegna fotografati di sbieco.",
    "crop_c1": "Raddrizza automaticamente", "crop_c1s": "Livella l'orizzonte leggendo le linee della foto.",
    "crop_c2": "Prospettiva a 4 punti", "crop_c2s": "Segna gli angoli di qualcosa di rettangolare: viene squadrato.",
    "crop_c3": "Proporzioni pronte", "crop_c3s": "1:1, 4:3, 3:2, 16:9 e libero, con ±45° di raddrizzamento.",
    "crop_alt": "Prospettiva a 4 punti: quattro maniglie sugli angoli di una fila di negozi fotografata di sbieco",
    "ret_eyebrow": "Ritocco", "ret_h2": "Via macchie, polvere e piccoli intrusi",
    "ret_p": "Tocca quello che vuoi togliere: Photograin lo copre con una zona vicina simile. Il cerchio tratteggiato mostra da dove prende il campione, e puoi spostarlo.",
    "ret_c1": "Correttivo", "ret_c1s": "Fonde la zona copiata con quella intorno.",
    "ret_c2": "Clona", "ret_c2s": "Una copia esatta, quando serve precisione.",
    "ret_c3": "Occhi rossi", "ret_c3s": "Un tocco per pupilla: il cerchio si adatta al rosso da solo.",
    "ret_alt": "Correttivo su una ninfea: un cerchio sulla macchia e un cerchio tratteggiato sulla zona da cui viene copiata",
    "cmp_h3": "Prima e dopo, a colpo d'occhio",
    "cmp_p": "Trascina la linea per confrontare l'originale con la versione modificata, oppure tieni premuto sulla foto.",
    "cmp_alt": "Vista divisa su un mercato: a sinistra la foto originale, a destra la stessa foto con la LUT Teal Orange",
    "exp_eyebrow": "Esportazione", "exp_h2": "Piena qualità, nei formati di oggi",
    "exp_p": "Le foto vengono elaborate a tasselli alla massima risoluzione, anche oltre il limite della GPU. Scegli formato, qualità, dimensione e profilo colore, e decidi quali dati di scatto tenere: la posizione è esclusa per impostazione predefinita.",
    "exp_c1": "Ingrandimento 2× o 4×", "exp_c1s": "Bicubico, oppure con l'IA (Real-ESRGAN) per recuperare i dettagli: il modello è nell'app, niente cloud.",
    "exp_c2": "Dati EXIF a scelta", "exp_c2s": "Data, fotocamera, impostazioni, autore, posizione.",
    "exp_c3": "Dove vuoi tu", "exp_c3s": "In Immagini/Photograin, in una cartella scelta, o chiedendo ogni volta.",
    "exp_alt": "Finestra di esportazione con i formati JPEG, PNG, WebP, AVIF, JPEG XL e TIFF, qualità e profilo colore",
    "gal_eyebrow": "Galleria", "gal_h2": "L'app in azione",
    "gal_p": "Material You con tema chiaro, scuro o di sistema. Tocca un'immagine per ingrandirla.",
    "g_home": "Foto recenti", "g_home_alt": "Schermata iniziale con le foto recenti e i pulsanti Apri e In serie",
    "g_luce": "Luce", "g_luce_alt": "Pannello Luce con i cursori di esposizione, contrasto e luci",
    "g_confronto": "Confronto", "g_confronto_alt": "Vista divisa prima/dopo con uno stile LUT",
    "g_prospettiva": "Prospettiva", "g_prospettiva_alt": "Prospettiva a 4 punti su una fila di negozi",
    "g_ritocco": "Ritocco", "g_ritocco_alt": "Correttivo su una ninfea",
    "g_esporta": "Esportazione", "g_esporta_alt": "Finestra di esportazione con formati e qualità",
    "dl_eyebrow": "Scarica", "dl_h2": "Libero, gratuito, senza sorprese",
    "dl_p": "Nessun account, nessuna pubblicità, nessun dato inviato a server esterni.",
    "dl_status": f"In arrivo su {NB}",
    "dl_h3": "Scarica l'APK da GitHub",
    "dl_text": f"Photograin arriverà su {NB} a gennaio 2027: lo store accoglie le nuove app quando il progetto ha almeno tre mesi. Nel frattempo installa l'APK della versione {VERSION} dalla pagina delle release di GitHub.",
    "dl_btn": "Scarica l'APK",
    "dl_hint": "Android ti chiederà di consentire l'installazione da questa fonte.",
    "fd_lang": "it", "fd_h3": f"Disponibile su {NB}",
    "fd_text": f"Installa Photograin da {NB}, lo store di app libere per Android: gli aggiornamenti arrivano da lì, in automatico.",
    "fd_btn": f"Scarica da {NB}",
    "fd_hint": f'Preferisci l\'APK? Lo trovi nelle <a href="{REPO}/releases" rel="noopener">release di GitHub</a>.',
    "req_h3": "Requisiti e permessi",
    "req_sys": "Sistema", "req_sys_v": "Android 8.0 o successivo",
    "req_ver": "Versione", "req_lic": "Licenza", "req_lic_v": "GNU GPL v3 o successiva",
    "chk1": "Nessun permesso", "chk1s": "Le foto si aprono e si salvano con le finestre di sistema.",
    "chk2": "Nessun accesso a internet", "chk2s": "Anche l'IA per l'ingrandimento gira sul telefono.",
    "chk3": "Nessun servizio Google", "chk3s": "Solo componenti liberi: LibRaw, libjxl, libavif, libaom, ncnn.",
    "src": "Codice sorgente su GitHub",
    "foot_lic": "Distribuito con licenza", "foot_src": "Codice sorgente", "foot_issue": "Segnala un problema",
    "lb_label": "Immagine ingrandita",
}

EN = {
    "lang": "en", "og_locale": "en_GB", "og_alt": "it_IT", "root": "../", "shots": "../img/en/", "img": "../img/",
    "title": "Photograin – Free, non-destructive photo editor for Android with RAW and real-time preview",
    "desc": "Photograin is a free and open source photo editor for Android: real-time GPU adjustments, RAW files, masks, retouching, perspective and export to JPEG XL, AVIF and 16-bit TIFF. No permissions, no ads.",
    "og_title": "Photograin – Free photo editor for Android",
    "og_desc": "Real-time GPU adjustments, RAW, masks, retouching and full-quality export. Free software under GPL-3.0, with no permissions and no ads.",
    "og_img": "og-en.jpg",
    "menu_open": "Open menu", "menu_close": "Close menu", "lang_label": "Language", "close": "Close", "details": "Details",
    "nav_features": "Features", "nav_tools": "Tools", "nav_export": "Export", "nav_gallery": "Gallery", "nav_download": "Download",
    "badge": "Free and open source · GPL-3.0 license",
    "h1": "Your photos, <em>developed properly.</em>",
    "lead": "Photograin is a photo editor for Android built for photographers: every adjustment shows up instantly thanks to the GPU, RAW files are developed right on your phone, and your original is never touched.",
    "cta_get": "Get the app", "cta_more": "See the features",
    "fact1": "No permissions", "fact2": "Works offline", "fact3": "No ads, no tracking",
    "hero_alt": "Photograin editor with a lake photo and the exposure, contrast and highlights sliders",
    "feat_eyebrow": "Features", "feat_h2": "A photo studio in your pocket",
    "feat_p": "The tools of a desktop editor, with controls made for fingers.",
    "c1_h": "Non-destructive", "c1_p": "Edits are a recipe saved separately: reopen a photo any time and pick up where you left off, with unlimited undo and redo.",
    "c2_h": "Real-time preview", "c2_p": "Light, curves, color, HSL, sharpening, clarity, dehaze, noise reduction, vignette and grain: all computed on the GPU as you move the slider.",
    "c3_h": "RAW and wide color", "c3_p": "DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2 and more, developed with LibRaw at 16 bits per channel. Display P3 and Adobe RGB photos keep their most saturated colors.",
    "c4_h": "Styles and LUTs", "c4_p": "Ready-made presets with adjustable strength, your own presets, and .cube LUTs imported from other apps.",
    "c5_h": "Local masks", "c5_p": "Linear and radial gradients, brush and whole photo, limited by brightness or color: darken just the sky or make your subject stand out.",
    "c6_h": "Batch editing", "c6_p": "Pick several photos, apply a preset or copied adjustments to all of them, and export them to a folder in one go.",
    "d1_title": "Adjustments",
    "d1_intro": "The whole pipeline runs on the GPU (OpenGL ES 3.0): preview and export share the same code, so the saved file matches what you see.",
    "d1_list": [
        ("Light:", "exposure, contrast, highlights, shadows, whites and blacks, plus tone curves for fine control."),
        ("Color:", "temperature, tint, vibrance, saturation; HSL to change a single color, and an eyedropper for white balance."),
        ("Detail:", "sharpening, clarity, dehaze, luminance and color noise reduction."),
        ("Effects:", "vignette, realistic film grain, frames (border or polaroid), text, shapes and watermark."),
        ("Compare:", "press and hold to see the original, or split the photo with a before/after line."),
    ],
    "d1_note": "Double-tap a slider's name to reset it.",
    "d3_title": "RAW and color management",
    "d3_intro": "RAW files are developed on the phone with LibRaw: a fast half-resolution preview, and full-resolution export with AHD demosaicing.",
    "d3_list": [
        ("16 bits all the way to the GPU:", "recover shadows and highlights without banding."),
        ("ICC profiles:", "Display P3 or Adobe RGB photos and RAW files are edited in Display P3 without losing their most saturated colors."),
        ("Export:", "in sRGB, Display P3 or Adobe RGB, with the profile embedded in the file."),
    ],
    "d3_note": "<strong>RAW formats:</strong> DNG, CR2, CR3, NEF, ARW, RAF, ORF, RW2 and the others supported by LibRaw 0.22.",
    "d5_title": "Local masks",
    "d5_intro": "In Retouch, Masks let you adjust just part of the photo, with every light, color and detail slider.",
    "d5_list": [
        ("Linear:", "darken a sky that is too bright or lift the foreground."),
        ("Radial:", "make a subject stand out or build a custom vignette."),
        ("Brush:", "paint the area with a finger; Erase fixes overspill, pinch to zoom."),
        ("Range:", "limit the mask to bright or dark areas, or to a color picked by tapping the photo."),
    ],
    "d5_note": "Show mask highlights where the adjustment applies; Invert applies it to everything else.",
    "crop_eyebrow": "Crop and perspective", "crop_h2": "Straight lines, even when they weren't",
    "crop_p": "Aspect ratio presets, rotate and flip, straighten by hand or automatically from the lines in the photo. Perspective correction fixes leaning buildings and squares up a painting, a document or a sign shot at an angle.",
    "crop_c1": "Auto straighten", "crop_c1s": "Levels the horizon by reading the lines in the photo.",
    "crop_c2": "4-point perspective", "crop_c2s": "Mark the corners of something rectangular and it gets squared.",
    "crop_c3": "Ratio presets", "crop_c3s": "1:1, 4:3, 3:2, 16:9 and free, with ±45° straightening.",
    "crop_alt": "4-point perspective: four handles on the corners of a row of shops shot at an angle",
    "ret_eyebrow": "Retouch", "ret_h2": "Remove spots, dust and small intruders",
    "ret_p": "Tap what you want gone: Photograin covers it with a similar nearby area. The dashed circle shows where the sample comes from, and you can move it.",
    "ret_c1": "Heal", "ret_c1s": "Blends the copied area into its surroundings.",
    "ret_c2": "Clone", "ret_c2s": "An exact copy, when you need precision.",
    "ret_c3": "Red eye", "ret_c3s": "One tap per pupil: the circle fits the red by itself.",
    "ret_alt": "Heal on a water lily: a circle on the spot and a dashed circle on the area it copies from",
    "cmp_h3": "Before and after, at a glance",
    "cmp_p": "Drag the line to compare the original with the edited version, or press and hold the photo.",
    "cmp_alt": "Split view of a market: the original photo on the left, the same photo with the Teal Orange LUT on the right",
    "exp_eyebrow": "Export", "exp_h2": "Full quality, in today's formats",
    "exp_p": "Photos are rendered tile by tile at full resolution, even beyond the GPU's limit. Choose format, quality, size and color profile, and decide which shooting data to keep: location is removed by default.",
    "exp_c1": "2× or 4× upscaling", "exp_c1s": "Bicubic, or with AI (Real-ESRGAN) to recover detail: the model ships with the app, no cloud.",
    "exp_c2": "Your choice of EXIF", "exp_c2s": "Date, camera, settings, author, location.",
    "exp_c3": "Wherever you want", "exp_c3s": "In Pictures/Photograin, in a folder you pick, or ask every time.",
    "exp_alt": "Export dialog with JPEG, PNG, WebP, AVIF, JPEG XL and TIFF formats, quality and color profile",
    "gal_eyebrow": "Gallery", "gal_h2": "The app in action",
    "gal_p": "Material You with light, dark or system theme. Tap an image to enlarge it.",
    "g_home": "Recent photos", "g_home_alt": "Home screen with recent photos and the Open and Batch buttons",
    "g_luce": "Light", "g_luce_alt": "Light panel with exposure, contrast and highlights sliders",
    "g_confronto": "Compare", "g_confronto_alt": "Before/after split view with a LUT style",
    "g_prospettiva": "Perspective", "g_prospettiva_alt": "4-point perspective on a row of shops",
    "g_ritocco": "Retouch", "g_ritocco_alt": "Heal on a water lily",
    "g_esporta": "Export", "g_esporta_alt": "Export dialog with formats and quality",
    "dl_eyebrow": "Download", "dl_h2": "Free, open source, no surprises",
    "dl_p": "No account, no ads, no data sent to outside servers.",
    "dl_status": f"Coming to {NB}",
    "dl_h3": "Get the APK from GitHub",
    "dl_text": f"Photograin will arrive on {NB} in January 2027: the store takes new apps once the project is at least three months old. In the meantime, install the version {VERSION} APK from the GitHub releases page.",
    "dl_btn": "Download the APK",
    "dl_hint": "Android will ask you to allow installs from this source.",
    "fd_lang": "en", "fd_h3": f"Available on {NB}",
    "fd_text": f"Install Photograin from {NB}, the store of free and open source apps for Android: updates arrive from there automatically.",
    "fd_btn": f"Get it on {NB}",
    "fd_hint": f'Prefer the APK? It is on the <a href="{REPO}/releases" rel="noopener">GitHub releases</a> page.',
    "req_h3": "Requirements and permissions",
    "req_sys": "System", "req_sys_v": "Android 8.0 or later",
    "req_ver": "Version", "req_lic": "License", "req_lic_v": "GNU GPL v3 or later",
    "chk1": "No permissions", "chk1s": "Photos are opened and saved through the system dialogs.",
    "chk2": "No internet access", "chk2s": "Even the AI upscaler runs on the phone.",
    "chk3": "No Google services", "chk3s": "Only free components: LibRaw, libjxl, libavif, libaom, ncnn.",
    "src": "Source code on GitHub",
    "foot_lic": "Released under the", "foot_src": "Source code", "foot_issue": "Report a problem",
    "lb_label": "Enlarged image",
}

STYLE = r"""
:root{
  --bg:#0b0d14; --bg2:#10131d; --surface:#171b28; --surface2:#1f2435; --line:#2a3044;
  --text:#eceef5; --muted:#a2a9bd; --dim:#717a92;
  --acc:#ff9f43; --acc-soft:rgba(255,159,67,.12); --acc-glow:rgba(255,159,67,.35);
  --blue:#8ab4ff; --pink:#ff6fa8; --violet:#b794ff; --green:#4fd6a0;
  --grad:linear-gradient(100deg,#ffb347 0%,#ff6fa8 45%,#8ab4ff 100%);
  --radius:18px; --max:1160px;
  --font:system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
  --mono:ui-monospace,"SF Mono","JetBrains Mono",Menlo,Consolas,monospace;
}
*{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth;scroll-padding-top:76px}
@media (prefers-reduced-motion:reduce){html{scroll-behavior:auto}*{transition:none!important;animation:none!important}}
body{font-family:var(--font);background:var(--bg);color:var(--text);line-height:1.6;-webkit-font-smoothing:antialiased;overflow-x:hidden}
img{display:block;max-width:100%;height:auto}
a{color:inherit}
::selection{background:var(--acc);color:var(--bg)}
.wrap{max-width:var(--max);margin:0 auto;padding:0 20px}
.icon{width:1.25em;height:1.25em;flex:none;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
:focus-visible{outline:2px solid var(--acc);outline-offset:3px;border-radius:6px}

/* Navigazione */
.nav{position:sticky;top:0;z-index:50;background:rgba(11,13,20,.86);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);border-bottom:1px solid var(--line)}
.nav .wrap{display:flex;align-items:center;justify-content:space-between;height:64px;gap:16px}
.brand{display:flex;align-items:center;gap:10px;text-decoration:none;font-weight:800;letter-spacing:-.01em;font-size:1.1rem}
.brand img{width:36px;height:36px;border-radius:10px}
.nav-links{display:flex;align-items:center;gap:24px;list-style:none}
.nav-links a{text-decoration:none;color:var(--muted);font-weight:600;font-size:.95rem}
.nav-links a:hover{color:var(--acc)}
.nav-links .pill{color:var(--text);border:1px solid var(--line);background:var(--surface);padding:7px 16px;border-radius:999px;display:inline-flex;align-items:center;gap:8px}
.nav-links .pill:hover{border-color:var(--acc)}
.menu-btn{display:none;background:none;border:0;color:var(--text);font-size:1.5rem;cursor:pointer;padding:6px}
@media (max-width:1000px){
  .menu-btn{display:block}
  .nav-links{display:none;position:absolute;top:64px;left:0;right:0;flex-direction:column;align-items:stretch;gap:0;background:var(--bg);border-bottom:1px solid var(--line);padding:8px 20px 16px}
  .nav-links.open{display:flex}
  .nav-links a{display:block;padding:12px 0;font-size:1.05rem}
  .nav-links .pill{justify-content:center;margin-top:8px}
}

/* Pulsanti */
.btn{display:inline-flex;align-items:center;justify-content:center;gap:10px;padding:14px 26px;border-radius:999px;font-weight:800;font-size:1.05rem;text-decoration:none;border:2px solid transparent;cursor:pointer;transition:transform .2s,box-shadow .2s,background .2s,border-color .2s;font-family:inherit}
.btn-primary{background:var(--acc);color:#1a1006;box-shadow:0 0 24px var(--acc-glow)}
.btn-primary:hover{transform:translateY(-2px);box-shadow:0 0 34px var(--acc-glow)}
.btn-ghost{background:var(--surface);color:var(--text);border-color:var(--line)}
.btn-ghost:hover{border-color:var(--muted)}

/* Hero */
.hero{position:relative;padding:72px 0 88px;overflow:hidden;
  background:
   radial-gradient(760px 420px at 85% 25%,rgba(255,111,168,.10),transparent 60%),
   radial-gradient(700px 420px at 70% 80%,rgba(138,180,255,.10),transparent 60%),
   radial-gradient(600px 400px at 5% 90%,rgba(255,159,67,.08),transparent 60%)}
.hero-grid{display:grid;grid-template-columns:1.15fr .85fr;gap:56px;align-items:center}
.badge{display:inline-flex;align-items:center;gap:10px;padding:6px 14px;border-radius:999px;background:var(--acc-soft);border:1px solid rgba(255,159,67,.28);color:var(--acc);font-weight:700;font-size:.85rem;margin-bottom:22px}
.dot{width:9px;height:9px;border-radius:50%;background:var(--acc);animation:pulse 2s infinite}
@keyframes pulse{0%{box-shadow:0 0 0 0 rgba(255,159,67,.6)}70%{box-shadow:0 0 0 10px rgba(255,159,67,0)}100%{box-shadow:0 0 0 0 rgba(255,159,67,0)}}
h1{font-size:clamp(2.2rem,5.2vw,3.9rem);line-height:1.08;letter-spacing:-.03em;font-weight:900;margin-bottom:22px}
h1 em{font-style:normal;background:var(--grad);-webkit-background-clip:text;background-clip:text;color:transparent}
.lead{font-size:clamp(1.05rem,1.6vw,1.22rem);color:var(--muted);max-width:36em;margin-bottom:32px}
.cta{display:flex;flex-wrap:wrap;gap:14px;margin-bottom:28px}
.facts{display:flex;flex-wrap:wrap;gap:8px 22px;list-style:none;color:var(--muted);font-size:.92rem}
.facts li{display:flex;align-items:center;gap:7px}
.facts .icon{color:var(--acc);width:1.05em;height:1.05em}
.phone{position:relative;width:min(320px,78vw);margin:0 auto;border:9px solid #1f2430;border-radius:40px;overflow:hidden;background:var(--bg);box-shadow:0 30px 60px -20px rgba(0,0,0,.8),0 0 80px -10px rgba(255,111,168,.20);aspect-ratio:540/1200}
.phone img{width:100%;height:100%;object-fit:cover;object-position:top}
.phone.sm{width:min(290px,72vw)}
@media (max-width:900px){.hero{padding:44px 0 64px}.hero-grid{grid-template-columns:1fr;gap:44px;text-align:center}.lead{margin-left:auto;margin-right:auto}.cta,.facts{justify-content:center}}

/* Sezioni */
section{padding:88px 0;border-top:1px solid rgba(42,48,68,.6)}
.alt{background:var(--bg2)}
.head{text-align:center;max-width:44em;margin:0 auto 52px}
.eyebrow{color:var(--acc);font-weight:800;text-transform:uppercase;letter-spacing:.12em;font-size:.78rem;margin-bottom:10px}
h2{font-size:clamp(1.8rem,3.6vw,2.6rem);line-height:1.15;letter-spacing:-.02em;font-weight:900;margin-bottom:14px}
.head p{color:var(--muted);font-size:1.1rem}
h3{font-size:1.22rem;font-weight:800;letter-spacing:-.01em}

/* Funzioni */
.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}
@media (max-width:960px){.cards{grid-template-columns:repeat(2,1fr)}}
@media (max-width:600px){.cards{grid-template-columns:1fr}}
.card{background:var(--surface);border:1px solid var(--line);border-radius:var(--radius);padding:26px;display:flex;flex-direction:column;gap:12px;transition:border-color .25s,transform .25s}
.card:hover{border-color:color-mix(in srgb,var(--accent,var(--acc)) 55%,transparent);transform:translateY(-3px)}
.card .ico{width:50px;height:50px;border-radius:14px;display:grid;place-items:center;color:var(--accent,var(--acc));background:color-mix(in srgb,var(--accent,var(--acc)) 13%,transparent)}
.card .ico .icon{width:26px;height:26px}
.card p{color:var(--muted);font-size:.97rem;flex:1}
.more{align-self:flex-start;background:none;border:0;color:var(--accent,var(--acc));font:inherit;font-weight:700;cursor:pointer;display:inline-flex;align-items:center;gap:6px;padding:4px 0}
.more:hover{text-decoration:underline}
.more .icon{width:1em;height:1em}

/* Blocchi affiancati */
.split{display:grid;grid-template-columns:1fr 1fr;gap:56px;align-items:center}
.split + .split{margin-top:88px}
@media (max-width:900px){.split{grid-template-columns:1fr;gap:36px}.split .media{order:-1}}
.split p{color:var(--muted);font-size:1.05rem;margin:14px 0 22px}
.checks{list-style:none;display:grid;gap:10px;margin-top:6px}
.checks li{display:flex;gap:10px;align-items:flex-start;color:var(--text)}
.checks .icon{color:var(--acc);margin-top:3px;width:1.05em;height:1.05em}
.checks small{display:block;color:var(--muted);font-size:.9rem}
.chips{display:flex;flex-wrap:wrap;gap:10px;margin:0 0 22px;list-style:none}
.chips li{background:var(--surface);border:1px solid var(--line);border-radius:999px;padding:7px 14px;font-size:.92rem;color:var(--text);font-family:var(--mono);font-weight:700}
.link{display:inline-flex;align-items:center;gap:8px;color:var(--text);text-decoration:none;border:1px solid var(--line);background:var(--surface);padding:10px 18px;border-radius:999px;font-weight:700}
.link:hover{border-color:var(--acc);color:var(--acc)}

/* Galleria */
.gallery{display:grid;grid-template-columns:repeat(6,1fr);gap:18px;list-style:none}
@media (max-width:1060px){.gallery{grid-template-columns:repeat(3,1fr)}}
@media (max-width:640px){.gallery{grid-template-columns:repeat(2,1fr);gap:12px}}
.shot{all:unset;cursor:zoom-in;display:block;width:100%}
.shot img{width:100%;aspect-ratio:540/1200;object-fit:cover;object-position:top;border-radius:16px;border:1px solid var(--line);transition:transform .35s,border-color .35s}
.shot:hover img,.shot:focus-visible img{transform:scale(1.03);border-color:var(--acc)}
.shot span{display:block;text-align:center;color:var(--muted);font-size:.88rem;margin-top:10px}

/* Download */
.get{display:grid;grid-template-columns:1.1fr .9fr;gap:22px}
@media (max-width:860px){.get{grid-template-columns:1fr}}
.panel{background:var(--surface);border:1px solid var(--line);border-radius:var(--radius);padding:30px}
.panel.hl{border-color:rgba(255,159,67,.35);background:linear-gradient(160deg,rgba(255,159,67,.07),var(--surface) 55%)}
.panel p{color:var(--muted);margin:10px 0 20px}
.panel .hint{font-size:.88rem;color:var(--dim);margin:14px 0 0}
.status{display:inline-flex;align-items:center;gap:8px;font-size:.8rem;font-weight:800;text-transform:uppercase;letter-spacing:.08em;color:var(--blue);background:rgba(138,180,255,.12);border:1px solid rgba(138,180,255,.3);border-radius:999px;padding:4px 12px;margin-bottom:14px}
.req{display:grid;grid-template-columns:auto 1fr;gap:6px 16px;margin-top:6px;font-size:.95rem}
.req dt{color:var(--dim)}
.req dd{color:var(--text)}

/* Footer */
footer{border-top:1px solid var(--line);padding:40px 0;color:var(--dim);font-size:.92rem}
footer .wrap{display:flex;flex-wrap:wrap;gap:16px 32px;align-items:center;justify-content:space-between}
footer nav{display:flex;flex-wrap:wrap;gap:18px}
footer a{color:var(--muted);text-decoration:none}
footer a:hover{color:var(--acc)}
.foot-langs{width:100%;padding-top:16px;border-top:1px solid var(--line);font-size:.88rem}
.foot-langs a[aria-current]{color:var(--acc)}

/* Finestre di dettaglio */
dialog{margin:auto;border:1px solid var(--line);border-radius:20px;background:var(--surface);color:var(--text);width:min(720px,calc(100% - 32px));max-height:min(88vh,900px);padding:0;box-shadow:0 30px 80px rgba(0,0,0,.6)}
dialog::backdrop{background:rgba(4,5,10,.78);backdrop-filter:blur(4px)}
.dlg{padding:30px;overflow:auto;max-height:min(88vh,900px)}
.dlg-head{display:flex;align-items:center;gap:14px;padding-bottom:16px;margin-bottom:18px;border-bottom:1px solid var(--line);padding-right:44px}
.dlg-head .ico{width:44px;height:44px;border-radius:12px;display:grid;place-items:center;flex:none;color:var(--accent);background:color-mix(in srgb,var(--accent) 13%,transparent)}
.close{position:absolute;top:16px;right:16px;width:40px;height:40px;border-radius:50%;border:0;background:var(--bg);color:var(--muted);display:grid;place-items:center;cursor:pointer}
.close:hover{color:var(--text)}
.dlg p{color:var(--muted);margin-bottom:14px}
.dlg ul{color:var(--muted);padding-left:20px;display:grid;gap:8px;margin-bottom:14px}
.dlg strong{color:var(--text)}
.dlg .note{background:var(--bg);border:1px solid var(--line);border-radius:12px;padding:14px 16px;font-size:.93rem}
#lightbox{width:auto;max-width:calc(100% - 32px);background:transparent;border:0;box-shadow:none;overflow:visible}
#lightbox img{max-height:86vh;width:auto;border-radius:18px;border:1px solid var(--line)}
#lightbox figcaption{text-align:center;color:var(--muted);margin-top:10px}
#lightbox .close{top:-14px;right:-14px;background:var(--surface)}

/* Lingua */
.lang-switch{display:inline-flex;border:1px solid var(--line);border-radius:999px;overflow:hidden}
.nav-links .lang-switch a{padding:6px 12px;font-size:.85rem;font-weight:800;letter-spacing:.04em}
.nav-links .lang-switch a[aria-current]{background:var(--surface2);color:var(--text)}
@media (max-width:1000px){.nav-links .lang-switch{align-self:center;margin-top:12px}.nav-links .lang-switch a{padding:8px 18px}}
"""

ICONS = r"""
<svg width="0" height="0" style="position:absolute" aria-hidden="true">
  <symbol id="i-layers" viewBox="0 0 24 24"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></symbol>
  <symbol id="i-sliders" viewBox="0 0 24 24"><line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/></symbol>
  <symbol id="i-aperture" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="14.31" y1="8" x2="20.05" y2="17.94"/><line x1="9.69" y1="8" x2="21.17" y2="8"/><line x1="7.38" y1="12" x2="13.12" y2="2.06"/><line x1="9.69" y1="16" x2="3.95" y2="6.06"/><line x1="14.31" y1="16" x2="2.83" y2="16"/><line x1="16.62" y1="12" x2="10.88" y2="21.94"/></symbol>
  <symbol id="i-palette" viewBox="0 0 24 24"><path d="M12 22a10 10 0 1 1 10-10c0 2.8-2.2 4-4 4h-2a2 2 0 0 0-1.5 3.3A1.6 1.6 0 0 1 12 22z"/><circle cx="7.5" cy="10.5" r="1"/><circle cx="12" cy="7" r="1"/><circle cx="16.5" cy="10.5" r="1"/></symbol>
  <symbol id="i-mask" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 2a10 10 0 0 0 0 20z" fill="currentColor" stroke="none"/></symbol>
  <symbol id="i-copy" viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></symbol>
  <symbol id="i-github" viewBox="0 0 24 24"><path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/></symbol>
  <symbol id="i-check" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></symbol>
  <symbol id="i-wifi-off" viewBox="0 0 24 24"><line x1="1" y1="1" x2="23" y2="23"/><path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55M5 12.55a10.94 10.94 0 0 1 5.17-2.39M10.71 5.05A16 16 0 0 1 22.58 9M1.42 9a15.91 15.91 0 0 1 4.7-2.88M8.53 16.11a6 6 0 0 1 6.95 0"/><line x1="12" y1="20" x2="12.01" y2="20"/></symbol>
  <symbol id="i-lock" viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></symbol>
  <symbol id="i-shield" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></symbol>
  <symbol id="i-download" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></symbol>
  <symbol id="i-x" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></symbol>
  <symbol id="i-menu" viewBox="0 0 24 24"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></symbol>
  <symbol id="i-right" viewBox="0 0 24 24"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></symbol>
  <symbol id="i-down" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></symbol>
</svg>"""


def esc(s):
    return s.replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;").replace(">", "&gt;")


def dialog(did, accent, icon, title, intro, items, note, close):
    lis = "\n".join(f"      <li><strong>{a}</strong> {b}</li>" for a, b in items)
    return f"""<dialog id="{did}" aria-labelledby="t-{did}" style="--accent:var({accent})">
  <button class="close" data-close aria-label="{close}"><svg class="icon"><use href="#i-x"/></svg></button>
  <div class="dlg">
    <div class="dlg-head"><div class="ico"><svg class="icon"><use href="#{icon}"/></svg></div><h3 id="t-{did}">{title}</h3></div>
    <p>{intro}</p>
    <ul>
{lis}
    </ul>
    <div class="note">{note}</div>
  </div>
</dialog>"""


def checks(t, keys):
    return "\n".join(
        f'          <li><svg class="icon"><use href="#i-check"/></svg><div>{t[k]}<small>{t[k + "s"]}</small></div></li>'
        for k in keys)


def download_panel(t):
    if FDROID:
        return f"""        <h3>{t['fd_h3']}</h3>
        <p>{t['fd_text']}</p>
        <a class="btn btn-primary" href="https://f-droid.org/{t['fd_lang']}/packages/{PKG}/" rel="noopener"><svg class="icon"><use href="#i-download"/></svg> {t['fd_btn']}</a>
        <p class="hint">{t['fd_hint']}</p>"""
    return f"""        <div class="status">{t['dl_status']}</div>
        <h3>{t['dl_h3']}</h3>
        <p>{t['dl_text']}</p>
        <a class="btn btn-primary" href="{RELEASE}" rel="noopener"><svg class="icon"><use href="#i-download"/></svg> {t['dl_btn']}</a>
        <p class="hint">{t['dl_hint']}</p>"""


def page(t):
    r, s, im = t["root"], t["shots"], t["img"]
    url = SITE + ("/" if t["lang"] == "it" else "/en/")
    it_cur = ' aria-current="true"' if t["lang"] == "it" else ""
    en_cur = ' aria-current="true"' if t["lang"] == "en" else ""
    langs = (f'<a href="{r or "./"}" hreflang="it" lang="it" data-lang="it"{it_cur}>IT</a>'
             f'<a href="{r}en/" hreflang="en" lang="en" data-lang="en"{en_cur}>EN</a>')
    foot_langs = (f'<a href="{r or "./"}" hreflang="it" lang="it" data-lang="it"{it_cur}>Italiano</a> · '
                  f'<a href="{r}en/" hreflang="en" lang="en" data-lang="en"{en_cur}>English</a>')
    redirect = ""
    if t["lang"] == "it":
        # Solo dalla pagina italiana (la radice): chi ha il telefono in un'altra lingua va su /en/
        redirect = """<script>
(function(){
  if(/bot|crawl|spider|slurp|preview|lighthouse/i.test(navigator.userAgent))return;
  var pick=null;
  try{pick=localStorage.getItem('pg-lang');}catch(e){}
  if(!pick){
    try{if(document.referrer&&new URL(document.referrer).host===location.host)return;}catch(e){}
    var l=navigator.languages&&navigator.languages.length?navigator.languages:[navigator.language||''];
    pick='en';
    for(var i=0;i<l.length;i++){if(String(l[i]).slice(0,2).toLowerCase()==='it'){pick='it';break;}}
  }
  if(pick==='en')location.replace('en/'+location.hash);
})();
</script>
"""
    gallery = "\n".join(
        f'      <li><button class="shot"><img src="{s}{n}.jpg" width="540" height="1200" loading="lazy" alt="{esc(t["g_" + n + "_alt"])}"><span>{t["g_" + n]}</span></button></li>'
        for n in ("home", "luce", "confronto", "prospettiva", "ritocco", "esporta"))
    formats = "".join(f"<li>{f}</li>" for f in ("JPEG", "PNG", "WebP", "AVIF", "JPEG XL", "TIFF 16 bit" if t["lang"] == "it" else "16-bit TIFF"))

    return f"""<!DOCTYPE html>
<html lang="{t['lang']}">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
{redirect}<title>{t['title']}</title>
<meta name="description" content="{esc(t['desc'])}">
<meta name="theme-color" content="#0b0d14">
<link rel="canonical" href="{url}">
<link rel="alternate" hreflang="it" href="{SITE}/">
<link rel="alternate" hreflang="en" href="{SITE}/en/">
<link rel="alternate" hreflang="x-default" href="{SITE}/en/">
<link rel="icon" type="image/png" sizes="48x48" href="{im}icon-48.png">
<link rel="icon" type="image/png" sizes="192x192" href="{im}icon-192.png">
<link rel="apple-touch-icon" href="{im}icon-180.png">
<meta property="og:type" content="website">
<meta property="og:locale" content="{t['og_locale']}">
<meta property="og:locale:alternate" content="{t['og_alt']}">
<meta property="og:title" content="{esc(t['og_title'])}">
<meta property="og:description" content="{esc(t['og_desc'])}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{SITE}/img/{t['og_img']}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<script type="application/ld+json">
{{"@context":"https://schema.org","@type":"SoftwareApplication","name":"Photograin","operatingSystem":"Android 8.0+","applicationCategory":"MultimediaApplication","softwareVersion":"{VERSION}","license":"https://www.gnu.org/licenses/gpl-3.0.html","offers":{{"@type":"Offer","price":"0","priceCurrency":"EUR"}},"downloadUrl":"{RELEASE}","image":"{SITE}/img/icon-512.png","author":{{"@type":"Person","name":"Stefano Spagnolo"}}}}
</script>
<style>{STYLE}</style>
</head>
<body>
{ICONS}

<header class="nav">
  <div class="wrap">
    <a class="brand" href="#top"><img src="{im}icon-192.png" width="36" height="36" alt=""> Photograin</a>
    <button class="menu-btn" aria-expanded="false" aria-controls="menu" aria-label="{t['menu_open']}" data-label-open="{t['menu_open']}" data-label-close="{t['menu_close']}"><svg class="icon"><use href="#i-menu"/></svg></button>
    <ul class="nav-links" id="menu">
      <li><a href="#features">{t['nav_features']}</a></li>
      <li><a href="#tools">{t['nav_tools']}</a></li>
      <li><a href="#export">{t['nav_export']}</a></li>
      <li><a href="#gallery">{t['nav_gallery']}</a></li>
      <li><a href="#download">{t['nav_download']}</a></li>
      <li><a class="pill" href="{REPO}" rel="noopener"><svg class="icon"><use href="#i-github"/></svg> GitHub</a></li>
      <li class="lang-switch" aria-label="{t['lang_label']}">{langs}</li>
    </ul>
  </div>
</header>

<main id="top">

<section class="hero" style="border-top:0">
  <div class="wrap hero-grid">
    <div>
      <div class="badge"><span class="dot"></span> {t['badge']}</div>
      <h1>{t['h1']}</h1>
      <p class="lead">{t['lead']}</p>
      <div class="cta">
        <a class="btn btn-primary" href="#download"><svg class="icon"><use href="#i-download"/></svg> {t['cta_get']}</a>
        <a class="btn btn-ghost" href="#features">{t['cta_more']} <svg class="icon"><use href="#i-down"/></svg></a>
      </div>
      <ul class="facts">
        <li><svg class="icon"><use href="#i-lock"/></svg> {t['fact1']}</li>
        <li><svg class="icon"><use href="#i-wifi-off"/></svg> {t['fact2']}</li>
        <li><svg class="icon"><use href="#i-shield"/></svg> {t['fact3']}</li>
      </ul>
    </div>
    <div class="phone"><img src="{s}luce.jpg" width="540" height="1200" alt="{esc(t['hero_alt'])}"></div>
  </div>
</section>

<section id="features">
  <div class="wrap">
    <div class="head">
      <div class="eyebrow">{t['feat_eyebrow']}</div>
      <h2>{t['feat_h2']}</h2>
      <p>{t['feat_p']}</p>
    </div>
    <div class="cards">
      <article class="card">
        <div class="ico"><svg class="icon"><use href="#i-layers"/></svg></div>
        <h3>{t['c1_h']}</h3>
        <p>{t['c1_p']}</p>
      </article>
      <article class="card" style="--accent:var(--pink)">
        <div class="ico"><svg class="icon"><use href="#i-sliders"/></svg></div>
        <h3>{t['c2_h']}</h3>
        <p>{t['c2_p']}</p>
        <button class="more" data-open="d-adj">{t['details']} <svg class="icon"><use href="#i-right"/></svg></button>
      </article>
      <article class="card" style="--accent:var(--blue)">
        <div class="ico"><svg class="icon"><use href="#i-aperture"/></svg></div>
        <h3>{t['c3_h']}</h3>
        <p>{t['c3_p']}</p>
        <button class="more" data-open="d-raw">{t['details']} <svg class="icon"><use href="#i-right"/></svg></button>
      </article>
      <article class="card" style="--accent:var(--violet)">
        <div class="ico"><svg class="icon"><use href="#i-palette"/></svg></div>
        <h3>{t['c4_h']}</h3>
        <p>{t['c4_p']}</p>
      </article>
      <article class="card" style="--accent:var(--green)">
        <div class="ico"><svg class="icon"><use href="#i-mask"/></svg></div>
        <h3>{t['c5_h']}</h3>
        <p>{t['c5_p']}</p>
        <button class="more" data-open="d-mask">{t['details']} <svg class="icon"><use href="#i-right"/></svg></button>
      </article>
      <article class="card">
        <div class="ico"><svg class="icon"><use href="#i-copy"/></svg></div>
        <h3>{t['c6_h']}</h3>
        <p>{t['c6_p']}</p>
      </article>
    </div>
  </div>
</section>

<section id="tools" class="alt">
  <div class="wrap">
    <div class="split">
      <div>
        <div class="eyebrow">{t['crop_eyebrow']}</div>
        <h2>{t['crop_h2']}</h2>
        <p>{t['crop_p']}</p>
        <ul class="checks">
{checks(t, ("crop_c1", "crop_c2", "crop_c3"))}
        </ul>
      </div>
      <div class="media"><div class="phone sm"><img src="{s}prospettiva.jpg" width="540" height="1200" loading="lazy" alt="{esc(t['crop_alt'])}"></div></div>
    </div>
    <div class="split">
      <div class="media"><div class="phone sm"><img src="{s}ritocco.jpg" width="540" height="1200" loading="lazy" alt="{esc(t['ret_alt'])}"></div></div>
      <div>
        <div class="eyebrow">{t['ret_eyebrow']}</div>
        <h2>{t['ret_h2']}</h2>
        <p>{t['ret_p']}</p>
        <ul class="checks">
{checks(t, ("ret_c1", "ret_c2", "ret_c3"))}
        </ul>
        <h3 style="margin-top:36px">{t['cmp_h3']}</h3>
        <p>{t['cmp_p']}</p>
      </div>
    </div>
  </div>
</section>

<section id="export">
  <div class="wrap">
    <div class="split">
      <div>
        <div class="eyebrow">{t['exp_eyebrow']}</div>
        <h2>{t['exp_h2']}</h2>
        <p>{t['exp_p']}</p>
        <ul class="chips">{formats}</ul>
        <ul class="checks">
{checks(t, ("exp_c1", "exp_c2", "exp_c3"))}
        </ul>
      </div>
      <div class="media"><div class="phone sm"><img src="{s}esporta.jpg" width="540" height="1200" loading="lazy" alt="{esc(t['exp_alt'])}"></div></div>
    </div>
  </div>
</section>

<section id="gallery" class="alt">
  <div class="wrap">
    <div class="head">
      <div class="eyebrow">{t['gal_eyebrow']}</div>
      <h2>{t['gal_h2']}</h2>
      <p>{t['gal_p']}</p>
    </div>
    <ul class="gallery">
{gallery}
    </ul>
  </div>
</section>

<section id="download">
  <div class="wrap">
    <div class="head">
      <div class="eyebrow">{t['dl_eyebrow']}</div>
      <h2>{t['dl_h2']}</h2>
      <p>{t['dl_p']}</p>
    </div>
    <div class="get">
      <div class="panel hl">
{download_panel(t)}
      </div>
      <div class="panel">
        <h3>{t['req_h3']}</h3>
        <dl class="req">
          <dt>{t['req_sys']}</dt><dd>{t['req_sys_v']}</dd>
          <dt>{t['req_ver']}</dt><dd>{VERSION}</dd>
          <dt>{t['req_lic']}</dt><dd>{t['req_lic_v']}</dd>
        </dl>
        <ul class="checks" style="margin-top:18px">
{checks(t, ("chk1", "chk2", "chk3"))}
        </ul>
      </div>
    </div>
    <p style="text-align:center;margin-top:28px"><a class="link" href="{REPO}" rel="noopener"><svg class="icon"><use href="#i-github"/></svg> {t['src']}</a></p>
  </div>
</section>

</main>

<footer>
  <div class="wrap">
    <div>© 2026 Stefano Spagnolo · {t['foot_lic']} <a href="https://www.gnu.org/licenses/gpl-3.0.html" rel="noopener">GNU GPL v3</a></div>
    <nav aria-label="GitHub">
      <a href="{REPO}" rel="noopener">{t['foot_src']}</a>
      <a href="{REPO}/issues" rel="noopener">{t['foot_issue']}</a>
    </nav>
    <nav class="foot-langs" aria-label="{t['lang_label']}">{foot_langs}</nav>
  </div>
</footer>

{dialog("d-adj", "--pink", "i-sliders", t['d1_title'], t['d1_intro'], t['d1_list'], t['d1_note'], t['close'])}

{dialog("d-raw", "--blue", "i-aperture", t['d3_title'], t['d3_intro'], t['d3_list'], t['d3_note'], t['close'])}

{dialog("d-mask", "--green", "i-mask", t['d5_title'], t['d5_intro'], t['d5_list'], t['d5_note'], t['close'])}

<dialog id="lightbox" aria-label="{t['lb_label']}">
  <button class="close" data-close aria-label="{t['close']}"><svg class="icon"><use href="#i-x"/></svg></button>
  <figure><img src="" alt=""><figcaption></figcaption></figure>
</dialog>

<script>
(function(){{
  var btn=document.querySelector('.menu-btn'), menu=document.getElementById('menu');
  function setMenu(open){{
    menu.classList.toggle('open',open);
    btn.setAttribute('aria-expanded',open);
    btn.setAttribute('aria-label',open?btn.dataset.labelClose:btn.dataset.labelOpen);
  }}
  btn.addEventListener('click',function(){{ setMenu(!menu.classList.contains('open')); }});
  menu.addEventListener('click',function(e){{ var a=e.target.closest('a'); if(a && a.getAttribute('href').charAt(0)==='#') setMenu(false); }});
  document.querySelectorAll('a[data-lang]').forEach(function(a){{
    a.addEventListener('click',function(){{ try{{ localStorage.setItem('pg-lang',a.dataset.lang); }}catch(err){{}} }});
  }});

  function show(d){{ if(d.showModal){{ d.showModal(); }} else {{ d.setAttribute('open',''); }} }}
  function hide(d){{ if(d.close){{ d.close(); }} else {{ d.removeAttribute('open'); }} }}
  document.querySelectorAll('[data-open]').forEach(function(b){{
    b.addEventListener('click',function(){{ show(document.getElementById(b.dataset.open)); }});
  }});
  document.querySelectorAll('dialog').forEach(function(d){{
    d.addEventListener('click',function(e){{ if(e.target===d || e.target.closest('[data-close]')) hide(d); }});
  }});

  var lb=document.getElementById('lightbox'), lbImg=lb.querySelector('img'), lbCap=lb.querySelector('figcaption');
  document.querySelectorAll('.shot').forEach(function(s){{
    s.addEventListener('click',function(){{
      var img=s.querySelector('img');
      lbImg.src=img.src; lbImg.alt=img.alt; lbCap.textContent=s.querySelector('span').textContent;
      show(lb);
    }});
  }});
}})();
</script>
</body>
</html>
"""


def main():
    OUT.mkdir(exist_ok=True)
    (OUT / "en").mkdir(exist_ok=True)
    if (OUT / "img").exists():
        shutil.rmtree(OUT / "img")
    shutil.copytree(BASE / "img", OUT / "img", ignore=shutil.ignore_patterns("*.md"))
    (OUT / "index.html").write_text(page(IT), encoding="utf-8")
    (OUT / "en" / "index.html").write_text(page(EN), encoding="utf-8")
    (OUT / "robots.txt").write_text(f"User-agent: *\nAllow: /\n\nSitemap: {SITE}/sitemap.xml\n", encoding="utf-8")
    alt = (f'    <xhtml:link rel="alternate" hreflang="it" href="{SITE}/"/>\n'
           f'    <xhtml:link rel="alternate" hreflang="en" href="{SITE}/en/"/>\n'
           f'    <xhtml:link rel="alternate" hreflang="x-default" href="{SITE}/en/"/>\n')
    urls = "".join(f"  <url>\n    <loc>{SITE}{p}</loc>\n{alt}  </url>\n" for p in ("/", "/en/"))
    (OUT / "sitemap.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
        f"{urls}</urlset>\n", encoding="utf-8")
    print("ok", OUT)


if __name__ == "__main__":
    main()
