package com.github.dominikstec.designpatternify.gui.dialogs

import ai.grazie.nlp.utils.tokenizeByWhitespace
import com.intellij.ide.wizard.withVisualPadding
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
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.dsl.builder.*
import com.intellij.util.IncorrectOperationException
import org.jetbrains.rpc.LOG
import java.awt.Color
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


class MainDecorator(event: AnActionEvent, fieldList: ArrayList<String>, methodList: ArrayList<String>, className: String) : DialogWrapper(true) {
    val e = event
    val setterFields = fieldList
    val setterMethods = methodList
    val setterClassName = className
    val ipublisher = "IPublisher.java"
    val isubscriber = "ISubscriber.java"
    var baseLevel = ""
    var rootPath = ""
    var okPathClick = true
    var runCancel = false
    val isPackageEmptySet: HashSet<Boolean> = HashSet()

    //static
    companion object {
        var sourcePath: String = ""
        var levels: String = ""
        var decorators: String = ""
        var fields: String = ""
        var methods: String = ""
    }

    init {
        title = "Design Patternify -> Dekorator"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return dialogPanelFactory()
    }

    fun dialogPanelFactory(): DialogPanel {


        super.setOKButtonText("GENERUJ")
        super.setCancelButtonText("anuluj")
        super.setUndecorated(true)

        //get project root directory path
        val manager = ModuleManager.getInstance(e.project!!)
        val modules: Array<Module> = manager.modules
        val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()

        return panel {

            row("Gdzie umieścić kod wzorca?") {
                label(">>   Wybierz pakiet źródłowy:").bold().align(AlignX.RIGHT)
                button(">> Wybór Pakietu <<", { _ ->
                    rootPath = ""
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
                }).align(AlignX.FILL)
            }

            row("Jakie mają być kolejne warstwy?") {
                label(">>   Podaj nazwy warstw:").bold().align(AlignX.RIGHT)
                textArea().rows(5).align(AlignX.RIGHT).columns(20).focused().bold()
                    .bindText(::levels)
            }

            row("Jakie dekoratory umieścić w kolejnych warstwach?") {
                label(">>   Podaj nazwy klas:").bold().align(AlignX.RIGHT)
                textArea().rows(5).align(AlignX.RIGHT).columns(20).bold()
                    .bindText(::decorators)
            }

            row("Jakie pola mają być dekorowane?") {
                label(">>   Podaj nazwy zmiennych z typem:").bold().align(AlignX.RIGHT)
                textArea().rows(5).align(AlignX.RIGHT).columns(20).bold()
                    .bindText(::fields)
            }

            row("Jakie metody mają być wspólne?") {
                label(">>   Podaj deklaracje metod:").bold().align(AlignX.RIGHT)
                textArea().rows(5).align(AlignX.RIGHT).columns(20).bold()
                    .bindText(::methods)
            }
        }.withBackground(Color(255, 255, 255))
            .withBorder(BorderFactory.createEmptyBorder(20, 50, 50, 20))
            .withVisualPadding(true)
    }

    override fun doOKAction() {
        super.doOKAction()

        val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)
        val rootPsi: PsiFile? = e.getData(CommonDataKeys.PSI_FILE)

