package com.newssourcecrawler.bean

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ReadSelectedTextAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val editor = event.getData(CommonDataKeys.EDITOR)
        val selectedText = editor?.selectionModel?.selectedText

        if (selectedText.isNullOrBlank()) {
            Messages.showInfoMessage(
                project,
                "Select pasted or saved path/message/device failure evidence first.",
                "bea-n"
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val output = BeaNRunner.readAutoJson(selectedText)
            val reportText = BeaNReportFormatter.format(output)
            val promptPacket = BeaNReportFormatter.promptPacket(output)

            ApplicationManager.getApplication().invokeLater {
                BeaNResultDialog(project, reportText, promptPacket).show()
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabled = editor?.selectionModel?.hasSelection() == true
    }
}

private object BeaNRunner {
    private const val DEV_BINARY_PATH = "/Users/ismcanga/dev/bea-n/target/debug/bea-n"

    private val publicInstallCandidates: List<String>
        get() {
            val home = System.getProperty("user.home")
            return listOf(
                "$home/.local/bin/bea-n",
                "/opt/homebrew/bin/bea-n",
                "/usr/local/bin/bea-n",
            )
        }

    private val binaryCandidates: List<String>
        get() = publicInstallCandidates + DEV_BINARY_PATH

    fun readAutoJson(evidenceText: String): String {
        val binary = resolveBinary()
            ?: return buildString {
                appendLine("bea-n CLI is not installed.")
                appendLine()
                appendLine("Download the macOS bea-n binary from GitHub Releases or install it with Homebrew, then place it at one of these paths:")
                publicInstallCandidates.forEach { appendLine("- $it") }
                appendLine()
                appendLine("The plugin does not download or run remote code automatically.")
            }.trimEnd()

        val process = ProcessBuilder(
            binary.absolutePath,
            "read",
            "auto",
            "--format",
            "json"
        )
            .redirectErrorStream(false)
            .start()

        process.outputStream.bufferedWriter().use { writer ->
            writer.write(evidenceText)
            writer.flush()
        }

        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return "bea-n timed out while reading selected evidence."
        }

        val stdout = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()

        if (process.exitValue() != 0) {
            return buildString {
                appendLine("bea-n failed with exit code ${process.exitValue()}.")
                if (stderr.isNotBlank()) {
                    appendLine()
                    appendLine("stderr:")
                    appendLine(stderr)
                }
                if (stdout.isNotBlank()) {
                    appendLine()
                    appendLine("stdout:")
                    appendLine(stdout)
                }
            }.trimEnd()
        }

        return stdout.ifBlank { "bea-n returned no output." }
    }

    private fun resolveBinary(): File? {
        return binaryCandidates
            .map { File(it) }
            .firstOrNull { it.exists() && it.canExecute() }
    }
}


private object BeaNReportFormatter {
    fun format(json: String): String {
        if (!json.trimStart().startsWith("{")) {
            return json
        }

        val family = stringField(json, "family")
        val layer = stringField(json, "layer")
        val formedStatus = stringField(json, "formed_status")
        val nextMove = stringField(json, "next_move")
        val primarySignal = stringField(json, "primary_signal")
        val blockedMove = stringField(json, "blocked_move")
        val evidence = evidenceLines(json)

        return buildString {
            appendLine("Bea-N case report")
            appendLine("Source: selected text only")
            appendLine("Mode: local read-only case formation")
            appendLine()
            appendLine("Family: $family")
            appendLine("Failure layer: $layer")
            appendLine("Formed status: $formedStatus")
            appendLine("Next move: $nextMove")
            appendLine()
            appendLine("Primary signal:")
            appendLine(primarySignal)
            appendLine()
            appendLine("Blocked move:")
            appendLine(blockedMove)
            appendLine()
            appendLine("Evidence:")
            if (evidence.isEmpty()) {
                appendLine("-")
            } else {
                evidence.forEach { appendLine("- $it") }
            }
        }.trimEnd()
    }

    fun promptPacket(json: String): String {
        return stringField(json, "prompt_packet").takeUnless { it == "-" } ?: json
    }

    private fun stringField(json: String, key: String): String {
        val pattern = Regex("\\\"$key\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        val raw = pattern.find(json)?.groupValues?.get(1) ?: return "-"
        return unescapeJsonString(raw)
    }

    private fun evidenceLines(json: String): List<String> {
        val keyIndex = json.indexOf("\"evidence_lines\"")
        if (keyIndex < 0) {
            return emptyList()
        }

        val arrayStart = json.indexOf('[', keyIndex)
        if (arrayStart < 0) {
            return emptyList()
        }

        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var escaped = false

        var index = arrayStart + 1
        while (index < json.length) {
            val ch = json[index]

            if (!inString && ch == ']') {
                break
            }

            if (inString) {
                if (escaped) {
                    current.append('\\')
                    current.append(ch)
                    escaped = false
                } else when (ch) {
                    '\\' -> escaped = true
                    '"' -> {
                        values.add(unescapeJsonString(current.toString()))
                        current.clear()
                        inString = false
                    }
                    else -> current.append(ch)
                }
            } else if (ch == '"') {
                inString = true
            }

            index += 1
        }

        return values
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}

private class BeaNResultDialog(
    project: Project?,
    private val reportText: String,
    private val promptPacket: String,
) : DialogWrapper(project) {
    init {
        title = "bea-n"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val textArea = JTextArea(reportText).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            rows = 22
            columns = 80
            caretPosition = 0
        }

        val copyButton = JButton("Copy Prompt Packet").apply {
            addActionListener {
                CopyPasteManager.getInstance().setContents(StringSelection(promptPacket))
            }
        }

        return JPanel(BorderLayout()).apply {
            add(JScrollPane(textArea), BorderLayout.CENTER)
            add(copyButton, BorderLayout.SOUTH)
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

}
