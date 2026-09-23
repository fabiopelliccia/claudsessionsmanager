package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudePaths
import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.github.fabiopelliccia.claudesessionsimportexport.core.ConflictPolicy
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Lets the user pick which sessions contained in an archive have to be restored, how a conflict is
 * resolved and which local folder the sessions are attached to.
 *
 * [existingIds] is read from disk by the caller, before the dialog is built, so that answering
 * "already present?" never touches the file system on the EDT.
 */
internal class ImportSessionsDialog(
    project: Project?,
    private val archive: Path,
    sessions: List<SessionInfo>,
    existingIds: Set<String>,
) : DialogWrapper(project, true) {

    private val conflictCombo = ComboBox(DefaultComboBoxModel(ConflictPolicy.entries.toTypedArray())).apply {
        renderer = SimpleListCellRenderer.create("") { it.label }
    }

    private val relocateCheckBox = JBCheckBox(ClaudeSessionsBundle.message("dialog.import.relocate.checkbox"))

    private val relocateField = TextFieldWithBrowseButton()

    private val panel = SessionSelectionPanel(
        rows = sessions.map { session ->
            val alreadyPresent = session.id in existingIds
            SessionRow(
                session = session,
                status = ClaudeSessionsBundle.message(
                    if (alreadyPresent) "dialog.import.status.present" else "dialog.import.status.new",
                ),
                selected = true,
            )
        },
        showStatus = true,
        onSelectionChanged = { isOKActionEnabled = true },
    )

    init {
        title = ClaudeSessionsBundle.message("dialog.import.title")
        setOKButtonText(ClaudeSessionsBundle.message("dialog.import.okButton"))
        conflictCombo.selectedItem = ConflictPolicy.DUPLICATE

        val projectPath = project?.basePath?.let(ClaudePaths::normalizeProjectPath)
        relocateField.text = projectPath.orEmpty()
        relocateField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleDir()
                .withTitle(ClaudeSessionsBundle.message("dialog.import.relocate.chooser.title"))
                .withDescription(ClaudeSessionsBundle.message("dialog.import.relocate.chooser.description")),
        )
        // `claude --resume` lists the sessions of the folder it is started from: an archive from
        // another machine is invisible here unless its recorded folder is remapped. With no project
        // open there is no sensible default, and the user can still tick the box and pick one.
        relocateCheckBox.isSelected = projectPath != null && sessions.any { session ->
            session.projectPath?.let(ClaudePaths::normalizeProjectPath) != projectPath
        }
        relocateCheckBox.addActionListener { updateRelocationState() }
        updateRelocationState()

        init()
    }

    private fun updateRelocationState() {
        relocateField.isEnabled = relocateCheckBox.isSelected
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        add(
            JBLabel(ClaudeSessionsBundle.message("dialog.import.archive", archive.toAbsolutePath()))
                .apply { border = JBUI.Borders.emptyBottom(8) },
            BorderLayout.NORTH,
        )
        add(panel, BorderLayout.CENTER)
        add(createOptionsPanel(), BorderLayout.SOUTH)
    }

    private fun createOptionsPanel(): JComponent = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.emptyTop(8)
        val gap = JBUI.insets(2, 0, 2, 6)

        add(
            relocateCheckBox,
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                insets = gap
            },
        )
        add(
            relocateField,
            GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(gap.top, 0, gap.bottom, 0)
            },
        )
        add(
            JBLabel(ClaudeSessionsBundle.message("dialog.import.relocate.hint"))
                .apply { foreground = UIUtil.getContextHelpForeground() },
            GridBagConstraints().apply {
                gridx = 0
                gridy = 1
                gridwidth = 2
                anchor = GridBagConstraints.WEST
                insets = JBUI.insetsBottom(8)
            },
        )
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                add(JBLabel(ClaudeSessionsBundle.message("dialog.import.conflict.label")))
                add(conflictCombo)
            },
            GridBagConstraints().apply {
                gridx = 0
                gridy = 2
                gridwidth = 2
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0)
            },
        )
    }

    override fun getPreferredFocusedComponent(): JComponent = panel

    override fun doValidate(): ValidationInfo? = when {
        !panel.hasSelection() ->
            ValidationInfo(ClaudeSessionsBundle.message("dialog.validation.selectSession"), panel)

        relocateCheckBox.isSelected && relocateField.text.isBlank() ->
            ValidationInfo(ClaudeSessionsBundle.message("dialog.validation.selectFolder"), relocateField)

        else -> null
    }

    fun selectedSessions(): List<SessionInfo> = panel.selectedSessions()

    fun conflictPolicy(): ConflictPolicy = conflictCombo.selectedItem as ConflictPolicy

    /** Destination folder for the imported sessions, or `null` to keep the one they were recorded in. */
    fun relocationTarget(): String? = relocateField.text.trim()
        .takeIf { relocateCheckBox.isSelected && it.isNotEmpty() }
        ?.let(ClaudePaths::normalizeProjectPath)

    override fun getDimensionServiceKey(): String = "ClaudeSessionsImportExport.ImportDialog"
}
