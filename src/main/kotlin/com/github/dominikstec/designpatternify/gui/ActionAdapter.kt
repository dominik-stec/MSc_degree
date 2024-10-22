package com.github.dominikstec.designpatternify.gui

import ai.grazie.nlp.utils.tokenizeByWhitespace
import ai.grazie.utils.capitalize
import com.github.dominikstec.designpatternify.gui.dialogs.MainDecorator
import com.github.dominikstec.designpatternify.gui.dialogs.SettersAdapter
import com.github.dominikstec.designpatternify.gui.dialogs.SettersDecorator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.PsiParameterizedCachedValue
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.intellij.spellchecker.tokenizer.PsiIdentifierOwnerTokenizer
import kotlinx.coroutines.processNextEventInCurrentThread
import java.lang.reflect.Parameter
import kotlin.collections.HashSet

class ActionAdapter : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {

        val psi: PsiFile? = event.getData(CommonDataKeys.PSI_FILE)

        var children: Array<PsiElement> = arrayOf()
        var accessClass = true
        var className = ""

        var execute = true
        try {
            children = psi!!.children
        } catch (exc: NullPointerException) {
            execute = false
        }

        //select class from project as parent for generation
        //class must be selected
        if (execute) {

            // can be class adaptive ?
            val ac = children.filter({ it.toString().contains("PsiClass:") })
                .map({ it.text.substring(0, it.text.indexOf("{")) })
                .filter({ (it.contains("private").not()) && (it.contains("protected").not()) && it.contains("abstract").not()})

            accessClass = ac.isNotEmpty()

            //get class name
            children.filter({ it.toString().contains("PsiClass:") })
                .forEach({ className = it.toString().removePrefix("PsiClass:") })

            // class can be adaptive
            if (accessClass) {

                //get setters class reference
                val psi = children.filter { child -> child.toString().contains("PsiClass:") }
                    .get(0).children.filter { f -> f.toString().contains("PsiMethod:") }


                //get all types of fields without comments with duplicate elimination
                val set = HashSet<String>()
                for (p in psi) {
                    for (method in p.childrenOfType<PsiElement>()) {
                        set.add(p.originalElement.text)
                    }
                }

                //get all types of PSI fields without comments with duplicate elimination
                val setPsi = HashSet<PsiElement>()
                for (p in psi) {
                    for (method in p.childrenOfType<PsiElement>()) {
                        setPsi.add(p.originalElement)
                    }
                }

//                2024-06-17T16:52:01.573+0200 [QUIET] [system.out] PSI ch: PsiIdentifier:getVal1
//                2024-06-17T16:52:01.573+0200 [QUIET] [system.out] PSI ch: PsiParameterList:(String val2, String val3)
                //get list of PSI element with PsiIdentifier:getVal1
                //and PsiParameterList:(String val2, String val3)
                val methodNameArgsMap = LinkedHashMap<String, String>()
                for(p in setPsi) {
                    val word = p.childrenOfType<PsiElement>()
                    var splitName = ""
                    var splitArgs = ""
                    for(w in word) {
                        val word = w.originalElement.toString()
                        if(word.contains("PsiIdentifier")) {
                            splitName = word.split(":").get(1)
                        }
                        else if(word.contains("PsiParameterList")) {
                            splitArgs = word.split(":").get(1)
                        }
                        if(splitName != "" && splitArgs != "") {
                            methodNameArgsMap[splitName] = splitArgs
                            splitName = ""
                            splitArgs = ""
                        }
                    }
                }


                //bug hotfix: if comment appears in results then delete it
                val hotfixSet = HashSet<String>()
                for (item in set) {
                    var part = listOf<String>()
                    if (item.contains("//")) {
                        part = item.split(";")
                    } else if (item.contains("/*")) {
                        part = item.split("*/")
                    }
                    if (part.isEmpty().not()) {
                        for (p in part) {
                            if (p.contains("//").not() && p.contains("/*").not()) {
                                hotfixSet.add(p)
                            }
                        }
                    } else {
                        hotfixSet.add(item)
                    }
                }

                //substring methods key words declaration
                val splitMethods = HashSet<String>()
                for(s in hotfixSet) {
                    val begin = 0
                    val end  = s.indexOf("{")
                    try{
                        val split = s.substring(begin, end)
                        if(split.contains("private") ||
                            split.contains("protected") ||
                                split.contains("static")) {
                            continue
                        } else {
                            splitMethods.add(split)
                        }
                    } catch (exc: IndexOutOfBoundsException) {
                        continue
                    }

                }

                //remove semicolons
                var newStr = ""
                val resultSetNoSemicolon = HashSet<String>()
                for (s in splitMethods) {
                    if (s.contains(";")) {
                        newStr = s.replace(";", "")
                        resultSetNoSemicolon.add(newStr)
                    } else {
                        resultSetNoSemicolon.add(s)
                    }
                }

                //remove empty strings
                val resultSetNoSemicolonNoEmpty = HashSet<String>()
                for (item in resultSetNoSemicolon) {
                    if (item.isEmpty().not())
                        resultSetNoSemicolonNoEmpty.add(item)
                }

                //remove multiple whitespaces from string
                val resultSetNoSemicolonNoEmptyNoSpaces = HashSet<String>()
                for (item in resultSetNoSemicolonNoEmpty) {
                    val temp = item.replace("\\s{2,}".toRegex(), " ")
                    resultSetNoSemicolonNoEmptyNoSpaces.add(temp)
                }

                //map method name for its arguments
                val methodNameArgsFilterMap = LinkedHashMap<String, String>()
                for(r in resultSetNoSemicolonNoEmptyNoSpaces) {
                    methodNameArgsMap.filter {

                        val concat = it.key + it.value
                        r.contains(concat)

                    }.forEach {
                        methodNameArgsFilterMap[it.key] = it.value
                    }
                }

                //convert set to list
                val filteredArray = arrayListOf("")
                for (s in resultSetNoSemicolonNoEmptyNoSpaces) {
                    filteredArray.add(s)
                }
                filteredArray.remove("")

                if (filteredArray.isNotEmpty())
                    SettersAdapter(true, event,arrayListOf(),filteredArray, className, methodNameArgsFilterMap).show()
                else {
                    //notification if methods in class was not point
                    Messages.showMessageDialog(
                        event.project,
                        "Klasa ${className} nie może być adaptowana.",
                        "Brak właściwych metod w klasie",
                        Messages.getInformationIcon()
                    )
                }

            } else {

                //notification if class was not properly
                Messages.showMessageDialog(
                    event.project,
                    "Klasa ${className} nie może być adaptowana.",
                    "Nie wskazano właściwej klasy",
                    Messages.getInformationIcon()
                )

            }
        } else {
            //notification if class was not point
            Messages.showMessageDialog(
                event.project,
                "Wskaż klasę do adaptowania.",
                "Nie wskazano klasy",
                Messages.getInformationIcon()
            )
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
