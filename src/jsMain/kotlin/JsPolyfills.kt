external fun require(name: String): dynamic
external val __dirname: dynamic

val fs = require("fs")
val path = require("path");

fun main() {
    val path = path.join(
            __dirname,
            "..\\..\\..\\..",
            "processedResources",
            "js",
            "main",
            "test.txt"
    )
    println(fs.readFileSync(path, "utf8"))
}