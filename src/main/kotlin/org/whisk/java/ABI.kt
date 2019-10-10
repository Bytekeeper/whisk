package org.whisk.java

import org.apache.logging.log4j.LogManager
import org.objectweb.asm.*
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * https://docs.oracle.com/javase/specs/jls/se7/html/jls-13.html#jls-13.1
 */
class ABI @Inject constructor() {
    private val log = LogManager.getLogger()

    fun toReducedABIClass(path: Path): ByteArray {
        val reader = ClassReader(Files.readAllBytes(path))
        val classWriter = ClassWriter(0)

        val filteredVisitor = object : ClassVisitor(Opcodes.ASM7, classWriter) {
            override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
                if (couldBeAccessedByDependends(access))
                    super.visitInnerClass(name, outerName, innerName, access)
            }

            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? =
                    if (couldBeAccessedByDependends(access))
                        super.visitField(access, name, descriptor, signature, value)
                    else {
                        null
                    }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? =
                    if (couldBeAccessedByDependends(access))
                        super.visitMethod(access, name, descriptor, signature, exceptions)
                    else {
                        null
                    }
        }
        reader.accept(filteredVisitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return classWriter.toByteArray()
    }

    private fun couldBeAccessedByDependends(access: Int) = access and Opcodes.ACC_PRIVATE == 0

    private val debugClassVisitor = object : ClassVisitor(Opcodes.ASM7) {
        override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor? {
            return super.visitModule(name, access, version)
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            if (couldBeAccessedByDependends(access)) {
                println("$name: $descriptor")
            }
            return null
        }

        override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
            super.visitInnerClass(name, outerName, innerName, access)
        }

        override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            if (couldBeAccessedByDependends(access)) {
                println("$name(): $descriptor")
            }
            return null
        }
    }
}