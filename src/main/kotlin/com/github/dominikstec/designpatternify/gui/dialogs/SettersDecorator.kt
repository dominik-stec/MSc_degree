package com.github.dominikstec.designpatternify.gui.dialogs

import ai.grazie.utils.capitalize
import com.github.dominikstec.designpatternify.gui.ActionAdapter
import com.github.dominikstec.designpatternify.gui.ActionDecorator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import javax.annotation.Nullable
import javax.swing.*

class SettersDecorator(canBeParent: Boolean, event: AnActionEvent, fieldList: ArrayList<String>, methodList: ArrayList<String>, className: String) : DialogWrapper(canBeParent) {

    val fieldList = fieldList
    val methodList = methodList
    val className = className
    val cboxFieldsHandlers: ArrayList<JCheckBox> = ArrayList()
    val fields: ArrayList<String> = ArrayList()
    val methods: ArrayList<String> = ArrayList()
    val event = event
    init {
        title = "Design Patternify -> Dekorator"
        init()
    }

    @Nullable
    override fun createCenterPanel(): JComponent {

        super.setOKButtonText("DALEJ")
        super.setCancelButtonText("anuluj")
        super.setUndecorated(true)

        val dialogPanel = JPanel(GridLayout(7,2, ))

        //label for fields
        val fieldLabel = JLabel("Jakie pola klasy ${className.capitalize()} będą dekorowane?")
        val breakLineFieldTop = JLabel("")
        breakLineFieldTop.preferredSize = Dimension(300, 10)
        val currentFont = fieldLabel.getFont()
        val boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        fieldLabel.setFont(boldFont)

        //fields label positioning
        fieldLabel.verticalAlignment = SwingConstants.TOP
        fieldLabel.horizontalAlignment = SwingConstants.LEFT
        dialogPanel.add(fieldLabel, BorderLayout.NORTH, -1)
        dialogPanel.add(breakLineFieldTop, BorderLayout.SOUTH, -1)

        //checkboxes for fields
        for(f in fieldList) {
            val cbox = JCheckBox(f, true)
            cbox.preferredSize = Dimension(15,15)
            cbox.verticalAlignment = SwingConstants.BOTTOM
            cbox.horizontalAlignment = SwingConstants.LEFT
            cboxFieldsHandlers.add(cbox)
            dialogPanel.add(cbox)
        }

        //<br/>
        val breakLineFieldBottom = JLabel("")
        breakLineFieldBottom.preferredSize = Dimension(300, 30)
        dialogPanel.add(breakLineFieldBottom, BorderLayout.SOUTH, -1)

        //<br/>
        val breakLineMethodBottom = JLabel("")
        breakLineMethodBottom.preferredSize = Dimension(300, 10)
        dialogPanel.add(breakLineMethodBottom, BorderLayout.SOUTH, -1)

        return dialogPanel
    }

    override fun doOKAction() {
        super.doOKAction()

        //filter selected checkboxes
        cboxFieldsHandlers.filter( { cbox -> cbox.isSelected } ).forEach( { cbox -> fields.add(cbox.text.substring(0, cbox.text.length) ) } )

        if(fields.isNotEmpty())
            MainDecorator(event,fields,methods,className).show()
        else {
            Messages.showMessageDialog(
                event.project,
                "Powtórz wybór pól w klasie ${className}.",
                "Nie zaznaczono żadnych pól",
                Messages.getInformationIcon()
            )
            ActionDecorator().actionPerformed(event)
        }
    }
}