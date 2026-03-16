package uk.co.developmentanddinosaurs.diplodokode.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import uk.co.developmentanddinosaurs.diplodokode.generator.openapi.Schema

private data class VariantInfo(
    val kotlinType: TypeName,
    val wrapperName: String,
    val encodeCall: String,
    val decodeCondition: String,
    val decodeExtract: String,
)

private const val KOTLINX_SERIALIZATION = "kotlinx.serialization"
private const val KOTLINX_SERIALIZATION_DESCRIPTORS = "kotlinx.serialization.descriptors"

internal class PrimitiveUnionGenerator(private val config: GeneratorConfig) {

    fun generate(name: String, schema: Schema): FileSpec {
        val interfaceName = config.namingStrategy.className(name)
        val interfaceClassName = ClassName(config.packageName, interfaceName)
        val serializerClassName = ClassName(config.packageName, "${interfaceName}Serializer")

        val variants = schema.oneOf!!
            .sortedBy { PRIMITIVE_DECODE_PRIORITY[it.type] ?: Int.MAX_VALUE }
            .mapNotNull { variantInfoFor(it.type, config.typeMappingStrategy) }

        val interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)
            .addModifiers(KModifier.SEALED)

        if (config.serialisationStrategy != null) {
            interfaceBuilder.addAnnotation(
                AnnotationSpec.builder(ClassName(KOTLINX_SERIALIZATION, "Serializable"))
                    .addMember("with = %T::class", serializerClassName)
                    .build()
            )
        }

        variants.forEach { variant ->
            interfaceBuilder.addType(buildValueClass(variant, interfaceClassName))
        }

        val fileBuilder = FileSpec.builder(config.packageName, interfaceName)
            .addType(interfaceBuilder.build())

        if (config.serialisationStrategy != null) {
            fileBuilder.addType(buildSerializer(interfaceName, interfaceClassName, variants))
        }

        return fileBuilder.build()
    }

    private fun buildValueClass(variant: VariantInfo, interfaceClassName: ClassName): TypeSpec =
        TypeSpec.classBuilder(variant.wrapperName)
            .addModifiers(KModifier.VALUE)
            .addAnnotation(JvmInline::class)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("value", variant.kotlinType)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("value", variant.kotlinType)
                    .initializer("value")
                    .build()
            )
            .addSuperinterface(interfaceClassName)
            .build()

    private fun buildSerializer(
        interfaceName: String,
        interfaceClassName: ClassName,
        variants: List<VariantInfo>,
    ): TypeSpec {
        val kSerializerType = ClassName(KOTLINX_SERIALIZATION, "KSerializer").parameterizedBy(interfaceClassName)
        val serialDescriptorType = ClassName(KOTLINX_SERIALIZATION_DESCRIPTORS, "SerialDescriptor")

        return TypeSpec.objectBuilder("${interfaceName}Serializer")
            .addSuperinterface(kSerializerType)
            .addProperty(buildDescriptorProperty(interfaceName, serialDescriptorType))
            .addFunction(buildSerializeFunction(interfaceClassName, variants))
            .addFunction(buildDeserializeFunction(interfaceName, interfaceClassName, variants))
            .build()
    }

    private fun buildDescriptorProperty(interfaceName: String, serialDescriptorType: TypeName): PropertySpec =
        PropertySpec.builder("descriptor", serialDescriptorType)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(
                "%T(%S, %T.STRING)",
                ClassName(KOTLINX_SERIALIZATION_DESCRIPTORS, "PrimitiveSerialDescriptor"),
                interfaceName,
                ClassName(KOTLINX_SERIALIZATION_DESCRIPTORS, "PrimitiveKind"),
            )
            .build()

    private fun buildSerializeFunction(interfaceClassName: ClassName, variants: List<VariantInfo>): FunSpec {
        val code = CodeBlock.builder()
        code.beginControlFlow("when (value)")
        variants.forEach { variant ->
            code.addStatement(
                "is %T.%L -> encoder.%L(value.value)",
                interfaceClassName,
                variant.wrapperName,
                variant.encodeCall,
            )
        }
        code.endControlFlow()

        return FunSpec.builder("serialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("encoder", ClassName("kotlinx.serialization.encoding", "Encoder"))
            .addParameter("value", interfaceClassName)
            .addCode(code.build())
            .build()
    }

    private fun buildDeserializeFunction(
        interfaceName: String,
        interfaceClassName: ClassName,
        variants: List<VariantInfo>,
    ): FunSpec {
        val jsonDecoderType = ClassName("kotlinx.serialization.json", "JsonDecoder")
        val jsonPrimitiveType = ClassName("kotlinx.serialization.json", "JsonPrimitive")
        val serializationExceptionType = ClassName(KOTLINX_SERIALIZATION, "SerializationException")

        val code = CodeBlock.builder()
        code.addStatement("val element = (decoder as %T).decodeJsonElement()", jsonDecoderType)
        code.addStatement(
            "if (element !is %T) throw %T(%S)",
            jsonPrimitiveType,
            serializationExceptionType,
            "Expected a primitive value for $interfaceName",
        )
        code.beginControlFlow("return when")
        variants.dropLast(1).forEach { variant ->
            code.addStatement(
                "%L -> %T.%L(element.%L)",
                variant.decodeCondition,
                interfaceClassName,
                variant.wrapperName,
                variant.decodeExtract,
            )
        }
        val last = variants.last()
        code.addStatement(
            "else -> %T.%L(element.%L)",
            interfaceClassName,
            last.wrapperName,
            last.decodeExtract,
        )
        code.endControlFlow()

        return FunSpec.builder("deserialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("decoder", ClassName("kotlinx.serialization.encoding", "Decoder"))
            .returns(interfaceClassName)
            .addCode(code.build())
            .build()
    }
}

private fun variantInfoFor(openApiType: String?, typeMappingStrategy: TypeMappingStrategy): VariantInfo? {
    val type = openApiType ?: return null
    val kotlinType = typeMappingStrategy.resolve(type, null) ?: return null
    val simpleName = (kotlinType as? ClassName)?.simpleName ?: return null
    return when (type) {
        "string" -> VariantInfo(kotlinType, "${simpleName}Value", "encodeString", "element.isString", "content")
        "boolean" -> VariantInfo(kotlinType, "${simpleName}Value", "encodeBoolean", "!element.isString && element.content.toBooleanStrictOrNull() != null", "content.toBooleanStrict()")
        "integer" -> VariantInfo(kotlinType, "${simpleName}Value", "encodeInt", "!element.isString && element.content.toIntOrNull() != null", "content.toInt()")
        "number" -> VariantInfo(kotlinType, "${simpleName}Value", "encodeDouble", "!element.isString && element.content.toDoubleOrNull() != null", "content.toDouble()")
        else -> null
    }
}
