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
import com.beust.kobalt.misc.KobaltLogger
import com.beust.kobalt.misc.log
import com.beust.kobalt.misc.warn
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.Singleton
import okhttp3.*
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
    private val COLORS_PROPERTY = "ve.colors"
    private val VERBOSE_PROPERTY = "ve.verbose"
    private val QUIET_PROPERTY = "ve.quiet"

    private val debug = System.getProperty("ve.debug", "false").toBoolean()
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

        // Load configuration
        configurationFor(project)?.let { config ->
            if (config.baseUrl.isBlank()) {
                warn("Please specify a valid VersionEye base URL.")
                return TaskResult()
            } else {
                // Load properties
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

                // API key?
                if (apiKey.isNullOrBlank()) {
                    apiKey = p.getProperty(API_KEY_PROPERTY)
                    if (apiKey.isNullOrBlank()) {
                        warn("Please provide a valid VersionEye API key.")
                        return TaskResult()
                    }
                }
                p.setProperty(API_KEY_PROPERTY, apiKey)

                // Project key?
                if (!projectKey.isNullOrBlank()) {
                    p.setProperty(PROJECT_KEY_PROPERTY, projectKey)
                }

                // Config parameters
                config.colors = System.getProperty(COLORS_PROPERTY, config.colors.toString()).toBoolean()
                config.verbose = System.getProperty(VERBOSE_PROPERTY, config.verbose.toString()).toBoolean()
                config.quiet = System.getProperty(QUIET_PROPERTY, config.quiet.toString()).toBoolean()

                // Get pom & proceed with update
                val pom = context.generatePom(project)
                val result = versionEyeUpdate(if (config.name.isNotBlank()) {
                    config.name
                } else {
                    project.name
                }, config, p, pom)

                // Save properties
                FileOutputStream(local).use { output ->
                    p.store(output, "")
                }

                return result
            }
        }
        return TaskResult()
    }

    private fun versionEyeUpdate(name: String, config: VersionEyeConfig, p: Properties, pom: String): TaskResult {
        val projectKey = p.getProperty(PROJECT_KEY_PROPERTY)
        val apiKey = p.getProperty(API_KEY_PROPERTY)
        val filePartName: String
        val endPoint: String

        // Set endpoint
        if (projectKey.isNullOrBlank()) {
            endPoint = "api/v2/projects"
            filePartName = "upload"
        } else {
            endPoint = "api/v2/projects/$projectKey"
            filePartName = "project_file"
        }

        // Build request body
        val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", name)
                .addFormDataPart(filePartName, "${config.name}.pom",
                        RequestBody.create(MediaType.parse("application/octet-stream"), pom))

        // Set organisation
        val hasOrg = config.org.isNotBlank()

        if (hasOrg) {
            requestBody.addFormDataPart("orga_name", config.org)
        }

        // Set team
        if (config.team.isNotBlank()) {
            if (hasOrg) {
                requestBody.addFormDataPart("team_name", config.team)
            } else {
                warn("You must provide a VersionEye project organisation in order to specify a team.")
            }
        }

        // Set visibility
        if (config.visibility.isNotBlank()) {
            if (config.visibility.equals("private", true)) {
                requestBody.addFormDataPart("visibility", "private")
            } else if (config.visibility.equals("public", true)) {
                requestBody.addFormDataPart("visibility", "public")
            }
        }

        if (debug) {
            requestBody.addFormDataPart("temp", "true")
        }

        // Build request
        val url = HttpUrl.parse(config.baseUrl).newBuilder()
                .addPathSegments(endPoint)
                .setQueryParameter("api_key", apiKey)
                .build()
        val request = Request.Builder()
                .url(url)
                .post(requestBody.build())
                .build()

        // Execute and handle request
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            warn("Unexpected response from VersionEye: " + response)
            return TaskResult()
        } else {
            // Parse json response
            val builder = GsonBuilder()
            val o = builder.create().fromJson(response.body().charStream(), JsonObject::class.java)

            // Get project key
            if (projectKey.isNullOrBlank()) {
                p.setProperty(PROJECT_KEY_PROPERTY, o.get("id").asString)
            }

            // Get deps, license and security counts
            val dep_number = o.get("dep_number").asInt
            val out_number = o.get("out_number").asInt
            val licenses_red = o.get("licenses_red").asInt
            val licenses_unknown = o.get("licenses_unknown").asInt
            val sv_count = o.get("sv_count").asInt

            // Sets deps, license and security failures
            val isFailDeps = Utils.isFail(config.failSet, Fail.dependenciesCheck)
            val isFailLicense = Utils.isFail(config.failSet, Fail.licensesCheck)
            val isFailUnknown = Utils.isFail(config.failSet, Fail.licensesUnknownCheck)
            val isFailSecurity = Utils.isFail(config.failSet, Fail.securityCheck)

            // Do nothing if quiet
            if (!config.quiet) {
                val lf = System.getProperty("line.separator")
                val depsInfo = StringBuilder()
                val licensesInfo = StringBuilder()
                val securityInfo = StringBuilder()

                // Parse dependencies
                o.getAsJsonArray("dependencies").forEach {
                    val dep = it.asJsonObject
                    val depName = dep.get("name").asString
                    val curVer = dep.get("version_current")

                    // Outdated dependencies
                    if (dep.get("outdated").asBoolean) {
                        if (!curVer.isJsonNull) {
                            if (depsInfo.isNotEmpty()) {
                                depsInfo.append(lf)
                            }
                            depsInfo.append(Utils.redLight("    - $depName -> "
                                    + curVer.asString, out_number, isFailDeps, config.colors))
                        }
                    }

                    // Parse licenses
                    var whitelisted: Int = 0
                    var unknowns: Int = 0
                    val licenses = dep.get("licenses").asJsonArray
                    if (licenses.size() > 0) {
                        licenses.forEach {
                            val license = it.asJsonObject
                            val onWhitelist = license.get("on_whitelist")
                            val onCwl = license.get("on_cwl")
                            if (!onWhitelist.isJsonNull) {
                                if (onWhitelist.asString.equals("false")) {
                                    if (onCwl.isJsonNull) {
                                        whitelisted++
                                    } else if (!onCwl.toString().equals("true")) {
                                        whitelisted++
                                    }
                                }
                            }
                        }
                    } else {
                        unknowns++
                    }

                    // Whitelisted
                    if (whitelisted > 0) {
                        if (licensesInfo.isNotEmpty()) {
                            licensesInfo.append(lf)
                        }
                        licensesInfo.append(Utils.redLight("    - $depName: $whitelisted whitelist "
                                + Utils.plural("violation", whitelisted, "s"), whitelisted, isFailLicense, config.colors))
                    }

                    // Unknowns
                    if (unknowns > 0) {
                        if (licensesInfo.isNotEmpty()) {
                            licensesInfo.append(lf)
                        }
                        licensesInfo.append(Utils.redLight("    - $depName: $unknowns "
                                + Utils.plural("unknown license", unknowns, "s"), unknowns, isFailUnknown, config.colors))
                    }

                    // Security vulnerabilities
                    val security = dep.get("security_vulnerabilities")
                    if (!security.isJsonNull) {
                        if (securityInfo.length > 0) {
                            securityInfo.append(lf)
                        }
                        val count = security.asJsonArray.size()

                        securityInfo.append(Utils.redLight("    - $depName: $count "
                                + Utils.plural("known issue", count, "s"), count, isFailSecurity, config.colors))
                    }
                }

                // Non-verbose failure
                val verbose = (KobaltLogger.LOG_LEVEL > 1 || config.verbose)
                val alt = " [FAILED]"

                // Log dependencies check results
                log(1, "  Dependencies: "
                        + Utils.redLight(out_number, isFailDeps, config.colors) + " outdated of $dep_number total"
                        + if (isFailDeps && !config.colors) alt else "")
                Utils.log(depsInfo, verbose)

                // Log licenses check results
                log(1, "  Licenses: "
                        + Utils.redLight(licenses_red, isFailLicense, config.colors)
                        + " whitelist, "
                        + Utils.redLight(licenses_unknown, isFailUnknown, config.colors)
                        + Utils.plural(" unknown", licenses_unknown, "s")
                        + if ((isFailLicense || isFailUnknown) && !config.colors) alt else "")
                Utils.log(licensesInfo, verbose)

                // Log security check results
                log(1, "  Security: "
                        + Utils.redLight(sv_count, isFailSecurity, config.colors)
                        + ' '
                        + Utils.plural("vulnerabilit", sv_count, "ies", "y")
                        + if (isFailSecurity && !config.colors) alt else "")
                Utils.log(securityInfo, verbose)

                // Show project url
                val baseUrl = if (config.baseUrl.endsWith('/')) config.baseUrl else config.baseUrl + '/'
                log(1, "  View more at: ${baseUrl}user/projects/$projectKey")
            }

            // Task failure
            if (out_number > 0 && isFailDeps
                    || licenses_red > 0 && isFailLicense
                    || licenses_unknown > 0 && isFailUnknown
                    || sv_count > 0 && isFailSecurity) {
                return TaskResult(false)
            }
        }
        return TaskResult()
    }
}

enum class Fail {
    dependenciesCheck, licensesUnknownCheck, licensesCheck, securityCheck
}

@Directive
class VersionEyeConfig() {
    var baseUrl = "https://www.versioneye.com/"
    var colors = true
    val failSet: MutableSet<Fail> = mutableSetOf(Fail.securityCheck)
    var name = ""
    var org = ""
    var quiet = false
    var team = ""
    var verbose = true
    var visibility = "public"

    fun failOn(vararg args: Fail) {
        if (failSet.isNotEmpty()) {
            failSet.clear()
        }
        args.forEach {
            failSet.add(it)
        }
    }
}

@Directive
fun Project.versionEye(init: VersionEyeConfig.() -> Unit) {
    VersionEyeConfig().let { config ->
        config.init()
        (Plugins.findPlugin(VersionEyePlugin.NAME) as VersionEyePlugin).addConfiguration(this, config)
    }
}
