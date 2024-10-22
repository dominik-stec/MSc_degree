package com.github.dominikstec.designpatternify.gui.dialogs

import ai.grazie.nlp.utils.dropWhitespaces
import ai.grazie.nlp.utils.tokenizeByWhitespace
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBTextField
import com.intellij.util.IncorrectOperationException
import java.awt.*
import java.util.*
import javax.annotation.Nullable
import javax.swing.*
import javax.swing.border.Border
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap


    public class MainAdapter(event: AnActionEvent, fieldList: ArrayList<String>, methodList: ArrayList<String>, className: String, argsMap: LinkedHashMap<String, String>): DialogWrapper(true) {

        //from setter
        val e = event
        val setterFields = fieldList
        val setterMethods = methodList
        val setterClassName = className

        //for maintain package source path
        var okPathClick = true
        var runCancel = false

        //methods map from setter
        var methodArgsMap = argsMap
        //mapped methods from dialog
        var inputTextHandlers: ArrayList<JBTextField> = ArrayList()
        //name of class and interface from dialog
        var inputAdapterNamesHandler: LinkedHashMap<String, JBTextField> = LinkedHashMap()

        //static
        companion object {
            var sourcePath: String = ""
            var iAdapter: String = ""
            var adapter: String = ""
            var methods: String = ""
            var dialogPanel = JPanel()
            var isMethodMappingMiss = true
        }

        init{
            init()
            title = "Design Patternify -> Adapter"
            //store methods map from setter when init
            methodArgsMap = argsMap
            iAdapter = getAdapterName()
        }

        @Nullable
        override fun createCenterPanel(): JPanel {

            super.setOKButtonText("GENERUJ")
            super.setCancelButtonText("anuluj")
            super.setUndecorated(true)

            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()

            //layout type and dimensions
            val rowSize = methodArgsMap.size + 3
            dialogPanel = JPanel(GridLayout(rowSize, 3, 20,20))
            dialogPanel.size = Dimension(400,300)

            // Create a border
            val grayBorder: Border = BorderFactory.createLineBorder(Color(255, 255, 255), 15)
            dialogPanel.border = grayBorder

            //path button
            val btnLabel = JLabel("Gdzie umieścić kod wzorca?")
            val btnLabelBold = JLabel(">>   Wybierz pakiet źródłowy:")
            // Get the current font or set a default one
            var currentFont: Font = btnLabelBold.getFont()
            var boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
            // Apply the bold font to the label
            btnLabelBold.setFont(boldFont)
            dialogPanel.add(btnLabel)
            dialogPanel.add(btnLabelBold)

            val btn = JButton(">> Wybór Pakietu <<")
            btn.addActionListener {
                var rootPath = ""
                val descriptor = FileChooserDescriptor(
                    false,
                    true,
                    false,
                    false,
                    false,
                    false
                )
                var path: String
                try {
                    path = FileChooser.chooseFile(descriptor, e.project!!, root)!!.path
                } catch (ex: Exception) {
                    path = ""
                    okPathClick = false
                }
                if(path != "") runCancel = true
                val start = path.indexOf("src/")
                if (start == -1) rootPath = ""
                val end = path.length
                if (start != -1) rootPath = path.substring(start + 4, end)
                if (rootPath.contains("/")) {
                    rootPath = rootPath.replace("/", ".", true)
                }
                sourcePath = rootPath
                if (rootPath != "") notificationInfo(e.project!!, "Wybrano pakiet: " + rootPath)
            }

            dialogPanel.add(btn)

            //text field for adapter interface
            var txtLabel = JLabel("Jaka ma się nazywać interfejs adaptera?")
            var txtLabelBold = JLabel(">>   Podaj nazwę interfejsu:")
            // Get the current font or set a default one
            currentFont = txtLabelBold.getFont()
            boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
            // Apply the bold font to the label
            txtLabelBold.setFont(boldFont)
            dialogPanel.add(txtLabel)
            dialogPanel.add(txtLabelBold)
            var adapterInterfaceField = JBTextField()
            currentFont = adapterInterfaceField.getFont()
            boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
            // Apply the bold font to the label
            adapterInterfaceField.setFont(boldFont)
            // Request focus for the text field
            SwingUtilities.invokeLater({ adapterInterfaceField.requestFocusInWindow()})

            //handle adapter interface name
            inputAdapterNamesHandler["interface"] = adapterInterfaceField

            dialogPanel.add(adapterInterfaceField)

            //text field for adapter interface
            txtLabel = JLabel("Jak ma się nazywać adapter?")
            txtLabelBold = JLabel(">>   Podaj nazwę klasy:")
            // Get the current font or set a default one
            currentFont = txtLabelBold.getFont()
            boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
            // Apply the bold font to the label
            txtLabelBold.setFont(boldFont)
            dialogPanel.add(txtLabel)
            dialogPanel.add(txtLabelBold)
            var adapterField = JBTextField()
            currentFont = adapterField.getFont()
            boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
            // Apply the bold font to the label
            adapterField.setFont(boldFont)

            //handle adapter name
            inputAdapterNamesHandler["class"] = adapterField

            dialogPanel.add(adapterField)

            //extract type of return value from method
            val idxArr: ArrayList<Int> = ArrayList()
            for(s in setterMethods) {
                val part = s.tokenizeByWhitespace()
                var idx = 0
                for (p in part) {
                    if(p.contains("("))
                        break
                    idx++
                }
                idxArr.add(idx-1)
            }
            val methodTypeArr: ArrayList<String> = ArrayList()
            var t = 0
            setterMethods.forEach{ methodTypeArr.add(it.tokenizeByWhitespace().get(idxArr[t]).dropWhitespaces()); t++ }

            var i = 0
            methodArgsMap.forEach {

                //text field for adapter interface
                txtLabel = JLabel("Jaka metoda ma być adapterem dla:")
                txtLabelBold = JLabel(">>   ${methodTypeArr[i]} ${it.key}${it.value}   >>")
                // Get the current font or set a default one
                currentFont = txtLabelBold.getFont()
                boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
                // Apply the bold font to the label
                txtLabelBold.setFont(boldFont)
                dialogPanel.add(txtLabel)
                dialogPanel.add(txtLabelBold)

                // text field declaration
                adapterField = JBTextField()
                currentFont = adapterField.getFont()
                boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
                // Apply the bold font to the label
                adapterField.setFont(boldFont)

                // adapter text fields handler
                inputTextHandlers.add(adapterField)

                dialogPanel.add(adapterField)
                i++
            }

            dialogPanel.background = Color(255, 255, 255)

            return dialogPanel
        }

    fun getJBTextFieldMappedMethodsArray(): ArrayList<JBTextField> {
        return inputTextHandlers
    }
        fun getInterfaceAdapterName(): String {
            return inputAdapterNamesHandler.get("interface")!!.getText()
        }
        fun getAdapterName(): String {
            return inputAdapterNamesHandler.get("class")!!.getText()
        }

        override fun doOKAction() {

            //set text fields values from dialog
            iAdapter = getInterfaceAdapterName() ?: ""
            adapter = getAdapterName() ?: ""

            //check if files are unique
            var isFileIAdapterUnique = true
            isFileIAdapterUnique = checkFileNameDuplication(e.project!!, sourcePath, iAdapter)
            var isFileAdapterUnique = true
            isFileAdapterUnique= checkFileNameDuplication(e.project!!, sourcePath, adapter)

            isMethodMappingMiss = true
            for(i in getJBTextFieldMappedMethodsArray()) {
                if(i.getText() == "") {
                    isMethodMappingMiss = true
                    break
                } else {
                    isMethodMappingMiss = false
                }
            }

            val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)
            val rootPsi: PsiFile? = e.getData(CommonDataKeys.PSI_FILE)

            //interface name is empty
            if (iAdapter == "") {

                notificationWarn(
                    project,
                    "Wpisz nazwę interfejsu."
                )

                //adapter class name is empty
            } else if (adapter == "") {

                notificationWarn(
                    project,
                    "Wpisz nazwę adaptera."
                )

                //mapping is empty
            } else if (isMethodMappingMiss) {

                notificationWarn(
                    project,
                    "Podaj mapowanie metod."
                )

                //file exist
            } else if(isFileIAdapterUnique.not()) {

                notificationWarn(
                    project,
                    "Plik ${iAdapter}.java już istnieje. Zmień nazwę interfejsu."
                )

                //file exist
            } else if(isFileAdapterUnique.not()) {

                notificationWarn(
                    project,
                    "Plik ${adapter}.java już istnieje. Zmień nazwę adaptera."
                )

            } else {

                //confirm dialog
                super.doOKAction()

                //create files
                createFiles(project, rootPsi)
                //create content
                createInterfaceContent(project)
                createAdapterContent(project)

                sourcePath = ""
                iAdapter = ""
                adapter = ""
                isMethodMappingMiss = true
                methodArgsMap.clear()

                notificationWarn(project, "Dodano pliki do projektu.")

            }
        }

        private fun checkFileNameDuplication(project: Project, sourcePath: String, fileName: String): Boolean {

            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
            var dir = PsiManager.getInstance(project).findDirectory(root)

            //if packages are nested
            if(sourcePath.contains(".")) {

                val singlePackageList = sourcePath.split(".")
                //reset directory to project root position
                dir = PsiManager.getInstance(project).findDirectory(root)

                //map packages path
                for (s in singlePackageList) {
                    try {
                        //go to nested packages
                        dir = dir!!.findSubdirectory(s)
                    } catch (exc: Exception) {
                        notificationError(project, "Nie znaleziono pakietu: ${s}")
                    }
                }
                //try to find a file
                try {
                    dir!!.findFile("${fileName}.java") ?: throw Exception()
                } catch(exc: Exception) {
                    return true
                }

            //if source path is project root
            } else if(sourcePath == "") {

                try {
                    //reset directory to project root position
                    dir = PsiManager.getInstance(project).findDirectory(root)

                } catch (exc: Exception) {
                    notificationError(project, "Nie znaleziono ścieżki")
                }
                //try to find a file
                try {
                    dir!!.findFile("${fileName}.java") ?: throw Exception()
                } catch(exc: Exception) {
                    return true
                }

            //if only one package
            } else {
                try {
                    //reset directory to project root position
                    dir = PsiManager.getInstance(project).findDirectory(root)
                    //go to separate layer package
                    dir = dir!!.findSubdirectory(sourcePath)

                } catch (exc: Exception) {
                    notificationError(project, "Nie znaleziono pakietu: ${sourcePath}")
                }
                //try to find a file
                try {
                    dir!!.findFile("${fileName}.java") ?: throw Exception()
                } catch(exc: Exception) {
                    return true
                }
            }
            return false
        }


        private fun createFiles(project: Project, rootPsi: PsiFile?) {

            val createFiles = Runnable {

                //access to write files in project
                ApplicationManager.getApplication().assertWriteAccessAllowed()

                //get project root directory path
                val manager = ModuleManager.getInstance(e.project!!)
                val modules: Array<Module> = manager.modules
                val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
                var dir = PsiManager.getInstance(project).findDirectory(root)

                //package with subpackages for files was select
                if (sourcePath != "") {

                    if (sourcePath.contains(".")) {

                        val path = sourcePath.split(".")

                        try {
                            for (subDir in path) {
                                //reference to packages path
                                dir = dir!!.findSubdirectory(subDir)
                            }
                            //create abstract for decorators for each level
                            dir!!.createFile("${iAdapter}.java")
                            dir!!.createFile("${adapter}.java")

                        } catch (exc: IncorrectOperationException) {
                            notificationError(project, "Nie utworzono wszystkich plików w ${sourcePath}.")
                        }
                        //single packcage for files was select
                    } else {
                        try {
                            //reference to packages path
                            dir = dir!!.findSubdirectory(sourcePath)

                            //create abstract for decorators for each level
                            dir!!.createFile("${iAdapter}.java")
                            dir!!.createFile("${adapter}.java")

                        } catch (exc: IncorrectOperationException) {
                            notificationError(project, "Nie utworzono wszystkich plików w ${sourcePath}.")
                        }
                    }

                    //package was not selected
                    // generate in project root path
                } else {
                    try {
                        //reset directory to project root position
                        dir!!.createFile("${iAdapter}.java")
                        dir!!.createFile("${adapter}.java")

                    } catch (exc: IncorrectOperationException) {
                        notificationError(project, "Nie utworzono wszystkich plików.")
                    }
                }
            }
            WriteCommandAction.runWriteCommandAction(project, createFiles)
        }

        private fun createInterfaceContent(project: Project) {
            val addFileContent = Runnable {
                ApplicationManager.getApplication().assertWriteAccessAllowed()

                methods = ""
                val methodList = getJBTextFieldMappedMethodsArray()
                var i = 0
                for(m in methodList) {
                    if(methodList.size == 1) {
                        methods = m.getText()
                    } else {
                        if(i == methodList.size -1) {
                            methods+="${m.getText()}"
                        } else {
                            methods+="${m.getText()}\n"
                        }
                    }
                    i++
                }

                var path = ""
                if(sourcePath != "") path = "${sourcePath}."


                //for first line abstract class
                var code = """"""
                if(sourcePath == "") {
                    code = """
                        public interface ${iAdapter} {
                            void methods();
                        }
                    """.trimIndent()
                }
                //for next lines of abstract classes
                else code = """
                        package ${sourcePath};

                        public interface ${iAdapter} {
                            void methods();
                        }
                    """.trimIndent()

                val method: List<String>
                //if more then 1 method
                if(methods.contains("\n")) {
                    method = methods.split("\n")
                }
                //if only one method
                else {
                    method = listOf("")
                }

                var toAddCode = ""

                //if more then 1 method
                if(method[0] != "") {
                    var i = 0
                    for(m in method) {
                        //first iter
                        if(i==0) {
                            toAddCode += ("public " + m + ";\n")
                            toAddCode.trimIndent()
                            //iter between first and last
                        } else if(i<method.size-1) {
                            toAddCode += ("\t" + "public " +  m + ";\n")
                            toAddCode.trimIndent()
                            //last iter
                        } else {
                            toAddCode += ("\t" + "public " + m + ";")
                            toAddCode.trimIndent()
                        }
                        i++
                    }
                    //if only one method
                } else {
                    toAddCode += ("public " + methods + ";")
                    toAddCode.trimIndent()
                }
                code = code.replace("void methods();", toAddCode, true)


                //get project root directory path
                val manager = ModuleManager.getInstance(e.project!!)
                val modules: Array<Module> = manager.modules
                val root = ModuleRootManager.getInstance(modules.first()).sourceRoots


                //convert source path format for find as psi file
                var slashPath: String
                if(sourcePath.contains(".")) {
                    slashPath = sourcePath.replace(".", "/", true)
                    slashPath = "/${slashPath}"
                } else if(sourcePath.contains(".").not() && sourcePath != ""){
                    slashPath = "/${sourcePath}"
                } else {
                    slashPath = sourcePath
                }

                //get file from relative to root directory path
                val dir = PsiManager.getInstance(project).findFile(root.first().findFileByRelativePath(slashPath + "/"  + iAdapter + ".java")!!)
                val uniqueVirtualFile = dir!!.containingFile.virtualFile
                val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

                //write to file
                documentFile!!.insertString(0, code)

            }
            WriteCommandAction.runWriteCommandAction(project, addFileContent)
        }

        private fun createAdapterContent(project: Project) {
            val addFileContent = Runnable {
                ApplicationManager.getApplication().assertWriteAccessAllowed()


                var path = ""
                if (sourcePath != "") path = "${sourcePath}."


                //for first line abstract class
                var code = """"""
                if (sourcePath == "") {
                    code = """
                        public class ${adapter} implements ${iAdapter} {
                            private ${setterClassName} ${toCamelCase(setterClassName)};
                            
                            public ${adapter}(${setterClassName} ${toCamelCase(setterClassName)}) {
                                this.${toCamelCase(setterClassName)} = ${toCamelCase(setterClassName)};
                            }
                            
                            public interfaceMethods();
                        }
                    """.trimIndent()
                }
                //for next lines of abstract classes
                else code = """
                        package ${sourcePath};
                
                        public class ${adapter} implements ${iAdapter} {
                            private ${setterClassName} ${toCamelCase(setterClassName)};
                            
                            public ${adapter}(${setterClassName} ${toCamelCase(setterClassName)}) {
                                this.${toCamelCase(setterClassName)} = ${toCamelCase(setterClassName)};
                            }
                            
                            public interfaceMethods();
                        }
                    """.trimIndent()


                val method: List<String>
                //if more then 1 method
                if (methods.contains("\n")) {
                    method = methods.split("\n")
                }
                //if only one method
                else {
                    method = listOf("")
                }

                val names: ArrayList<String> = ArrayList()
                val args: ArrayList<String> = ArrayList()
                for(m in methodArgsMap) {
                    names.add(m.key)
                    args.add(m.value)
                }

                //extract methods arguments names
                val argNameList: ArrayList<String> = ArrayList()
                argNameList.clear()
                for(a in args) {
                    if(a.dropWhitespaces().length == 2) {
                        argNameList.add("()")
                    } else {
                        var r = a.replace("(", "")
                        r = r.replace(")", "")
                        var argName = "("
                        if(r.contains(",")) {
                            val multi = r.split(",")
                            var i = 0
                            for(m in multi) {
                                if(i == multi.size -1)
                                    argName += m.tokenizeByWhitespace().get(1).dropWhitespaces()
                                else argName += m.tokenizeByWhitespace().get(1).dropWhitespaces() + ","
                                i++
                            }
                        } else {
                            var p = a.tokenizeByWhitespace().get(1)
                            p = p.replace(")", "")
                            argName += p.dropWhitespaces()
                        }
                        argName += ")"
                        argNameList.add(argName)
                    }
                }

                var toAddCode = """"""

                //if more then 1 method
                if (method[0] != "") {
                    var i = 0
                    for (m in method) {
                        //without return statement
                        if(m.contains("void")) {
                            //first iter
                            if (i == 0) {
                                toAddCode += "@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}${toCamelCase(setterClassName)}.${names[i]}${argNameList[i]};
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //iter between first and last
                            } else if (i < method.size - 1) {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}${toCamelCase(setterClassName)}.${names[i]}${argNameList[i]};
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //last iter
                            } else {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}${toCamelCase(setterClassName)}.${names[i]}${argNameList[i]};
                            |${"\t"}}
                        """.trimMargin())
                                toAddCode.trimIndent()
                            }
                            i++
                        //with return statement
                        } else {
                            //first iter
                            if (i == 0) {
                                toAddCode += "@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}return ${toCamelCase(setterClassName)}.${names[i]}${argNameList[i]};
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //iter between first and last
                            } else if (i < method.size - 1) {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}return ${toCamelCase(setterClassName)}.${names[i]}${argNameList[i]};
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //last iter
                            } else {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}return ${toCamelCase(setterClassName)}.${names[i]}${argNameList[i]};
                            |${"\t"}}
                        """.trimMargin())
                                toAddCode.trimIndent()
                            }
                            i++
                        }
                    }
                    //if only one method
                } else {
                    //without return statement
                    if(methods.contains("void")) {
                        toAddCode += "@Override\n"
                        toAddCode += ("\tpublic $methods" + """ {
                            |   ${"\t\t"}${toCamelCase(setterClassName)}.${names[0]}${argNameList[0]};
                            |${"\t"}}
                        """.trimMargin() + "\n")
                        toAddCode.trimIndent()
                    //with return statement
                    } else {
                        toAddCode += "@Override\n"
                        toAddCode += ("\tpublic $methods" + """ {
                            |   ${"\t\t"}return ${toCamelCase(setterClassName)}.${names[0]}${argNameList[0]};
                            |${"\t"}}
                        """.trimMargin() + "\n")
                        toAddCode.trimIndent()
                    }

                }
                code = code.replace("public interfaceMethods();",  toAddCode, true)

                //get project root directory path
                val manager = ModuleManager.getInstance(e.project!!)
                val modules: Array<Module> = manager.modules
                val root = ModuleRootManager.getInstance(modules.first()).sourceRoots


                //convert source path format for find as psi file
                var slashPath: String
                if (sourcePath.contains(".")) {
                    slashPath = sourcePath.replace(".", "/", true)
                    slashPath = "/${slashPath}"
                } else if (sourcePath.contains(".").not() && sourcePath != "") {
                    slashPath = "/${sourcePath}"
                } else {
                    slashPath = sourcePath
                }

                //get file from relative to root directory path
                val dir = PsiManager.getInstance(project)
                    .findFile(root.first().findFileByRelativePath(slashPath + "/" + adapter + ".java")!!)
                val uniqueVirtualFile = dir!!.containingFile.virtualFile
                val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

                //write to file
                documentFile!!.insertString(0, code)

            }
            WriteCommandAction.runWriteCommandAction(project, addFileContent)

        }

        private fun cutDeclarationOfMethod(method: List<String>): Any {
            for(m in method) {
                var list = m.tokenizeByWhitespace()

                //if space between declaration and bracket then join it
                var right = -1
                var left = -1
                for(l in list) {
                    if(l.startsWith("(")) {
                        right = list.indexOf(l)
                        left = right - 1
                    }
                }
                if(left!=-1 && right!=-1) {
                    val temp: List<String> = listOf()
                    val concat = list.get(left) + list.get(right)
                    list.filter { it -> it.equals(list.get(left)).not() }
                        .filter { it -> it.equals(list.get(right)).not() }
                        .forEach {temp.plus(it)}
                    temp.plus(concat)
                }

                //if

                if(list.size==0)
                    if(list.contains("public")) {

                    }
                for(l in list) {

                }
            }
            return 0
        }

        private fun toCamelCase(pascalCase: String): String {
            val strList = pascalCase.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val lowerCaseStr = strList[0].lowercase(Locale.getDefault())
            strList[0] = lowerCaseStr
            val camelCaseStr = java.lang.String.join("", *strList)
            return camelCaseStr
        }

        override fun doCancelAction() {
            super.doCancelAction()

            if (okPathClick && runCancel) {

                val rmDir = Runnable {

                    ApplicationManager.getApplication().assertWriteAccessAllowed()

                    try {
                        //global scope of variables
                        val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)

                        //get project root directory path
                        val manager = ModuleManager.getInstance(project)
                        val modules: Array<Module> = manager.modules
                        val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
                        var dir = PsiManager.getInstance(e.project!!).findDirectory(root)


                        //find package
                        //delete only new package path
                        //don't delete existing files and packages
                        if (sourcePath.contains(".")) {
                            var packageNameList = sourcePath.split(".")
                            var i = packageNameList.size
                            while (i > 0) {
                                var dir = PsiManager.getInstance(e.project!!).findDirectory(root)
                                packageNameList.forEach {
                                    dir = dir!!.findSubdirectory(it)
                                }
                                packageNameList = packageNameList.subList(0, packageNameList.lastIndex)
                                val isDirEmptyFromFiles = dir!!.files.isEmpty()
                                val isDirEmptyFromPackages = dir!!.subdirectories.isEmpty()
                                if (isDirEmptyFromFiles && isDirEmptyFromPackages)
                                    dir!!.delete()
                                i--
                            }

                        } else {
                            dir = dir!!.findSubdirectory(sourcePath)
                            val isDirEmptyFromFiles = dir!!.files.isEmpty()
                            val isDirEmptyFromPackages = dir!!.subdirectories.isEmpty()
                            if (isDirEmptyFromFiles && isDirEmptyFromPackages)
                                dir!!.delete()

                        }
                    }catch (exc: Exception) {

                        notificationError(e.project!!, "Błąd podczas wycofywania zmian z projektu")
                    }

                }

                WriteCommandAction.runWriteCommandAction(e.project!!, rmDir)

                sourcePath = ""
                iAdapter = ""
                adapter = ""


                notificationInfo(e.project!!, "Anulowano generowanie plików.")
            }

        }

        private fun notificationInfo(project: Project, text: String) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("DesignPatternify.NotificationGroup")
                .createNotification(
                    text,
                    NotificationType.INFORMATION
                )
                .setTitle("DESIGN PATTERNIFY")
                .notify(project)

        }
        private fun notificationError(project: Project, text: String) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("DesignPatternify.NotificationGroup")
                .createNotification(
                    text,
                    NotificationType.ERROR
                )
                .setTitle("DESIGN PATTERNIFY")
                .notify(project)
        }
        private fun notificationWarn(project: Project, text: String) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("DesignPatternify.NotificationGroup")
                .createNotification(
                    text,
                    NotificationType.WARNING
                )
                .setTitle("DESIGN PATTERNIFY")
                .notify(project)
        }
}

