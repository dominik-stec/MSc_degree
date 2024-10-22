package com.github.dominikstec.designpatternify.gui

import ai.grazie.nlp.utils.tokenizeByWhitespace
import ai.grazie.utils.capitalize
import com.github.dominikstec.designpatternify.gui.dialogs.MainDecorator
import com.github.dominikstec.designpatternify.gui.dialogs.SettersDecorator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import java.util.HashSet

class ActionDecorator : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {

        val psi: PsiFile? = event.getData(CommonDataKeys.PSI_FILE)

        var children: Array<PsiElement> = arrayOf()
        var accessClass = true
        var className = ""

        var execute = true
        try {
            children = psi!!.children
        } catch(exc: NullPointerException) {
            execute = false
        }

        //select class from project as parent for generation
        if(execute) {

            // can be class extend ?
            val ac = children.filter({ it.toString().contains("PsiClass:") })
                .map({ it.text.substring(0, it.text.indexOf("{")) })
                .filter({ (it.contains("final").not() && it.contains("private").not()) && it.contains("abstract").not()})

            accessClass = ac.isNotEmpty()

            //get class name
            children.filter({ it.toString().contains("PsiClass:") }).forEach({ className = it.toString().removePrefix("PsiClass:") })

            // class can be extended
            if(accessClass) {

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
                        if(l.equals("static") || l.equals("final") || l.equals("abstract")){
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
                val filteredArray = arrayListOf("")
                for(s in resultSetNoSemicolonNoEmptyNoSpaces) {
                    filteredArray.add(s)
                }
                filteredArray.remove("")

                if(filteredArray.isNotEmpty())
                SettersDecorator(true, event, filteredArray, arrayListOf(), className).show()
                else MainDecorator(event, arrayListOf(), arrayListOf(), className).show()

            //class can not be extended
            } else {

                Messages.showMessageDialog(event.project,
                    "Pominięto pola klasy w procesie generowania",
                    "Klasa ${className.capitalize()} nie może być użyta",
                    Messages.getInformationIcon())

                MainDecorator(event, arrayListOf(), arrayListOf(), className).show()
            }

        //without class selection as parent for generation
        } else {
            //run without checkbox form
            MainDecorator(event, arrayListOf(), arrayListOf(), className).show()
        }
    }

    private fun extractChildrenBy(
        children: Array<PsiElement>,
        fullFieldList: ArrayList<String>,
        key: String
    ) {
        children.filter { child -> child.toString().contains("PsiClass:") }
            .get(0).children
            .filter { f -> f.toString().contains(key) }
            .forEach { fullFieldList.add(it.text) }
    }

    private fun checkPrivateOrFinal(array: ArrayList<String>) {
        val onlyType = arrayListOf<String>()
        for (a in array) {
            if (a.contains("private") || a.contains("final")) {
                continue
            } else {
                var str = ""
                if (a.tokenizeByWhitespace().size > 2) {
                    var tokens = a.tokenizeByWhitespace()
                    tokens = tokens.takeLast(2)
                    str = tokens.joinToString(separator = " ")
                    onlyType.add(str)
                } else {
                    onlyType.add(a)
                }
            }
        }
        if (onlyType.isNotEmpty()) {
            array.clear()
            array.addAll(onlyType)
        }
    }

    private fun checkComments(
        fieldList: ArrayList<String>,
        array: ArrayList<String>
    ) {
        val multiLinesSlashComm = fieldList.filter({ l -> l.contains("//") })
            .toTypedArray()
        val strComm = ArrayList<String>()
        multiLinesSlashComm.forEach {
            strComm.add(
                it.split("\n")
                    .filter({ f -> f.contains("//").not() })
                    .first()
                    .trimStart()
            )
        }


        val fieldListSlashComm = fieldList.filter { r ->
            r.contains("//").not()
                    && r.contains("/*").not()
                    && r.contains("*/").not()
        }
            .toTypedArray()


        val fieldListBlockComm = fieldList.filter { r ->
            r.contains("/*")
                    || r.contains("*/")
        }
            .toTypedArray()
        var start = 0
        var end = 0
        val result = ArrayList<String>()
        fieldListBlockComm.forEach {
            start = it.indexOf("/*");
            end = it.indexOf("*/");
            if (start.equals(-1).not() && end.equals(-1).not()) {
                result.add(it.substring(end + 2, it.length).trimStart())
            }
        }

        if (fieldListSlashComm.isEmpty().not()) {
            array.addAll(fieldListSlashComm)
        }
        if (strComm.isEmpty().not()) {
            array.addAll(strComm)
        }
        if (result.isEmpty().not()) {
            array.addAll(result)
        }
    }
}

