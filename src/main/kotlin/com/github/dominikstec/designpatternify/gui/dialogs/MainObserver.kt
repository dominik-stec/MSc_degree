package com.github.dominikstec.designpatternify.gui.dialogs

import ai.grazie.nlp.utils.tokenizeByWhitespace
import com.esotericsoftware.minlog.Log
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
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.util.IncorrectOperationException
import org.jetbrains.rpc.LOG
import java.awt.Color
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JComponent


class MainObserver(event: AnActionEvent, list: ArrayList<String>) : DialogWrapper(true) {
    val e = event
    val setterFields = list
    val ipublisher = "IPublisher.java"
    val isubscriber = "ISubscriber.java"
    var rootPath = ""
    var okPathClick = true
    var runCancel = false

    //static, save state for fields during form validation
    companion object {
        var sourcePath: String = ""
        var sender: String = ""
        var subscribers: String = ""
        var fields: String = ""
    }
    init {
        title = "Design Patternify -> Obserwator"
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
                button(">> Wybór Pakietu <<", {_ ->
                    val descriptor = FileChooserDescriptor(
                        false,
                        true,
                        false,
                        false,
                        false,
                        false)
                    var path: String
                    try {
                        path = FileChooser.chooseFile(descriptor, e.project!!, root)!!.path
                    } catch(ex: Exception) {
                        path = ""
                        okPathClick = false
                    }
                    if(path != "") runCancel = true
                    val start = path.indexOf("src/")
                    if(start == -1) rootPath = ""
                    val end = path.length
                    if(start != -1) rootPath = path.substring(start + 4, end)
                    if (rootPath.contains("/")) {
                        rootPath = rootPath.replace("/", ".", true)
                    }
                    sourcePath = rootPath
                    if(rootPath != "") notificationInfo(e.project!!, "Wybrano pakiet: " + rootPath)
                }).align(AlignX.FILL)
            }

            row("Jak nazywa się nadawca?") {
                label(">>   Nazwa klasy:").bold().align(AlignX.RIGHT)
                textField().align(AlignX.RIGHT).focused().bold()
                    .bindText(::sender)
            }

            row("Jak nazywają się odbiorcy?") {
                label(">>   Podaj nazwy klas:").bold().align(AlignX.RIGHT)
                textArea().rows(7).align(AlignX.RIGHT).bold()
                    .bindText(::subscribers)
            }

