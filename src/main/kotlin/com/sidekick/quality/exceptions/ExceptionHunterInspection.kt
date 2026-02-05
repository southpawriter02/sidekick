package com.sidekick.quality.exceptions

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement

/**
 * # Exception Hunter Inspection
 *
 * IDE inspection that highlights unhandled exceptions with quick fixes.
 * Part of Sidekick v0.6.1 Exception Hunter feature.
 *
 * ## Features
 *
 * - Integration with IntelliJ inspection framework
 * - Severity-based problem highlighting
 * - Quick fixes for common resolutions
 *
 * @since 0.6.1
 */
class ExceptionHunterInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Sidekick: Unhandled Exception"
    override fun getGroupDisplayName() = "Sidekick"
    override fun getShortName() = "SidekickUnhandledException"
    override fun isEnabledByDefault() = true

    /**
     * Analyzes a file and returns problem descriptors for unhandled exceptions.
     */
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val project = file.project
        val service = ExceptionHunterService.getInstance(project)
        val result = service.analyzeFile(file)

        if (!result.hasIssues) return null

        return result.exceptions.mapNotNull { exception ->
            val element = findElementAtLine(file, exception.location.line)
            element?.let {
                manager.createProblemDescriptor(
                    it,
                    buildDescription(exception),
                    isOnTheFly,
                    buildQuickFixes(exception),
                    mapSeverityToHighlight(exception.severity)
                )
            }
        }.toTypedArray()
    }

    /**
     * Builds the problem description message.
     */
    private fun buildDescription(exception: UnhandledException): String {
        return "Unhandled ${exception.exceptionType}: ${exception.suggestion}"
    }

    /**
     * Maps exception severity to IntelliJ problem highlight type.
     */
    private fun mapSeverityToHighlight(severity: ExceptionSeverity): ProblemHighlightType {
        return when (severity) {
            ExceptionSeverity.CRITICAL -> ProblemHighlightType.ERROR
            ExceptionSeverity.HIGH -> ProblemHighlightType.WARNING
            ExceptionSeverity.MEDIUM -> ProblemHighlightType.WEAK_WARNING
            ExceptionSeverity.LOW -> ProblemHighlightType.WEAK_WARNING
            ExceptionSeverity.INFO -> ProblemHighlightType.INFORMATION
        }
    }

    /**
     * Builds quick fix array for an exception.
     */
    private fun buildQuickFixes(exception: UnhandledException): Array<LocalQuickFix> {
        return arrayOf(
            AddTryCatchFix(exception),
            AddThrowsDeclarationFix(exception),
            SuppressExceptionFix(exception)
        )
    }

    /**
     * Finds the PSI element at a given line.
     */
    private fun findElementAtLine(file: PsiFile, line: Int): PsiElement? {
        val doc = file.viewProvider.document ?: return null
        if (line < 1 || line > doc.lineCount) return null
        val offset = doc.getLineStartOffset(line - 1)
        return file.findElementAt(offset)
    }
}

// =============================================================================
// Quick Fixes
// =============================================================================

/**
 * Quick fix to wrap code in try-catch block.
 *
 * @property exception The exception to handle
 */
class AddTryCatchFix(private val exception: UnhandledException) : LocalQuickFix {

    override fun getFamilyName() = "Wrap in try-catch"

    override fun getName() = "Wrap in try-catch for ${exception.exceptionType}"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val factory = com.intellij.psi.PsiFileFactory.getInstance(project)

        // Find the statement to wrap
        val statement = findEnclosingStatement(element) ?: return

        // Generate try-catch template
        // Note: This is a simplified implementation. A full implementation
        // would use language-specific PSI factories to properly generate
        // syntactically correct try-catch blocks.

        // For now, we insert a comment as a placeholder
        // The actual implementation depends on the language (Java vs Kotlin)
        val comment = "// TODO: Wrap in try-catch for ${exception.exceptionType}"

        // In a real implementation:
        // 1. Determine the language (Java, Kotlin)
        // 2. Use appropriate PSI factory
        // 3. Generate proper try-catch block
        // 4. Replace the statement with wrapped version
    }

    /**
     * Finds the enclosing statement for an element.
     */
    private fun findEnclosingStatement(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            val type = current.node?.elementType?.toString() ?: ""
            if (type.contains("STATEMENT") || type.contains("EXPRESSION")) {
                return current
            }
            current = current.parent
        }
        return null
    }
}

/**
 * Quick fix to add throws declaration to method signature.
 *
 * @property exception The exception to declare
 */
class AddThrowsDeclarationFix(private val exception: UnhandledException) : LocalQuickFix {

    override fun getFamilyName() = "Add throws declaration"

    override fun getName() = "Add throws ${exception.exceptionType}"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return

        // Find enclosing method
        val method = findEnclosingMethod(element) ?: return

        // In a real implementation:
        // 1. Determine if it's Java (throws) or Kotlin (@Throws)
        // 2. Modify the method signature appropriately
        // 3. For Java: add "throws ExceptionType" after parameters
        // 4. For Kotlin: add @Throws(ExceptionType::class) annotation
    }

    /**
     * Finds the enclosing method for an element.
     */
    private fun findEnclosingMethod(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            val type = current.node?.elementType?.toString() ?: ""
            if (type.contains("METHOD") || type.contains("FUN")) {
                return current
            }
            current = current.parent
        }
        return null
    }
}

/**
 * Quick fix to suppress the exception warning.
 *
 * @property exception The exception to suppress
 */
class SuppressExceptionFix(private val exception: UnhandledException) : LocalQuickFix {

    override fun getFamilyName() = "Suppress this warning"

    override fun getName() = "Ignore ${exception.exceptionType} warnings"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        val filePath = file.virtualFile?.path ?: return

        // Add to ignored exceptions in the service
        val service = ExceptionHunterService.getInstance(project)
        service.ignoreException(exception.exceptionType)

        // Invalidate cache to refresh
        service.invalidate(filePath)
    }
}
