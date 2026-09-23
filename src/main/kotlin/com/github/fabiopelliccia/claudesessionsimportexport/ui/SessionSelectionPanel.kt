package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionInfo
import com.github.fabiopelliccia.claudesessionsimportexport.core.TimestampShift
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

internal class SessionRow(
    val session: SessionInfo,
    val status: String = "",
    var selected: Boolean = false,
)

/**
 * Columns of the session table. The model keys its logic on the constant, never on the header text:
 * that text is translated and would otherwise change the behaviour of the table with the language.
 */
internal enum class SessionColumn(private val key: String?, val width: Int) {
    SELECTION(null, 34),
    SESSION("table.column.session", 320),
    FOLDER("table.column.folder", 240),
    BRANCH("table.column.branch", 90),
    UPDATED("table.column.updated", 110),
    MESSAGES("table.column.messages", 70),
    SIZE("table.column.size", 70),
    STATUS("table.column.status", 90),
    ID("table.column.id", 240);

    val title: String get() = key?.let { ClaudeSessionsBundle.message(it) }.orEmpty()
}

internal class SessionTableModel(
    val rows: List<SessionRow>,
    showStatus: Boolean,
) : AbstractTableModel() {

    private val columns: List<SessionColumn> = SessionColumn.entries.filter { showStatus || it != SessionColumn.STATUS }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column].title

    fun column(index: Int): SessionColumn = columns[index]

    fun indexOf(column: SessionColumn): Int = columns.indexOf(column)

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columns[columnIndex]) {
        SessionColumn.SELECTION -> Boolean::class.javaObjectType
        SessionColumn.MESSAGES -> Int::class.javaObjectType
        SessionColumn.SIZE -> Long::class.javaObjectType
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columns[columnIndex] == SessionColumn.SELECTION

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val row = rows[rowIndex]
        val session = row.session
        return when (columns[columnIndex]) {
            SessionColumn.SELECTION -> row.selected
            // A session with no title of its own (typically one with no message at all) gets a
            // label instead of its id, which the ID column already shows.
            SessionColumn.SESSION -> session.summary?.takeIf { it.isNotBlank() }
                ?: ClaudeSessionsBundle.message("table.session.untitled")
            SessionColumn.FOLDER -> session.projectPath ?: session.projectFolderName
            SessionColumn.BRANCH -> session.gitBranch.orEmpty()
            SessionColumn.UPDATED -> localTime(session.lastTimestamp ?: session.firstTimestamp)
            SessionColumn.MESSAGES -> session.messageCount
            SessionColumn.SIZE -> session.sizeBytes
            SessionColumn.STATUS -> row.status
            SessionColumn.ID -> session.id
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columns[columnIndex] == SessionColumn.SELECTION) {
            rows[rowIndex].selected = aValue == true
            fireTableRowsUpdated(rowIndex, rowIndex)
        }
    }

    fun setAllSelected(visibleRows: List<Int>, selected: Boolean) {
        visibleRows.forEach { rows[it].selected = selected }
        fireTableDataChanged()
    }

    fun selected(): List<SessionInfo> = rows.filter { it.selected }.map { it.session }

    private fun localTime(timestamp: String?): String =
        timestamp?.let(TimestampShift::parse)?.atZone(ZoneId.systemDefault())?.format(LOCAL_TIME) ?: timestamp.orEmpty()

    private companion object {
        // Sorts correctly as plain text, which is how the row sorter compares this column.
        val LOCAL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

/**
 * Reusable table with a checkbox column and a quick filter, shared by the export and the import
 * dialogs.
 */
internal class SessionSelectionPanel(
    rows: List<SessionRow>,
    showStatus: Boolean,
    private val onSelectionChanged: () -> Unit,
) : JPanel(BorderLayout()) {

    private val model = SessionTableModel(rows, showStatus)
    private val table = JBTable(model)
    private val sorter = TableRowSorter(model)
    private val searchField = SearchTextField()
    private val counterLabel = JBLabel()

    init {
        table.rowSorter = sorter
        table.setShowGrid(false)
        table.autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.columnModel.getColumn(0).apply {
            maxWidth = JBUI.scale(SessionColumn.SELECTION.width)
            minWidth = JBUI.scale(SessionColumn.SELECTION.width)
        }
        for (index in 1 until model.columnCount) {
            table.columnModel.getColumn(index).preferredWidth = JBUI.scale(model.column(index).width)
        }
        table.columnModel.getColumn(model.indexOf(SessionColumn.SIZE)).cellRenderer = SizeRenderer()
        model.addTableModelListener {
            updateCounter()
            onSelectionChanged()
        }

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = applyFilter()
        })

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            add(JButton(ClaudeSessionsBundle.message("table.selectAll")).apply {
                addActionListener { this@SessionSelectionPanel.model.setAllSelected(visibleModelRows(), true) }
            })
            add(JButton(ClaudeSessionsBundle.message("table.selectNone")).apply {
                addActionListener { this@SessionSelectionPanel.model.setAllSelected(visibleModelRows(), false) }
            })
        }

        val header = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(searchField, BorderLayout.CENTER)
            add(actions, BorderLayout.EAST)
            border = JBUI.Borders.emptyBottom(6)
        }

        add(header, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        add(counterLabel.apply { border = JBUI.Borders.emptyTop(6) }, BorderLayout.SOUTH)
        preferredSize = Dimension(JBUI.scale(980), JBUI.scale(460))
        updateCounter()
    }

    private fun visibleModelRows(): List<Int> = (0 until table.rowCount).map { table.convertRowIndexToModel(it) }

    private fun applyFilter() {
        val text = searchField.text.trim()
        val searchableColumns = (1 until model.columnCount).toList().toIntArray()
        sorter.rowFilter =
            if (text.isEmpty()) null
            else RowFilter.regexFilter("(?i)" + Regex.escape(text), *searchableColumns)
    }

    private fun updateCounter() {
        counterLabel.text = ClaudeSessionsBundle.message("table.counter", model.selected().size, model.rowCount)
    }

    fun selectedSessions(): List<SessionInfo> = model.selected()

    fun hasSelection(): Boolean = model.rows.any { it.selected }

    /** Keeps the column sortable by byte count while showing a human readable size. */
    private class SizeRenderer : DefaultTableCellRenderer() {
        init {
            horizontalAlignment = SwingConstants.RIGHT
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val text = (value as? Long)?.let { StringUtil.formatFileSize(it) } ?: value
            return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
        }
    }
}
