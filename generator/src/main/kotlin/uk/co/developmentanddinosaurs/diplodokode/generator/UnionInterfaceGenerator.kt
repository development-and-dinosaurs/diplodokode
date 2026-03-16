package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName

internal class UnionInterfaceGenerator(private val config: GeneratorConfig) {

    fun generate(arity: Int): FileSpec {
        val typeName = "Union$arity"
        val typeParams = (1..arity).map { TypeVariableName(variantLetter(it)) }
        val rTypeVar = TypeVariableName("R")

        val interfaceBuilder = TypeSpec.interfaceBuilder(typeName)
            .addTypeVariables(typeParams)

        // Abstract fold
        val foldBuilder = FunSpec.builder("fold")
            .addModifiers(KModifier.ABSTRACT)
            .addTypeVariable(rTypeVar)
        typeParams.forEachIndexed { i, typeParam ->
            val lambdaType = LambdaTypeName.get(
                parameters = listOf(ParameterSpec("", typeParam)),
                returnType = rTypeVar,
            )
            foldBuilder.addParameter("on${ordinalName(i + 1)}", lambdaType)
        }
        foldBuilder.returns(rTypeVar)
        interfaceBuilder.addFunction(foldBuilder.build())

        // {ordinal}OrNull and {ordinal} accessors
        typeParams.forEachIndexed { i, typeParam ->
            val ordinal = ordinalName(i + 1)
            val methodName = ordinal.lowercase()
            val args = (1..arity).joinToString(", ") { j -> if (j == i + 1) "{ it }" else "{ null }" }

            interfaceBuilder.addFunction(
                FunSpec.builder("${methodName}OrNull")
                    .returns(typeParam.copy(nullable = true))
                    .addCode("return fold($args)\n")
                    .build()
            )

            interfaceBuilder.addFunction(
                FunSpec.builder(methodName)
                    .returns(typeParam)
                    .addCode("return ${methodName}OrNull() ?: error(\"Expected $ordinal variant of $typeName\")\n")
                    .build()
            )
        }

        return FileSpec.builder(config.packageName, typeName)
            .addType(interfaceBuilder.build())
            .build()
    }

    private fun variantLetter(idx: Int) = ('A' + idx - 1).toString()
}

internal fun ordinalName(idx: Int) = when (idx) {
    1 -> "First"
    2 -> "Second"
    3 -> "Third"
    4 -> "Fourth"
    else -> "Variant$idx"
}
