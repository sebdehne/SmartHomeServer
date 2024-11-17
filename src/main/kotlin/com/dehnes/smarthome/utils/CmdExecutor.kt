package com.dehnes.smarthome.utils

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

object CmdExecutor {

    fun run(
        args: List<String>,
        timeout: Duration = Duration.ofSeconds(1),
        onResult: (exitCode: Int, out: ByteArray, err: ByteArray) -> Unit
    ) {
        val process = ProcessBuilder(args).start()

        val output = process.inputStream.readAllBytes()
        val error = process.errorStream.readAllBytes()

        check(
            process.waitFor(
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
            )
        ) { "Timeout while waiting for process to exit" }

        onResult(
            process.exitValue(),
            output,
            error
        )
    }

    fun runToString(args: List<String>, timeout: Duration = Duration.ofSeconds(1)): String {
        var out = ""
        var err = ""
        var exit = -1
        run(
            args,
            timeout
        ) { e, o, er ->
            exit = e
            out = o.toString(StandardCharsets.UTF_8)
            err = er.toString(StandardCharsets.UTF_8)
        }

        check(exit == 0) { "Exit code was $exit error=$err out=$out" }
        return out
    }

    fun streamToCommand(
        data: ByteArray,
        cmd: List<String>,
        timeout: Duration = Duration.ofSeconds(1),
    ): String {
        val process = ProcessBuilder(cmd).start()

        process.outputStream.write(data)
        process.outputStream.flush()
        process.outputStream.close()

        val output = process.inputStream.readAllBytes()
        val error = process.errorStream.readAllBytes()

        check(process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))

        val exitValue = process.exitValue()
        val errorString = error.toString(StandardCharsets.UTF_8)
        check(exitValue == 0) { "Invalid exitcode $exitValue for cmd $cmd, error=$errorString" }
        check(errorString.isEmpty()) { "Error output is not empty" }


        return output.toString(StandardCharsets.UTF_8)
    }

}