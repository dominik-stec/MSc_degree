package com.github.dominikstec.designpatternify.gui.dialogs

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
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.IncorrectOperationException
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.*
import javax.annotation.Nullable
import javax.swing.*
import javax.swing.border.Border


public class MainFactory(event: AnActionEvent): DialogWrapper(true) {

    //from setter
    val e = event

    //for maintain package source path
    var okPathClick = true
    var runCancel = false

    //mapped methods from dialog
    var ifNameHandler: ArrayList<JBTextField> = ArrayList()
    //name of class and interface from dialog
    var inputFactoryNamesHandler: LinkedHashMap<String, JBTextArea> = LinkedHashMap()

    //static
    companion object {
        var sourcePath: String = ""
        var imethods: String = ""
        var objects: String = ""
        var dialogPanel = JPanel()
        var isMethodMappingMiss = true
        var interfaceName = ""
    }

    init{
        init()
        title = "Design Patternify -> Factory"
        //store methods map when init
        imethods = getObjectsFactoryName()
        objects = getObjectsFactoryName()
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


        //layout
        val width = 40
        val height = 30
        val dimWidth = 300
        val dimHeight = 100
        val layout = GridBagLayout()
        layout.rowHeights = intArrayOf(30, 30)
        layout.columnWidths = intArrayOf(300, 300)
        dialogPanel = JPanel(layout)
        dialogPanel.preferredSize = Dimension(1000,450)
        dialogPanel.border = BorderFactory.createLineBorder(Color(255,255,255),15)
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.BOTH

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
        c.gridx = 0;
        c.gridy = 0;
        btnLabel.preferredSize = Dimension(dimWidth,dimHeight)
        btnLabel.setBounds(0,0,width,height)
        dialogPanel.add(btnLabel, c)
        c.gridx = 1;
        c.gridy = 0;
        btnLabelBold.preferredSize = Dimension(dimWidth,dimHeight)
        btnLabelBold.setBounds(0,0,width,height)
        dialogPanel.add(btnLabelBold ,c )


        //button path
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

        c.gridx = 2;
        c.gridy = 0;
        btn.preferredSize = Dimension(dimWidth,dimHeight)
        btn.setBounds(0,0,width,height)
        btn.border = BorderFactory.createLineBorder(Color(20,40,60), 1)
        dialogPanel.add(btn, c)


        //text field for adapter interface
        var txtLabel = JLabel("Jakiego typu obiektów dotyczy fabryka?")
        var txtLabelBold = JLabel(">>   Podaj nazwę interfejsu:")
        // Get the current font or set a default one
        currentFont = txtLabelBold.getFont()
        boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        // Apply the bold font to the label
        txtLabelBold.setFont(boldFont)
        c.gridx = 0;
        c.gridy = 1;
        txtLabel.preferredSize = Dimension(dimWidth,dimHeight)
        txtLabel.setBounds(0,0,width,height)
        dialogPanel.add(txtLabel, c)
        c.gridx = 1;
        c.gridy = 1;
        txtLabelBold.setBounds(0,0,width,height)
        dialogPanel.add(txtLabelBold, c)
        val factoryInterfaceField = JBTextField()
        currentFont = factoryInterfaceField.getFont()
        boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        // Apply the bold font to the label
        factoryInterfaceField.setFont(boldFont)
        // Request focus for the text field
        SwingUtilities.invokeLater({ factoryInterfaceField.requestFocusInWindow()})

        //handle adapter interface name
        ifNameHandler.add(factoryInterfaceField)

        c.gridx = 2;
        c.gridy = 1;
        factoryInterfaceField.preferredSize = Dimension(dimWidth,dimHeight)
        factoryInterfaceField.setBounds(0,0,width,height)
        factoryInterfaceField.border = BorderFactory.createLineBorder(Color(20,40,60), 1)
        dialogPanel.add(factoryInterfaceField, c)


        //text area for factory methods
        txtLabel = JLabel("Jakie metody ma mieć fabryka?")
        txtLabelBold = JLabel(">>   Podaj deklaracje metod:")
        // Get the current font or set a default one
        currentFont = txtLabelBold.getFont()
        boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        // Apply the bold font to the label
        txtLabelBold.setFont(boldFont)
        c.gridx = 0;
        c.gridy = 2;
        txtLabel.preferredSize = Dimension(dimWidth,dimHeight)
        txtLabel.setBounds(0,0,width,height)
        dialogPanel.add(txtLabel, c)
        c.gridx = 1;
        c.gridy = 2;
        txtLabelBold.preferredSize = Dimension(dimWidth,dimHeight)
        txtLabelBold.setBounds(0,0,width,height)
        dialogPanel.add(txtLabelBold, c)

        //store methods name from dialog
        val factoryMethods = JBTextArea()
        currentFont = factoryMethods.getFont()
        boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        // Apply the bold font to the label
        factoryMethods.setFont(boldFont)
        factoryMethods.border = BorderFactory.createLineBorder(Color(20,40,60), 1)

        //handle adapter name
        inputFactoryNamesHandler["methods"] = factoryMethods

        c.gridx = 2;
        c.gridy = 2;
        factoryMethods.preferredSize = Dimension(dimWidth,dimHeight)
        factoryMethods.setBounds(0,0,width,height)
        dialogPanel.add(factoryMethods, c)


        //text area for factory objects
        txtLabel = JLabel("Jakie obiekty ma produkować fabryka?")
        txtLabelBold = JLabel(">>   Podaj nazwy klas:")
        // Get the current font or set a default one
        currentFont = txtLabelBold.getFont()
        boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        // Apply the bold font to the label
        txtLabelBold.setFont(boldFont)
        c.gridx = 0;
        c.gridy = 3;
        txtLabel.preferredSize = Dimension(dimWidth,dimHeight)
        txtLabel.setBounds(0,0,width,height)
        dialogPanel.add(txtLabel, c)
        c.gridx = 1;
        c.gridy = 3;
        txtLabelBold.preferredSize = Dimension(dimWidth,dimHeight)
        txtLabelBold.setBounds(0,0,width,height)
        dialogPanel.add(txtLabelBold, c)

        //store objects name from dialog
        val factoryObjects = JBTextArea()
        currentFont = factoryObjects.getFont()
        boldFont = Font(currentFont.fontName, Font.BOLD, currentFont.size)
        // Apply the bold font to the label
        factoryObjects.setFont(boldFont)
        factoryObjects.border = BorderFactory.createLineBorder(Color(20,40,60), 1)

        //handle objects
        inputFactoryNamesHandler["objects"] = factoryObjects

        c.gridx = 2;
        c.gridy = 3;
        factoryObjects.setBounds(0,0,width,height)
        factoryObjects.preferredSize = Dimension(dimWidth,dimHeight)
        dialogPanel.add(factoryObjects, c)

        dialogPanel.background = Color(255, 255, 255)

        return dialogPanel
    }


