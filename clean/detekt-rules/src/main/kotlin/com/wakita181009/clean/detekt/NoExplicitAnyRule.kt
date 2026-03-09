package com.wakita181009.clean.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import java.net.URI

/**
 * Forbids explicit use of `Any` or `Any?` in type declarations.
 *
 * Using `Any` erases type information and defeats the type-safe, FP-oriented
 * design of this project. Use specific types, generics, or sealed interfaces instead.
 *
 * Allowed exceptions:
 * - Generic upper-bound constraints: `<T : Any>` (idiomatic Kotlin for non-nullable bounds)
 * - Test files
 */
class NoExplicitAnyRule(config: Config) : Rule(
    config,
    "Explicit use of Any/Any? is forbidden. Use specific types, generics, or sealed interfaces instead.",
    URI("https://github.com/wakita181009/clean-architecture-kotlin-in-practice"),
) {

    override fun visitTypeReference(typeReference: KtTypeReference) {
        super.visitTypeReference(typeReference)

        val file = typeReference.containingKtFile
        val filePath = file.virtualFilePath
        if (filePath.contains("/test/") || filePath.contains("/build/")) return

        val text = typeReference.text
        if (text != "Any" && text != "Any?") return

        // Allow generic upper-bound constraints: <T : Any>
        if (typeReference.parent is KtTypeParameter) return

        report(
            Finding(
                Entity.from(typeReference),
                "Explicit use of '$text' detected. Use a specific type, generic parameter, or sealed interface instead.",
            ),
        )
    }
}
