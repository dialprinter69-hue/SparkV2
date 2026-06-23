package com.example.sparkv2

object SparkConstants {
    val SPARK_PACKAGES = setOf(
        "com.walmart.sparkdriver",
        "com.sparkdelivery",
        "co.com.spark",
    )

    fun isSparkPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName in SPARK_PACKAGES) return true
        val p = packageName.lowercase()
        return p.contains("spark") &&
            (p.contains("walmart") || p.contains("driver") || p.contains("deliver"))
    }
}
