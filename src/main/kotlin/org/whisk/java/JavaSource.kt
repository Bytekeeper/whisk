package org.whisk.java

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.stmt.BlockStmt
import java.nio.file.Path
import javax.inject.Inject
import kotlin.streams.asSequence

class JavaSource @Inject constructor() {
    fun test(path: Path) {
        val compilationUnit = StaticJavaParser.parse(path)
        compilationUnit.findAll(MethodDeclaration::class.java)
                .filter {
                    !it.isPrivate
                }
                .forEach {
                    val x = it.stream(Node.TreeTraversal.DIRECT_CHILDREN)
                            .asSequence()
                            .filter { it !is BlockStmt }.map { it.toString() }
                            .joinToString()
//                    println(x)
                }
        compilationUnit.findAll(FieldDeclaration::class.java)
                .filter {
                    !it.isPrivate
                }.forEach {
                    //                    println(it)
                }
        compilationUnit.findAll(FieldAccessExpr::class.java)
                .forEach {
                    //                    println(it)
                }
    }
}