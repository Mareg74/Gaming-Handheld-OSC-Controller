# Design: THOR OSC Controller

**Date:** 2026-09-12  
**Produit:** THOR OSC Controller (AYN Thor)  
**Repo:** `mareg/THOR-OSC-Controller`  
**Statut:** design approuvé par Mareg74 (2026-09-12) — plan d'implémentation rédigé  
**Approche retenue:** Compose + `Presentation` + pipeline Gamepad → OSC UDP (approche 1)

## 1. Contexte et but

Contrôleur OSC **natif Android/Kotlin** pour l’AYN Thor (console de jeu dual-écran).

- **But principal:** surface de contrôle OSC vers des softs show (cible générique).
- **Priorité input:** les **touches / sticks de jeu** du Thor sont les contrôles principaux ; l’écran complète (labels, pages, mapping, réglages).
- **Cibles OSC:** adresses génériques configurables + **catalogue de présélection** basé sur **Resolume Arena** (éviter la saisie une à une).
- **Réseau v1:** OSC **UDP** bidirectionnel (envoi + écoute feedback).
- **Base code:** greenfield natif (pas Capacitor / WebView). Le prototype `ThorOscControlDeck` et THOR LXCTRL (Art-Net) sont des **sources d’idées** seulement ; toute réutilisation concrète doit être **validée avec Mareg74**.

Hors périmètre v1: TCP, Companion/bridge, Capacitor, Art-Net/DMX, app lumière LXCTRL.

## 2. Objectifs et critères de succès

### Dans le périmètre v1

- Gamepad Thor → messages OSC UDP vers IP/port configurés.
- Mapping par page ; édition sur écran haut ; présélection Resolume Arena ou adresse libre.
- Dual-écran: haut = contrôles / mapping / réglages ; bas (`Presentation`) = moniteur OSC + onglet **légende boutons** (notes éditables, ex. `A = STROBE`).
- Feedback OSC entrant: Resolume peut renvoyer des états vers un port UDP local ; affichage moniteur + labels.
- Fallback 1 écran: moniteur + légende accessibles sur l’écran haut.
- Persistance locale: cible, port listen, mappings, notes, page active.

### Critères de succès

1. Appuyer A (mappé) envoie l’OSC attendu vers Resolume (ou mock UDP).
2. Sticks produisent des floats mappés sans bloquer l’UI.
3. Avec 2 displays: bas montre moniteur et légende ; notes éditables visibles.
4. Avec 1 display: pas de crash ; moniteur/légende en fallback haut.
5. Feedback: message OSC reçu sur le port listen met à jour FeedbackStore + UI.
6. Catalogue Arena permet d’assigner une commande sans retaper l’adresse complète.

## 3. Architecture

Une `Activity` (écran haut, landscape). Écran bas = `android.app.Presentation` Compose ; no-op si &lt; 2 displays.

```
[Gamepad / touches Thor]
        ↓
  GamepadInput
        ↓
  MappingEngine  ←→  ConfigStore
        ↓
  OscUdpSender  →  Resolume (UDP out)
        ↓
  EventBus / journal UI

  OscUdpReceiver ← Resolume (UDP in / feedback)
        ↓
  FeedbackStore → UI labels + moniteur

Écran haut (Activity)          Écran bas (Presentation)
  Pages / mapping /              Onglets:
  réglages / labels                • Moniteur OSC (tx/rx)
                                   • Légende boutons (+ notes)
```

## 4. Composants

| Unité | Responsabilité | Dépendances |
|-------|----------------|-------------|
| `GamepadInput` | Normalise KeyEvent / MotionEvent (id, down/up, axe −1…1) | Android input |
| `MappingEngine` | Contrôle → OSC (adresse + args) selon page active | ConfigStore |
| `OscUdpSender` | Encode + envoi Datagram UDP | ConfigStore (cible) |
| `OscUdpReceiver` | Bind port listen, parse OSC entrant | ConfigStore (port) |
| `FeedbackStore` | Dernière valeur par adresse ; ignore malformés | OscUdpReceiver |
| `ResolumeCatalog` | Liste embarquée commandes Arena (présélection) | Asset JSON |
| `ConfigStore` | Persiste cible, listen, mappings, notes, page | Stockage local |
| `PrimaryUi` | Écran haut Compose | MappingEngine, ConfigStore, FeedbackStore |
| `SecondaryUi` | Presentation: Moniteur + Légende | EventBus, ConfigStore, FeedbackStore |
| `SecondaryDisplayHost` | Attach/detach Presentation | DisplayManager |

## 5. Flux de données

### Sortant (chemin chaud)

1. Input Thor → `GamepadInput`
2. `MappingEngine` (page active) → adresse + args (bang / float)
3. `OscUdpSender` envoie ; journal local (ok / erreur)
4. UI haut « last fired » ; moniteur bas affiche la ligne

### Entrant (feedback)

1. `OscUdpReceiver` écoute le port UDP local (réglable)
2. Parse → `FeedbackStore`
3. Moniteur + labels UI reflètent l’état
4. Resolume doit être configuré pour renvoyer vers IP Thor + port listen (documenté dans Réglages)

### Édition (chemin froid)

- IP + port out + port listen → ConfigStore
- Mapping: contrôle → pick catalogue Arena **ou** adresse libre
- Légende: note texte par bouton → ConfigStore ; onglet bas en live

## 6. Erreurs et états

| Cas | Comportement |
|-----|----------------|
| IP/port cible invalide | Pas d’envoi ; message dans Réglages + moniteur |
| Échec envoi UDP | Journal `erreur` ; pas de crash ; pas de file infinie |
| Bind listen échoue | Banner + Réglages ; feedback off jusqu’à correction |
| OSC entrant mal formé | Ignore + compteur drop moniteur |
| Contrôle non mappé | Pas d’envoi ; feedback discret « non mappé » |
| 1 écran | Secondary no-op ; moniteur/légende en fallback haut |
| Config corrompue | Reset soft defaults + toast |

Pas de dialogue modal sur le chemin chaud gamepad.

## 7. Tests

**Unitaires (JVM):** encode/decode OSC ; MappingEngine (mappé / non mappé / axe) ; FeedbackStore ; ConfigStore round-trip + reset.

**Device Thor:** chaque bouton/stick ; dual-écran Presentation + fallback 1 écran ; smoke UDP tx vers Resolume/mock + rx feedback.

**Hors v1:** UI Compose exhaustifs, fuzz réseau.

## 8. Décisions tranchées (brainstorm)

| Sujet | Décision |
|-------|----------|
| But | Surface OSC + jeu Thor |
| Rôle gamepad | Premier plan |
| Cibles | OSC générique + présélection Resolume Arena |
| Dual-écran | Haut contrôles ; bas moniteur + légende notes |
| Stack | Greenfield Kotlin/Compose (pas Capacitor) |
| Transport | UDP only, tx + rx |
| Architecture | Approche 1: Compose + Presentation + pipeline gamepad |

## 9. Hors scope explicite (v1)

TCP ; Companion/bridge ; Capacitor/WebView ; Art-Net/DMX ; intégration LXCTRL ; miroir des deux écrans ; import live de la doc Resolume réseau (catalogue embarqué figé/évolutif par asset).
