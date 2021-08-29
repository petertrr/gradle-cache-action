/*
 * Copyright 2020 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.burrunan.gradle.proxy

import actions.exec.exec
import actions.glob.removeFiles
import com.github.burrunan.gradle.cache.CacheService
import com.github.burrunan.test.runTest
import com.github.burrunan.wrappers.nodejs.mkdir
import fs2.promises.writeFile
import kotlinx.serialization.json.encodeToDynamic
import kotlinx.serialization.json.Json
import process
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class CacheProxyTest {
    // Emulates Azure Cache Backend for @actions/cache
    val cacheService = CacheService()

    // Implements Gradle HTTP Build Cache via @actions/cache
    val cacheProxy = CacheProxy(0)

    @Test
    fun abc() = runTest {
        val z = mapOf("a" to 4, "b" to 6)
        println("json: " + JSON.stringify(Json.encodeToDynamic(z)))
    }

    @Test
    fun cacheProxyWorks() = runTest {
        val dir = "remote_cache_test"
        mkdir(dir)
        cacheService {
            cacheProxy {
                val outputFile = "build/out.txt"
                removeFiles(listOf("$dir/$outputFile"))
                writeFile(
                    "$dir/settings.gradle",
                    """
                        rootProject.name = 'sample'
                        buildCache {
                            local {
                                // Only remote cache should be used
                                enabled = false
                            }
                            remote(HttpBuildCache) {
                                url = '${process.env["GHA_CACHE_URL"]}'
                                push = true
                            }
                        }
                    """.trimIndent(),
                )
                writeFile(
                    "$dir/build.gradle",
                    """
                        tasks.create('props', WriteProperties) {
                          outputFile = file("$outputFile")
                          property("hello", "world")
                        }
                        tasks.create('props2', WriteProperties) {
                          outputFile = file("${outputFile}2")
                          property("hello", "world2")
                        }
                    """.trimIndent(),
                )
                writeFile(
                    "$dir/gradle.properties",
                    """
                    org.gradle.caching=true
                    #org.gradle.caching.debug=true
                    """.trimIndent(),
                )

                val out = exec("gradle", "props", "-i", "--build-cache", captureOutput = true) {
                    cwd = dir
                    silent = true
                }
                if (out.exitCode != 0) {
                    fail("Unable to execute :props task: ${out.stdout}")
                }
                assertTrue("1 actionable task: 1 executed" in out.stdout, out.stdout)

                removeFiles(listOf("$dir/$outputFile"))
                val out2 = exec("gradle", "props", "-i", "--build-cache", captureOutput = true) {
                    cwd = dir
                    silent = true
                }
                if (out.exitCode != 0) {
                    fail("Unable to execute :props task: ${out.stdout}")
                }
                assertTrue("1 actionable task: 1 from cache" in out2.stdout, out2.stdout)
            }
        }
    }
}
