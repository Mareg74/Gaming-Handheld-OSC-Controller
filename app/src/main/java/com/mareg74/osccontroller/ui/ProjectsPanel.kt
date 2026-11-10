package com.mareg74.osccontroller.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mareg74.osccontroller.ThorOscApp
import com.mareg74.osccontroller.config.AppConfig
import com.mareg74.osccontroller.config.ConfigProjectMeta
import com.mareg74.osccontroller.config.ProjectPart
import com.mareg74.osccontroller.config.ProjectStore
import com.mareg74.osccontroller.config.ProjectTemplates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NewProjectStep { NONE, OFFER_SAVE, CREATE }

@Composable
fun ProjectsTab(
    app: ThorOscApp,
    onConfigChanged: () -> Unit,
) {
    var projectName by remember { mutableStateOf("") }
    var projectListTick by remember { mutableIntStateOf(0) }
    var expandPartialId by remember { mutableStateOf<String?>(null) }
    var partOsc by remember { mutableStateOf(true) }
    var partMapping by remember { mutableStateOf(true) }
    var partCurves by remember { mutableStateOf(false) }
    var partCalib by remember { mutableStateOf(false) }
    var partDisplay by remember { mutableStateOf(false) }
    var newProjectStep by remember { mutableStateOf(NewProjectStep.NONE) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectTemplate by remember { mutableStateOf(false) } // false = Vide, true = Template
    var pendingDelete by remember { mutableStateOf<ConfigProjectMeta?>(null) }
    var blockedOpenName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val projects = remember(projectListTick) { app.projectStore.list() }
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE) }
    val exportNameFmt = remember { SimpleDateFormat("yyyyMMdd-HHmm", Locale.US) }

    fun selectedParts(): Set<ProjectPart> = buildSet {
        if (partOsc) add(ProjectPart.OSC_NETWORK)
        if (partMapping) add(ProjectPart.MAPPING)
        if (partCurves) add(ProjectPart.CURVES)
        if (partCalib) add(ProjectPart.CALIBRATION)
        if (partDisplay) add(ProjectPart.DISPLAY)
    }

    /**
     * Persist live config and keep a recovery snapshot before replacing it.
     * Opening the Autosave project itself must not overwrite that snapshot.
     */
    fun safeguardBeforeReplace(openingProjectId: String? = null) {
        app.persist()
        val autosave = app.projectStore.list()
            .find { it.name.equals(ProjectStore.AUTOSAVE_NAME, ignoreCase = true) }
        if (openingProjectId != null && autosave?.id == openingProjectId) {
            val prior = app.projectStore.list()
                .find { it.name.equals("Avant ouverture", ignoreCase = true) }
            app.projectStore.save("Avant ouverture", app.config, existingId = prior?.id)
        } else {
            app.projectStore.saveAutosave(app.config)
        }
        projectListTick++
    }

    /** Save current under [projectName], or Autosave if the field is empty. */
    fun saveCurrentBeforeNew() {
        app.persist()
        val name = projectName.trim()
        if (name.isEmpty()) {
            app.projectStore.saveAutosave(app.config)
            projectListTick++
            Toast.makeText(context, "Config sauvée dans Autosave", Toast.LENGTH_SHORT).show()
        } else {
            val sameName = app.projectStore.list().find { it.name.equals(name, ignoreCase = true) }
            val meta = app.projectStore.save(name, app.config, existingId = sameName?.id)
            app.projectStore.setActiveId(meta.id)
            projectListTick++
            projectName = meta.name
            Toast.makeText(context, "Projet « ${meta.name} » sauvegardé", Toast.LENGTH_SHORT).show()
        }
    }

    fun openCreatePopup() {
        newProjectName = ProjectTemplates.nextDefaultName(app.projectStore.list().map { it.name })
        newProjectTemplate = false
        newProjectStep = NewProjectStep.CREATE
    }

    fun createNewProject() {
        val name = newProjectName.trim().ifBlank {
            ProjectTemplates.nextDefaultName(app.projectStore.list().map { it.name })
        }
        safeguardBeforeReplace()
        val cfg: AppConfig = if (newProjectTemplate) {
            ProjectTemplates.basicOsc(app.config)
        } else {
            ProjectTemplates.empty(app.config)
        }
        val meta = app.projectStore.save(name, cfg)
        app.config = cfg
        app.persist()
        app.projectStore.setActiveId(meta.id)
        projectListTick++
        projectName = meta.name
        newProjectStep = NewProjectStep.NONE
        onConfigChanged()
        Toast.makeText(
            context,
            if (newProjectTemplate) {
                "Projet « ${meta.name} » créé (template) — Mapping/Play mis à jour"
            } else {
                "Projet « ${meta.name} » créé (vide) — Mapping/Play mis à jour"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            app.persist()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(app.exportConfigText().toByteArray(Charsets.UTF_8))
            } ?: error("stream")
            Toast.makeText(context, "Configuration exportée", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Échec de l’export", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            safeguardBeforeReplace()
            val raw = context.contentResolver.openInputStream(uri)?.use { inp ->
                inp.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("stream")
            if (!app.importConfigText(raw)) {
                Toast.makeText(context, "Fichier de configuration invalide", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            app.projectStore.clearActiveId()
            projectName = ""
            onConfigChanged()
            Toast.makeText(context, "Configuration importée (Autosave conservé)", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Échec de l’import", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Sauvegarde OSC + mapping (et options). Nouveau projet : Vide ou Template basique. " +
                "Avant une ouverture / un import / un nouveau, la config courante est protégable (Autosave).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it },
            label = { Text("Nom du projet") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val name = projectName.trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Indique un nom de projet", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                app.persist()
                val sameName = projects.find { it.name.equals(name, ignoreCase = true) }
                val meta = app.projectStore.save(name, app.config, existingId = sameName?.id)
                app.projectStore.setActiveId(meta.id)
                projectListTick++
                projectName = meta.name
                Toast.makeText(
                    context,
                    if (sameName != null) "Projet « ${meta.name} » mis à jour"
                    else "Projet « ${meta.name} » enregistré",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sauvegarder la config actuelle")
        }
        Button(
            onClick = { newProjectStep = NewProjectStep.OFFER_SAVE },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nouveau projet")
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val stamp = exportNameFmt.format(Date())
                    exportLauncher.launch("thor-osc-$stamp.txt")
                },
                modifier = Modifier.weight(1f),
            ) { Text("Exporter") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text("Importer") }
        }

        if (projects.isEmpty()) {
            Text(
                "Aucun projet enregistré",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            projects.forEach { meta ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(meta.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Modifié ${dateFmt.format(Date(meta.updatedAtMs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val project = app.projectStore.get(meta.id)
                                    if (project == null) {
                                        Toast.makeText(context, "Projet introuvable", Toast.LENGTH_SHORT).show()
                                        projectListTick++
                                        return@Button
                                    }
                                    safeguardBeforeReplace(openingProjectId = meta.id)
                                    app.applyProject(project.config, ProjectPart.entries.toSet())
                                    app.projectStore.setActiveId(meta.id)
                                    projectName = meta.name
                                    onConfigChanged()
                                    Toast.makeText(
                                        context,
                                        "Projet « ${meta.name} » ouvert",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Ouvrir tout") }
                            OutlinedButton(
                                onClick = {
                                    expandPartialId =
                                        if (expandPartialId == meta.id) null else meta.id
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    if (expandPartialId == meta.id) "Masquer"
                                    else "Ouvrir seulement.."
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    app.persist()
                                    app.projectStore.save(meta.name, app.config, existingId = meta.id)
                                    app.projectStore.setActiveId(meta.id)
                                    projectName = meta.name
                                    projectListTick++
                                    Toast.makeText(
                                        context,
                                        "« ${meta.name} » sauvegardé",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Sauvegarder") }
                            TextButton(
                                onClick = {
                                    if (app.projectStore.isActive(meta.id)) {
                                        blockedOpenName = meta.name
                                        return@TextButton
                                    }
                                    pendingDelete = meta
                                },
                            ) { Text("Suppr.") }
                        }
                        if (expandPartialId == meta.id) {
                            Text(
                                "Choisir les paramètres à rappeler :",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            ProjectPartCheck(
                                label = "Réseau OSC (IP / ports)",
                                checked = partOsc,
                                onCheckedChange = { partOsc = it },
                            )
                            ProjectPartCheck(
                                label = "Mapping (pages, adresses, notes)",
                                checked = partMapping,
                                onCheckedChange = { partMapping = it },
                            )
                            ProjectPartCheck(
                                label = "Courbes sticks / gâchettes",
                                checked = partCurves,
                                onCheckedChange = { partCurves = it },
                            )
                            ProjectPartCheck(
                                label = "Calibration sticks",
                                checked = partCalib,
                                onCheckedChange = { partCalib = it },
                            )
                            ProjectPartCheck(
                                label = "Affichage (thème / palette / mono-écran)",
                                checked = partDisplay,
                                onCheckedChange = { partDisplay = it },
                            )
                            Button(
                                onClick = {
                                    val parts = selectedParts()
                                    if (parts.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Sélectionne au moins un paramètre",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@Button
                                    }
                                    val project = app.projectStore.get(meta.id)
                                    if (project == null) {
                                        Toast.makeText(context, "Projet introuvable", Toast.LENGTH_SHORT).show()
                                        projectListTick++
                                        return@Button
                                    }
                                    safeguardBeforeReplace(openingProjectId = meta.id)
                                    app.applyProject(project.config, parts)
                                    onConfigChanged()
                                    Toast.makeText(
                                        context,
                                        "Paramètres ouverts depuis « ${meta.name} »",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Rappeler la sélection")
                            }
                        }
                    }
                }
            }
        }
    }

    if (newProjectStep == NewProjectStep.OFFER_SAVE) {
        PresentationSafePopup(onDismiss = { newProjectStep = NewProjectStep.NONE }) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Sauvegarder le projet courant ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "La config live sera remplacée par le nouveau projet. " +
                        "Tu peux d’abord l’enregistrer sous le nom indiqué ci-dessus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        saveCurrentBeforeNew()
                        openCreatePopup()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Oui, sauvegarder") }
                OutlinedButton(
                    onClick = { openCreatePopup() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Non") }
                TextButton(
                    onClick = { newProjectStep = NewProjectStep.NONE },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Annuler") }
            }
        }
    }

    if (newProjectStep == NewProjectStep.CREATE) {
        PresentationSafePopup(onDismiss = { newProjectStep = NewProjectStep.NONE }) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Nouveau projet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Contenu initial",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !newProjectTemplate,
                        onClick = { newProjectTemplate = false },
                        label = { Text("Vide") },
                    )
                    FilterChip(
                        selected = newProjectTemplate,
                        onClick = { newProjectTemplate = true },
                        label = { Text("Template basique") },
                    )
                }
                if (newProjectTemplate) {
                    Text(
                        "Pad + Tactile (3 faders + 4×4) ; /thor/trigger|fader|BTN|AXIS ; grille 64, Violet scène.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Aucune commande mappée (page Pad vide).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { createNewProject() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Créer") }
                TextButton(
                    onClick = { newProjectStep = NewProjectStep.NONE },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Annuler") }
            }
        }
    }

    pendingDelete?.let { meta ->
        PresentationSafePopup(onDismiss = { pendingDelete = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Supprimer le projet « ${meta.name} » ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Le projet sera retiré de la liste. La configuration live reste inchangée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Annuler")
                    }
                    TextButton(
                        onClick = {
                            if (app.projectStore.isActive(meta.id)) {
                                pendingDelete = null
                                blockedOpenName = meta.name
                                return@TextButton
                            }
                            app.projectStore.delete(meta.id)
                            if (expandPartialId == meta.id) expandPartialId = null
                            projectListTick++
                            pendingDelete = null
                            Toast.makeText(
                                context,
                                "« ${meta.name} » supprimé",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ) {
                        Text("Supprimer")
                    }
                }
            }
        }
    }

    blockedOpenName?.let { name ->
        PresentationSafePopup(onDismiss = { blockedOpenName = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Projet ouvert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Impossible de supprimer le projet ouvert « $name ». " +
                        "Ouvre un autre projet avant de le retirer de la liste.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { blockedOpenName = null },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun ProjectPartCheck(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
