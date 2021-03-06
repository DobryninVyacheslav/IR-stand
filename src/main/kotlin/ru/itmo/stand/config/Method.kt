package ru.itmo.stand.config

import ru.itmo.stand.index.model.DocumentBm25
import ru.itmo.stand.index.model.DocumentSnrm

enum class Method(val indexName: String) {
    BM25(DocumentBm25.DOCUMENT_BM25),
    SNRM(DocumentSnrm.DOCUMENT_SNRM);
}
