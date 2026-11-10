# Design: Moniteur NDI (écran bas + suite Play)

**Date:** 2026-09-15  
**Produit:** THOR OSC Controller (AYN Thor)  
**Repo:** `mareg/THOR-OSC-Controller`  
**Statut:** spec approuvée par Mareg74 (2026-09-15) — plan d’implémentation rédigé  
**Approche retenue:** NDI natif Android SDK (approche 1) ; proxy JPEG = plan B si SDK bloquant

## 1. Contexte et but

Compléter le contrôleur OSC Thor avec un **aperçu vidéo PGM** (source NDI, typiquement Resolume).

- **But principal:** voir le PGM pendant le jeu.
- **v1:** destination **« NDI Monitor »** sur l’écran bas (sous « Moniteur ») **et** sur l’écran haut (sous Play / Mapping / Projets), partageant **une seule** source NDI via `NdiSession` (refcount viewers).
- **Suite (hors v1):** moniteur NDI au **centre Play** (zone vide grille 2×2) branché sur le même pipeline ; multi-sources simultanées.
- **Priorité transport:** latence minimale → SDK NDI Android natif.
- **Réglages flux:** sur la page, bouton **engrenage** → **popup** (qualité, résolution max, FPS max, audio, on/pause).

Hors v1: centre Play, multi-sources simultanées, helper PC JPEG/WebRTC (sauf plan B documenté), métriques latence chiffrées.

## 2. Objectifs et critères de succès

### Dans le périmètre v1

- Entrée drawer bas: `Moniteur` → **`NDI Monitor`** → `Mapping` → …
- Entrée drawer haut (dual): `Play` → … → **`NDI Monitor`** → `Réglages` (même session / une source).
- Découverte auto des sources NDI + choix manuel ; dernière source mémorisée.
- Affichage live plein cadre ; on/pause ; popup réglages ; persistance locale.
- Quitter **toutes** les pages moniteur ou passer l’app en background → **disconnect** (libère la bande passante). Une page qui reste ouverte garde la session.
- Audio **off** par défaut.

### Critères de succès

1. Ouvrir « NDI Monitor » liste / reconnecte la dernière source sans crash.
2. Connect PGM Resolume → image live sur l’écran bas.
3. Baisser FPS / résolution via popup → charge perçue en baisse (obs. qualitative).
4. Quitter **toutes** les pages moniteur → plus de réception NDI.
5. États UI explicites: idle / discovering / connecting / live / paused / error.

## 3. Architecture

```
Écran bas (Presentation) — drawer destinations
  Moniteur (OSC log) | NDI Monitor | Mapping | Projets | Réglages

[NdiMonitorPage]
        ↓
  NdiSession  ←→  NdiPrefs
        ↓
  NdiReceiver (NDI SDK Android)
        ↓
  frames → texture / ImageBitmap → Compose

Suite (hors v1): même NdiSession → slot centre Play (grille 2×2)
```

**Couches**

1. **UI Compose** — page plein cadre + chrome (source, on/pause, engrenage).
2. **NdiSession** — discover → connect → receive → pause/stop/disconnect.
3. **NDI SDK Android** — discovery + receiver ; un receiver à la fois.
4. **NdiPrefs** — source choisie + réglages popup.

Plan B (non implémenté en v1): proxy JPEG léger si licence/SDK/perf bloquants — décision Mareg74 avant bascule.

## 4. Composants

| Unité | Responsabilité | Dépendances |
|-------|----------------|-------------|
| Drawer destinations | Ajouter « NDI Monitor » juste sous « Moniteur » | `SecondaryScreen` |
| `NdiMonitorPage` | Zone vidéo + overlay état + on/pause + engrenage | `NdiSession` |
| `NdiSettingsPopup` | Qualité, rés max, FPS max, audio on/off ; apply + persist | `NdiPrefs`, `NdiSession` |
| `NdiSourcePicker` | Liste discovery + refresh + sélection | `NdiSession` |
| `NdiSession` | StateFlow status / frame / sources ; cycle de vie | `NdiReceiver`, `NdiPrefs` |
| `NdiReceiver` | Wrap SDK (JNI) ; connect / frames / disconnect | NDI SDK Android |
| `NdiPrefs` | Persiste source + réglages | Stockage local |

## 5. Flux

**Ouverture page**

1. User ouvre « NDI Monitor ».
2. Source mémorisée → connect auto avec derniers réglages.
3. Sinon → idle + invite picker.

**Discovery / choix**

1. Engrenage ou action Source → liste (refresh manuel / périodique).
2. Tap source → disconnect ancienne → connect nouvelle → persist.

**Réception**

1. Frames SDK → texture UI (fit, letterbox).
2. Pause = stop receive ; On = reprise.
3. Quit page / background = **disconnect** (pas pause).

**Popup réglages**

1. Engrenage → popup.
2. Changer qualité / rés / FPS / audio → appliquer (reconnect si besoin) → persist.
3. Fermer popup.

**Machine d’états:** `idle` → `discovering` → `connecting` → `live` | `paused` | `error` (message court + retry).

## 6. Erreurs et edge cases

| Cas | Comportement |
|-----|--------------|
| Aucune source | Liste vide + « Aucune source NDI » + refresh |
| Source disparue | `error` + message + retry discover |
| Connect timeout / échec SDK | `error` + retry ; dernière frame figée si dispo |
| Wi‑Fi / réseau perdu | `error` ; reconnect auto limité (backoff) |
| SDK indispo / licence | Message bloquant + note plan B (pas d’implémentation auto) |
| Changement réglages échoue | Garder anciens réglages + toast erreur |
| App background | Disconnect (même règle que quit page) |
| Audio off | Pas de piste audio ouverte (défaut v1) |

Toujours un état UI explicite ; pas de crash silencieux.

## 7. Tests

**Unit**

- `NdiPrefs` : sérialisation source + réglages.
- Machine d’états `NdiSession` avec fake receiver.
- Règle quit / background → disconnect.

**UI (Compose)**

- Idle sans source → invite picker.
- Live → overlay + contrôles.
- Error → message + retry.
- Engrenage → popup ; apply persiste.

**Manuel device**

- Discovery réseau ; connect PGM Resolume live.
- Popup FPS/rés → charge réseau qualitative.
- Quit / background → plus de réception.

**Hors v1:** latence chiffrée, multi-sources, centre Play, plan B.

## 8. Hors scope / suite

- **v1.x:** moniteur NDI centre Play (même `NdiSession`, un affichage à la fois ou règle à définir).
- Plan B proxy JPEG/WebRTC uniquement si SDK natif bloqué (validation Mareg74).
- Pas de Settings NDI séparés : tout via popup page.

## 9. Décisions brainstorm (résumé)

| Sujet | Choix |
|-------|--------|
| But | PGM pendant le jeu + page bas NDI Monitor |
| Portée v1 | Page bas d’abord ; centre Play ensuite |
| Source | Découverte auto + choix manuel |
| Réglages | Engrenage → popup sur la page |
| Transport | Latence mini → NDI SDK natif |
| Quit page | Disconnect |
| Audio défaut | Off |
