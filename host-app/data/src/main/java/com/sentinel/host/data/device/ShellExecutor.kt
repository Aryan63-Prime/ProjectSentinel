package com.sentinel.host.data.device

import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellExecutor @Inject constructor() {

    fun execute(command: String): Map<String, Any> {
        return try {
            val isRooted = checkRootAvailable()
            val execCmd = if (isRooted && !command.startsWith("su ")) {
                arrayOf("su", "-c", command)
            } else {
                arrayOf("sh", "-c", command)
            }

            val process = Runtime.getRuntime().exec(execCmd)
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
                "isRooted" to isRooted,
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

    private fun checkRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
