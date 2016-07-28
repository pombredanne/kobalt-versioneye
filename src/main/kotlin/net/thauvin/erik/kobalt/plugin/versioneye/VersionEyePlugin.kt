/*
 * VersionEyePlugin.kt
 *
 * Copyright (c) 2016, Erik C. Thauvin (erik@thauvin.net)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *   Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *   Neither the name of this project nor the names of its contributors may be
 *   used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.thauvin.erik.kobalt.plugin.versioneye

import com.beust.kobalt.Plugins
import com.beust.kobalt.TaskResult
import com.beust.kobalt.api.*
import com.beust.kobalt.api.annotation.Directive
import com.beust.kobalt.api.annotation.Task
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.Singleton
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Singleton
class VersionEyePlugin @Inject constructor(val configActor: ConfigActor<VersionEyeConfig>,
                                           val taskContributor: TaskContributor) :
        BasePlugin(), ITaskContributor, IConfigActor<VersionEyeConfig> by configActor {
    private val API_KEY_PROPERTY = "versioneye.apiKey"
    private val PROJECT_KEY_PROPERTY = "versioneye.projectKey"

    private val debug = System.getProperty("debug", "false").toBoolean()
    private val httpClient = OkHttpClient()

    // ITaskContributor
    override fun tasksFor(project: Project, context: KobaltContext): List<DynamicTask> = taskContributor.dynamicTasks

    companion object {
        const val NAME: String = "VersionEye"
    }

    override val name = NAME

    override fun apply(project: Project, context: KobaltContext) {
        super.apply(project, context)
        taskContributor.addVariantTasks(this, project, context, "versionEye", group = "publish",
                runTask = { versionEye(project) })
    }

    @Task(name = "versionEye", description = "Update and check dependencies on VersionEye")
    fun versionEye(project: Project): TaskResult {
        if (debug) {
            log(1, "  Using Fiddler proxy 127.0.0.1:8888")
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("https.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "8888")
            System.setProperty("https.proxyPort", "8888")
        }

        val local = project.directory + "/local.properties"

        configurationFor(project)?.let { config ->
            if (config.baseUrl.isBlank()) {
                warn("Please specify a valid VersionEye base URL.")
                return TaskResult()
            } else {
                val projectKey = System.getProperty(PROJECT_KEY_PROPERTY)
                var apiKey = System.getProperty(API_KEY_PROPERTY)
                val p = Properties()
                Paths.get(local).let { path ->
                    if (path.toFile().exists()) {
                        Files.newInputStream(path).use {
                            p.load(it)
                        }
                    }
                }


                if (apiKey.isNullOrBlank()) {
                    apiKey = p.getProperty(API_KEY_PROPERTY)
                    if (apiKey.isNullOrBlank()) {
                        warn("Please provide a valid VersionEye API key.")
                        return TaskResult()
                    }
                }
                p.setProperty(API_KEY_PROPERTY, apiKey)

                if (!projectKey.isNullOrBlank()) {
                    p.setProperty(PROJECT_KEY_PROPERTY, projectKey)
                }

                val result = versionEyeUpdate(if (config.name.isNotBlank()) {
                    config.name
                } else {
                    project.name
                }, config, p)

                FileOutputStream(local).use { output ->
                    p.store(output, "")
                }

                return result
            }
        }
        return TaskResult()
    }

    private fun versionEyeUpdate(name: String, config: VersionEyeConfig, p: Properties): TaskResult {
        val projectKey = p.getProperty(PROJECT_KEY_PROPERTY)
        val apiKey = p.getProperty(API_KEY_PROPERTY)
        val endPoint = if (projectKey.isNullOrBlank()) {
            "api/v2/projects"
        } else {
            "api/v2/project/$projectKey"
        }

        val file = File("../kobaltBuild/libs/kobalt-versioneye-0.4.0-beta.pom")
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", name)
                .addFormDataPart("upload", file.name,
                        RequestBody.create(MediaType.parse("application/octet-stream"), file))

        val hasOrg = config.org.isNotBlank()

        if (hasOrg) {
            requestBody.addFormDataPart("orga_name", config.org)
        }

        if (config.team.isNotBlank()) {
            if (hasOrg) {
                requestBody.addFormDataPart("team_name", config.team)
            } else {
                warn("You must provide a VersionEye project organisation in order to specify a team.")
            }
        }

        if (debug) {
            requestBody.addFormDataPart("temp", "true")
        }

        val url = HttpUrl.parse(config.baseUrl).newBuilder()
                .addPathSegments(endPoint)
                .setQueryParameter("api_key", apiKey)
                .build()
        val request = Request.Builder()
                .url(url)
                .post(requestBody.build())
                .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            warn("Unexpected response from VersionEye: " + response)
            return TaskResult()
        } else {
            val builder = GsonBuilder()
            val o = builder.create().fromJson(response.body().charStream(), JsonObject::class.java)
            println(o)
        }
        return TaskResult()
    }
}

@Directive
class VersionEyeConfig() {
    var baseUrl = "https://www.versioneye.com/"
    var failOnUnknownLicense = false
    var licenseCheck = false
    var name = ""
    var org = ""
    var securityCheck = false
    var team = ""
    var visibility = true
}

@Directive
fun Project.versionEye(init: VersionEyeConfig.() -> Unit) {
    VersionEyeConfig().let { config ->
        config.init()
        (Plugins.findPlugin(VersionEyePlugin.NAME) as VersionEyePlugin).addConfiguration(this, config)
    }
}
