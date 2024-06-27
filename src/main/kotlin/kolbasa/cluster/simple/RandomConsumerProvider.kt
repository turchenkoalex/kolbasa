package kolbasa.cluster.simple

import kolbasa.consumer.ConsumerOptions
import kolbasa.consumer.datasource.Consumer
import kolbasa.consumer.datasource.ConsumerInterceptor
import kolbasa.consumer.datasource.DatabaseConsumer
import kolbasa.queue.Queue
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.random.Random

class RandomConsumerProvider(
    private val dataSources: List<DataSource>,
    private val consumerOptions: ConsumerOptions = ConsumerOptions(),
    private val interceptors: List<ConsumerInterceptor<*, *>> = emptyList()
) : ConsumerProvider {

    private val consumers: MutableMap<Queue<*, *>, List<Consumer<*, *>>> = ConcurrentHashMap()

    init {
        check(dataSources.isNotEmpty()) {
            "You have to provide at least one DataSource"
        }
    }

    override fun <Data, Meta : Any> consumer(queue: Queue<Data, Meta>): Consumer<Data, Meta> {
        val queueConsumers = consumers.computeIfAbsent(queue) {
            generateConsumers(queue)
        }

        @Suppress("UNCHECKED_CAST")
        return queueConsumers.random() as Consumer<Data, Meta>
    }

    override fun <Data, Meta : Any> consumer(queue: Queue<Data, Meta>, shard: Int): Consumer<Data, Meta> {
        val queueConsumers = consumers.computeIfAbsent(queue) {
            generateConsumers(queue)
        }

        @Suppress("UNCHECKED_CAST")
        return if (Random.nextInt(RANDOM_CONSUMER_PROBABILITY) == 0) {
            queueConsumers.random()
        } else {
            val index = shard % queueConsumers.size
            queueConsumers[index]
        } as Consumer<Data, Meta>
    }

    private fun <Data, Meta : Any> generateConsumers(queue: Queue<Data, Meta>): List<Consumer<Data, Meta>> {
        return dataSources.map { dataSource ->
            val castedIntr = if (interceptors.isEmpty()) {
                emptyList()
            } else {
                // ugly hack for a few weeks
                interceptors as List<ConsumerInterceptor<Data, Meta>>
            }

            DatabaseConsumer(dataSource, queue, consumerOptions, castedIntr)
        }
    }

    internal companion object {
        const val RANDOM_CONSUMER_PROBABILITY = 20  // 20 means 1/20
    }

}
