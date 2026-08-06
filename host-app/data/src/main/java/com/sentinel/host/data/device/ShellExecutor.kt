package com.sentinel.host.data.device

import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellExecutor @Inject constructor() {

    fun execute(command: String): Map<String, Any> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            val exitCode = process.waitFor()

            mapOf(
                "command" to command,
                "exitCode" to exitCode,
                "output" to output.toString().ifEmpty { errorOutput.toString() }.ifEmpty { "Success (no output)" }
            )
        } catch (e: Exception) {
            mapOf(
                "command" to command,
                "exitCode" to -1,
                "output" to "Execution error: ${e.localizedMessage}"
            )
        }
    }

    private fun processErrorStream(process: Process): java.io.InputStream {
        return process.errorStream
    }
}
