package com.github.dominikstec.designpatternify.gui

import ai.grazie.nlp.utils.tokenizeByWhitespace
import com.github.dominikstec.designpatternify.gui.dialogs.MainObserver
import com.github.dominikstec.designpatternify.gui.dialogs.SettersObserver
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import java.util.HashSet


class ActionObserver : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {

        val psi: PsiFile? = event.getData(CommonDataKeys.PSI_FILE)

        var filteredArray = arrayOf<String>()
        var children: Array<PsiElement> = arrayOf()
        var className = ""
        var execute = true
        try {
            children = psi!!.children
        } catch(exc: NullPointerException) {
            execute = false
        }
        if(execute) {

            //get setters class reference
            val psi = children.filter { child -> child.toString().contains("PsiClass:") }
                .get(0).children.filter { f ->  f.toString().contains("PsiField:") }


            //get all types of fields without comments with duplicate elimination
            val set = HashSet<String>()
            for(p in psi) {
                for(field in p.childrenOfType<PsiElement>()) {
                    set.add(p.originalElement.text)
                }
            }

            //bug hotfix: if comment appears in results then delete it
            val hotfixSet = HashSet<String>()
            for(item in set) {
                var part = listOf<String>()
                if(item.contains("//")) {
                    part = item.split(";")
                } else if(item.contains("/*")) {
                    part = item.split("*/")
                }
                if(part.isEmpty().not()) {
                    for(p in part) {
                        if(p.contains("//").not() && p.contains("/*").not()) {
                            hotfixSet.add(p)
                        }
                    }
                } else {
                    hotfixSet.add(item)
                }
            }

            //tokenize full field declaration to partial words
            val listOfList: List<List<String>>
            listOfList = listOf(listOf())
            val tokenSet = HashSet<List<String>>()
            for(s in hotfixSet) {
                val token = s.tokenizeByWhitespace()
                tokenSet.add(token)
                listOfList.plus(token)
            }

            //extract only type and name from field declaration
            val resultSet = HashSet<String>()
            var element: String
            var i: Int
            for(token in tokenSet) {
                element = ""
                i = 0
                for(l in token) {
                    if(i==2) break
                    if(l.equals("static") || l.equals("final")) {
                        continue
                    }
                    if(l.equals("public") || l.equals("protected") || l.equals("private")) {
                        continue
                    }
                    element += l
                    if(i<1) element += " "
                    i++
                }
                resultSet.add(element)
            }

            //remove semicolons
            var newStr = ""
            val resultSetNoSemicolon = HashSet<String>()
            for(s in resultSet) {
                println("TYPE AND FIELD: " + s)
                if(s.contains(";")) {
                    newStr = s.replace(";", "")
                    resultSetNoSemicolon.add(newStr)
                } else {
                    resultSetNoSemicolon.add(s)
                }
            }

            //remove empty strings
            val resultSetNoSemicolonNoEmpty = HashSet<String>()
            for(item in resultSetNoSemicolon) {
                if(item.isEmpty().not())
                    resultSetNoSemicolonNoEmpty.add(item)
            }

            //remove multiple whitespaces from string
            val resultSetNoSemicolonNoEmptyNoSpaces = HashSet<String>()
            for(item in resultSetNoSemicolonNoEmpty) {
                val temp = item.replace("\\s{2,}".toRegex(), "")
                resultSetNoSemicolonNoEmptyNoSpaces.add(temp)
            }

            //convert set to list
            filteredArray = resultSetNoSemicolonNoEmptyNoSpaces.toTypedArray()

            //get class name
            children.filter({ it.toString().contains("PsiClass:") }).forEach({ className = it.toString().removePrefix("PsiClass:") })

        }

        //setter activation or not if setters are empty
        if(filteredArray.isEmpty()) {
            MainObserver(event, arrayListOf()).show()
        } else {
            SettersObserver(true, event, filteredArray, className).show()
        }
    }

}