    fun getMethodsFactoryName(): String {
        return inputFactoryNamesHandler.get("methods")!!.getText()
    }
    fun getObjectsFactoryName(): String {
        return inputFactoryNamesHandler.get("objects")!!.getText()
    }
    fun getInterfaceNameHandler(): String {
        return ifNameHandler[0].getText()
    }

    override fun doOKAction() {

        //set text fields values from dialog
        imethods = getMethodsFactoryName() ?: ""
        objects = getObjectsFactoryName() ?: ""
        interfaceName = getInterfaceNameHandler()

        //split text fields to arrays
        var imethodsArr = imethods.split("\n")
        var objectsArr = objects.split("\n")

        //check if files are unique
        var isFileInerfaceUnique = true
        isFileInerfaceUnique = checkFileNameDuplication(e.project!!, sourcePath, interfaceName)
        var isFileObjectsUnique = true
        if(objectsArr.isNotEmpty()) {
            for(o in objectsArr) {
                if(checkFileNameDuplication(e.project!!, sourcePath, o)) {
                    isFileObjectsUnique = true
                } else {
                    isFileObjectsUnique = false
                    break
                }
            }
        }


        val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)
        val rootPsi: PsiFile? = e.getData(CommonDataKeys.PSI_FILE)

        //interface name is empty
        if (interfaceName == "") {

            notificationWarn(
                project,
                "Wpisz nazwę interfejsu."
            )

            //adapter class name is empty
        } else if (imethods == "") {

            notificationWarn(
                project,
                "Wpisz nazwy metod."
            )

            //mapping is empty
        } else if (objects == "") {

            notificationWarn(
                project,
                "Wpisz nazwy klas."
            )

            //file exist
        } else if(isFileInerfaceUnique.not()) {

            notificationWarn(
                project,
                "Plik ${interfaceName}.java już istnieje. Zmień nazwę interfejsu."
            )

            //file exist
        } else if(isFileObjectsUnique.not()) {

            notificationWarn(
                project,
                "Plik klasy już istnieje. Zmień nazwy klas."
            )


        } else {

            //confirm dialog
            super.doOKAction()

            //extract given units of methods and objects names
            var imethodsArr = imethods.split("\n")
            var objectsArr = objects.split("\n")

            //create files
            createFiles(project, rootPsi, interfaceName, objectsArr)
            //create content
            createInterfaceContent(project, interfaceName, imethodsArr)
            createObjectsClassesContent(project, interfaceName, imethodsArr, objectsArr)
            createFactoryClassContent(project, interfaceName, objectsArr)

            sourcePath = ""
            imethods = ""
            objects = ""
            isMethodMappingMiss = true
            interfaceName = ""
            imethodsArr = listOf()
            objectsArr = listOf()

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


    private fun createFiles(project: Project, rootPsi: PsiFile?, ifName: String, objects: List<String>) {

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
                        dir!!.createFile("${ifName}.java")
                        dir!!.createFile("${ifName}Factory.java")
                        objects.forEach{dir!!.createFile("${it}.java")}

                    } catch (exc: IncorrectOperationException) {
                        notificationError(project, "Nie utworzono wszystkich plików w ${sourcePath}.")
                    }
                    //single packcage for files was select
                } else {
                    try {
                        //reference to packages path
                        dir = dir!!.findSubdirectory(sourcePath)

                        //create abstract for decorators for each level
                        dir!!.createFile("${ifName}.java")
                        dir!!.createFile("${ifName}Factory.java")
                        objects.forEach{dir!!.createFile("${it}.java")}

                        } catch (exc: IncorrectOperationException) {
                        notificationError(project, "Nie utworzono wszystkich plików w ${sourcePath}.")
                    }
                }

