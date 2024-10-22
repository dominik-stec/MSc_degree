package com.github.dominikstec.designpatternify.gui

import com.github.dominikstec.designpatternify.gui.dialogs.MainFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ActionFactory : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        MainFactory(event).show()
    }
}