        //dialog level fields is empty
        if (levels == "") {

            notificationWarn(
                project,
                "Wpisz pole warstw."
            )
            MainDecorator(e, setterFields, setterMethods, setterClassName).show()

            //decorators class names are empty
        } else if (decorators == "") {

            notificationWarn(
                project,
                "Wpisz pole dekoratorów."
            )
            MainDecorator(e, setterFields, setterMethods, setterClassName).show()

            //fields are empty
        } else if (fields == "" && setterFields.isEmpty()) {

            notificationWarn(
                project,
                "Wpisz pole zmiennych."
            )
            MainDecorator(e, setterFields, setterMethods, setterClassName).show()

            //number of levels must be the same as number of levels for decorators
        } else if (levels.split("\n").size != decorators.split("\n").size) {

            notificationWarn(
                project, "Zmień ilość warstw."
            )
            MainDecorator(e, setterFields, setterMethods, setterClassName).show()

        } else {



            checkIfFilesAreUniqueInPackageThenCreate(rootPsi, project, "")
//            createFiles(project, rootPsi)

            sourcePath = ""
            levels = ""
            decorators = ""
            fields = ""
            methods = ""
            rootPath = ""

            notificationWarn(project, "Dodano pliki do projektu.")

        }
    }

    //reset values after cancel
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
            levels = ""
            decorators = ""
            fields = ""
            methods = ""
            rootPath = ""

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

    private fun createFiles(project: Project, rootPsi: PsiFile?) {

        //class file is not selected as entry point
        var isSelectedF = false
        //class file is selected as entry point
        if (rootPsi != null) isSelectedF = true

            //repeat dialog when exception occurs
            var repeatDialog = false

            val createFiles = Runnable {

                //access to write files in project
                ApplicationManager.getApplication().assertWriteAccessAllowed()

                //get project root directory path
                val manager = ModuleManager.getInstance(e.project!!)
                val modules: Array<Module> = manager.modules
                val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
                var dir = PsiManager.getInstance(project).findDirectory(root)

                //package for files was select
                if (sourcePath != "") {

                //generate packages for each decorator level
                val levelsSplit = levels.split("\n")
                //if more then 1 package
                if (sourcePath.contains(".")) {
                    val path = sourcePath.split(".")
                    for (subDir in path) {
                        //reference to packages path
                        dir = dir!!.findSubdirectory(subDir)
                    }
                    //create packages
                    if (!repeatDialog) {
                        for (l in levelsSplit) {
                            try {
                                dir!!.checkCreateSubdirectory(l + "Decorator")
                                dir!!.createSubdirectory(l + "Decorator")

                            } catch (exc: IncorrectOperationException) {
                                notificationError(project, "Nie utworzono pakietów w ${sourcePath}")
                            }
                        }
                    }
                    //if only 1 package
                } else {
                    if (!repeatDialog) {
                        for (l in levelsSplit) {
                            try {
                                //create packages
                                dir = PsiManager.getInstance(project).findDirectory(root)
                                dir = dir!!.findSubdirectory(sourcePath)
                                dir!!.checkCreateSubdirectory(l + "Decorator")
                                dir!!.createSubdirectory(l + "Decorator")

                            } catch (exc: IncorrectOperationException) {
                                notificationError(project, "Nie utworzono pakietu ${sourcePath}.${l + "Decorator"}")
                            }
                        }
                    }
                }

                //if more then one package
                if (sourcePath.contains(".")) {

                    val path = sourcePath.split(".")

                    //if packages names are unique
                    if (!repeatDialog) {
                        //generate files of abstract decorators for each level
                        for (l in levelsSplit) {
                            try {
                                //reset directory to project root position
                                dir = PsiManager.getInstance(project).findDirectory(root)
                                for (subDir in path) {
                                    //reference to packages path
                                    dir = dir!!.findSubdirectory(subDir)
                                }
                                //go to separate layer package
                                dir = dir!!.findSubdirectory(l + "Decorator")
                                //create abstract for decorators for each level
                                dir!!.createFile(l + ".java")
                            } catch (exc: IncorrectOperationException) {
                                notificationError(project, "Nie utworzono pliku ${sourcePath}.${l + "Decorator"}")
                            }
                        }
                    }

                    //if packages names are unique
                    if (!repeatDialog) {
                        //generate files of decorators for each level
                        var i = 0
                        val decoratorsLineList = decorators.split("\n")
                        val levelsSplit = levels.split("\n")
                        for (l in levelsSplit) {
                            try {
                                //reset directory to project root position
                                dir = PsiManager.getInstance(project).findDirectory(root)
                                for (subDir in path) {
                                    //reference to packages path
                                    dir = dir!!.findSubdirectory(subDir)
                                }
                                //go to separate layer package
                                dir = dir!!.findSubdirectory(l + "Decorator")
                                //create  decorators for each level
                                //split array with lines of decorator class names
                                val decorators = decoratorsLineList.get(i++).split(" ")
                                for (d in decorators) {
                                    dir!!.createFile(d + ".java")
                                }
                            } catch (exc: IncorrectOperationException) {
                                notificationError(project, "Nie utworzono plików w ${sourcePath}.${l + "Decorator"}")
                            }
                        }
                    }

                    //if only one package
                } else {

                    //if packages names are unique
                    if (!repeatDialog) {
                        //generate files of abstract decorators for each level
                        for (l in levelsSplit) {
                            try {
                                //reset directory to project root position
                                dir = PsiManager.getInstance(project).findDirectory(root)
                                //set current directory path
                                dir = dir!!.findSubdirectory(sourcePath)
                                //go to separate layer package
                                dir = dir!!.findSubdirectory(l + "Decorator")
                                //create abstract for decorators for each level
                                dir!!.createFile(l + ".java")
                            } catch (exc: Exception) {
                                notificationError(project, "Nie utworzono pliku ${sourcePath}.${l + ".java"}")
                            }
                        }
                    }

                    //if packages names are unique
                    if (!repeatDialog) {
                        //generate files of decorators for each level
                        var i = 0
                        val decoratorsLineList = decorators.split("\n")
                        val levelsSplit = levels.split("\n")
                        for (l in levelsSplit) {
                            try {
                                //reset directory to project root position
                                dir = PsiManager.getInstance(project).findDirectory(root)
                                //set current directory path
                                dir = dir!!.findSubdirectory(sourcePath)
                                //go to separate layer package
                                dir = dir!!.findSubdirectory(l + "Decorator")
                                //split array with lines of decorator class names
                                val decorators = decoratorsLineList.get(i++).split(" ")
                                for (d in decorators) {
                                    dir!!.createFile(d + ".java")
                                }
                            } catch (exc: Exception) {
                                notificationError(project, "Nie utworzono pliku ${sourcePath}.${l + "Decorator"}")
                            }
                        }
                    }
                }

                //package was not select
                } else {

                    dir = PsiManager.getInstance(project).findDirectory(root)
                    val levelsSplit = levels.split("\n")
                    //generate packages for each decorator level
                    for (l in levelsSplit) {
                        try {
                            dir!!.checkCreateSubdirectory(l + "Decorator")
                            dir.createSubdirectory(l + "Decorator")

                        } catch (exc: IncorrectOperationException) {
                            notificationError(project, "Nie utworzono pakietów w root path projektu")
                        }
                    }

                    for (l in levelsSplit) {
                        try {
                            //reset directory to project root position
                            dir = PsiManager.getInstance(project).findDirectory(root)
                            //go to separate layer package
                            dir = dir!!.findSubdirectory(l + "Decorator")
                            //create abstract for decorators for each level
                            dir!!.createFile(l + ".java")
                        } catch (exc: Exception) {
                            notificationError(project, "Nie utworzono pliku: ${l + ".java"} w pakiecie: ${l+"Decorator"}")
                        }
                    }

                    //generate files of decorators for each level
                    var i = 0
                    val decoratorsLineList = decorators.split("\n")
                    for (l in levelsSplit) {
                        try {
                            //reset directory to project root position
                            dir = PsiManager.getInstance(project).findDirectory(root)
                            //go to separate layer package
                            dir = dir!!.findSubdirectory(l + "Decorator")
                            //split array with lines of decorator class names
                            val decorators = decoratorsLineList.get(i++).split(" ")
                            for (d in decorators) {
                                dir!!.createFile(d + ".java")
                            }
                        } catch (exc: Exception) {
                            notificationError(project, "Nie utworzono plików dekoratorów w pakiecie: ${l+"Decorator"}")
                        }
                    }

                }

            }
            WriteCommandAction.runWriteCommandAction(project, createFiles)

            //show dialog again
            if (repeatDialog) MainDecorator(e, setterFields, setterMethods, setterClassName).show()

            //create files content
            var i = 0
            while (i < levels.split("\n").size) {
                layerAbstractClassContent(project, isSelectedF, i)
                layerClassesContent(project, isSelectedF, i++)
            }

    }

    private fun checkNumOfLayersFollowMethodsFileds(
        project: Project,
        isSelectedF: Boolean,
        rootPsi: PsiFile?
    ) {
        try {
            //add file content
            var i = 0
            while (i < levels.split("\n").size) {
                layerAbstractClassContent(project, isSelectedF, i)
                layerClassesContent(project, isSelectedF, i++)
            }
        } catch (exc: Exception) {
            notificationInfo(
                project, "Nie podano odpowiedniej liczby pól i metod dla deklarowanych poziomów. " +
                        "Zmień liczbę poziomów lub dopasuj liczbę pól i metod do liczby deklarowanych poziomów."
            )
            val deleteFiles = Runnable {
                //access to write files in project
                ApplicationManager.getApplication().assertWriteAccessAllowed()
                //set current directory path the same as selected class
                var dir = rootPsi!!.containingDirectory
                //go to package with given name
                dir = dir!!.findSubdirectory(sourcePath)
                //delete package with content
                dir.delete()
            }
            WriteCommandAction.runWriteCommandAction(project, deleteFiles)
            MainDecorator(e, setterFields, setterMethods, setterClassName).show()
        }
    }

    private fun layerAbstractClassContent(project: Project, isSelectedFile: Boolean, line: Int) {
        val addFileContent = Runnable {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            val level1 = levels.split("\n").get(line)
            var path = ""
            if(sourcePath != "") path = "$sourcePath."

            //for first line abstract class
            var code = """"""
            if(line == 0) {
                code = """
                        package ${level1}Decorator;
                        
                        public abstract class Layer1 {
                        public String field1;
                        public abstract String method1();
                        }
                    """.trimIndent()
//                baseLevel = level1
            }
            //for next lines of abstract classes
            else {
                baseLevel =  levels.split("\n").get(line-1);
                code = """
                        package ${level1}Decorator;
                        
                        import ${path}${baseLevel}Decorator.${baseLevel};
                        
                        public abstract class Layer1 extends ${baseLevel}{
                            public ${baseLevel} ${toCamelCase(baseLevel)};
                        
                        public String field1;
                        public abstract String method1();
                        }
                    """.trimIndent()
            }
            //add package declaration
            if(sourcePath.isNotEmpty())
                code = code.replace("package ${level1}Decorator;",
                    "package ${path}${level1}Decorator;", true)

            val layer1 = levels.split("\n").get(line)
            code = code.replace("Layer1", layer1, true)

            var toAddCode = ""
            //with given fields from dialog
            if(fields.isNotEmpty() ) {
                val field1 = fields.split("\n")
                //no entry class selected
                if (setterFields.isEmpty()) {
                    for (f in field1) {
                        toAddCode += ("\t" + "public " + f + ";\n")
                        toAddCode.trimIndent()
                    }
                    //with given setters
                } else {
                    for (s in setterFields) {
                        toAddCode += ("\t" + "public " + s + ";\n")
                        toAddCode.trimIndent()
                    }
                    for (f in field1) {
                        toAddCode += ("\t" + "public " + f + ";\n")
                        toAddCode.trimIndent()
                    }
                }
                code = code.replace("public String field1;", toAddCode, true)

                //without given fields from dialog
            } else {

                //setter fields not empty
                if (setterFields.isNotEmpty()) {
                    for (s in setterFields) {
                        toAddCode += ("\t" + "public " + s + ";\n")
                        toAddCode.trimIndent()
                    }
                }
                code = code.replace("public String field1;", toAddCode, true)
            }


            //with given methods from dialog
            if(methods.isNotEmpty()) {
                toAddCode = ""
                val method1 = methods.split("\n")

                    for (m in method1) {
                        toAddCode += ("\t" + "public abstract " + m + ";\n")
                        toAddCode.trimIndent()
                    }

                code = code.replace("public abstract String method1();", toAddCode, true)

                //without given methods from dialog
            } else {
                toAddCode = ""
                code = code.replace("public abstract String method1();", toAddCode, true)
            }


                //get project root directory path
                val manager = ModuleManager.getInstance(e.project!!)
                val modules: Array<Module> = manager.modules
                val root = ModuleRootManager.getInstance(modules.first()).sourceRoots

                //convert source path format for find as psi file
                var slashPath = ""
                if(sourcePath.isNotEmpty()) {
                    slashPath = sourcePath.replace(".", "/", true)
                    slashPath = "/${slashPath}"
                }

                //get file from relative to root directory path
                val dir = PsiManager.getInstance(project).findFile(root.first().findFileByRelativePath(slashPath + "/" +  level1 + "Decorator" + "/" + level1 + ".java")!!)
                val uniqueVirtualFile = dir!!.containingFile.virtualFile
                val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

                //write to file
                documentFile!!.insertString(0, code)

        }
        WriteCommandAction.runWriteCommandAction(project, addFileContent)
    }

    private fun layerClassesContent(project: Project, isSelectedFile: Boolean, line: Int) {
        val addFileContent = Runnable {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            val level1 = levels.split("\n").get(line)
            val beginLevel = levels.split("\n").get(0)

            var path = ""
            if(sourcePath != "") path = "$sourcePath."

            //generate constructor for imported setters
            //for first line
            var code = ""
            if(setterClassName != "" && line == 0) {
                var constructorCode = ""
                for(f in setterFields) {
                    val field = f.tokenizeByWhitespace().get(1)

                    constructorCode += """this.${field} = ${toCamelCase(setterClassName)}.${field};
                        
                    """.trimIndent()

                    constructorCode += "\t\t"
                }
                code = """
                        package ${path}${level1}Decorator;
                        
                        import ${path}${beginLevel}Decorator.${beginLevel};
                        
                        public class Layer1 extends ${level1}{
                            public Layer1 (${setterClassName} ${toCamelCase(setterClassName)}) {
                                
                                ${constructorCode}
                            }
                            
                        public String field1;
                        public String method1();
                                               
                        }
                    """.trimIndent()

            //next lines with setters
            } else if(setterClassName != "" && line>0) {
                val beginLevel = levels.split("\n").get(line-1)
                code = """
                        package ${path}${level1}Decorator;
                        
                        import ${path}${beginLevel}Decorator.${beginLevel};
                        import ${path}${level1}Decorator.${level1};
                        
                        public class Layer1 extends ${level1}{
                            public Layer1 (${beginLevel} ${toCamelCase(beginLevel)}) {
                                this.${toCamelCase(beginLevel)} = ${toCamelCase(beginLevel)};
                            }
                            
                        public String field1;
                        public String method1();
                                               
                        }
                    """.trimIndent()

                //no setters and first line
            } else if(setterClassName == "" && line == 0) {

                code = """
                        package ${path}${level1}Decorator;
                        
                        import ${path}${level1}Decorator.${level1};
                        
                        public class Layer1 extends ${level1}{
                            public Layer1 (  ) {  }
                            
                        public String field1;
                        public String method1();
                                               
                        }
                    """.trimIndent()

                //no setter and next lines
            } else if(setterClassName == "" && line > 0) {

                val beginLevel = levels.split("\n").get(line-1)

                code = """
                        package ${path}${level1}Decorator;
                        
                        import ${path}${beginLevel}Decorator.${beginLevel};
                        import ${path}${level1}Decorator.${level1};
                        
                        public class Layer1 extends ${level1}{
                            public Layer1 (${beginLevel} ${toCamelCase(beginLevel)}) {
                                this.${toCamelCase(beginLevel)} = ${toCamelCase(beginLevel)};
                            }
                            
                        public String field1;
                        public String method1();
                                               
                        }
                    """.trimIndent()

            }


            //add package declaration
            if(sourcePath.isNotEmpty())
                code = code.replace("package ${level1}Decorator;",
                    "package ${path}${level1}Decorator;", true)

            val decoratorsLine = decorators.split("\n").get(line)
            val decoratorList = decoratorsLine.split(" ")

            var toAddCode = ""
            //with given fields from dialog
            if(fields.isNotEmpty() ) {

//                val field1 = fields.split("\n")
//                //no entry class selected
//                if (setterFields.isEmpty()) {
//                    for (f in field1) {
//                        toAddCode += ("\t" + "public " + f + ";\n")
//                        toAddCode.trimIndent()
//                    }
//                    //with given setters
//                } else {
//                    for (s in setterFields) {
//                        toAddCode += ("\t" + "public " + s + ";\n")
//                        toAddCode.trimIndent()
//                    }
//                    for (f in field1) {
//                        toAddCode += ("\t" + "public " + f + ";\n")
//                        toAddCode.trimIndent()
//                    }
//                }
                code = code.replace("public String field1;", toAddCode, true)

                //without given fields from dialog
            } else {
                //setter fields not empty
                if (setterFields.isNotEmpty()) {
                    for (s in setterFields) {
//                        toAddCode += ("\t" + "public " + s + ";\n")
                        toAddCode = ""
                        toAddCode.trimIndent()
                    }
                }
                code = code.replace("public String field1;", toAddCode, true)
            }

            //with given methods from dialog
            if(methods.isNotEmpty()) {
                toAddCode = ""

                val method1 = methods.split("\n")

                    for (m in method1) {
                        //if method return nothing
                        if(m.contains("void")) {
                            toAddCode += ("\t" + "public " + m + """ {  } """.trimMargin() + "\n")
                            toAddCode.trimIndent()
                        //if method return number
                        } else if(m.contains("byte") || m.contains("short") || m.contains("int")
                            || m.contains("long") || m.contains("float") || m.contains("double")) {
                            toAddCode += ("\t" + "public " + m + """ { return 0; } """.trimMargin() + "\n")
                            toAddCode.trimIndent()
                        //if method return char
                        } else if(m.contains("char")) {
                            toAddCode += ("\t" + "public " + m + """ { return ''; } """.trimMargin() + "\n")
                            toAddCode.trimIndent()
                            //if method return boolean
                        } else if(m.contains("boolean")) {
                            toAddCode += ("\t" + "public " + m + """ { return false; } """.trimMargin() + "\n")
                            toAddCode.trimIndent()
                            //if method return object
                        } else {
                            toAddCode += ("\t" + "public " + m + """ { return null; } """.trimMargin() + "\n")
                            toAddCode.trimIndent()
                        }
                    }

                code = code.replace("public String method1();", toAddCode, true)

                //without given methods from dialog
            } else {

                code = code.replace("public String method1();", "", true)

            }

                //get file from relative to root directory path
                    decoratorList.forEach {
                        code = code.replace("Layer1", it, false)

                        //get project root directory path
                        val manager = ModuleManager.getInstance(e.project!!)
                        val modules: Array<Module> = manager.modules
                        val root = ModuleRootManager.getInstance(modules.first()).sourceRoots

                        //convert source path format for find as psi file
                        var slashPath = ""
                        if(sourcePath.isNotEmpty()) {
                            slashPath = sourcePath.replace(".", "/", true)
                            slashPath = "/${slashPath}"
                        }

                        //get file from relative path according to selected class dir
                        val dir = PsiManager.getInstance(project).findFile(root.first().findFileByRelativePath(slashPath + "/" +  level1 + "Decorator" + "/" + it + ".java")!!)
                        val uniqueVirtualFile = dir!!.containingFile.virtualFile
                        val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)

                        //write to file
                        documentFile!!.insertString(0, code)

                        code = code.replace("class ${it}", "class Layer1", false)
                        code = code.replace("public ${it}", "public Layer1", false)

                    }
            }

        WriteCommandAction.runWriteCommandAction(project, addFileContent)
    }

    private fun checkIfFilesAreUniqueInPackageThenCreate(
        rootPsi: PsiFile?,
        project: Project,
        filePath: String
    ) {
            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()

            //get proper path format
            var slashPath = ""
            if (sourcePath.isNotEmpty()) {
                slashPath = sourcePath.replace(".", "/", true)
                slashPath = "/${slashPath}"
            }

            //check if abstract class exist
            for (l in levels.split("\n")) {
                try {
                    PsiManager.getInstance(project)
                        .findFile(root.findFileByRelativePath(slashPath + "/" + l + "Decorator" + "/" + l + ".java")!!)
                    isPackageEmptySet.add(false)
                } catch(exc: NullPointerException) {
                    isPackageEmptySet.add(true)
                }

                //check if java class exist
                for (fi in decorators.split("\n")) {
                    for (f in fi) {
                        try {
                            PsiManager.getInstance(project)
                                .findFile(root.findFileByRelativePath(slashPath + "/" + l + "Decorator" + "/" + f + ".java")!!)
                            isPackageEmptySet.add(false)
                        } catch(exc: NullPointerException) {
                            isPackageEmptySet.add(true)
                        }
                    }
                }
            }


        //if duplicate exist
        var isDuplicate = false
        for(b in isPackageEmptySet) {
            if(b.not()) {
                isDuplicate = true
                break
            }
        }

            //duplicated files name exist in directory of pointed class
            //repeat dialog
            if (isDuplicate) {

                notificationError(project, "Pliki już istnieją. Zmień pakiet.")
                MainDecorator(e, setterFields, setterMethods, setterClassName).show()

                //without duplicated file names
            } else {

                createFiles(project, rootPsi)

            }


    }

    private fun getProjectsPath(project: Project): String {
        //get paths of existing in project packages
        var rootPath = ""
        ProjectRootManager.getInstance(project).fileIndex.iterateContent { file: VirtualFile ->
            if (file.isDirectory) {
                if (file.path.contains(sourcePath)) {
                    val path = file.path
                    val start = path.indexOf("src/")
//                    var mapedSourcePath = ""
//                    if(sourcePath.contains(".")) {
//                        mapedSourcePath = sourcePath.replace(".", "/", true)
//                    } else  {
//                        mapedSourcePath = sourcePath
//                    }
                    val end = path.indexOf("/" + sourcePath)
                    rootPath = path.substring(start + 4, end)
                    if (rootPath.contains("/")) {
                        rootPath = rootPath.replace("/", ".", true)
                    }
                }
            }
            true
        }
        return rootPath
    }

    private fun toCamelCase(pascalCase: String): String {
        val strList = pascalCase.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val lowerCaseStr = strList[0].lowercase(Locale.getDefault())
        strList[0] = lowerCaseStr
        val camelCaseStr = java.lang.String.join("", *strList)
        return camelCaseStr
    }

    private fun clearFiles(project: Project) {
        val clearFiles = Runnable {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            clearFileByName(project, MainObserver.sender)
            clearFileByName(project, ipublisher)
            clearFileByName(project, isubscriber)

            val subscribers = MainObserver.subscribers.split("\n")
            for (s in subscribers) {
                clearFileByName(project, s)
            }

        }
        WriteCommandAction.runWriteCommandAction(project, clearFiles)
    }

    private fun clearFileByName(project: Project, name: String) {
        val virtualFile =
            FilenameIndex.getVirtualFilesByName(name + ".java", GlobalSearchScope.allScope(project))
        val documentFile = FileDocumentManager.getInstance().getDocument(virtualFile.first())
        val beginOffset = documentFile!!.getLineStartOffset(0)
        val endOffset = documentFile.getLineEndOffset(documentFile.lineCount - 1)
        documentFile.deleteString(beginOffset, endOffset)
    }

    private fun createFilesSameDirAsClass(rootPsi: PsiFile?, project: Project) {
        val createFiles = Runnable {

            ApplicationManager.getApplication().assertWriteAccessAllowed()

            if(rootPsi == null) {

                val manager = ModuleManager.getInstance(e.project!!)
                val modules: Array<Module> = manager.modules
                val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
                var dir = PsiManager.getInstance(project).findDirectory(root)

                try {
                    dir!!.checkCreateSubdirectory(MainObserver.sourcePath)
                    dir = dir.createSubdirectory(MainObserver.sourcePath)
                } catch(exc: IncorrectOperationException) {
                    LOG.info("Directory: ${MainObserver.sourcePath} already exist. Add pattern files into: ${MainObserver.sourcePath}")
                }

                dir!!.createFile(ipublisher)
                dir.createFile(isubscriber)
                dir.createFile(MainObserver.sender + ".java")

                val subscribers = MainObserver.subscribers.split("\n")
                for (s in subscribers) {
                    dir.createFile(s + ".java")
                }

            } else {

                if(MainObserver.sourcePath == "") {

                    try {
                        rootPsi.containingDirectory.createFile(ipublisher)
                        rootPsi.containingDirectory.createFile(isubscriber)
                        rootPsi.containingDirectory.createFile(MainObserver.sender + ".java")

                        val subscribers = MainObserver.subscribers.split("\n")
                        for (s in subscribers) {
                            rootPsi.containingDirectory.createFile(s + ".java")
                        }
                    } catch(exc: IncorrectOperationException) {

                        notificationInfo(project, "Pliki wzorca projektowego 'obserwator' już istnieją w podanej lokalizacji. Zmień lokalizację i powtórz generowanie plików.")
                        MainObserver(e, setterFields).show()

                    }


                    val vFile: VirtualFile = rootPsi.virtualFile
                    val doc = FileDocumentManager.getInstance().getDocument(vFile)

                    if (doc != null) {
                        LOG.info("is writing document allowed: " + FileDocumentManager.getInstance().requestWriting(doc, project))
                        FileDocumentManager.getInstance().saveDocument(doc)
                    } else {
                        LOG.error("document is null")
                    }

                } else {

                    try {
                        rootPsi.containingDirectory.checkCreateSubdirectory(MainObserver.sourcePath)
                        rootPsi.containingDirectory.createSubdirectory(MainObserver.sourcePath)
                    } catch(exc: IncorrectOperationException) {
                        LOG.info("Directory: ${MainObserver.sourcePath} already exist. Add pattern files into: ${MainObserver.sourcePath}")
                    }
                    rootPsi.containingDirectory.findSubdirectory(MainObserver.sourcePath)!!.createFile(ipublisher)
                    rootPsi.containingDirectory.findSubdirectory(MainObserver.sourcePath)!!.createFile(isubscriber)
                    rootPsi.containingDirectory.findSubdirectory(MainObserver.sourcePath)!!.createFile(MainObserver.sender + ".java")


                    val subscribers = MainObserver.subscribers.split("\n")
                    for (s in subscribers) {
                        rootPsi.containingDirectory.findSubdirectory(MainObserver.sourcePath)!!.createFile(s + ".java")
                    }

                    val vFile: VirtualFile = rootPsi.virtualFile
                    val doc = FileDocumentManager.getInstance().getDocument(vFile)

                    if (doc != null) {
                        LOG.info("is writing document allowed: " + FileDocumentManager.getInstance().requestWriting(doc, project))
                        FileDocumentManager.getInstance().saveDocument(doc)
                    } else {
                        LOG.error("document is null")
                    }

                }
            }
        }
        WriteCommandAction.runWriteCommandAction(project, createFiles)
    }
}
