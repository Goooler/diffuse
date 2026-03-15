package com.jakewharton.diffuse.report

import com.jakewharton.diffuse.io.SizeFormat

interface Report {
  fun write(appendable: Appendable)

  interface Factory {
    fun toTextReport(
      summaryOnly: Boolean = false,
      sizeFormat: SizeFormat = SizeFormat.Binary,
    ): Report

    fun toHtmlReport(
      summaryOnly: Boolean = false,
      sizeFormat: SizeFormat = SizeFormat.Binary,
    ): Report {
      TODO("Implement HTML reporting")
    }
  }
}
