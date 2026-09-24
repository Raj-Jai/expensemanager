package com.naveenapps.expensemanager.feature.transaction.import.parser

import com.naveenapps.expensemanager.core.model.ParsedTransaction

fun interface StatementParser {
    fun parse(text: String): List<ParsedTransaction>
}