            row("Jakie dane będą obserwowane?") {
                label(">>   Podaj nazwy zmiennych z typem:").bold().align(AlignX.RIGHT)
                textArea().rows(7).align(AlignX.RIGHT).bold()
                    .bindText(::fields)
            }
        }.withBackground(Color(255,255,255))
            .withBorder(BorderFactory.createEmptyBorder(20, 50, 50, 20))
            .withVisualPadding(true)

    }


    override fun doOKAction() {
        super.doOKAction()

        //global scope of variables
        val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)
        val rootPsi: PsiFile? = e.getData(CommonDataKeys.PSI_FILE)

        //sender fields is empty
        if(sender == "") {

            notificationError(project, "Uzupełnij pole nadawcy.")
            MainObserver(e, setterFields).show()

        //subscribers field is empty
        } else if(subscribers == "") {

            notificationError(project, "Uzupełnij pole odbiorców.")
            MainObserver(e, setterFields).show()

        //doesn't point any fields to observe
        } else if(fields == "" && setterFields.isEmpty()) {

            notificationError(project, "Uzupełnij pole danych.")
            MainObserver(e, setterFields).show()

        //pass validation and clear fields from data
        } else {

            try {

                //check and create files
                checkIfPatternFilesAreUniqueInPackage(rootPsi, project, sourcePath)
                sourcePath = ""
                sender = ""
                subscribers = ""
                fields = ""

            //catch if files to generate exist in pointed localization
            } catch (exc: IncorrectOperationException) {

                notificationError(project, "Wystąpił błąd. Powtórz generowanie.")

                MainObserver(e, setterFields).show()
            }
        }
    }

    //clear fields from data if canceled dialog
    override fun doCancelAction() {
        super.doCancelAction()

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
                            dir = PsiManager.getInstance(e.project!!).findDirectory(root)
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
            sender = ""
            subscribers = ""
            fields = ""
            rootPath = ""

            notificationInfo(e.project!!, "Anulowano generowanie plików.")
        }
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

        //none class point as entry data
        if(rootPsi == null) {

            //source path is empty for not pointed files in project
            if(sourcePath == "") {

                createFilesForEmptyPath(project)

                addFilesContent(project)

            //user set path name in dialog but not point class in project
            } else {

                createFilesForGivenPath(project)

                addFilesContent(project)
            }

        //mark class from project as entry point
        } else {

                //custom path is empty
                if(sourcePath == "") {

                    createFilesForEmptyPath(project)

                    addFilesContent(project)

                //custom path set by user for selected project file
                } else {

                    createFilesForGivenPath(project)

                    addFilesContent(project)
                }
        }
    }

    private fun createFilesForGivenPath(project: Project) {
        val createFiles = Runnable {

            ApplicationManager.getApplication().assertWriteAccessAllowed()

            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
            var dir = PsiManager.getInstance(project).findDirectory(root)

            //find subdirectory
            if (sourcePath.contains(".")) {
                val packageNameList = sourcePath.split(".")
                packageNameList.forEach {
                    dir = dir!!.findSubdirectory(it)
                }
            } else {
                dir = dir!!.findSubdirectory(sourcePath)
            }

            //generate new empty files
            dir!!.createFile(ipublisher)
            dir!!.createFile(isubscriber)
            dir!!.createFile(sender + ".java")

            val subscribersSplit = subscribers.split("\n")
            for (s in subscribersSplit) {
                dir!!.createFile(s + ".java")
            }
        }
        WriteCommandAction.runWriteCommandAction(project, createFiles)
    }

    private fun createFilesForEmptyPath(project: Project) {
        val createFiles = Runnable {

            ApplicationManager.getApplication().assertWriteAccessAllowed()

            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
            val dir = PsiManager.getInstance(project).findDirectory(root)

            //generate new empty files
            dir!!.createFile(ipublisher)
            dir.createFile(isubscriber)
            dir.createFile(sender + ".java")

            val subscribersSplit = subscribers.split("\n")
            for (s in subscribersSplit) {
                dir.createFile(s + ".java")
            }
        }
        WriteCommandAction.runWriteCommandAction(project, createFiles)
    }

    private fun checkIfPatternFilesAreUniqueInPackage(
        rootPsi: PsiFile?,
        project: Project,
        sourcePath: String
    ) {
        //not file and package selected or
        //file selected but not package
        val res: List<PsiFile>
        if(rootPsi == null && sourcePath == ""
            || rootPsi != null && sourcePath == "") {

            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
            val dir = PsiManager.getInstance(project).findDirectory(root)
            res = dir!!.files
                .filter {
                    it.toString().substring(12, it.toString().length)
                        .equals(sender)
                            || it.toString().substring(12, it.toString().length)
                        .equals(subscribers)
                            || it.toString().substring(12, it.toString().length)
                        .equals(isubscriber)
                            || it.toString().substring(12, it.toString().length)
                        .equals(ipublisher)
                }

        //not file selected but package selected
        //package and file selected
        } else {

            //get project root directory path
            val manager = ModuleManager.getInstance(e.project!!)
            val modules: Array<Module> = manager.modules
            val root = ModuleRootManager.getInstance(modules.first()).sourceRoots.first()
            var dir = PsiManager.getInstance(project).findDirectory(root)

                if(sourcePath.contains(".")) {
                    val packageNameList = Companion.sourcePath.split(".")
                    packageNameList.forEach {
                        dir = dir!!.findSubdirectory(it)
                    }
                } else {
                    dir = dir!!.findSubdirectory(sourcePath)
                }

                res = dir!!.files
                    .filter {
                        it.toString().substring(12, it.toString().length)
                            .equals(sender)
                                || it.toString().substring(12, it.toString().length)
                            .equals(subscribers)
                                || it.toString().substring(12, it.toString().length)
                            .equals(isubscriber)
                                || it.toString().substring(12, it.toString().length)
                            .equals(ipublisher)
                    }
            }

        //duplicated files name exist in directory of pointed class
        if (res.isNotEmpty()) {

            notificationError(project,"Pliki już istnieją. Zmień pakiet.")
            MainObserver(e, setterFields).show()

        //without duplicated file names
        } else {

            createFiles(project, rootPsi)

        }
    }

    private fun addFilesContent(project: Project) {
        publisherInterfaceContent(project)
        subscriberInterfaceContent(project)
        subscriberClassesContent(project)
        publisherClassContent(project)
        notificationWarn(project, "Dodano pliki do projektu.")
    }

    private fun publisherClassContent(project: Project) {
        val addFileContent = Runnable {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            if(sourcePath.contains("/")) sourcePath = sourcePath.replace("/", ".", true)

            val packageName = """
                package ${sourcePath};
                
                
            """.trimIndent()

            var code = """
                        import java.util.ArrayList;
                        import java.util.List;
                        
                        public class ${sender} implements IPublisher {
                            private List<ISubscriber> subscribers;
                        private String info1;
                            
                            public ${sender}() {
                                subscribers = new ArrayList<ISubscriber>();
                            }
                            
                            @Override
                            public void subscribe(ISubscriber s) {
                                subscribers.add(s);
                            }
                            
                            @Override
                            public void unsubscribe(ISubscriber s) {
                                subscribers.remove(s);
                            }
                            
                            @Override
                            public void notification() {
                                for(ISubscriber subscriber : subscribers) {
                                    subscriber.info(info1);
                                }
                            }
                            
                            public void pushNotification(String info) {
                        this.info1 = info;
                                notification();
                            }
                        }
                    """.trimIndent()

            //add package declaration
            if(sourcePath.isNotEmpty())
            code = packageName + code

            var fie = arrayOf<String>()
            if(fields.isNotEmpty()) fie = fields.split("\n").toTypedArray()
            fie = fie.plus(setterFields)
            var toAddCode = ""
            for(f in fie) {
                toAddCode += ("\t" + f + ";\n")
                toAddCode.trimIndent()
            }
            code = code.replace("private String info1;", toAddCode, true)

            toAddCode = ""
            for(f in fie) {
                val firstLast = f.tokenizeByWhitespace()
                val last = firstLast.last()
                if(f == fie.last()) {
                    toAddCode += (last)
                    toAddCode.trimIndent()
                } else {
                    toAddCode += (last + ", ")
                    toAddCode.trimIndent()
                }
            }
            code = code.replace("subscriber.info(info1);", "subscriber.info(" + toAddCode + ");", true)

            toAddCode = ""
            for(f in fie) {
                if(f == fie.last()) {
                    toAddCode += (f)
                    toAddCode.trimIndent()
                } else {
                    toAddCode += (f + ", ")
                    toAddCode.trimIndent()
                }
            }
            code = code.replace("String info", toAddCode, true)

            toAddCode = ""
            for(f in fie) {
                val firstLast = f.tokenizeByWhitespace()
                val last = firstLast.last()
                toAddCode += ("\t\t" + "this." + last + " = " + last + ";\n")
                toAddCode.trimIndent()
            }
            code = code.replace("this.info1 = info;", toAddCode, true)


            //with given or empty package name
            genContentOfClassForProjectRoot(project, code, sender + ".java")

        }
        WriteCommandAction.runWriteCommandAction(project, addFileContent)
    }

    private fun publisherInterfaceContent(project: Project) {
        val addFileContent = Runnable {
            ApplicationManager.getApplication().assertWriteAccessAllowed()

            if(sourcePath.contains("/")) sourcePath = sourcePath.replace("/", ".", true)

            val packageName = """
                package ${sourcePath};
                
                
            """.trimIndent()

            var code = """
                        public interface IPublisher {
                            public void subscribe(ISubscriber s);
                            public void unsubscribe(ISubscriber s);
                            public void notification();
                        }
                    """.trimIndent()

            //add package declaration
            if(sourcePath.isNotEmpty())
                code = packageName + code

            //with given or empty package name
            genContentOfClassForProjectRoot(project, code, ipublisher)

        }
        WriteCommandAction.runWriteCommandAction(project, addFileContent)
    }

    private fun genContentOfClassForProjectRoot(project: Project, code: String, className: String) {
        //get project root directory path
        val manager = ModuleManager.getInstance(e.project!!)
        val modules: Array<Module> = manager.modules
        val root = ModuleRootManager.getInstance(modules.first()).sourceRoots

        //source is empty
        val dir: PsiFile?
        if (sourcePath == "") {
            //get file from relative to root directory path
            dir = PsiManager.getInstance(project)
                .findFile(root.first().findFileByRelativePath(sourcePath + "/" + className)!!)
            //source not empty
        } else {
            //get file from relative to root directory path
            //with nested packages
            if (sourcePath.contains(".")) sourcePath = sourcePath.replace(".", "/")
            dir = PsiManager.getInstance(project)
                .findFile(root.first().findFileByRelativePath(sourcePath + "/" + className)!!)
        }

        //write to file
        val uniqueVirtualFile = dir!!.containingFile.virtualFile
        val documentFile = FileDocumentManager.getInstance().getDocument(uniqueVirtualFile)
        documentFile!!.insertString(0, code)
    }

    private fun subscriberInterfaceContent(project: Project) {

        val addFileContent = Runnable {

            ApplicationManager.getApplication().assertWriteAccessAllowed()

            if(sourcePath.contains("/")) sourcePath = sourcePath.replace("/", ".", true)

            val packageName = """
                package ${sourcePath};
                
                
            """.trimIndent()

            var code = """                        
                        public interface ISubscriber {
                            public void info(String info1);
                        }
                    """.trimIndent()

            //add package declaration
            if(sourcePath.isNotEmpty())
                code = packageName + code

            var fie = arrayOf<String>()
            if(fields.isNotEmpty()) fie = fields.split("\n").toTypedArray()
            fie = fie.plus(setterFields)
            var toAddCode = ""
            for(f in fie) {
                if(f == fie.last()) {
                    toAddCode += (f)
                    toAddCode.trimIndent()
                } else {
                    toAddCode += (f + ", ")
                    toAddCode.trimIndent()
                }
            }
            code = code.replace("String info1", toAddCode, true)

            //with given or empty package name
            genContentOfClassForProjectRoot(project, code, isubscriber)
        }
        WriteCommandAction.runWriteCommandAction(project, addFileContent)
    }

    private fun subscriberClassesContent(project: Project) {

        val subscribers = subscribers.split("\n")

        for(s in subscribers) {

            val addFileContent = Runnable {
                ApplicationManager.getApplication().assertWriteAccessAllowed()

                if(sourcePath.contains("/")) sourcePath = sourcePath.replace("/", ".", true)

                val packageName = """
                package ${sourcePath};
                
                
            """.trimIndent()

                val camelCaseStr = toCamelCase(sender)
                var code = """                        
                        public class ${s} implements ISubscriber {
                        private String info1;
                            private ${sender} ${camelCaseStr};
                            
                            public ${s}(${sender} ${camelCaseStr}) {
                                this.${camelCaseStr} = ${camelCaseStr};
                                ${camelCaseStr}.subscribe(this);
                            }
                             
                            public void info(String info1) {
                        this.info1 = info1;                              
                            }
                        }
                    """.trimIndent()

                //add package declaration
                if(sourcePath.isNotEmpty())
                    code = packageName + code

                var fie = arrayOf<String>()
                if(fields.isNotEmpty()) fie = fields.split("\n").toTypedArray()
                fie = fie.plus(setterFields)
                var toAddCode = ""
                for(f in fie) {
                    toAddCode += ("\t" + f + ";\n")
                    toAddCode.trimIndent()
                }
                code = code.replace("private String info1;", toAddCode, true)

                toAddCode = ""
                for(f in fie) {
                    if(f == fie.last()) {
                        toAddCode += (f)
                        toAddCode.trimIndent()
                    } else {
                        toAddCode += (f + ", ")
                        toAddCode.trimIndent()
                    }
                }
                code = code.replace("String info1", toAddCode, true)

                toAddCode = ""
                for(f in fie) {
                    val firstLast = f.tokenizeByWhitespace()
                    val last = firstLast.last()
                    toAddCode += ("\t\t" + "this." + last + " = " + last + ";\n")
                    toAddCode.trimIndent()
                }
                code = code.replace("this.info1 = info1;", toAddCode, true)

                //with given or empty package name
                genContentOfClassForProjectRoot(project, code, s + ".java")

            }
            WriteCommandAction.runWriteCommandAction(project, addFileContent)

        }

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

            clearFileByName(project, sender)
            clearFileByName(project, ipublisher)
            clearFileByName(project, isubscriber)

            val subscribers = subscribers.split("\n")
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
                    dir!!.checkCreateSubdirectory(sourcePath)
                    dir = dir.createSubdirectory(sourcePath)
                } catch(exc: IncorrectOperationException) {
                    LOG.info("Directory: ${sourcePath} already exist. Add pattern files into: ${sourcePath}")
                }

                dir!!.createFile(ipublisher)
                dir.createFile(isubscriber)
                dir.createFile(sender + ".java")

                val subscribers = subscribers.split("\n")
                for (s in subscribers) {
                    dir.createFile(s + ".java")
                }

            } else {

                if(sourcePath == "") {

                    try {
                        rootPsi.containingDirectory.createFile(ipublisher)
                        rootPsi.containingDirectory.createFile(isubscriber)
                        rootPsi.containingDirectory.createFile(sender + ".java")

                        val subscribers = subscribers.split("\n")
                        for (s in subscribers) {
                            rootPsi.containingDirectory.createFile(s + ".java")
                        }
                    } catch(exc: IncorrectOperationException) {

                        notificationError(project, "Pliki wzorca projektowego 'obserwator' już istnieją w podanej lokalizacji. Zmień lokalizację i powtórz generowanie plików.")
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
                        rootPsi.containingDirectory.checkCreateSubdirectory(sourcePath)
                        rootPsi.containingDirectory.createSubdirectory(sourcePath)
                    } catch(exc: IncorrectOperationException) {
                        LOG.info("Directory: ${sourcePath} already exist. Add pattern files into: ${sourcePath}")
                    }
                    rootPsi.containingDirectory.findSubdirectory(sourcePath)!!.createFile(ipublisher)
                    rootPsi.containingDirectory.findSubdirectory(sourcePath)!!.createFile(isubscriber)
                    rootPsi.containingDirectory.findSubdirectory(sourcePath)!!.createFile(sender + ".java")


                    val subscribers = subscribers.split("\n")
                    for (s in subscribers) {
                        rootPsi.containingDirectory.findSubdirectory(sourcePath)!!.createFile(s + ".java")
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