                //package was not selected
                // generate in project root path
            } else {
                try {
                    //reset directory to project root position
                    dir!!.createFile("${ifName}.java")
                    dir!!.createFile("${ifName}Factory.java")
                    objects.forEach{dir!!.createFile("${it}.java")}

                } catch (exc: IncorrectOperationException) {
                    notificationError(project, "Nie utworzono wszystkich plików.")
                }
            }
        }
        WriteCommandAction.runWriteCommandAction(project, createFiles)
    }

    private fun createInterfaceContent(project: Project, ifName: String, methodsList: List<String>) {
        val addFileContent = Runnable {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            var path = ""
            if(sourcePath != "") path = "${sourcePath}."


            //for first line abstract class
            var code = """"""
            if(sourcePath == "") {
                code = """
                        public interface ${ifName} {
                            void methods();
                        }
                    """.trimIndent()
            }
            //for next lines of abstract classes
            else code = """
                        package ${sourcePath};

                        public interface ${ifName} {
                            void methods();
                        }
                    """.trimIndent()

            val method: List<String>
            //if more then 1 method
            if(methodsList.size>1) {
                method = methodsList
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
                toAddCode += ("public " + methodsList[0] + ";")
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
            val dir = PsiManager.getInstance(project).findFile(root.first().findFileByRelativePath(slashPath + "/"  + ifName + ".java")!!)
            val uniqueVirtualFile = dir!!.containingFile.virtualFile
            val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

            //write to file
            documentFile!!.insertString(0, code)

        }
        WriteCommandAction.runWriteCommandAction(project, addFileContent)
    }


    private fun createObjectsClassesContent(project: Project, ifName: String, methodsList: List<String>, objects: List<String>) {

            //create file for each object
            objects.forEach {

                val addFileContent = Runnable {
                    ApplicationManager.getApplication().assertWriteAccessAllowed()

                var path = ""
                if (sourcePath != "") path = "${sourcePath}."


                //for first line abstract class
                var code = """"""
                if (sourcePath == "") {
                    code = """
                        public class ${it} implements ${ifName} {
                            
                            public interfaceMethods();
                        }
                    """.trimIndent()
                }
                //for next lines of abstract classes
                else code = """
                        package ${sourcePath};
                
                        public class ${it} implements ${ifName} {

                            public interfaceMethods();
                        }
                    """.trimIndent()


                val method: List<String>
                //if more then 1 method
                if (methodsList.size > 1) {
                    method = methodsList
                }
                //if only one method
                else {
                    method = listOf("")
                }

                var toAddCode = """"""

                //if more then 1 method
                if (methodsList.size > 1) {
                    var i = 0
                    for (m in methodsList) {
                        //without return statement
                        if (m.contains("void")) {
                            //first iter
                            if (i == 0) {
                                toAddCode += "@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}System.out.println("TODO ${it}.${m.replace("void ", "")}");
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //iter between first and last
                            } else if (i < method.size - 1) {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}System.out.println("TODO ${it}.${m.replace("void ", "")}");
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //last iter
                            } else {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}System.out.println("TODO ${it}.${m.replace("void ", "")}");
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
                            |    ${"\t\t"}return
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //iter between first and last
                            } else if (i < method.size - 1) {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}return
                            |${"\t"}}
                        """.trimMargin() + "\n")
                                toAddCode.trimIndent()
                                //last iter
                            } else {
                                toAddCode += "\t@Override\n"
                                toAddCode += ("\tpublic $m" + """ {
                            |    ${"\t\t"}return
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
                    if (methodsList.contains("void")) {
                        toAddCode += "@Override\n"
                        toAddCode += ("\tpublic ${methodsList[0]}" + """ {
                            |   ${"\t\t"}System.out.println("TODO ${it}.${methodsList[0].replace("void ", "")}");
                            |${"\t"}}
                        """.trimMargin() + "\n")
                        toAddCode.trimIndent()
                        //with return statement
                    } else {
                        toAddCode += "@Override\n"
                        toAddCode += ("\tpublic ${methodsList[0]}" + """ {
                            |   ${"\t\t"}return
                            |${"\t"}}
                        """.trimMargin() + "\n")
                        toAddCode.trimIndent()
                    }

                }
                code = code.replace("public interfaceMethods();", toAddCode, true)

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
                    .findFile(root.first().findFileByRelativePath(slashPath + "/" + it + ".java")!!)
                val uniqueVirtualFile = dir!!.containingFile.virtualFile
                val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

                //write to file
                documentFile!!.insertString(0, code)

            }
            WriteCommandAction.runWriteCommandAction(project, addFileContent)
        }
    }

    private fun createFactoryClassContent(project: Project, ifName: String, objects: List<String>) {

            val addFileContent = Runnable {
                ApplicationManager.getApplication().assertWriteAccessAllowed()

                var path = ""
                if (sourcePath != "") path = "${sourcePath}."

                //for first line class
                var i = 0
                var code = """"""
                    if (sourcePath == "") {
                        code = """
                        public class ${ifName}Factory {
                        
                            public enum ${ifName}Type {
                                toDelBefore${objects.map{it.uppercase()}}toDelAfter
                            }
                            
                            public ${ifName} create${ifName}(${ifName}Type type) {
                                if(type == null) {
                                    return null;
                                }
                                ${objects.map{
                            """else if(type.equals(${ifName}Type.${it.uppercase()})) {
                                   return new ${it}();
                               }
                               """.trimIndent()
                                }}
                                return null;
                            }
                        }
                    """.trimIndent()
                    }
                    //for next lines of abstract classes
                    else code = """
                        package ${sourcePath};
                        
                        public class ${ifName}Factory {
                        
                            public enum ${ifName}Type {
                                toDelBefore${objects.map{it.uppercase()}}toDelAfter
                            }
                            
                            public ${ifName} create${ifName}(${ifName}Type type) {
                                if(type == null) {
                                    return null;
                                }
                                ${objects.map{ 
                             """else if(type.equals(${ifName}Type.${it.uppercase()})) {
                                    return new ${it}();
                                }
                                    """.trimIndent()
                    }}
                                return null;
                            }
                        }
                    """.trimIndent()

                code = code.replace("[else", "else")
                code = code.replace("}]", "}")
                code = code.replace("},", "}")

                code = code.replace("toDelBefore[", "")
                code = code.replace("]toDelAfter", "")

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
                    .findFile(root.first().findFileByRelativePath(slashPath + "/" + ifName + "Factory.java")!!)
                val uniqueVirtualFile = dir!!.containingFile.virtualFile
                val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

                //write to file
                documentFile!!.insertString(0, code)

            }
            WriteCommandAction.runWriteCommandAction(project, addFileContent)

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
            imethods = ""
            objects = ""
            isMethodMappingMiss = true
            interfaceName = ""


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
}

