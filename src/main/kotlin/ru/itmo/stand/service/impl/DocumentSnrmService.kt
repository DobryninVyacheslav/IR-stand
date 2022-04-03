package ru.itmo.stand.service.impl

import edu.stanford.nlp.pipeline.StanfordCoreNLP
import java.nio.IntBuffer
import java.nio.file.Files
import java.nio.file.Paths
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import ru.itmo.stand.config.Method
import ru.itmo.stand.model.DocumentSnrm
import ru.itmo.stand.repository.DocumentSnrmRepository
import ru.itmo.stand.service.DocumentService

@Service
class DocumentSnrmService(
    private val documentSnrmRepository: DocumentSnrmRepository,
    private val stanfordCoreNlp: StanfordCoreNLP,
) : DocumentService {

    override val method: Method
        get() = Method.SNRM

    override fun find(id: String): String? = documentSnrmRepository.findByIdOrNull(id)?.content

    override fun search(query: String): List<String> {
        val processedQuery = preprocess(query)
        return documentSnrmRepository.findByRepresentation(processedQuery)
            .map { it.id ?: throwDocIdNotFoundEx() }
    }

    override fun save(content: String): String {
        val representation = preprocess(content)
        return documentSnrmRepository.save(
            DocumentSnrm(content = content, representation = representation)
        ).id ?: throwDocIdNotFoundEx()
    }

    override fun saveInBatch(contents: List<String>): List<String> {
        TODO("Not yet implemented")
    }

    override fun getFootprint(): String? {
        TODO("Not yet implemented")
    }

    override fun preprocess(content: String): String {
        val modelPath = "src/main/resources/models/snrm/frozen"
        val b = SavedModelBundle.load(modelPath, "serve")
        // create session
        val sess = b.session()

        // load termToId dictionary
        val termToId = mutableMapOf("UNKNOWN" to 0)
        var id = 1
        Files.lines(Paths.get("src/main/resources/data/tokens.txt")).forEach { termToId[it] = id++ }

        // load stopwords
        val stopwords = Files.lines(Paths.get("src/main/resources/data/stopwords.txt")).toList().toSet()

        // tokenization
        val tokens = stanfordCoreNlp.processToCoreDocument(content)
            .tokens()
            .map { it.lemma().lowercase() }

        // form term id list
        val termIds = tokens.filter { !stopwords.contains(it) }
            .map { if (termToId.containsKey(it)) termToId[it]!! else termToId["UNKNOWN"]!! }
            .toMutableList()
        println("Test: $termIds")

        // fill until max doc length or trim for it
        val maxDocLength = 103
        for (i in 1..(maxDocLength - termIds.size)) termIds.add(0)
        val preparedTermIds: MutableList<Int> = termIds.subList(0, maxDocLength)

        // create tensor
        val x = Tensor.create(
            longArrayOf(maxDocLength.toLong()),
            IntBuffer.wrap(preparedTermIds.toIntArray())
        )

        // inference
        val y = sess.runner()
            .feed("Placeholder_4", x)
            .fetch("Mean_5")
            .run()[0]

        // print shape
        println(y.shape().contentToString())

        // print representation
        val initArray = Array(1) { FloatArray(5000) }
//        val representation = Array(1) { Array(1) { Array(50) { FloatArray(5000) } } }
//        println(y.copyTo(representation).contentDeepToString())

        return y.copyTo(initArray)[0]
//            .mapIndexed { index, fl -> Pair(index, fl) }
            .filter { it != 0.0f }
            .joinToString(" ")
    }
}
