/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package com.beust.kash.script

import com.beust.kash.DotKashJsonReader
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import java.io.File
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.jvm.JvmScriptCompilationConfigurationBuilder
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl

class ScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {

    private val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<ScriptDefinition>()
    private val evaluationConfiguration = createJvmEvaluationConfigurationFromTemplate<ScriptDefinition>()

    override fun getExtensions(): List<String> = listOf("kash.kts")

    @Synchronized
    protected fun JvmScriptCompilationConfigurationBuilder.calculateClasspath() {
        //
        // Read ~/.kash.json, configure the classpath of the script engine
        //
        val userClasspath = DotKashJsonReader.dotKash?.classPath?.map { File(it) } ?: emptyList()

        val currentClassLoader = Thread.currentThread().contextClassLoader
        val classPath =
            scriptCompilationClasspathFromContext(
                classLoader = currentClassLoader,
                wholeClasspath = true,
                unpackJarCollections = true
            ) + userClasspath
        updateClasspath(classPath)
    }

    override fun getScriptEngine(): ScriptEngine =
        KotlinJsr223ScriptEngineImpl(
            this,
            ScriptCompilationConfiguration(compilationConfiguration) {
                jvm {
                    calculateClasspath()
                }
            },
            evaluationConfiguration,
            getScriptArgs = { context: ScriptContext ->
                ScriptArgsWithTypes(
                    arrayOf(context.getBindings(ScriptContext.ENGINE_SCOPE).orEmpty()),
                    arrayOf(Bindings::class)
                )
            }
        )
}

