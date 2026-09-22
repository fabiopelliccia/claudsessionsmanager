package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A plain checkbox list of sessions, used as-is by both the export and the import action - just a
 * pick-and-confirm dialog, no search, sorting or per-row conflict controls.
 *
 * When [showTargetPathField] is set (import only), an extra folder field lets the user say which
 * local project the sessions should be attached to - required because the archive's own recorded
 * path comes from a different machine (possibly a different user entirely) and is meaningless here.
 */
class SessionCheckboxListDialog(
    project: Project?,
    private val dialogTitle: String,
    private val sessions: List<SessionInfo>,
    alreadyPresentIds: Set<String>,
    initiallyChecked: Boolean,
    showTargetPathField: Boolean = false,
    targetPathDefault: String = "",
) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<SessionInfo>()

    private val targetPathField: TextFieldWithBrowseButton? = if (showTargetPathField) {
        TextFieldWithBrowseButton().apply {
            text = targetPathDefault
            addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.singleDir()
                    .withTitle("Select Target Project Folder")
                    .withDescription("The imported sessions will be attached to this folder on this machine"),
            )
        }
    } else null

    init {
        title = dialogTitle
        sessions.forEach { session ->
            val suffix = if (session.id in alreadyPresentIds) "  [already present]" else ""
            checkBoxList.addItem(session, "${session.displayName}  —  ${session.projectFolderName}$suffix", initiallyChecked)
        }
        init()
    }

    val selectedSessions: List<SessionInfo>
        get() = sessions.filter { checkBoxList.isItemSelected(it) }

    /** Non-blank only when [showTargetPathField] was set and the user filled it in. */
    val targetProjectPath: String?
        get() = targetPathField?.text?.trim()?.takeIf { it.isNotEmpty() }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        val north = JPanel()
        north.layout = BoxLayout(north, BoxLayout.Y_AXIS)
        targetPathField?.let { field ->
            val row = JPanel(BorderLayout(0, 4))
            row.add(JBLabel("Attach the imported sessions to this project folder:"), BorderLayout.NORTH)
            row.add(field, BorderLayout.CENTER)
            row.border = javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0)
            north.add(row)
        }
        north.add(JBLabel("${sessions.size} session(s) found. Select the ones to include:"))
        panel.add(north, BorderLayout.NORTH)

        val scroll = JBScrollPane(checkBoxList)
        scroll.preferredSize = Dimension(560, 360)
        panel.add(scroll, BorderLayout.CENTER)

        val buttons = JPanel()
        val selectAll = JButton("Select All").apply { addActionListener { setAllChecked(true) } }
        val selectNone = JButton("Select None").apply { addActionListener { setAllChecked(false) } }
        buttons.add(selectAll)
        buttons.add(selectNone)
        panel.add(buttons, BorderLayout.SOUTH)

        return panel
    }

    private fun setAllChecked(checked: Boolean) {
        for (i in sessions.indices) {
            checkBoxList.setItemSelected(sessions[i], checked)
        }
        checkBoxList.repaint()
    }

    override fun doValidate(): ValidationInfo? {
        if (targetPathField != null && targetProjectPath == null) {
            return ValidationInfo("Choose the project folder to attach these sessions to", targetPathField)
        }
        if (selectedSessions.isEmpty()) {
            return ValidationInfo("Select at least one session", checkBoxList)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = checkBoxList
}
