package com.jakewharton.diffuse.format

import com.jakewharton.diffuse.io.Input
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassModel
import java.lang.classfile.constantpool.MethodHandleEntry
import java.lang.classfile.instruction.FieldInstruction
import java.lang.classfile.instruction.InvokeDynamicInstruction
import java.lang.classfile.instruction.InvokeInstruction
import kotlin.jvm.optionals.getOrNull

@Suppress("unused") // Used by Multi-Release JARs for Java 24+.
internal fun Input.toClassImpl(): Class {
  val classModel = ClassFile.of().parse(toByteArray())
  val type = TypeDescriptor("L${classModel.thisClass().asInternalName()};")
  val (declaredMembers, referencedMembers) = classModel.parseMembers(type)
  return Class(type, declaredMembers.sorted(), referencedMembers.sorted())
}

private fun ClassModel.parseMembers(type: TypeDescriptor): Pair<List<Member>, Set<Member>> {
  val declaredMembers = mutableListOf<Member>()
  val referencedMembers = mutableSetOf<Member>()

  for (field in fields()) {
    declaredMembers +=
      Field(
        type,
        field.fieldName().stringValue(),
        TypeDescriptor(field.fieldTypeSymbol().descriptorString()),
      )
  }

  for (method in methods()) {
    declaredMembers +=
      parseMethod(
        type,
        method.methodName().stringValue(),
        method.methodTypeSymbol().descriptorString(),
      )

    method.code().getOrNull()?.let { codeModel ->
      for (instruction in codeModel) {
        when (instruction) {
          is FieldInstruction -> {
            val ownerType = parseOwner(instruction.owner().name().stringValue())
            val name = instruction.name().stringValue()
            val descriptor = instruction.type().stringValue()
            referencedMembers += Field(ownerType, name, TypeDescriptor(descriptor))
          }
          is InvokeInstruction -> {
            val ownerType = parseOwner(instruction.owner().name().stringValue())
            val name = instruction.name().stringValue()
            val descriptor = instruction.type().stringValue()
            referencedMembers += parseMethod(ownerType, name, descriptor)
          }
          is InvokeDynamicInstruction -> {
            val bootstrapMethodEntry = instruction.invokedynamic().bootstrap()
            referencedMembers += parseHandle(bootstrapMethodEntry.bootstrapMethod())

            if (
              bootstrapMethodEntry.bootstrapMethod().reference().owner().name().stringValue() ==
                "java/lang/invoke/LambdaMetafactory" &&
                bootstrapMethodEntry.bootstrapMethod().reference().name().stringValue() ==
                  "metafactory"
            ) {
              // LambdaMetaFactory.metafactory accepts 6 arguments. The first 3 are
              // provided automatically and the latter 3 are supplied as the arguments to
              // this method. The second of those is a MethodHandle to the lambda
              // implementation which needs to be counted as a method reference.
              val implementationHandle = bootstrapMethodEntry.arguments()[1] as MethodHandleEntry
              referencedMembers += parseHandle(implementationHandle)
            }
          }
          else -> Unit
        }
      }
    }
  }

  return declaredMembers to referencedMembers
}

private fun parseHandle(handle: MethodHandleEntry): Member {
  val ref = handle.reference()
  val handlerOwner = parseOwner(ref.owner().name().stringValue())
  val handlerName = ref.name().stringValue()
  val handlerDescriptor = ref.type().stringValue()
  return if (handlerDescriptor.startsWith('(')) {
    parseMethod(handlerOwner, handlerName, handlerDescriptor)
  } else {
    Field(handlerOwner, handlerName, TypeDescriptor(handlerDescriptor))
  }
}

private fun parseOwner(owner: String): TypeDescriptor {
  val ownerDescriptor = if (owner.startsWith('[')) owner else "L$owner;"
  return TypeDescriptor(ownerDescriptor)
}

@Suppress("DuplicatedCode") // Reuse this function by internal will cause NoSuchMethodError.
private fun parseMethod(owner: TypeDescriptor, name: String, descriptor: String): Method {
  val parameterTypes = mutableListOf<TypeDescriptor>()
  var i = 1
  while (true) {
    if (descriptor[i] == ')') {
      break
    }
    var typeIndex = i
    while (descriptor[typeIndex] == '[') {
      typeIndex++
    }
    val end =
      if (descriptor[typeIndex] == 'L') {
        descriptor.indexOf(';', startIndex = typeIndex)
      } else {
        typeIndex
      }
    val parameterDescriptor = descriptor.substring(i, end + 1)
    parameterTypes += TypeDescriptor(parameterDescriptor)
    i += parameterDescriptor.length
  }
  val returnType = TypeDescriptor(descriptor.substring(i + 1))
  return Method(owner, name, parameterTypes, returnType)
}
