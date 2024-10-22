package com.github.dominikstec.designpatternify.gui.dialogs

import ai.grazie.utils.capitalize
import com.github.dominikstec.designpatternify.gui.ActionObserver
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import java.awt.*
import javax.annotation.Nullable
import javax.swing.*


class SettersObserver(canBeParent: Boolean, event: AnActionEvent, fieldList: Array<String>, className: String) : DialogWrapper(canBeParent) {

    val fieldList = fieldList
    val cboxHandlers: ArrayList<JCheckBox> = ArrayList()
    val fields: ArrayList<String> = ArrayList()
    val className = className
    val event = event
    init {
        title = "Design Patternify -> Obserwator"
        init()
    }

    @Nullable
    override fun createCenterPanel(): JPanel {

        super.setOKButtonText("DALEJ")
        super.setCancelButtonText("anuluj")
        super.setUndecorated(true)

            val dialogPanel = JPanel(GridLayout(7,4, ))

            val label = JLabel("Jakie pola klasy ${className.capitalize()} będą obserwowane?")
            label.setSize(300, 100)
            label.verticalAlignment = 0
            label.horizontalAlignment = 0
            val currentFont = label.getFont()
            val boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
            label.setFont(boldFont)
            val breakLine = JLabel("")
            breakLine.preferredSize = Dimension(300, 20)

            label.preferredSize = Dimension(300, 30)
            dialogPanel.add(label, BorderLayout.NORTH,-1)

            dialogPanel.add(breakLine, BorderLayout.SOUTH, -1)

            for(f in fieldList) {
                val cbox = JCheckBox(f, true)
                cbox.preferredSize = Dimension(15,15)
                cbox.verticalAlignment = SwingConstants.BOTTOM
                cbox.horizontalAlignment = SwingConstants.LEFT
                cboxHandlers.add(cbox)
                dialogPanel.add(cbox, BorderLayout.SOUTH, -1)
            }
            return dialogPanel
    }

    override fun doOKAction() {
            super.doOKAction()

            cboxHandlers.filter( { cbox -> cbox.isSelected } ).forEach( { cbox -> fields.add(cbox.text) } )

        if(fields.isNotEmpty())
            MainObserver(event,fields).show()
        else {
            Messages.showMessageDialog(
                event.project,
                "Powtórz wybór pól w klasie ${className}.",
                "Nie zaznaczono żadnych pól",
                Messages.getInformationIcon()
            )
            ActionObserver().actionPerformed(event)
        }
    }
}