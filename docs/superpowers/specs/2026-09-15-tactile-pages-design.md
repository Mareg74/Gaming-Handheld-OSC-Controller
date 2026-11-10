# Design: Pages tactiles (triggers / faders) + types de page

**Date:** 2026-09-15  
**Produit:** THOR OSC Controller  
**Statut:** approuvé Mareg74 (1C + 2B)

## Décisions

1. **Deux types de page** à la création :
   - **Contrôleur physique** — mapping pad/sticks actuel (comportement existant).
   - **Page tactile (perso)** — widgets libres (triggers + faders) éditables.
2. **Play dual** :
   - **Écran haut** : selon la page active — pad physique *ou* surface tactile.
   - **Écran bas** : nouvel onglet **Play** qui n’affiche **que** les pages tactiles (jamais le pad classique).
3. **Éditeur** : ajouter / supprimer / placer librement triggers et faders (pas de grille figée).

Hors v1 : multi-touch avancé, presets Resolume dédiés widgets, synchro page haut/bas indépendante.

## Modèle

```
PageType = PHYSICAL | TACTILE

TactileWidget(
  id, page, kind: TRIGGER|FADER, label,
  x,y,w,h in 0…1 (relatif au canvas Play/Mapping)
)

ControlMapping.controlId = widget.id  // OSC (adresse, ArgKind, float*)
```

- Pages existantes sans type → `PHYSICAL`.
- `activePage` = Play **haut** + gamepad + Mapping.
- `bottomPlayPage` = Play **bas** (pages tactiles), indépendant — changer l’un ne bascule pas l’autre.

## UI

| Surface | PHYSICAL | TACTILE |
|---------|----------|---------|
| Play haut | `PlayGamepadLayout` | canvas widgets (touch → OSC) |
| Play bas | — (onglet ne liste pas ces pages) | idem canvas, switcher tactiles only |
| Mapping | pad + éditeur actuel | canvas édition + panneau OSC (réutilise champs adresse) |

**Création page** (Mapping + Réglages) : overlay titre + choix type (radio), puis Créer.

## OSC

- Trigger : press → BANG ou FLOAT fixe (comme bouton digital).
- Fader : drag → FLOAT continu (comme axe), plage min/max optionnelle.
- `MappingEngine.resolveId(controlId, value, continuous)`.
- `ThorOscApp.emitControl(controlId, value, continuous)` pour l’UI Compose.

## Persistance

`ConfigCodec` : lignes supplémentaires `pageTypes` + `widgets` (rétrocompat : absents → PHYSICAL, widgets vides).

## Critères de succès

1. Créer une page « perso » via popup titre + type.
2. Ajouter un trigger + un fader, les placer, mapper OSC, test envoi.
3. Play haut et Play bas affichent la surface tactile ; bas n’offre pas le pad.
4. Page physique inchangée (pad + gamepad).
5. Quit/reload : types + widgets restaurés.
